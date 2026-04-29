"""Sync jobs from the Spring Boot API into the ML service.

Usage:
  python scripts/sync_jobs_from_api.py --api http://127.0.0.1:8080 --ml http://127.0.0.1:8000 --train-reranker

This script fetches /api/jobs from the Java app, transforms each Job into the expected
job dict format, and posts them to the ML service /upload_jobs endpoint.
"""
import argparse
import requests
import sys


def fetch_jobs(api_url):
    url = api_url.rstrip('/') + '/api/jobs'
    resp = requests.get(url, timeout=10)
    resp.raise_for_status()
    return resp.json()


def transform_job(j):
    # Map Java Job fields to keys expected by ml/upload_jobs (case-insensitive)
    return {
        'job_id': j.get('id') or j.get('jobId') or None,
        'Job Title': j.get('title') or '',
        'Company': j.get('company') or '',
        'Location': j.get('location') or '',
        'Experience Level': j.get('experience') or j.get('experienceLevel') or '',
        'Salary': j.get('salary') or '',
        'Industry': j.get('jobType') or '',
        'Required Skills': j.get('skills') or j.get('skills') or '',
    }


def upload_jobs(ml_url, jobs, train_reranker=False):
    url = ml_url.rstrip('/') + '/upload_jobs'
    payload = {'jobs': jobs, 'train_reranker': bool(train_reranker)}
    resp = requests.post(url, json=payload, timeout=60)
    resp.raise_for_status()
    return resp.json()


def main():
    p = argparse.ArgumentParser()
    p.add_argument('--api', default='http://127.0.0.1:8080', help='Base URL of Spring Boot API')
    p.add_argument('--ml', default='http://127.0.0.1:8000', help='Base URL of ML service')
    p.add_argument('--train-reranker', action='store_true', help='Ask ML service to train a light reranker (optional)')
    args = p.parse_args()

    try:
        print(f'Fetching jobs from {args.api}/api/jobs')
        jobs = fetch_jobs(args.api)
    except Exception as e:
        print('Failed to fetch jobs from API:', e)
        sys.exit(2)

    if not isinstance(jobs, list):
        print('Unexpected response from API: expected a list of jobs')
        sys.exit(3)

    transformed = [transform_job(j) for j in jobs]
    print(f'Fetched {len(transformed)} jobs; uploading to ML at {args.ml}/upload_jobs')

    try:
        resp = upload_jobs(args.ml, transformed, train_reranker=args.train_reranker)
        print('ML upload response:', resp)
    except Exception as e:
        print('Failed to upload jobs to ML:', e)
        sys.exit(4)


if __name__ == '__main__':
    main()
