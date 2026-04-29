"""Retrain reranker from serving artifacts (ml/artifacts/jobs.parquet + job_emb.npy).

This script builds a synthetic training set from actual DB jobs and trains a LogisticRegression
or LGBMClassifier depending on availability. It saves reranker.joblib (sklearn) and optionally
reranker.pt (PyTorch) to ml/artifacts.
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
RERANKER_PT = os.path.join(ARTIFACTS, 'reranker.pt')

try:
    import lightgbm as lgb
    LGB_AVAILABLE = True
except Exception:
    LGB_AVAILABLE = False

try:
    import torch
    import torch.nn as nn
    import torch.optim as optim
    TORCH_AVAILABLE = True
except Exception:
    TORCH_AVAILABLE = False

if __name__ == '__main__':
    # Prefer serving artifacts, but fall back to training artifacts if serving set is too small
    use_train = False
    if not os.path.exists(JOB_PARQUET) or not os.path.exists(JOB_EMB):
        use_train = True
    else:
        df_tmp = pd.read_parquet(JOB_PARQUET)
        if len(df_tmp) < 50:
            print('Serving artifacts too small (found', len(df_tmp), 'jobs). Falling back to training artifacts for retraining.')
            use_train = True

    if use_train:
        JOB_PARQUET = os.path.join(ARTIFACTS, 'train_jobs.parquet')
        JOB_EMB = os.path.join(ARTIFACTS, 'train_job_emb.npy')
        if not os.path.exists(JOB_PARQUET) or not os.path.exists(JOB_EMB):
            raise SystemExit('Training artifacts not found. Run ml/build_artifacts.py first.')

    df = pd.read_parquet(JOB_PARQUET)
    embs = np.load(JOB_EMB)
    print('Loaded', len(df), 'jobs and embeddings shape', embs.shape)

    # Build small synthetic training set by sampling user profiles from job skills
    X_rows = []
    y = []
    for idx, row in df.sample(min(2000, len(df))).iterrows():
        skills = '' if pd.isna(row.get('Required Skills','')) else str(row.get('Required Skills',''))
        if not skills:
            continue
        user_emb = embs[idx]
        cand_idx = np.random.choice(len(df), size=min(50, len(df)), replace=False)
        for j in cand_idx:
            job_emb = embs[j]
            cos = float(np.dot(user_emb, job_emb) / (np.linalg.norm(user_emb) * np.linalg.norm(job_emb) + 1e-9))
            job_skills = '' if pd.isna(df.iloc[j].get('Required Skills','')) else str(df.iloc[j].get('Required Skills',''))
            set_a = set([s.strip().lower() for s in skills.split(',') if s.strip()])
            set_b = set([s.strip().lower() for s in job_skills.split(',') if s.strip()])
            overlap = len(set_a & set_b) / max(1, len(set_b)) if len(set_b)>0 else 0.0
            X_rows.append({'embed_cos': cos, 'skill_overlap': overlap})
            y.append(1 if (cos > 0.6 or overlap > 0.5) else 0)

    if len(y) < 50:
        raise SystemExit('Not enough samples to train reranker')

    X = pd.DataFrame(X_rows)
    X_train, X_val, y_train, y_val = train_test_split(X, y, test_size=0.2, random_state=42)

    # Prefer LightGBM if available
    if LGB_AVAILABLE:
        try:
            from lightgbm import LGBMClassifier
            print('Training LightGBM...')
            clf = LGBMClassifier(n_estimators=200)
            try:
                clf.fit(X_train, y_train, eval_set=[(X_val, y_val)], early_stopping_rounds=20, verbose=False)
            except TypeError:
                clf.fit(X_train, y_train)
            y_pred = clf.predict_proba(X_val)[:,1]
            print('Val AUC:', roc_auc_score(y_val, y_pred))
            joblib.dump(clf, RERANKER_PATH)
            print('Saved', RERANKER_PATH)
        except Exception as e:
            print('LightGBM training failed, falling back to LogisticRegression', e)

    if not LGB_AVAILABLE:
        print('Training LogisticRegression...')
        clf = LogisticRegression(max_iter=200)
        clf.fit(X_train, y_train)
        y_pred = clf.predict_proba(X_val)[:,1]
        print('Val AUC:', roc_auc_score(y_val, y_pred))
        joblib.dump(clf, RERANKER_PATH)
        print('Saved', RERANKER_PATH)

    # Optional: train small PyTorch MLP
    if TORCH_AVAILABLE:
        try:
            import numpy as _np
            class SimpleMLP(nn.Module):
                def __init__(self, in_dim):
                    super().__init__()
                    self.net = nn.Sequential(
                        nn.Linear(in_dim,32), nn.ReLU(), nn.Linear(32,16), nn.ReLU(), nn.Linear(16,1), nn.Sigmoid()
                    )
                def forward(self,x):
                    return self.net(x)
            X_all = _np.vstack([X_train.values, X_val.values])
            y_all = np.concatenate([y_train, y_val])
            model = SimpleMLP(X_train.shape[1])
            device = torch.device('cuda' if torch.cuda.is_available() else 'cpu')
            model.to(device)
            criterion = nn.BCELoss()
            optimizer = optim.Adam(model.parameters(), lr=1e-3)
            X_tensor = torch.tensor(X_all, dtype=torch.float32).to(device)
            y_tensor = torch.tensor(y_all.reshape(-1,1), dtype=torch.float32).to(device)
            dataset = torch.utils.data.TensorDataset(X_tensor, y_tensor)
            loader = torch.utils.data.DataLoader(dataset, batch_size=256, shuffle=True)
            for epoch in range(10):
                model.train()
                total=0.0
                for xb,yb in loader:
                    optimizer.zero_grad()
                    preds = model(xb)
                    loss = criterion(preds, yb)
                    loss.backward()
                    optimizer.step()
                    total += loss.item()*xb.size(0)
                print('Epoch', epoch+1, 'loss', total/len(dataset))
            torch.save(model.state_dict(), RERANKER_PT)
            print('Saved', RERANKER_PT)
        except Exception as e:
            print('PyTorch training failed', e)