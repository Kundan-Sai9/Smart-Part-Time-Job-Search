import os
import pandas as pd
import numpy as np
from typing import Dict, Any, List
from fastapi import HTTPException
from api.schemas import UploadJobsRequest
from core import state
from services.recommender import _build_job_text

def _refresh_vectorstore_from_df(df: pd.DataFrame):
    if not state.LANGCHAIN_RAG_AVAILABLE:
        return

    try:
        if state.hf_embeddings is None:
            state.hf_embeddings = state.HuggingFaceEmbeddings(model_name=state.EMB_MODEL_NAME)

        texts = df['job_text'].fillna('').astype(str).tolist()
        metadatas = []
        for _, row in df.iterrows():
            metadatas.append({
                'job_id': int(row.get('job_id', 0)),
                'title': str(row.get('Job Title', '')),
                'company': str(row.get('Company', '')),
                'location': str(row.get('Location', '')),
                'skills': str(row.get('Required Skills', ''))
            })

        state.vectorstore = state.LCFAISS.from_texts(texts, state.hf_embeddings, metadatas=metadatas)
        state.vectorstore.save_local(state.FAISS_INDEX_DIR)
        print('Saved FAISS vector index to', state.FAISS_INDEX_DIR)
    except Exception as e:
        print('Failed to build/save FAISS vector index:', e)

def _normalize_job_payload(j: Dict[str, Any], i: int) -> Dict[str, Any]:
    def col(k):
        for candidate in [k, k.replace(' ', '_'), k.replace(' ', ''), k.replace(' ', '').replace('_', '')]:
            if candidate in j:
                return j[candidate]
        for key in j.keys():
            if key.lower().replace(' ', '') == k.lower().replace(' ', ''):
                return j[key]
        return ''

    row = {
        'Job Title': col('Job Title') or col('title') or '',
        'Company': col('Company') or col('company') or '',
        'Location': col('Location') or col('location') or '',
        'Experience Level': col('Experience Level') or col('experience') or '',
        'Salary': col('Salary') or col('salary') or '',
        'Industry': col('Industry') or col('industry') or '',
        'Required Skills': col('Required Skills') or col('skills') or '',
        'job_id': int(col('job_id') or i)
    }
    row['job_text'] = _build_job_text(row)
    return row

def upload_jobs(payload: UploadJobsRequest):
    if state.embedder is None:
        raise HTTPException(status_code=503, detail='Embedder not loaded')

    jobs_list = payload.jobs
    if not isinstance(jobs_list, list) or len(jobs_list) == 0:
        raise HTTPException(status_code=400, detail='jobs must be a non-empty list')

    with state.upload_lock:
        rows = [_normalize_job_payload(j, i) for i, j in enumerate(jobs_list)]
        df = pd.DataFrame(rows)

        try:
            texts = df['job_text'].tolist()
            embs = state.embedder.encode(texts, show_progress_bar=False, convert_to_numpy=True)
        except Exception as e:
            raise HTTPException(status_code=500, detail=f'Embedding computation failed: {e}')

        try:
            os.makedirs(state.MODEL_DIR, exist_ok=True)
            df.to_parquet(state.JOB_META_PATH, index=False)
            np.save(state.JOB_EMB_PATH, embs)
            _refresh_vectorstore_from_df(df)
        except Exception as e:
            raise HTTPException(status_code=500, detail=f'Failed to save artifacts: {e}')

        state.jobs = df
        state.job_embeddings = embs

        if payload.train_reranker:
            try:
                from sklearn.model_selection import train_test_split
                from sklearn.linear_model import LogisticRegression
                import joblib

                feature_rows = []
                labels = []
                for idx, row in df.sample(min(500, len(df))).iterrows():
                    skills = str(row.get('Required Skills', ''))
                    if not skills:
                        continue
                    user_emb = state.embedder.encode([skills], convert_to_numpy=True)[0]
                    cand_idx = np.random.choice(len(df), size=min(20, len(df)), replace=False)
                    for j in cand_idx:
                        job_emb = embs[j]
                        cos = float(np.dot(user_emb, job_emb) / (np.linalg.norm(user_emb) * np.linalg.norm(job_emb) + 1e-9))
                        job_skills = str(df.iloc[j].get('Required Skills', ''))
                        set_a = set([s.strip().lower() for s in skills.split(',') if s.strip()])
                        set_b = set([s.strip().lower() for s in job_skills.split(',') if s.strip()])
                        overlap = len(set_a & set_b) / max(1, len(set_b)) if len(set_b) > 0 else 0.0
                        feature_rows.append({'embed_cos': cos, 'skill_overlap': overlap})
                        labels.append(1 if (cos > 0.6 or overlap > 0.5) else 0)

                if len(labels) >= 50:
                    X = pd.DataFrame(feature_rows)
                    y = np.array(labels)
                    X_train, X_val, y_train, y_val = train_test_split(X, y, test_size=0.2, random_state=42)
                    clf = LogisticRegression(max_iter=200)
                    clf.fit(X_train, y_train)
                    joblib.dump(clf, state.RERANKER_PATH)
                    state.reranker = clf
            except Exception as e:
                print('Reranker training failed during upload:', e)

    return {'status': 'ok', 'jobs_count': len(df), 'model_version': 'uploaded-v2-rag'}
