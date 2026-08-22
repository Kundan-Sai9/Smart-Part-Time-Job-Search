from fastapi import APIRouter
from api.schemas import (
    RecommendRequest,
    LlmRecommendationRequest,
    ProfileRequest,
    UploadJobsRequest
)
from services import recommender
from services import agent
from services import data_ingestion

router = APIRouter()

@router.post('/recommend')
def recommend(req: RecommendRequest):
    return recommender.get_recommendations(req)

@router.post('/llm/job-recommendations')
def llm_job_recommendations(req: LlmRecommendationRequest):
    return agent.llm_job_recommendations(req)

@router.post('/profile/score')
def score_profile(req: ProfileRequest):
    return agent.score_profile(req)

@router.post('/profile/analyze-agent')
def analyze_profile_agent(req: ProfileRequest):
    return agent.analyze_profile_agent(req)

@router.post('/upload_jobs')
def upload_jobs(payload: UploadJobsRequest):
    return data_ingestion.upload_jobs(payload)
