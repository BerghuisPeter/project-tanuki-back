import base64
import json
import pytest
from fastapi.testclient import TestClient
from main import app

client = TestClient(app)


def test_health_check():
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json() == {"status": "ok"}


def test_root():
    response = client.get("/")
    assert response.status_code == 200
    assert "message" in response.json()


def test_handle_enrichment_invalid_format():
    response = client.post("/enrich", json={"invalid": "payload"})
    assert response.status_code == 400


def test_handle_enrichment_valid(monkeypatch):
    # Mock GoshuinClient and processor external calls so test doesn't require live services or keys
    async def mock_process(self, request):
        return None

    from app.services.enrichment_processor import EnrichmentProcessor
    monkeypatch.setattr(EnrichmentProcessor, "process", mock_process)

    payload = {
        "resourceType": "TEMPLE",
        "resourceId": "123e4567-e89b-12d3-a456-426614174000",
        "originalLocale": "ja"
    }
    encoded_data = base64.b64encode(json.dumps(payload).encode("utf-8")).decode("utf-8")
    envelope = {
        "message": {
            "data": encoded_data,
            "messageId": "test-msg-id"
        }
    }

    response = client.post("/enrich", json=envelope)
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "complete"
    assert data["id"] == payload["resourceId"]


@pytest.mark.asyncio
async def test_ai_service_fallback_without_client():
    from app.services.ai_service import AIService
    service = AIService()
    service.client = None
    description = await service.generate_description("Kinkaku-ji", category="Temple")
    assert "Kinkaku-ji is a historic temple known for its serene grounds" in description


@pytest.mark.asyncio
async def test_ai_service_generate_content_success():
    from app.services.ai_service import AIService
    from unittest.mock import MagicMock

    service = AIService()
    mock_client = MagicMock()
    mock_response = MagicMock()
    mock_response.text = "Generated description of Kinkaku-ji"
    mock_client.models.generate_content.return_value = mock_response
    service.client = mock_client

    description = await service.generate_description("Kinkaku-ji", category="Temple", context="Golden Pavilion")
    assert description == "Generated description of Kinkaku-ji"
    mock_client.models.generate_content.assert_called_once_with(
        model="gemini-1.5-flash",
        contents="Write a comprehensive, culturally rich, and engaging description in Japanese for the Temple named 'Kinkaku-ji'. Additional context: Golden Pavilion. Describe its history, architectural significance, and spiritual atmosphere in 2-3 paragraphs."
    )


@pytest.mark.asyncio
async def test_ai_service_generate_content_error_fallback():
    from app.services.ai_service import AIService
    from unittest.mock import MagicMock

    service = AIService()
    mock_client = MagicMock()
    mock_client.models.generate_content.side_effect = RuntimeError("API rate limit")
    service.client = mock_client

    description = await service.generate_description("Kinkaku-ji", category="Temple")
    assert "Kinkaku-ji is a historic temple known for its serene grounds" in description


def test_test_maps_endpoint_with_name_and_city(monkeypatch):
    from unittest.mock import MagicMock
    from app.routers.enrichment import processor

    mock_client = MagicMock()
    mock_client.geocode.return_value = [
        {
            "formatted_address": "Japan, Tokyo, Senso-ji",
            "geometry": {"location": {"lat": 35.7148, "lng": 139.7967}},
            "place_id": "ChIJb8u7...",
            "types": ["point_of_interest", "establishment"]
        }
    ]
    monkeypatch.setattr(processor.geocoding_service, "_get_client", lambda: mock_client)

    response = client.get("/maps/test?name=Senso-ji&city=Tokyo")
    assert response.status_code == 200
    data = response.json()
    assert isinstance(data, list)
    assert len(data) > 0
    assert "Senso-ji" in data[0]["formatted_address"]


def test_test_maps_endpoint_with_name_only(monkeypatch):
    from unittest.mock import MagicMock
    from app.routers.enrichment import processor

    mock_client = MagicMock()
    mock_client.geocode.return_value = [
        {
            "formatted_address": "Japan, Kyoto, Senso-ji",
            "geometry": {"location": {"lat": 35.7148, "lng": 139.7967}},
            "place_id": "ChIJb8u7...",
            "types": ["point_of_interest", "establishment"]
        }
    ]
    monkeypatch.setattr(processor.geocoding_service, "_get_client", lambda: mock_client)

    response = client.get("/maps/test?name=Senso-ji")
    assert response.status_code == 200
    data = response.json()
    assert isinstance(data, list)
    assert len(data) > 0
    assert "Senso-ji" in data[0]["formatted_address"]


def test_test_maps_endpoint_aliases(monkeypatch):
    from unittest.mock import MagicMock
    from app.routers.enrichment import processor

    mock_client = MagicMock()
    mock_client.geocode.return_value = [
        {
            "formatted_address": "Japan, Kyoto, Kinkaku-ji",
            "geometry": {"location": {"lat": 35.0394, "lng": 135.7292}},
            "place_id": "ChIJb8u7...",
            "types": ["point_of_interest", "establishment"]
        }
    ]
    monkeypatch.setattr(processor.geocoding_service, "_get_client", lambda: mock_client)

    response_test_maps = client.get("/test/maps?name=Kinkaku-ji&city=Kyoto")
    assert response_test_maps.status_code == 200
    assert "Kinkaku-ji" in response_test_maps.json()[0]["formatted_address"]


def test_test_maps_endpoint_missing_name():
    response = client.get("/maps/test")
    assert response.status_code == 422


def test_test_maps_endpoint_api_error(monkeypatch):
    from unittest.mock import MagicMock
    from app.routers.enrichment import processor

    mock_client = MagicMock()
    mock_client.geocode.side_effect = RuntimeError("REQUEST_DENIED")
    monkeypatch.setattr(processor.geocoding_service, "_get_client", lambda: mock_client)

    response = client.get("/maps/test?name=Senso-ji")
    assert response.status_code == 502
    assert "REQUEST_DENIED" in response.json()["detail"]


def test_test_maps_endpoint_post(monkeypatch):
    from unittest.mock import MagicMock
    from app.routers.enrichment import processor

    mock_client = MagicMock()
    mock_client.geocode.return_value = [
        {
            "formatted_address": "Japan, Tokyo, Meiji Jingu",
            "geometry": {"location": {"lat": 35.6764, "lng": 139.6993}},
            "place_id": "ChIJb8u7...",
            "types": ["point_of_interest", "establishment"]
        }
    ]
    monkeypatch.setattr(processor.geocoding_service, "_get_client", lambda: mock_client)

    response = client.post("/maps/test", json={"name": "Meiji Jingu", "city": "Tokyo"})
    assert response.status_code == 200
    data = response.json()
    assert isinstance(data, list)
    assert len(data) > 0
    assert "Meiji Jingu" in data[0]["formatted_address"]


@pytest.mark.asyncio
async def test_geocoding_service_geocode_raw_with_client():
    from app.services.geocoding_service import GeocodingService
    from unittest.mock import MagicMock

    service = GeocodingService()
    mock_client = MagicMock()
    mock_client.geocode.return_value = [
        {
            "formatted_address": "2-3-1 Asakusa, Taito City, Tokyo, Japan",
            "geometry": {
                "location": {"lat": 35.7148, "lng": 139.7967}
            },
            "place_id": "ChIJb8u7..."
        }
    ]
    service.client = mock_client

    result = await service.geocode_raw("Senso-ji Tokyo")
    assert result == mock_client.geocode.return_value
    mock_client.geocode.assert_called_once_with("Senso-ji Tokyo")
