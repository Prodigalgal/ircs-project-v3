from __future__ import annotations

from typing import Any
from uuid import UUID

from pydantic import BaseModel, Field


class ModelTextItem(BaseModel):
    id: UUID
    text: str = Field(default="", max_length=4096)


class ModelBatchRequest(BaseModel):
    items: list[ModelTextItem] = Field(default_factory=list)


class ModelTextResult(BaseModel):
    id: UUID
    adultScore: float
    label: str
    raw: dict[str, Any] = Field(default_factory=dict)


class ModelBatchResponse(BaseModel):
    items: list[ModelTextResult] = Field(default_factory=list)
