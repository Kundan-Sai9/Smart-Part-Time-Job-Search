from fastapi import FastAPI
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

app.include_router(router)
