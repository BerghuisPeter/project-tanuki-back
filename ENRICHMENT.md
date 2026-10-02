# Goshuin Enrichment

## Concept Overview

A goshuin finder app stores records about Japanese temple seals (goshuins). Each goshuin belongs to a temple. Both have
internationalized (i18n) variants for name and description.

The core idea is that a user — or an automated bot — should be able to create a new goshuin record from as little
information as possible (e.g. just a name and a city), and have the system automatically complete the missing data in
the background.

### The Enrichment Concept

When a new goshuin (or temple) record is created, it is unlikely to be complete. It may be missing:

- The temple's full address
- Geographic coordinates (latitude / longitude)
- Translated versions of the name and description
- A description altogether

Rather than requiring the creator to provide all of this upfront, the system queues the record for asynchronous
enrichment. The creator provides the minimum viable information; the system does the rest.

### Lifecycle

Every goshuin tracks an enrichment status:

- **Pending** — just inserted, not yet picked up for enrichment
- **Processing** — enrichment has started
- **Complete** — all data has been successfully filled in
- **Failed** — enrichment was attempted but could not complete

This status, combined with nullable fields on the record itself, gives a precise picture of what is still missing and
why.

### What the Enrichment Does

Enrichment runs a series of steps against the incomplete record:

- **Geocoding** — from the temple name and city, resolve the full address and geographic coordinates using a location
  data source
- **Translation** — translate existing name and description fields into missing language variants
- **Description generation** — if no description exists in any language, generate one based on what is known about the
  temple

Each step is independent and idempotent: if a field is already populated, that step is skipped. This means enrichment
can safely be re-run on a record without overwriting data that was already correctly filled in.

### How it is Triggered

When a record is inserted, the system immediately:

1. Sets the enrichment status to **Processing**
2. Emits an event carrying the record's identifier

A separate background worker receives that event and performs the enrichment steps. On success it marks the record
**Complete** and records a timestamp. On failure it records the error and marks it **Failed** so it can be retried or
inspected.

The event-based trigger decouples the insert from the enrichment work, keeping the user-facing write fast and
non-blocking.

### Resilience

Because the enrichment runs asynchronously and external services can fail:

- Failed or timed-out enrichment attempts are retried automatically via the event system's built-in retry mechanism
- A periodic background sweep checks for records that have been in **Processing** state longer than expected and
  re-queues them, catching any cases where the event was lost or the worker crashed mid-run
- The number of attempts is tracked so runaway retries can be detected

### Data Ownership

The enrichment worker owns a specific set of fields — coordinates, address, translations — and only writes to those. It
never touches fields managed by the main application. This keeps the boundary clear even though the worker shares the
same underlying data store.

Because a temple can have multiple goshuins, geocoding is skipped if the temple already has coordinates — avoiding
redundant external calls when a second goshuin is added to a temple that was already enriched.

### Goal

The end result is a self-completing dataset. Contributors add the minimum they know. The system fills in the rest
quietly in the background, leaving the record in a fully enriched state — translated, located, and described — ready to
be displayed to users in any supported language.

---

## Implementation Proposal

Based on the current architecture (Spring Boot, Java 25, GCP) and the requirements for Geocoding, Translation, and
LLM-based generation, here is the recommended implementation strategy.

### 1. Messaging & Decoupling: GCP Pub/Sub

Using **GCP Pub/Sub** is the superior choice for this architecture.

- **Why**: It ensures the "Create" operation is non-blocking and fast. It provides built-in retry logic, exponential
  backoff, and Dead Letter Queues (DLQ) for resilient handling of external API failures (Google Maps, Gemini).
- **Topic**: `enrichment-requests`
- **Message Format**:
  ```json
  {
    "jobId": "01990c1e-...",
    "resourceType": "TEMPLE",
    "resourceId": "3490-afde-..."
  }
  ```
  The message deliberately carries identifiers only. The worker reads `originalLocale` and everything else from the
  internal API (section 5).

### 2. The Worker: Separate Python Service (Cloud Run)

I recommend a **separate Python-based service** (or Cloud Function) for the enrichment logic.

- **Why Python?**:
    - **AI/ML Native**: Python has first-class support for **Vertex AI / Gemini SDKs** and **Google Maps SDK**.
    - **Lightweight**: Enrichment is a background, bursty task. Python Cloud Functions or Cloud Run instances are more
      cost-effective and faster to scale for this purpose than a full Spring Boot worker.
- **Responsibility**: The worker is the "owner" of the address, coordinates, and translation fields.

### 3. Data Model & Lifecycle

Update the `TempleEntity` and `GoshuinEntity` to track the enrichment lifecycle.

**New Enrichment Metadata Model:**
Both entities and DTOs use a common `EnrichmentMetadata` structure (mapped to `enrichment` field/property):

- `status`: `PENDING`, `PROCESSING`, `COMPLETE`, `FAILED`.
- `error`: A string to store the last failure reason.
- `attempts`: Counter to prevent infinite loops.
- `lastAt`: Timestamp for auditing.

Each enrichment request is also tracked as a row in the `enrichment_job` table (`jobId`, `resourceType`, `resourceId`,
`status`, `error`, `attempts`, `completedAt`). This is intentional: the sweep (section 6) can find stuck work by
querying one small table instead of scanning `temple` and `goshuin`. The job row and the `enrichment` metadata on the
resource must always agree, so every status change is applied to both **in the same transaction**. The status endpoints
in section 5 do this.

### 4. Integration Flow

1. **Creation**: `goshuin-service` receives a minimal `TempleCreate` (Name + City).
2. **Persistence**: The record is saved with `enrichment_status = PENDING`.
3. **Trigger**: An `AFTER_COMMIT` event publishes a message to Pub/Sub.
4. **Enrichment**:
    - **Geocoding**: Python worker uses `googlemaps` to find Lat/Lng and full address.
    - **Translation**: Uses `google-cloud-translate` to fill missing `TempleI18nEntity` records.
    - **Description**: Uses **Gemini (Vertex AI)** to generate a summary based on the temple name and affiliation.
5. **Callback**: The worker calls the secured **Internal API** in `goshuin-service` (see section 5) to read the
   record, report progress, and write the enriched fields and final status.

### 5. Internal API (`goshuin-service`)

The Pub/Sub message only carries identifiers, so the worker reads everything else from `goshuin-service` and writes its
results back through the same internal API. These endpoints are not part of the public `openapi.yaml` contract for
frontend clients; they live under `/internal/v1` and are only called by the enrichment worker. The path convention is
`/internal/v1/{temples|goshuins}/{id}`, which is what `resourceType` (`TEMPLE` / `GOSHUIN`) maps to.

| Method  | Path                                    | Purpose                                                                             |
|:--------|:----------------------------------------|:------------------------------------------------------------------------------------|
| `GET`   | `/internal/v1/temples/{id}`             | Read a temple with its i18n variants and enrichment metadata                        |
| `GET`   | `/internal/v1/goshuins/{id}`            | Read a goshuin with its i18n variants, enrichment metadata and its temple           |
| `PATCH` | `/internal/v1/temples/{id}/enrichment`  | Report progress and write enriched temple data (address, coordinates, translations) |
| `PATCH` | `/internal/v1/goshuins/{id}/enrichment` | Report progress and write enriched goshuin data (translations)                      |

Common behaviour:

- `{id}` is the resource UUID from the Pub/Sub message (`resourceId`).
- Unknown IDs return `404`; malformed bodies return `400`. Errors use the shared `ErrorResponse`.
- Locale keys are locale codes such as `ja` and `en`, matching the `locale` column of the i18n tables.

#### 5.1 `GET /internal/v1/temples/{id}`

Returns everything the worker needs to decide which steps to run. A step is skipped when its target is already
populated.

```json
{
  "id": "3490-afde-...",
  "originalLocale": "ja",
  "latitude": null,
  "longitude": null,
  "affiliationType": "Shingon",
  "enrichment": {
    "status": "PROCESSING",
    "error": null,
    "attempts": 1,
    "lastAt": "2026-01-01T10:00:00Z"
  },
  "translations": {
    "ja": {
      "name": "浅草寺",
      "region": null,
      "postalCode": null,
      "prefecture": null,
      "city": "東京",
      "address": null,
      "description": null
    }
  }
}
```

| Worker step            | Reads                                                                                                         |
|:-----------------------|:--------------------------------------------------------------------------------------------------------------|
| Geocoding              | `latitude` / `longitude` (skip if both set); `translations[originalLocale].name` + `.city` to build the query |
| Translation            | Which locales exist in `translations`, and the source text in `translations[originalLocale]`                  |
| Description generation | `translations[*].description` (skip if any locale has one); `name` and `affiliationType` as prompt input      |

#### 5.2 `GET /internal/v1/goshuins/{id}`

Same shape as the temple read, with goshuin fields and the parent temple embedded. Embedding the temple lets the
worker check whether the temple already has coordinates, and use its name for description generation, without a second
call.

```json
{
  "id": "7710-c2d1-...",
  "originalLocale": "ja",
  "format": "KAMIGATA",
  "enrichment": { "status": "PROCESSING", "error": null, "attempts": 1, "lastAt": "2026-01-01T10:00:00Z" },
  "translations": {
    "ja": { "label": "本尊", "description": null }
  },
  "temple": {
    "id": "3490-afde-...",
    "latitude": 35.7148,
    "longitude": 139.7967,
    "affiliationType": "Shingon",
    "translations": {
      "ja": { "name": "浅草寺", "city": "東京", "address": "東京都台東区浅草2-3-1", "description": "..." }
    }
  }
}
```

The embedded `temple` is read-only context. The worker never writes temple data through the goshuin endpoints.

#### 5.3 `PATCH /internal/v1/temples/{id}/enrichment`

A single endpoint carries the whole lifecycle for a temple. The worker calls it up to twice per attempt: once to
mark the record `PROCESSING`, and once with the result (`COMPLETE` or `FAILED`).

Request body (every field is optional):

```json
{
  "jobId": "01990c1e-...",
  "status": "COMPLETE",
  "error": null,
  "latitude": 35.7148,
  "longitude": 139.7967,
  "translations": {
    "ja": { "address": "東京都台東区浅草2-3-1", "description": "..." },
    "en": { "name": "Senso-ji", "city": "Tokyo", "address": "2-3-1 Asakusa, Taito City, Tokyo", "description": "..." }
  }
}
```

| Field                    | Rule                                                                                                                                                                                              |
|:-------------------------|:--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `jobId`                  | Optional. When present, the matching `enrichment_job` row gets the same status, error, attempts and completion time, in the same transaction as the temple's `enrichment` metadata                |
| `status`                 | Optional. `PROCESSING`, `COMPLETE` or `FAILED`. `PENDING` is set only by `goshuin-service`. When omitted, the data is written and the lifecycle (`enrichment` metadata and job) is left untouched |
| `error`                  | Stored as `enrichment.error` on `FAILED`. Cleared on `PROCESSING` and `COMPLETE`                                                                                                                  |
| `latitude` / `longitude` | Written only if the temple's coordinates are currently null. Must be sent together                                                                                                                |
| `translations`           | Map of `locale` to `{name, region, postalCode, prefecture, city, address, description}`                                                                                                           |

Write semantics are **fill-only** so the endpoint stays idempotent and never overwrites human-entered data:

- Coordinates and every i18n column are written only where the stored value is currently null or blank.
- A locale row that does not exist is created. `name` is `NOT NULL` on `temple_i18n`, so a new locale row requires
  `name`; otherwise the request is rejected with `400`.
- Existing non-null values are left untouched, even if the request carries a different value.
- Replaying the same request produces the same stored state.

Status handling:

| Incoming status | Effect on `enrichment`                                                                    |
|:----------------|:------------------------------------------------------------------------------------------|
| `PROCESSING`    | `status = PROCESSING`, `attempts += 1`, `lastAt = now`, `error = null`                    |
| `COMPLETE`      | `status = COMPLETE`, `lastAt = now`, `error = null`                                       |
| `FAILED`        | `status = FAILED`, `lastAt = now`, `error` stored (field data in the body is not applied) |

Response: `204 No Content`.

#### 5.4 `PATCH /internal/v1/goshuins/{id}/enrichment`

Same lifecycle fields (`jobId`, `status`, `error`) and the same status handling as the temple endpoint, including the
dual update of the goshuin's `enrichment` metadata and its `enrichment_job` row. Goshuins have no coordinates or
address, so the data part is translations only:

```json
{
  "jobId": "01990c1e-...",
  "status": "COMPLETE",
  "translations": {
    "en": { "label": "Main Hall", "description": "..." }
  }
}
```

- `translations` maps `locale` to `{label, description}`, with the same fill-only rules. `label` is the only
  i18n column besides `description`, and a new locale row is created when it does not exist.
- Geocoding results for a goshuin belong to its **temple**. The worker writes them with
  `PATCH /internal/v1/temples/{templeId}/enrichment`, using the `temple.id` returned by the goshuin read. It does this
  only when the temple's coordinates are still null, which is how a second goshuin on an already-enriched temple
  avoids a redundant geocoding call. That request has no `status`, so it writes the data without touching the temple's
  own enrichment lifecycle.
- Response: `204 No Content`.

#### 5.5 Authentication

These endpoints read and change data without a user context, so only the enrichment worker may call them.
`goshuin-service` has to stay publicly invokable on Cloud Run because the frontend calls it, and Cloud Run IAM applies
to a whole service, not to a path. `/internal/**` is therefore protected inside the application:

- The worker runs on Cloud Run as its own service account and sends a Google-signed **OIDC identity token** with every
  call: `Authorization: Bearer <id token>`, with the audience set to the `goshuin-service` URL.
- `goshuin-service` validates that token on `/internal/**` only: signature (Google's public keys), issuer
  (`https://accounts.google.com`), expiry, audience (its own URL), and that `email` is verified and equals the
  configured worker service account. Anything else gets `401`/`403`.
- The user-facing JWT filter is not involved, so a user token, even an admin one, can never call `/internal/**`.
- No shared secret is distributed. Configuration is the expected audience and the allowed caller email.
- The worker's service account and its identity are provisioned in the separate Terraform repository, not here.
- Locally the check is disabled with a profile flag and the worker calls `goshuin-service` without a token.

Configuration:

| Where               | Setting                                                                          | Effect                                                                                                                           |
|:--------------------|:---------------------------------------------------------------------------------|:---------------------------------------------------------------------------------------------------------------------------------|
| `goshuin-service`   | `app.internal-auth.enabled` (default `true`, `false` in `application-local.yml`) | `false` leaves `/internal/**` open. When `true` the service refuses to start without the two settings below                      |
| `goshuin-service`   | `INTERNAL_AUTH_AUDIENCE`                                                         | Expected `aud`: the `goshuin-service` URL, identical to the worker's `GOSHUIN_SERVICE_URL` (no trailing slash)                   |
| `goshuin-service`   | `INTERNAL_AUTH_ALLOWED_EMAIL`                                                    | Email of the worker's service account                                                                                            |
| `enrichment-worker` | `GOSHUIN_AUTH_ENABLED` (default `false`)                                         | `true` sends the identity token (fetched from the Cloud Run metadata server, `format=full` so `email` is included) on every call |

Responses: `401` for a missing, malformed, expired or wrongly signed token or wrong audience; `403` for a valid token
of any other caller than the allowed service account.

The same mechanism applies to the other service-to-service `/internal/**` endpoints (for example `profile-service`).

#### 5.6 Not an endpoint: the sweep

The resilience sweep in section 6 runs inside `goshuin-service` and re-publishes events directly, so it needs no
internal endpoint.

### 6. Resilience: The Background Sweep

Even with Pub/Sub, messages can occasionally be lost or stuck in `PROCESSING` if a worker crashes.

- **Scheduled Task**: Implement a `@Scheduled` job in `goshuin-service` that queries for:
    - Records in `PROCESSING` state for $> 15$ minutes.
    - Records in `PENDING` state older than 5 minutes.
- **Action**: It re-publishes the Pub/Sub event to ensure no record is left in an incomplete state.

### 7. API Changes (Minimal Info)

The current `openapi.yaml` requires many fields (e.g., `longitude`, `latitude`, `address`) for `TempleCreate`. These
should be marked as **optional** (nullable) to support the "minimal information" goal, allowing the enrichment process
to fill them in later.

---

### Comparison of Alternatives

| Feature                | Async Spring Service  | Separate Python Worker          |
|:-----------------------|:----------------------|:--------------------------------|
| **LLM/AI Integration** | Harder (Limited SDKs) | **Best-in-class (Native SDKs)** |
| **Maintenance**        | Single Language Repo  | Two Languages                   |
| **Resource Usage**     | Shares JVM Memory     | **Isolated & Scalable**         |
| **Failure Isolation**  | Can impact main app   | **Completely Isolated**         |

**Conclusion**: Use **Pub/Sub** for communication and a **Python worker** for the specialized enrichment tasks to
leverage the best tools for AI and data processing.
