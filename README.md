# CloudSpring Intelligence RAG API

A production-grade **Retrieval-Augmented Generation (RAG)** API built with:

- **Spring Boot 4.0.5** + **Spring AI 2.0.0**
- **Amazon Bedrock** — Titan Embed Text v2 (embeddings) + Claude 3 Haiku (generation)
- **Qdrant** — vector store for semantic document retrieval
- **Resilience4j** — retry, circuit breaker, and bulkhead
- **Caffeine** — embedding result cache

---

## Architecture