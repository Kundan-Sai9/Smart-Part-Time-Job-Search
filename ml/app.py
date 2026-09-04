from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from api.routes import router
from core import state
import contextlib

@contextlib.asynccontextmanager
async def lifespan(app: FastAPI):
    # Load artifacts on startup
    state.load_artifacts()
    yield
    # Cleanup on shutdown if necessary
    pass

app = FastAPI(title='Job Recommendation Prototype', lifespan=lifespan)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(router)
