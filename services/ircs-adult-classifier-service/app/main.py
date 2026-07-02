from __future__ import annotations

from contextlib import asynccontextmanager

from fastapi import Depends, FastAPI

from .classifier import AdultTextClassifier
from .schemas import ModelBatchRequest, ModelBatchResponse
from .security import assert_internal_access
from .settings import settings

classifier: AdultTextClassifier | None = None


@asynccontextmanager
async def lifespan(app: FastAPI):
    global classifier
    classifier = AdultTextClassifier(settings)
    yield


app = FastAPI(title="IRCS Adult Classifier Service", version="1.0.0", lifespan=lifespan)


@app.get("/health")
def health() -> dict[str, object]:
    return {"status": "UP", "model": settings.model_name, "ready": classifier is not None}


@app.get("/actuator/health/liveness")
def liveness() -> dict[str, str]:
    return {"status": "UP"}


@app.get("/actuator/health/readiness")
def readiness() -> dict[str, object]:
    return health()


@app.post(
    "/internal/v1/adult-classifier:classify",
    response_model=ModelBatchResponse,
    dependencies=[Depends(assert_internal_access)],
)
def classify(request: ModelBatchRequest) -> ModelBatchResponse:
    if classifier is None:
        return ModelBatchResponse(items=[])
    return ModelBatchResponse(items=classifier.classify(request.items))
