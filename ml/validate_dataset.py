import pandas as pd
import os
import sys

DATA_DIR = os.path.join(os.path.dirname(__file__), 'data')
JOB_CSV = os.path.join(DATA_DIR, 'jobs.csv')

REQUIRED_COLUMNS = ['Job Title','Company','Location','Experience Level','Salary','Industry','Required Skills']


def validate():
    if not os.path.exists(JOB_CSV):
        print(f"Dataset not found. Please place your CSV at: {JOB_CSV}")
        sys.exit(2)

    try:
        df = pd.read_csv(JOB_CSV)
    except Exception as e:
        print('Failed to read CSV:', e)
        sys.exit(2)

    print('Columns found:', list(df.columns))
    missing = [c for c in REQUIRED_COLUMNS if c not in df.columns]
    if missing:
        print('Missing required columns:', missing)
        print('Please ensure the CSV has the exact column names (case-sensitive).')
        sys.exit(2)

    print('Row count:', len(df))
    print('\nSample rows:')
    print(df.head(5).to_string(index=False))

    # Basic checks
    if df['Required Skills'].isnull().any():
        print('\nWarning: Some jobs have empty Required Skills.')

    print('\nValidation OK. You can now run the notebook ml/prototype_notebook.ipynb to build embeddings.')


if __name__ == '__main__':
    validate()
