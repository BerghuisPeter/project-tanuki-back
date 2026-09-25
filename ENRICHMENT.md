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
    "resourceType": "TEMPLE",
    "resourceId": "3490-afde-...",
    "originalLocale": "ja"
  }
  ```

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

### 4. Integration Flow

1. **Creation**: `goshuin-service` receives a minimal `TempleCreate` (Name + City).
2. **Persistence**: The record is saved with `enrichment_status = PENDING`.
3. **Trigger**: An `AFTER_COMMIT` event publishes a message to Pub/Sub.
4. **Enrichment**:
    - **Geocoding**: Python worker uses `googlemaps` to find Lat/Lng and full address.
    - **Translation**: Uses `google-cloud-translate` to fill missing `TempleI18nEntity` records.
    - **Description**: Uses **Gemini (Vertex AI)** to generate a summary based on the temple name and affiliation.
5. **Callback**: The worker calls a secured **Internal API** in `goshuin-service` (e.g.,
   `PATCH /internal/v1/temples/{id}/enrichment`) to update the fields and set status to `COMPLETE`.

### 5. Resilience: The Background Sweep

Even with Pub/Sub, messages can occasionally be lost or stuck in `PROCESSING` if a worker crashes.

- **Scheduled Task**: Implement a `@Scheduled` job in `goshuin-service` that queries for:
    - Records in `PROCESSING` state for $> 15$ minutes.
    - Records in `PENDING` state older than 5 minutes.
- **Action**: It re-publishes the Pub/Sub event to ensure no record is left in an incomplete state.

### 6. API Changes (Minimal Info)

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
