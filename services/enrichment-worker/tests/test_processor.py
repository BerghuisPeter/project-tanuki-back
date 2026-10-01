import pytest
from app.models.enrichment import EnrichmentRequest, GeocodingResult
from app.services.enrichment_processor import EnrichmentProcessor
from unittest.mock import AsyncMock

TEMPLE_ID = "3490-afde"
GOSHUIN_ID = "7710-c2d1"
JOB_ID = "job-1"


def make_processor(resource, geocode_result=None):
    processor = EnrichmentProcessor()
    processor.goshuin_client.get_resource = AsyncMock(return_value=resource)
    processor.goshuin_client.send_enrichment_callback = AsyncMock(return_value=True)
    processor.geocoding_service.geocode = AsyncMock(
        return_value=geocode_result or GeocodingResult(address="2-3-1 Asakusa", latitude=35.7, longitude=139.8))
    processor.ai_service.generate_description = AsyncMock(return_value="generated")
    processor.translation_service.translate_text = AsyncMock(
        side_effect=lambda text, target, source: f"{target}:{text}")
    return processor


def sent(processor):
    """(resource_type, resource_id, body) for every callback, in order."""
    return [(call.args[0], call.args[1], call.args[2].model_dump(exclude_none=True))
            for call in processor.goshuin_client.send_enrichment_callback.call_args_list]


def temple(latitude=None, longitude=None, translations=None):
    return {
        "id": TEMPLE_ID,
        "originalLocale": "ja",
        "latitude": latitude,
        "longitude": longitude,
        "affiliationType": "Shingon",
        "translations": translations or {"ja": {"name": "浅草寺", "city": "東京", "description": None}},
    }


@pytest.mark.asyncio
async def test_temple_is_geocoded_described_translated_and_completed():
    processor = make_processor(temple())

    await processor.process(EnrichmentRequest(jobId=JOB_ID, resourceType="TEMPLE", resourceId=TEMPLE_ID))

    processing, complete = sent(processor)
    assert processing == ("TEMPLE", TEMPLE_ID, {"jobId": JOB_ID, "status": "PROCESSING"})
    assert complete[:2] == ("TEMPLE", TEMPLE_ID)
    body = complete[2]
    assert body["status"] == "COMPLETE"
    assert (body["latitude"], body["longitude"]) == (35.7, 139.8)
    assert body["translations"]["ja"] == {"address": "2-3-1 Asakusa", "description": "generated"}
    assert body["translations"]["en"] == {"name": "en:浅草寺", "description": "en:generated"}
    processor.geocoding_service.geocode.assert_awaited_once_with("浅草寺 東京")
    processor.ai_service.generate_description.assert_awaited_once_with(
        "浅草寺", category="Temple", context="Shingon", locale="ja")


@pytest.mark.asyncio
async def test_temple_with_coordinates_and_description_skips_geocoding_and_generation():
    full = temple(35.7, 139.8, {
        "ja": {"name": "浅草寺", "city": "東京", "description": "説明"},
        "en": {"name": "Senso-ji", "description": "Description"},
    })
    processor = make_processor(full)

    await processor.process(EnrichmentRequest(jobId=JOB_ID, resourceType="TEMPLE", resourceId=TEMPLE_ID))

    processor.geocoding_service.geocode.assert_not_awaited()
    processor.ai_service.generate_description.assert_not_awaited()
    processor.translation_service.translate_text.assert_not_awaited()
    assert sent(processor)[-1][2] == {"jobId": JOB_ID, "status": "COMPLETE"}


@pytest.mark.asyncio
async def test_goshuin_writes_geocoding_to_temple_without_status_and_translations_to_goshuin():
    goshuin = {
        "id": GOSHUIN_ID,
        "originalLocale": "ja",
        "translations": {"ja": {"label": "本尊", "description": "説明"}},
        "temple": temple(),
    }
    processor = make_processor(goshuin)

    await processor.process(EnrichmentRequest(jobId=JOB_ID, resourceType="GOSHUIN", resourceId=GOSHUIN_ID))

    processing, temple_write, complete = sent(processor)
    assert processing == ("GOSHUIN", GOSHUIN_ID, {"jobId": JOB_ID, "status": "PROCESSING"})
    assert temple_write == ("TEMPLE", TEMPLE_ID, {
        "latitude": 35.7, "longitude": 139.8, "translations": {"ja": {"address": "2-3-1 Asakusa"}}})
    assert complete == ("GOSHUIN", GOSHUIN_ID, {
        "jobId": JOB_ID, "status": "COMPLETE",
        "translations": {"en": {"label": "en:本尊", "description": "en:説明"}}})
    processor.ai_service.generate_description.assert_not_awaited()


@pytest.mark.asyncio
async def test_goshuin_whose_temple_has_coordinates_does_not_geocode():
    goshuin = {
        "id": GOSHUIN_ID,
        "originalLocale": "ja",
        "translations": {"ja": {"label": "本尊", "description": "説明"}},
        "temple": temple(35.7, 139.8),
    }
    processor = make_processor(goshuin)

    await processor.process(EnrichmentRequest(jobId=JOB_ID, resourceType="GOSHUIN", resourceId=GOSHUIN_ID))

    processor.geocoding_service.geocode.assert_not_awaited()
    assert all(resource_type == "GOSHUIN" for resource_type, _, _ in sent(processor))


@pytest.mark.asyncio
async def test_failure_reports_failed_status_and_reraises():
    processor = make_processor(temple())
    processor.geocoding_service.geocode = AsyncMock(side_effect=RuntimeError("REQUEST_DENIED"))

    with pytest.raises(RuntimeError, match="REQUEST_DENIED"):
        await processor.process(EnrichmentRequest(jobId=JOB_ID, resourceType="TEMPLE", resourceId=TEMPLE_ID))

    assert sent(processor)[-1] == (
        "TEMPLE", TEMPLE_ID, {"jobId": JOB_ID, "status": "FAILED", "error": "REQUEST_DENIED"})


@pytest.mark.asyncio
async def test_unreadable_resource_reports_failed_instead_of_inventing_data():
    processor = make_processor(None)

    with pytest.raises(RuntimeError, match="could not be read"):
        await processor.process(EnrichmentRequest(jobId=JOB_ID, resourceType="TEMPLE", resourceId=TEMPLE_ID))

    processor.geocoding_service.geocode.assert_not_awaited()
    assert sent(processor)[-1][2]["status"] == "FAILED"
