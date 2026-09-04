"""Retrain reranker from job dataframe & embeddings.

This script/module builds synthetic training samples from jobs data and trains a LogisticRegression
or LightGBM reranker, saving reranker.joblib to ml/artifacts.
"""
import os
import pandas as pd
import numpy as np
import joblib
from sklearn.model_selection import train_test_split
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import roc_auc_score

ROOT = os.path.dirname(__file__)
ARTIFACTS = os.path.join(ROOT, 'artifacts')
JOB_PARQUET = os.path.join(ARTIFACTS, 'jobs.parquet')
JOB_EMB = os.path.join(ARTIFACTS, 'job_emb.npy')
RERANKER_PATH = os.path.join(ARTIFACTS, 'reranker.joblib')

try:
    from lightgbm import LGBMClassifier
    LGB_AVAILABLE = True
except Exception:
    LGB_AVAILABLE = False


def train_reranker_from_df(df: pd.DataFrame, embs: np.ndarray, save_path: str = RERANKER_PATH):
    """Generates synthetic pairwise features and trains a logistic/LGBM reranker."""
    if len(df) < 5 or embs is None or len(embs) == 0:
        print('Dataset too small to train reranker.')
        return None

    X_rows = []
    y = []
    
    sample_size = min(1000, len(df))
    sample_df = df.sample(sample_size, random_state=42)

    for idx, row in sample_df.iterrows():
        skills = '' if pd.isna(row.get('Required Skills', '')) else str(row.get('Required Skills', ''))
        if not skills:
            continue
            
        pos_in_embs = df.index.get_loc(idx) if idx in df.index else 0
        user_emb = embs[pos_in_embs]
        
        cand_indices = np.random.choice(len(df), size=min(30, len(df)), replace=False)
        set_a = set([s.strip().lower() for s in skills.split(',') if s.strip()])

        for j in cand_indices:
            job_emb = embs[j]
            norm_product = (np.linalg.norm(user_emb) * np.linalg.norm(job_emb) + 1e-9)
            cos = float(np.dot(user_emb, job_emb) / norm_product)
            
            job_skills = '' if pd.isna(df.iloc[j].get('Required Skills', '')) else str(df.iloc[j].get('Required Skills', ''))
            set_b = set([s.strip().lower() for s in job_skills.split(',') if s.strip()])
            overlap = len(set_a & set_b) / max(1, len(set_b)) if len(set_b) > 0 else 0.0
            
            X_rows.append({'embed_cos': cos, 'skill_overlap': overlap})
            y.append(1 if (cos > 0.6 or overlap > 0.5) else 0)

    if len(y) < 30:
        print('Insufficient synthetic samples generated for reranker training.')
        return None

    X = pd.DataFrame(X_rows)
    y_arr = np.array(y)

    if len(np.unique(y_arr)) < 2:
        print('Synthetic labels contain only one class; skipping reranker fit.')
        return None

    X_train, X_val, y_train, y_val = train_test_split(X, y_arr, test_size=0.2, random_state=42)

    if LGB_AVAILABLE:
        try:
            clf = LGBMClassifier(n_estimators=100, random_state=42)
            clf.fit(X_train, y_train)
            clf_model = clf
        except Exception as e:
            print('LGBMClassifier fit failed, fallback to LogisticRegression:', e)
            clf_model = LogisticRegression(max_iter=200)
            clf_model.fit(X_train, y_train)
    else:
        clf_model = LogisticRegression(max_iter=200)
        clf_model.fit(X_train, y_train)

    if len(y_val) > 0 and len(np.unique(y_val)) > 1:
        try:
            probs = clf_model.predict_proba(X_val)[:, 1]
            auc = roc_auc_score(y_val, probs)
            print(f'Reranker validation AUC: {auc:.4f}')
        except Exception:
            pass

    os.makedirs(os.path.dirname(save_path), exist_ok=True)
    joblib.dump(clf_model, save_path)
    print('Saved reranker model to', save_path)
    return clf_model


if __name__ == '__main__':
    if not os.path.exists(JOB_PARQUET) or not os.path.exists(JOB_EMB):
        print('Serving artifacts (jobs.parquet, job_emb.npy) not found in ml/artifacts.')
    else:
        df_jobs = pd.read_parquet(JOB_PARQUET)
        job_embeddings = np.load(JOB_EMB)
        print(f'Loaded {len(df_jobs)} jobs for reranker training.')
        train_reranker_from_df(df_jobs, job_embeddings)