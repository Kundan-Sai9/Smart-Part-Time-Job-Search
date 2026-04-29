from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
import numpy as np
import pandas as pd
import joblib
from sentence_transformers import SentenceTransformer
from sklearn.metrics.pairwise import cosine_similarity
import os
from typing import List, Optional, Dict, Any
import threading

try:
    import torch
    import torch.nn as nn
    TORCH_AVAILABLE = True
except Exception:
    torch = None
    nn = None
    TORCH_AVAILABLE = False

# Optional LangChain imports for RAG and agentic workflows
try:
    from langchain_community.vectorstores import FAISS as LCFAISS
    from langchain_huggingface import HuggingFaceEmbeddings
    LANGCHAIN_RAG_AVAILABLE = True
except Exception:
    LCFAISS = None
    HuggingFaceEmbeddings = None
    LANGCHAIN_RAG_AVAILABLE = False

try:
    from langchain_cohere import ChatCohere
    from langchain_core.prompts import ChatPromptTemplate, PromptTemplate
    from langchain_core.output_parsers import JsonOutputParser
    from langchain_core.tools import tool
    from langchain.agents import create_react_agent, AgentExecutor
    LANGCHAIN_LLM_AVAILABLE = True
except Exception:
    ChatCohere = None
    ChatPromptTemplate = None
    PromptTemplate = None
    JsonOutputParser = None
    tool = None
    create_react_agent = None
    AgentExecutor = None
    LANGCHAIN_LLM_AVAILABLE = False

app = FastAPI(title='Job Recommendation Prototype')

MODEL_DIR = os.path.join(os.path.dirname(__file__), 'artifacts')
JOB_META_PATH = os.path.join(MODEL_DIR, 'jobs.parquet')
JOB_EMB_PATH = os.path.join(MODEL_DIR, 'job_emb.npy')
RERANKER_PATH = os.path.join(MODEL_DIR, 'reranker.joblib')
RERANKER_PT = os.path.join(MODEL_DIR, 'reranker.pt')
FAISS_INDEX_DIR = os.path.join(MODEL_DIR, 'job_index')
EMB_MODEL_NAME = 'all-MiniLM-L6-v2'

TRAIN_JOB_META_PATH = os.path.join(MODEL_DIR, 'train_jobs.parquet')
TRAIN_JOB_EMB_PATH = os.path.join(MODEL_DIR, 'train_job_emb.npy')

print('Loading artifacts...')
if not os.path.exists(MODEL_DIR):
    print('Artifacts directory not found; create ml/artifacts and run the notebook to populate it')

jobs = None
job_embeddings = None
embedder = None
reranker = None
neural_reranker = None
vectorstore = None
hf_embeddings = None

if os.path.exists(JOB_META_PATH):
    jobs = pd.read_parquet(JOB_META_PATH)
else:
    print('jobs.parquet not found')

if os.path.exists(JOB_EMB_PATH):
    job_embeddings = np.load(JOB_EMB_PATH)
else:
    print('job_emb.npy not found')

if os.path.exists(RERANKER_PATH):
    try:
        reranker = joblib.load(RERANKER_PATH)
        print('Loaded sklearn reranker')
    except Exception as e:
        print('Failed to load sklearn reranker:', e)
else:
    print('reranker.joblib not found')

try:
    embedder = SentenceTransformer(EMB_MODEL_NAME)
except Exception as e:
    print('SentenceTransformer load failed:', e)

try:
    SKILL_BOOST = float(os.environ.get('ML_SKILL_BOOST', '2.0'))
except Exception:
    SKILL_BOOST = 2.0
print('Using SKILL_BOOST =', SKILL_BOOST)

if TORCH_AVAILABLE and os.path.exists(RERANKER_PT):
    try:
        class SimpleMLP(nn.Module):
            def __init__(self, in_dim):
                super().__init__()
                self.net = nn.Sequential(
                    nn.Linear(in_dim, 32),
                    nn.ReLU(),
                    nn.Linear(32, 16),
                    nn.ReLU(),
                    nn.Linear(16, 1),
                    nn.Sigmoid()
                )

            def forward(self, x):
                return self.net(x)

        neural_reranker = RERANKER_PT
        print('PyTorch reranker found at', RERANKER_PT)
    except Exception as e:
        print('Failed to prepare PyTorch reranker loader:', e)

if LANGCHAIN_RAG_AVAILABLE:
    try:
        hf_embeddings = HuggingFaceEmbeddings(model_name=EMB_MODEL_NAME)
        if os.path.exists(FAISS_INDEX_DIR):
            vectorstore = LCFAISS.load_local(
                FAISS_INDEX_DIR,
                hf_embeddings,
                allow_dangerous_deserialization=True
            )
            print('Loaded FAISS vector index from', FAISS_INDEX_DIR)
        else:
            print('FAISS index not found at startup')
    except Exception as e:
        print('Failed to initialize FAISS vector store:', e)


class RecommendRequest(BaseModel):
    user_profile_text: Optional[str] = None
    profile: Optional[dict] = None
    query: Optional[str] = None
    top_k: int = 10


class JobItem(BaseModel):
    job_id: Optional[int]
    Job_Title: Optional[str] = ''
    Company: Optional[str] = ''
    Location: Optional[str] = ''
    Experience_Level: Optional[str] = ''
    Salary: Optional[str] = ''
    Industry: Optional[str] = ''
    Required_Skills: Optional[str] = ''


class UploadJobsRequest(BaseModel):
    jobs: List[dict]
    train_reranker: Optional[bool] = False


class LlmRecommendationRequest(BaseModel):
    user_profile: str
    jobs: List[Dict[str, Any]]
    top_k: int = 5


class ProfileRequest(BaseModel):
    name: str = ''
    bio: str = ''
    skills: str = ''
    experience: str = ''
    role: str = ''


_upload_lock = threading.Lock()


def _build_job_text(row: Dict[str, Any]) -> str:
    parts = [
        str(row.get('Job Title', '') or ''),
        str(row.get('Required Skills', '') or ''),
        str(row.get('Industry', '') or ''),
        str(row.get('Company', '') or '')
    ]
    return ' '.join([p for p in parts if p])


def _refresh_vectorstore_from_df(df: pd.DataFrame):
    global vectorstore, hf_embeddings
    if not LANGCHAIN_RAG_AVAILABLE:
        return

    try:
        if hf_embeddings is None:
            hf_embeddings = HuggingFaceEmbeddings(model_name=EMB_MODEL_NAME)

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

        vectorstore = LCFAISS.from_texts(texts, hf_embeddings, metadatas=metadatas)
        vectorstore.save_local(FAISS_INDEX_DIR)
        print('Saved FAISS vector index to', FAISS_INDEX_DIR)
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


@app.post('/recommend')
def recommend(req: RecommendRequest):
    if jobs is None or job_embeddings is None or embedder is None:
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

    user_emb = embedder.encode([profile_text], convert_to_numpy=True)
    sims = cosine_similarity(user_emb, job_embeddings)[0]

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
    if vectorstore is not None:
        try:
            docs = vectorstore.similarity_search(profile_text, k=max(req.top_k, 30))
            rag_job_ids = [int(d.metadata.get('job_id', -1)) for d in docs if d.metadata.get('job_id') is not None]
            if len(rag_job_ids) > 0:
                id_to_idx = {}
                for i, row in jobs.iterrows():
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
        for i, row in jobs.iterrows():
            job_skills = '' if pd.isna(row.get('Required Skills', '')) else str(row.get('Required Skills', ''))
            set_job = set([s.strip().lower() for s in job_skills.split(',') if s.strip()])
            if len(set_user & set_job) > 0:
                overlap_indices.append(i)
        if len(overlap_indices) > 0:
            overlap_sims = [(i, float(sims[i])) for i in overlap_indices]
            overlap_sims_sorted = sorted(overlap_sims, key=lambda x: -x[1])[:max(req.top_k, 1000)]
            top_idx = np.array([i for i, _ in overlap_sims_sorted])[:max(req.top_k, 30)]

    candidates = []
    for idx in top_idx:
        row = jobs.iloc[idx]
        job_skills = '' if pd.isna(row.get('Required Skills', '')) else str(row.get('Required Skills', ''))
        set_job = set([s.strip().lower() for s in job_skills.split(',') if s.strip()])
        overlap = len(set_user & set_job) / max(1, len(set_job)) if len(set_job) > 0 else 0.0
        embed_cos = float(sims[idx])
        candidates.append({'idx': int(idx), 'embed_cos': embed_cos, 'skill_overlap': overlap})

    if skills_provided:
        filtered = [c for c in candidates if c['skill_overlap'] > 0]
        if len(filtered) > 0:
            candidates = filtered
        candidates = sorted(candidates, key=lambda x: (-x['skill_overlap'], -x['embed_cos']))

    used_reranker = None

    if TORCH_AVAILABLE and neural_reranker and isinstance(neural_reranker, str) and os.path.exists(neural_reranker):
        try:
            class SimpleMLP(nn.Module):
                def __init__(self, in_dim):
                    super().__init__()
                    self.net = nn.Sequential(
                        nn.Linear(in_dim, 32),
                        nn.ReLU(),
                        nn.Linear(32, 16),
                        nn.ReLU(),
                        nn.Linear(16, 1),
                        nn.Sigmoid()
                    )

                def forward(self, x):
                    return self.net(x)

            model = SimpleMLP(2)
            device = torch.device('cuda' if torch.cuda.is_available() else 'cpu')
            model.load_state_dict(torch.load(neural_reranker, map_location=device))
            model.to(device)
            model.eval()

            X = np.array([[c['embed_cos'], c['skill_overlap']] for c in candidates], dtype=np.float32)
            with torch.no_grad():
                X_t = torch.tensor(X, dtype=torch.float32).to(device)
                preds = model(X_t).cpu().numpy().ravel()

            for i, c in enumerate(candidates):
                c['score'] = float(preds[i])
            candidates = sorted(candidates, key=lambda x: -x['score'])
            used_reranker = 'neural'
        except Exception as e:
            print('Neural reranker failed to load/predict:', e)

    if used_reranker is None and reranker is not None:
        X = pd.DataFrame([{'embed_cos': c['embed_cos'], 'skill_overlap': c['skill_overlap']} for c in candidates])
        try:
            scores = reranker.predict_proba(X)[:, 1] if hasattr(reranker, 'predict_proba') else reranker.predict(X)
            for i, c in enumerate(candidates):
                c['score'] = float(scores[i])
            candidates = sorted(candidates, key=lambda x: -x['score'])
            used_reranker = 'sklearn'
        except Exception as e:
            print('Reranker predict failed:', e)
            if os.path.exists(RERANKER_PATH):
                try:
                    loaded = joblib.load(RERANKER_PATH)
                    scores = loaded.predict_proba(X)[:, 1] if hasattr(loaded, 'predict_proba') else loaded.predict(X)
                    for i, c in enumerate(candidates):
                        c['score'] = float(scores[i])
                    candidates = sorted(candidates, key=lambda x: -x['score'])
                    used_reranker = 'sklearn'
                except Exception as e2:
                    print('Lazy load reranker failed:', e2)

    if used_reranker is None:
        for c in candidates:
            c['score'] = c['embed_cos']
        candidates = sorted(candidates, key=lambda x: -x['score'])

    for c in candidates:
        try:
            if c.get('skill_overlap', 0) > 0:
                c['score'] = float(c['score'] * (1.0 + (SKILL_BOOST - 1.0) * c['skill_overlap']))
        except Exception:
            pass

    candidates = sorted(candidates, key=lambda x: -x['score'])

    results = []
    for c in candidates[:req.top_k]:
        row = jobs.iloc[c['idx']]
        results.append({
            'job_id': int(row['job_id']) if 'job_id' in row else int(c['idx']),
            'title': str(row.get('Job Title', '')),
            'company': str(row.get('Company', '')),
            'location': str(row.get('Location', '')),
            'score': float(c['score'])
        })

    return {'recommendations': results, 'model_version': 'prototype-v2-rag'}


@app.post('/llm/job-recommendations')
def llm_job_recommendations(req: LlmRecommendationRequest):
    if not req.user_profile or not isinstance(req.jobs, list):
        raise HTTPException(status_code=400, detail='user_profile and jobs are required')

    top_k = max(1, min(req.top_k, 10))

    if LANGCHAIN_LLM_AVAILABLE and os.environ.get('COHERE_API_KEY'):
        try:
            prompt = ChatPromptTemplate.from_template(
                """
You are a job matching expert.
Given this user profile:
{profile}

And these job listings:
{jobs}

Return ONLY valid JSON in this format:
[
  {
    "jobId": 123,
    "matchScore": 85,
    "reasons": ["reason 1", "reason 2"]
  }
]

Rules:
- Return top {top_k} jobs.
- matchScore must be integer 0-100.
- jobId must come from the provided jobs.
"""
            )

            llm = ChatCohere(model='command-r', temperature=0.2)
            parser = JsonOutputParser()
            chain = prompt | llm | parser
            parsed = chain.invoke({
                'profile': req.user_profile,
                'jobs': req.jobs,
                'top_k': top_k
            })

            if not isinstance(parsed, list):
                raise ValueError('LLM returned non-list JSON')

            return {
                'recommendations': parsed[:top_k],
                'insights': [
                    'LangChain orchestration enabled',
                    'Cohere command-r generated structured ranking'
                ],
                'model_version': 'langchain-cohere-v1'
            }
        except Exception as e:
            print('LangChain LLM recommendations failed, using fallback:', e)

    profile_words = set([w.strip().lower() for w in req.user_profile.replace(',', ' ').split() if len(w.strip()) > 2])
    scored = []
    for j in req.jobs:
        job_id = j.get('id') if j.get('id') is not None else j.get('jobId')
        text = ' '.join([
            str(j.get('title', '')),
            str(j.get('description', '')),
            str(j.get('skills', '')),
            str(j.get('company', '')),
            str(j.get('location', ''))
        ]).lower()
        overlap = len([w for w in profile_words if w in text])
        score = min(100, int(20 + overlap * 8))
        scored.append({
            'jobId': job_id,
            'matchScore': score,
            'reasons': [
                f'Keyword overlap score: {overlap}',
                'Fallback matching used (LangChain unavailable)'
            ]
        })

    scored = sorted(scored, key=lambda x: -x['matchScore'])[:top_k]
    return {
        'recommendations': scored,
        'insights': ['Fallback deterministic scoring used'],
        'model_version': 'fallback-matcher-v1'
    }


@app.post('/profile/score')
def score_profile(req: ProfileRequest):
    score = 0
    if req.name and req.name.strip():
        score += 10
    if req.bio and req.bio.strip():
        score += 20
    if req.skills and req.skills.strip():
        score += 20
    if req.experience and req.experience.strip():
        score += 15
    score = min(score, 100)

    if score < 30:
        suggestion = 'Complete basic profile fields: name, bio, skills.'
    elif score < 60:
        suggestion = 'Add more skills and expand experience details.'
    elif score < 80:
        suggestion = 'Specify preferred job type and location for better matches.'
    else:
        suggestion = 'Profile looks good. Keep it updated.'

    return {'score': score, 'suggestion': suggestion, 'model_version': 'profile-proto-1'}


@app.post('/profile/analyze-agent')
def analyze_profile_agent(req: ProfileRequest):
    profile_text = f"Name: {req.name}\nBio: {req.bio}\nSkills: {req.skills}\nExperience: {req.experience}\nRole: {req.role}"

    if LANGCHAIN_LLM_AVAILABLE and os.environ.get('COHERE_API_KEY'):
        try:
            @tool
            def check_skill_demand(skill: str) -> str:
                """Check whether a skill is currently in-demand in uploaded job listings."""
                if jobs is None or len(jobs) == 0:
                    return 'No jobs loaded yet.'
                skill_l = skill.strip().lower()
                total = len(jobs)
                hits = 0
                for _, row in jobs.iterrows():
                    skills = str(row.get('Required Skills', '') or '').lower()
                    title = str(row.get('Job Title', '') or '').lower()
                    if skill_l and (skill_l in skills or skill_l in title):
                        hits += 1
                pct = round((hits / max(total, 1)) * 100, 1)
                return f"Skill '{skill}' appears in {hits}/{total} jobs ({pct}%)."

            @tool
            def suggest_certifications(role: str) -> str:
                """Suggest useful certifications for a given job role."""
                role_l = role.lower()
                if 'data' in role_l or 'ml' in role_l or 'ai' in role_l:
                    return 'Google Professional ML Engineer, AWS ML Specialty, Databricks ML Associate.'
                if 'cloud' in role_l or 'devops' in role_l:
                    return 'AWS Solutions Architect Associate, Azure Administrator Associate, CKAD.'
                if 'java' in role_l or 'backend' in role_l:
                    return 'Oracle Java SE Developer, AWS Developer Associate, Spring Professional.'
                if 'frontend' in role_l or 'react' in role_l:
                    return 'Meta Front-End Developer, JavaScript Algorithms and Data Structures.'
                return 'AWS Cloud Practitioner, Scrum Master, and role-specific vendor certifications.'

            llm = ChatCohere(model='command-r', temperature=0.2)
            tools = [check_skill_demand, suggest_certifications]

            react_prompt = PromptTemplate.from_template(
                """You are a career profile analysis agent.
You can use tools to check market demand and certification guidance.

Available tools:
{tools}

Tool names:
{tool_names}

Use this format:
Question: the input question
Thought: think about what to do
Action: one of [{tool_names}]
Action Input: input to the action
Observation: result of action
... (repeat Thought/Action/Action Input/Observation as needed)
Thought: I now know the final answer
Final Answer: concise actionable advice

Question: {input}
Thought:{agent_scratchpad}
"""
            )

            agent = create_react_agent(llm, tools, react_prompt)
            executor = AgentExecutor(
                agent=agent,
                tools=tools,
                verbose=False,
                handle_parsing_errors=True,
                max_iterations=4
            )

            user_question = (
                'Analyze this profile and provide: '
                '1) top improvement actions, '
                '2) in-demand skills to add, '
                '3) certifications to pursue. '\
                f'\n\nProfile:\n{profile_text}'
            )

            result = executor.invoke({'input': user_question})
            return {
                'analysis': result.get('output', ''),
                'model_version': 'langchain-agent-v1',
                'agentic': True
            }
        except Exception as e:
            print('Agentic profile analysis failed, using fallback:', e)

    score_resp = score_profile(req)
    fallback_analysis = (
        f"Profile score: {score_resp['score']}/100. "
        "Add measurable achievements to experience, include 8-12 core skills, "
        "and align your role focus with in-demand jobs in the platform."
    )
    return {
        'analysis': fallback_analysis,
        'model_version': 'fallback-profile-analyzer-v1',
        'agentic': False
    }


@app.post('/upload_jobs')
def upload_jobs(payload: UploadJobsRequest):
    if embedder is None:
        raise HTTPException(status_code=503, detail='Embedder not loaded')

    jobs_list = payload.jobs
    if not isinstance(jobs_list, list) or len(jobs_list) == 0:
        raise HTTPException(status_code=400, detail='jobs must be a non-empty list')

    with _upload_lock:
        rows = [_normalize_job_payload(j, i) for i, j in enumerate(jobs_list)]
        df = pd.DataFrame(rows)

        try:
            texts = df['job_text'].tolist()
            embs = embedder.encode(texts, show_progress_bar=False, convert_to_numpy=True)
        except Exception as e:
            raise HTTPException(status_code=500, detail=f'Embedding computation failed: {e}')

        try:
            os.makedirs(MODEL_DIR, exist_ok=True)
            df.to_parquet(JOB_META_PATH, index=False)
            np.save(JOB_EMB_PATH, embs)
            _refresh_vectorstore_from_df(df)
        except Exception as e:
            raise HTTPException(status_code=500, detail=f'Failed to save artifacts: {e}')

        global jobs, job_embeddings
        jobs = df
        job_embeddings = embs

        if payload.train_reranker:
            try:
                from sklearn.model_selection import train_test_split
                from sklearn.linear_model import LogisticRegression

                feature_rows = []
                labels = []
                for idx, row in df.sample(min(500, len(df))).iterrows():
                    skills = str(row.get('Required Skills', ''))
                    if not skills:
                        continue
                    user_emb = embedder.encode([skills], convert_to_numpy=True)[0]
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
                    joblib.dump(clf, RERANKER_PATH)
                    global reranker
                    reranker = clf
            except Exception as e:
                print('Reranker training failed during upload:', e)

    return {'status': 'ok', 'jobs_count': len(df), 'model_version': 'uploaded-v2-rag'}
