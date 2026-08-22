from pydantic import BaseModel
from typing import List, Optional, Dict, Any

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
