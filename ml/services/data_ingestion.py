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
                from retrain_reranker import train_reranker_from_df
                state.reranker = train_reranker_from_df(df, embs)
            except Exception as e:
                print('Reranker training failed during upload:', e)

    return {'status': 'ok', 'jobs_count': len(df), 'model_version': 'uploaded-v2-rag'}
