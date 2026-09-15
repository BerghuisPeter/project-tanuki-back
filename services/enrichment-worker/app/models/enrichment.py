from pydantic import BaseModel, Field
from typing import Optional, Dict, Any


class EnrichmentRequest(BaseModel):
    resourceType: str = Field(..., description="Type of resource to enrich (e.g., TEMPLE, GOSHUIN)")
    resourceId: str = Field(..., description="Unique identifier (UUID) of the resource")
    originalLocale: str = Field(default="ja", description="Original locale of the resource data")


class PubSubMessage(BaseModel):
    data: str = Field(..., description="Base64 encoded message payload")
    messageId: Optional[str] = None
    publishTime: Optional[str] = None


class PubSubEnvelope(BaseModel):
    message: PubSubMessage


class GeocodingResult(BaseModel):
    address: str
    latitude: float
    longitude: float


class EnrichmentUpdate(BaseModel):
    status: str = Field(..., description="Enrichment status: COMPLETE, FAILED, etc.")
    address: Optional[str] = None
    latitude: Optional[float] = None
    longitude: Optional[float] = None
    translations: Optional[Dict[str, Dict[str, str]]] = None
    description: Optional[str] = None
    error: Optional[str] = None
