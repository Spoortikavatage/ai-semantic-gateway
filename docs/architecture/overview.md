# Architecture Overview (V1)

## Components

- API Gateway
- Semantic Engine
- Redis Cache
- PostgreSQL (pgvector)
- Embedding Service
- LLM (Ollama)

## Goal

Build a semantic caching layer that:
- avoids repeated LLM calls
- reduces latency
- improves efficiency