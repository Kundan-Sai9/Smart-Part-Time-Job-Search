import os
import threading
import pandas as pd
import numpy as np
import joblib

try:
    from sentence_transformers import SentenceTransformer
    SENTENCE_TRANSFORMER_AVAILABLE = True
except Exception:
    SENTENCE_TRANSFORMER_AVAILABLE = False

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

MODEL_DIR = os.path.join(os.path.dirname(os.path.dirname(__file__)), 'artifacts')
JOB_META_PATH = os.path.join(MODEL_DIR, 'jobs.parquet')
JOB_EMB_PATH = os.path.join(MODEL_DIR, 'job_emb.npy')
RERANKER_PATH = os.path.join(MODEL_DIR, 'reranker.joblib')
FAISS_INDEX_DIR = os.path.join(MODEL_DIR, 'job_index')
EMB_MODEL_NAME = 'all-MiniLM-L6-v2'

# Global state
jobs = None
job_embeddings = None
embedder = None
reranker = None
vectorstore = None
hf_embeddings = None
upload_lock = threading.Lock()

try:
    SKILL_BOOST = float(os.environ.get('ML_SKILL_BOOST', '1.0'))
except Exception:
    SKILL_BOOST = 1.0

USE_RERANKER = os.environ.get('ML_USE_RERANKER', 'false').lower() == 'true'

def load_artifacts():
    global jobs, job_embeddings, embedder, reranker, vectorstore, hf_embeddings
    
    print('Loading artifacts...')
    if not os.path.exists(MODEL_DIR):
        print('Artifacts directory not found; create ml/artifacts to store models')

    if os.path.exists(JOB_META_PATH):
        jobs = pd.read_parquet(JOB_META_PATH)
    else:
        print('jobs.parquet not found')

    if os.path.exists(JOB_EMB_PATH):
        job_embeddings = np.load(JOB_EMB_PATH)
    else:
        print('job_emb.npy not found')

    if not USE_RERANKER:
        print('Reranker disabled; using retrieval similarity ranking')
    elif os.path.exists(RERANKER_PATH):
        try:
            reranker = joblib.load(RERANKER_PATH)
            print('Loaded sklearn reranker')
        except Exception as e:
            print('Failed to load sklearn reranker:', e)
    else:
        print('Reranker enabled but reranker.joblib not found; using retrieval similarity ranking')

    if SENTENCE_TRANSFORMER_AVAILABLE:
        try:
            embedder = SentenceTransformer(EMB_MODEL_NAME)
        except Exception as e:
            print('SentenceTransformer load failed:', e)

    print('Using SKILL_BOOST =', SKILL_BOOST)

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

