# AI Semantic Gateway — System Design Document

> **Distributed Semantic Caching Layer for AI Applications**
> Version: 1.0 (MVP) | Status: Draft | Owner: Platform / Infra Team

---

## 1. Problem Statement

Large Language Models (LLMs) power modern AI assistants, copilots, customer support systems, enterprise AI tools, agentic workflows, and RAG applications. However, production AI systems consistently hit three infrastructure pain points:

| Problem | Business Impact |
|---|---|
| **High LLM inference cost** | Every prompt → expensive API call (per-token billing) |
| **High response latency** | LLM round-trips take 2–8 seconds, degrading UX |
| **Redundant computation** | 60–80% of user prompts are semantically similar repeats |

**The Insight:** Traditional key-value caches (Redis `GET key`) fail here because *"How do I reset my password?"* and *"I forgot my password, help"* are **lexically different but semantically identical**. We need a cache that understands meaning, not just strings.

**Goal:** Build a gateway that sits in front of any LLM and serves semantically similar prompts from cache — cutting cost and latency by an order of magnitude.

---

## 2. Functional Requirements

### 2.1 MVP (Phase 1)

**FR-1: Prompt Ingestion**
The system accepts a user prompt over HTTP and returns either a cached response or a freshly generated LLM response — transparently to the caller.

**FR-2: Embedding Generation**
For every incoming prompt, the system generates a dense vector embedding (e.g., 768 or 1536 dimensions) using a configured embedding model.

**FR-3: Semantic Similarity Search**
The system performs an Approximate Nearest Neighbor (ANN) search over stored embeddings using cosine similarity. A configurable threshold (default: `0.90`) determines a cache hit.

**FR-4: Cache-Hit / Cache-Miss Flow**
- **Hit** (similarity ≥ threshold): return the cached response immediately.
- **Miss** (similarity < threshold): forward to LLM, return response to user, asynchronously persist `{prompt, embedding, response}`.

**FR-5: Persistence**
Persist `(prompt, embedding, response, metadata)` so future similar prompts benefit. Hot entries promoted to Redis; cold entries live in PostgreSQL + pgvector.

### 2.2 Phase 2 (Post-MVP)

| ID | Feature | Rationale |
|---|---|---|
| FR-6 | Cache hit/miss telemetry | Measure ROI and tune threshold |
| FR-7 | Multi-tenant isolation | One org's cache must not leak to another |
| FR-8 | Cache invalidation via Kafka | TTL + event-driven busting for stale answers |
| FR-9 | Stats / admin API | Operators need visibility |
| FR-10 | Pluggable embedding models | Support OpenAI, Cohere, local BGE, etc. |
| FR-11 | Pluggable LLM backends | Avoid vendor lock-in |

---

## 3. Non-Functional Requirements

### 3.1 Performance

| Metric | Target | Notes |
|---|---|---|
| Cache-hit latency (p95) | **< 200 ms** | Embedding + ANN + Redis fetch |
| Cache-miss latency (p95) | **< 3 s** | Bounded by upstream LLM |
| Embedding latency (p95) | < 80 ms | Local model or batched API |
| Throughput | 1,000+ RPS per node | Stateless app servers |

### 3.2 Scalability
- **Stateless application tier** — horizontally scale behind a load balancer.
- **Vector store** — pgvector for MVP; migrate to Qdrant / Milvus / Weaviate when row count exceeds ~10M.
- **Redis cluster** for hot-set caching; sharded by tenant hash.

### 3.3 Availability
- Target: **99.9%** for the gateway.
- **Graceful degradation**: if Redis is down → fall back to pgvector. If pgvector is down → fall back to direct LLM call (lose cache, never break the user).
- No component is a single point of failure for the request path.

### 3.4 Consistency
- **Eventual consistency** is acceptable. A user may see a fresh LLM response on request *N* and a cached one on request *N+1*; that's fine.
- Writes to the cache are **asynchronous** (post-response) — write latency must never block the user.

### 3.5 Cost Efficiency *(primary business driver)*
- Cache hit rate target: **≥ 60%** within 30 days of warm-up.
- Every cache hit avoids one LLM call → direct $ savings.
- Embedding model should be cheap or local (BGE-small, MiniLM) — avoid paying for embeddings to save on LLM.

### 3.6 Observability
- **Metrics** (Prometheus): hit rate, p50/p95/p99 latency, LLM token spend, embedding latency, error rate.
- **Tracing** (OpenTelemetry): end-to-end span per request.
- **Logging**: structured JSON, prompt-hash only (never raw prompt at INFO level — PII risk).

### 3.7 Security
- API key auth on every endpoint (MVP); JWT + tenant scoping (Phase 2).
- Strict input validation: max prompt length, content-type, rate limiting per key.
- Prompts may contain PII → encrypt at rest, hash for logs, support per-tenant deletion.

---

## 4. API Design

All endpoints are versioned under `/api/v1`. JSON over HTTPS. Auth via `Authorization: Bearer <api-key>` header (MVP).

### 4.1 `POST /api/v1/query` — Primary Endpoint

The single endpoint clients integrate against.

**Request**

```http
POST /api/v1/query
Content-Type: application/json
Authorization: Bearer sk_live_xxx
```

```json
{
  "prompt": "How do I reset my password?",
  "options": {
    "similarity_threshold": 0.90,
    "bypass_cache": false,
    "model": "gpt-4o-mini"
  }
}
```

**Response — Cache Hit (HTTP 200)**

```json
{
  "response": "To reset your password, go to Settings → Security → Reset Password...",
  "source": "cache",
  "similarity": 0.94,
  "matched_prompt_id": "8f3a2c91-...",
  "latency_ms": 142,
  "request_id": "req_01HXYZ..."
}
```

**Response — Cache Miss (HTTP 200)**

```json
{
  "response": "To reset your password, navigate to...",
  "source": "llm",
  "similarity": null,
  "model_used": "gpt-4o-mini",
  "tokens": { "prompt": 12, "completion": 84 },
  "latency_ms": 2150,
  "request_id": "req_01HXYZ..."
}
```

**Error Response (HTTP 4xx/5xx)**

```json
{
  "error": {
    "code": "PROMPT_TOO_LONG",
    "message": "Prompt exceeds 8000 character limit",
    "request_id": "req_01HXYZ..."
  }
}
```

### 4.2 `GET /api/v1/health` — Liveness Probe

```json
{
  "status": "UP",
  "components": {
    "redis": "UP",
    "postgres": "UP",
    "embedding_service": "UP",
    "llm_upstream": "UP"
  }
}
```

### 4.3 `GET /api/v1/metrics` — Cache Statistics *(Phase 2)*

```json
{
  "window": "last_24h",
  "total_requests": 12480,
  "cache_hits": 8910,
  "cache_misses": 3570,
  "hit_rate": 0.714,
  "avg_latency_ms": { "hit": 138, "miss": 2240 },
  "estimated_llm_cost_saved_usd": 47.20
}
```

### 4.4 `DELETE /api/v1/cache/{id}` — Manual Invalidation *(Phase 2)*

Delete a single cached entry by ID. Returns `204 No Content`.

### 4.5 Error Codes

| HTTP | Code | Meaning |
|---|---|---|
| 400 | `INVALID_PROMPT` | Empty or malformed prompt |
| 401 | `UNAUTHORIZED` | Missing / invalid API key |
| 413 | `PROMPT_TOO_LONG` | Exceeds size limit |
| 429 | `RATE_LIMITED` | Per-key quota exhausted |
| 502 | `LLM_UPSTREAM_ERROR` | Backend LLM failed |
| 503 | `CACHE_DEGRADED` | Cache layer unavailable, served from LLM |

---

## 5. Data Model

### 5.1 PostgreSQL + pgvector — Source of Truth

#### Table: `semantic_cache`

| Column | Type | Notes |
|---|---|---|
| `id` | `UUID` | Primary key |
| `tenant_id` | `UUID` | Multi-tenant isolation (indexed) |
| `prompt` | `TEXT` | Original normalized prompt |
| `prompt_hash` | `CHAR(64)` | SHA-256 of normalized prompt — exact-match shortcut |
| `embedding` | `VECTOR(1536)` | Dense vector from embedding model |
| `response` | `TEXT` | LLM-generated response |
| `model` | `VARCHAR(64)` | LLM that generated the response |
| `embedding_model` | `VARCHAR(64)` | Embedding model used (so we don't mix spaces) |
| `hit_count` | `BIGINT` | Times this entry served a hit (LRU/LFU signal) |
| `created_at` | `TIMESTAMPTZ` | Insert time |
| `last_hit_at` | `TIMESTAMPTZ` | Last time served from cache |
| `expires_at` | `TIMESTAMPTZ` | NULL = no expiry |

#### Indexes

```sql
-- Exact-match fast path (skip ANN if hash collides)
CREATE UNIQUE INDEX idx_cache_tenant_hash
  ON semantic_cache (tenant_id, prompt_hash);

-- Vector ANN index (HNSW preferred over IVFFlat for recall)
CREATE INDEX idx_cache_embedding_hnsw
  ON semantic_cache USING hnsw (embedding vector_cosine_ops)
  WITH (m = 16, ef_construction = 64);

-- TTL eviction scans
CREATE INDEX idx_cache_expires_at ON semantic_cache (expires_at)
  WHERE expires_at IS NOT NULL;
```

#### Table: `query_log` *(Phase 2 — analytics)*

| Column | Type | Notes |
|---|---|---|
| `id` | `UUID` | Primary key |
| `tenant_id` | `UUID` | |
| `prompt_hash` | `CHAR(64)` | Don't log raw prompts at scale |
| `source` | `ENUM('cache','llm')` | |
| `similarity` | `FLOAT` | NULL on miss |
| `latency_ms` | `INT` | |
| `tokens_used` | `INT` | NULL on hit |
| `created_at` | `TIMESTAMPTZ` | Partitioned by day |

### 5.2 Redis — Hot-Set Layer

Two key patterns:

**1. Exact-match cache** (sub-millisecond shortcut before ANN):

```
KEY:   sc:exact:{tenant_id}:{sha256(prompt)}
VALUE: { "response": "...", "cached_id": "...", "model": "..." }
TTL:   1 hour
```

**2. Hot-vector cache** *(Phase 2)*:
LFU eviction; mirrors the top-N most-hit entries from Postgres for zero-DB-roundtrip reads.

```
KEY:   sc:hot:{tenant_id}:{cached_id}
VALUE: { "response": "...", "embedding": [...], "hit_count": 42 }
TTL:   24 hours, refreshed on hit
```

### 5.3 Normalization Rules

Before hashing or embedding, prompts are normalized to maximize hit rate:

- Lowercase
- Trim + collapse internal whitespace
- Strip trailing punctuation (`?`, `.`, `!`)
- Unicode NFKC

This is intentionally conservative — aggressive normalization (stemming, stopword removal) hurts semantic search quality and is left to the embedding model.

---

## 6. Request Lifecycle (End-to-End)

```
Client
  │
  ▼
[1] POST /api/v1/query
  │
  ▼
[2] Gateway: validate + auth + rate-limit
  │
  ▼
[3] Normalize prompt → compute SHA-256
  │
  ▼
[4] Redis exact-match lookup ──HIT──► return (source: cache, < 20ms)
  │
  MISS
  ▼
[5] Embed prompt (local model or API)
  │
  ▼
[6] pgvector ANN search (top-1, cosine)
  │
  ├── similarity ≥ threshold ──► return cached response
  │                              + async: bump hit_count, populate Redis
  │
  └── similarity < threshold
        │
        ▼
[7] Call upstream LLM
        │
        ▼
[8] Return response to client (source: llm)
        │
        ▼
[9] Async: persist {prompt, embedding, response} to Postgres
                   + populate Redis exact-match key
```

Steps 9 happens **after** the response is flushed to the client — write latency never blocks the user.

---

## 7. Design Decisions & Trade-offs

| Decision | Chosen | Rejected | Why |
|---|---|---|---|
| Vector store (MVP) | **pgvector** | Pinecone, Qdrant | One less moving part; fine up to ~10M rows |
| ANN index | **HNSW** | IVFFlat | Better recall at query time; worth the build cost |
| Cache write | **Async, post-response** | Sync write-through | Never block the user on a cache write |
| Similarity metric | **Cosine** | Euclidean, dot product | Standard for normalized text embeddings |
| Threshold | **0.90 default, tunable per tenant** | Fixed global | Different domains need different recall/precision |
| Embedding model | **Local (BGE-small)** | OpenAI ada-002 | Caching to save money on a paid embedding API defeats the purpose |
| Exact-match fast path | **SHA-256 + Redis** | ANN only | Identical prompts shouldn't pay for an embedding call |

---

## 8. Out of Scope (MVP)

Explicitly **not** in v1, to keep the scope tight:

- Streaming responses (SSE / WebSocket)
- Conversation / multi-turn context caching
- Cache warming from historical logs
- Cross-tenant cache sharing
- Fine-grained RBAC
- Self-serve dashboard UI

These are tracked for v2+.

---

## 9. Open Questions

1. Should the similarity threshold be **learned per tenant** based on user-reported "bad answer" feedback?
2. How do we handle **prompts with PII** — refuse to cache, or cache with encryption at rest?
3. Cache eviction policy: **LFU**, **LRU**, or **time-decay weighted by hit_count**?
4. Do we need a **negative cache** (remember "this prompt always misses") to skip the ANN search?

---

*— End of document —*


Client
  ↓
API Gateway (Spring Boot)
  ↓
Semantic Engine
  ↓
 ┌──────────────┬──────────────┬──────────────┐
 │              │              │              │
Redis       PostgreSQL     Embedding      LLM
Cache       + pgvector     Service        (Ollama)