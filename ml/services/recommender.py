import numpy as np
import pandas as pd
from typing import Dict, Any, List
from fastapi import HTTPException
from core import state
from api.schemas import RecommendRequest

LOCATION_ALIASES = {
    'hyd': 'hyderabad',
    'hyderabad': 'hyderabad',
    'blr': 'bangalore',
    'bengaluru': 'bangalore',
    'bangalore': 'bangalore',
    'mum': 'mumbai',
    'mumbai': 'mumbai',
    'del': 'delhi',
    'new delhi': 'delhi',
    'delhi': 'delhi',
}

def normalize_location(value):
    normalized = ' '.join(str(value or '').lower().replace(',', ' ').split())
    return LOCATION_ALIASES.get(normalized, normalized)

def _build_job_text(row: Dict[str, Any]) -> str:
    parts = [
        str(row.get('Job Title', '') or ''),
        str(row.get('Required Skills', '') or ''),
        str(row.get('Industry', '') or ''),
        str(row.get('Company', '') or '')
    ]
    return ' '.join([p for p in parts if p])

def get_recommendations(req: RecommendRequest):
    if state.jobs is None or state.job_embeddings is None or state.embedder is None:
        raise HTTPException(status_code=503, detail='Model artifacts not available. Run upload/build first.')

    set_user = set()
    skills_provided = False

    profile_text = None
    if req.user_profile_text:
        profile_text = req.user_profile_text
    elif req.profile:
        p = req.profile
        parts = []
        if isinstance(p, dict):
            if p.get('summary'):
                parts.append(str(p.get('summary')))
            skills = p.get('skills')
            if isinstance(skills, list):
                parts.append(','.join(skills))
            elif isinstance(skills, str) and skills.strip():
                parts.append(skills)
        profile_text = ' '.join(parts).strip()
    elif req.query:
        profile_text = req.query

    if not profile_text or profile_text.strip() == '':
        raise HTTPException(status_code=400, detail='user_profile_text/profile/query required')

    user_emb = state.embedder.encode([profile_text], convert_to_numpy=True)
    from sklearn.metrics.pairwise import cosine_similarity
    sims = cosine_similarity(user_emb, state.job_embeddings)[0]

    if req.profile and isinstance(req.profile, dict):
        sk = req.profile.get('skills')
        if isinstance(sk, list):
            set_user = set([s.strip().lower() for s in sk if isinstance(s, str) and s.strip()])
            skills_provided = len(set_user) > 0
        elif isinstance(sk, str) and sk.strip():
            set_user = set([s.strip().lower() for s in sk.split(',') if s.strip()])
            skills_provided = len(set_user) > 0

    if not skills_provided and profile_text:
        set_user = set([s.strip().lower() for s in profile_text.split(',') if s.strip()])

    top_idx = np.argsort(-sims)[:req.top_k]

    # RAG candidate generation from FAISS (if available)
    if state.vectorstore is not None:
        try:
            docs = state.vectorstore.similarity_search(profile_text, k=max(req.top_k, 30))
            rag_job_ids = [int(d.metadata.get('job_id', -1)) for d in docs if d.metadata.get('job_id') is not None]
            if len(rag_job_ids) > 0:
                id_to_idx = {}
                for i, row in state.jobs.iterrows():
                    try:
                        id_to_idx[int(row.get('job_id', -1))] = i
                    except Exception:
                        continue
                mapped = [id_to_idx[jid] for jid in rag_job_ids if jid in id_to_idx]
                if len(mapped) > 0:
                    mapped = sorted(mapped, key=lambda i: -float(sims[i]))
                    top_idx = np.array(mapped[:max(req.top_k, 30)])
        except Exception as e:
            print('RAG retrieval fallback to embedding argsort due to:', e)

    if skills_provided:
        overlap_indices = []
        for i, row in state.jobs.iterrows():
            job_skills = '' if pd.isna(row.get('Required Skills', '')) else str(row.get('Required Skills', ''))
            set_job = set([s.strip().lower() for s in job_skills.split(',') if s.strip()])
            if len(set_user & set_job) > 0:
                overlap_indices.append(i)
        if len(overlap_indices) > 0:
            overlap_sims = [(i, float(sims[i])) for i in overlap_indices]
            overlap_sims_sorted = sorted(overlap_sims, key=lambda x: -x[1])[:max(req.top_k, 1000)]
            top_idx = np.array([i for i, _ in overlap_sims_sorted])[:max(req.top_k, 30)]

    candidates = []
    profile_terms = set([term.lower() for term in profile_text.replace(',', ' ').split() if len(term.strip()) > 2])
    preferred_location = ''
    preferred_job_type = ''
    if req.profile and isinstance(req.profile, dict):
        preferred_location = normalize_location(req.profile.get('preferred_location', ''))
        preferred_job_type = str(req.profile.get('preferred_job_type', '') or '').strip().lower()

    for idx in top_idx:
        row = state.jobs.iloc[idx]
        job_skills = '' if pd.isna(row.get('Required Skills', '')) else str(row.get('Required Skills', ''))
        set_job = set([s.strip().lower() for s in job_skills.split(',') if s.strip()])
        overlap = len(set_user & set_job) / max(1, len(set_job)) if len(set_job) > 0 else 0.0
        embed_cos = float(sims[idx])
        job_location = normalize_location(row.get('Location', ''))
        job_text = ' '.join([str(row.get(column, '') or '') for column in ['Job Title', 'Required Skills', 'Industry', 'Company', 'Location']]).lower()
        location_match = 1.0 if preferred_location and (
            preferred_location in job_location or job_location in preferred_location
        ) else 0.0
        job_type_match = 1.0 if preferred_job_type and preferred_job_type in job_text else 0.0
        matched_terms = sorted([term for term in profile_terms if term in job_text])[:12]
        candidates.append({
            'idx': int(idx),
            'embed_cos': embed_cos,
            'skill_overlap': overlap,
            'location_match': location_match,
            'job_type_match': job_type_match,
            'matched_profile_terms': matched_terms
        })

    if skills_provided:
        filtered = [c for c in candidates if c['skill_overlap'] > 0]
        if len(filtered) > 0:
            candidates = filtered

    used_reranker = None

    if state.reranker is not None and len(candidates) > 0:
        try:
            X = pd.DataFrame([{'embed_cos': c['embed_cos'], 'skill_overlap': c['skill_overlap']} for c in candidates])
            scores = state.reranker.predict_proba(X)[:, 1] if hasattr(state.reranker, 'predict_proba') else state.reranker.predict(X)
            for i, c in enumerate(candidates):
                c['score'] = float(scores[i])
            used_reranker = 'sklearn'
        except Exception as e:
            print('Reranker predict failed:', e)

    if used_reranker is None:
        for c in candidates:
            c['score'] = c['embed_cos']

    candidates = sorted(candidates, key=lambda x: -x['score'])

    results = []
    for c in candidates[:req.top_k]:
        row = state.jobs.iloc[c['idx']]
        results.append({
            'job_id': int(row['job_id']) if 'job_id' in row else int(c['idx']),
            'title': str(row.get('Job Title', '')),
            'company': str(row.get('Company', '')),
            'location': str(row.get('Location', '')),
            'score': float(max(0.0, min(1.0, c['score']))),
            'final_score': float(max(0.0, min(1.0, c['score']))),
            'retrieval_similarity': float(c['embed_cos']),
            'skill_overlap': float(c['skill_overlap']),
            'location_match': float(c['location_match']),
            'job_type_match': float(c['job_type_match']),
            'matched_profile_terms': c['matched_profile_terms'],
            'score_source': 'reranker' if used_reranker else 'retrieval_similarity'
        })

    return {
        'recommendations': results,
        'model_version': 'prototype-v3-rag-evidence',
        'reranker_enabled': bool(used_reranker),
        'reranker_training': 'synthetic_labels' if used_reranker else 'disabled_until_real_feedback'
    }
