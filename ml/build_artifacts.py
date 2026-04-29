
import os
import pandas as pd
import numpy as np
from sentence_transformers import SentenceTransformer
from sklearn.model_selection import train_test_split
try:
    import lightgbm as lgb
    LGB_AVAILABLE = True
except Exception:
    lgb = None
    LGB_AVAILABLE = False
try:
    import torch
    import torch.nn as nn
    import torch.optim as optim
    TORCH_AVAILABLE = True
except Exception:
    torch = None
    TORCH_AVAILABLE = False
import joblib
from sklearn.metrics import roc_auc_score

ROOT = os.path.dirname(__file__)
DATA_DIR = os.path.join(ROOT, 'data')
ARTIFACTS_DIR = os.path.join(ROOT, 'artifacts')
os.makedirs(DATA_DIR, exist_ok=True)
os.makedirs(ARTIFACTS_DIR, exist_ok=True)

JOB_CSV = os.path.join(DATA_DIR, 'jobs.csv')
# Training artifacts: write to training-specific files so serving artifacts (jobs.parquet/job_emb.npy)
# remain under control of the running service (via /upload_jobs that persists production DB jobs).
JOB_PARQUET = os.path.join(ARTIFACTS_DIR, 'train_jobs.parquet')
JOB_EMB = os.path.join(ARTIFACTS_DIR, 'train_job_emb.npy')
RERANKER_PATH = os.path.join(ARTIFACTS_DIR, 'reranker.joblib')
RERANKER_PT = os.path.join(ARTIFACTS_DIR, 'reranker.pt')
EMB_MODEL = 'all-MiniLM-L6-v2'

REQUIRED_COLS = ['Job Title','Company','Location','Experience Level','Salary','Industry','Required Skills']

if not os.path.exists(JOB_CSV):
    raise SystemExit(f"Place your jobs CSV at {JOB_CSV}")

print('Loading CSV...')
df = pd.read_csv(JOB_CSV)
for c in REQUIRED_COLS:
    if c not in df.columns:
        raise SystemExit(f"Missing column: {c}")

# create job_text
def safe_get(row, col):
    return '' if pd.isna(row.get(col, '')) else str(row.get(col, ''))

def make_job_text(row):
    parts = [safe_get(row,'Job Title'), safe_get(row,'Required Skills'), safe_get(row,'Industry'), safe_get(row,'Company')]
    return ' '.join([p for p in parts if p])

if 'job_id' not in df.columns:
    df['job_id'] = range(len(df))

print('Creating job_text...')
df['job_text'] = df.apply(make_job_text, axis=1)

# compute embeddings
print('Loading embedding model...')
model = SentenceTransformer(EMB_MODEL)
texts = df['job_text'].tolist()
print('Computing embeddings...')
embs = model.encode(texts, show_progress_bar=True, convert_to_numpy=True)
print('Embeddings shape:', embs.shape)

# save artifacts
print('Saving training artifacts (will not overwrite serving jobs.parquet/job_emb.npy)')
df.to_parquet(JOB_PARQUET, index=False)
np.save(JOB_EMB, embs)

# Build synthetic training data for reranker
# Create synthetic users by sampling skill subsets from jobs
print('Building synthetic training data...')
user_profiles = []
labels = []
feature_rows = []

for idx, row in df.sample(min(2000, len(df))).iterrows():
    # create a user profile from this job's skills
    skills = safe_get(row, 'Required Skills')
    if not skills:
        continue
    user_text = skills
    user_emb = model.encode([user_text], convert_to_numpy=True)[0]
    # sample candidate jobs
    cand_idx = np.random.choice(len(df), size=min(50, len(df)), replace=False)
    for j in cand_idx:
        job_emb = embs[j]
        cos = np.dot(user_emb, job_emb) / (np.linalg.norm(user_emb) * np.linalg.norm(job_emb) + 1e-9)
        skill_overlap = 0
        job_skills = safe_get(df.iloc[j], 'Required Skills')
        if job_skills:
            set_a = set([s.strip().lower() for s in skills.split(',') if s.strip()])
            set_b = set([s.strip().lower() for s in job_skills.split(',') if s.strip()])
            if len(set_b) > 0:
                skill_overlap = len(set_a & set_b) / len(set_b)
        feature_rows.append({'embed_cos': cos, 'skill_overlap': skill_overlap})
        # label positive if cos>0.6 or overlap>0.5
        labels.append(1 if (cos > 0.6 or skill_overlap > 0.5) else 0)

X = pd.DataFrame(feature_rows)
y = np.array(labels)
print('Training set size:', len(y))

if len(y) < 50:
    print('Not enough synthetic training data, skipping reranker training')
else:
    if not LGB_AVAILABLE:
        print('LightGBM not installed. Skipping reranker training. Install lightgbm to enable reranker training.')
    else:
        X_train, X_val, y_train, y_val = train_test_split(X, y, test_size=0.2, random_state=42)
        try:
            from lightgbm import LGBMClassifier
            print('Training LightGBM (sklearn API)...')
            clf = LGBMClassifier(n_estimators=200)
            # Some lightgbm builds don't accept early_stopping_rounds in fit kwargs; use try/except
            try:
                clf.fit(X_train, y_train, eval_set=[(X_val, y_val)], early_stopping_rounds=20, verbose=False)
            except TypeError:
                clf.fit(X_train, y_train)
            y_pred = clf.predict_proba(X_val)[:, 1]
            print('Validation AUC:', roc_auc_score(y_val, y_pred))
            joblib.dump(clf, RERANKER_PATH)
            print('Saved reranker to', RERANKER_PATH)
        except Exception as e:
            print('LightGBM sklearn API training failed or not available:', e)
            print('Skipping LightGBM reranker saving.')

        # Try training a small PyTorch MLP reranker if available
        if TORCH_AVAILABLE:
            try:
                print('Training PyTorch MLP reranker...')
                import numpy as _np
                X_all = _np.vstack([X_train.values, X_val.values])
                y_all = _np.concatenate([y_train, y_val])

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
                    total_loss = 0.0
                    for xb, yb in loader:
                        optimizer.zero_grad()
                        preds = model(xb)
                        loss = criterion(preds, yb)
                        loss.backward()
                        optimizer.step()
                        total_loss += loss.item() * xb.size(0)
                    avg_loss = total_loss / len(dataset)
                    print(f'Epoch {epoch+1}/10, loss={avg_loss:.4f}')

                # Evaluate AUC on validation split
                model.eval()
                with torch.no_grad():
                    X_val_t = torch.tensor(X_val.values, dtype=torch.float32).to(device)
                    y_val_t = y_val
                    preds = model(X_val_t).cpu().numpy().ravel()
                try:
                    auc = roc_auc_score(y_val, preds)
                    print('PyTorch reranker Validation AUC:', auc)
                except Exception:
                    print('Could not compute AUC for PyTorch reranker')

                # Save model state dict
                torch.save(model.state_dict(), RERANKER_PT)
                print('Saved PyTorch reranker to', RERANKER_PT)
            except Exception as e:
                print('PyTorch reranker training failed:', e)
        else:
            print('PyTorch not available, skipping neural reranker training')

print('Build complete')
