# Java / Python AI 服务架构

## 边界

```text
Client
  ↓
Java Spring Boot
  ├─ Security / JWT / RBAC / object authorization
  ├─ Ticket Service / MySQL / Redis / transactions
  └─ AI Service Client
         ↓ internal HTTP + token
      FastAPI
        ├─ Structured provider
        ├─ Reply draft
        ├─ Read-only Agent / tools
        ├─ local RAG
        └─ MCP read-only capability
```

Java owns business truth. Python produces AI capability. Python has no Java database credentials and never updates `tickets`; Java first authenticates the caller, applies role/object authorization, and sends only the authorized ticket fields needed by the AI use case.

## Java responsibilities

- `POST /api/tickets/{id}/ai-analysis` and `POST /api/tickets/{id}/ai-reply-draft` are `AGENT`/`ADMIN` endpoints.
- Java queries the ticket through `TicketService`, then calls Python using the configured base URL, internal token, connect timeout and read timeout.
- AI analysis returns advice only. Reply generation returns a human-reviewable draft only. Neither endpoint updates ticket status, priority, assignee, or replies.
- `POST /api/tickets/{id}/ai-agent` is also role-protected. Python can read ticket detail/history only through Java internal endpoints guarded by a separate internal token.
- Connection refusal, timeout and Python 5xx map to `50300`; malformed JSON, schema mismatch and upstream request 4xx map to `50200`.

## Python responsibilities

- `FakeTicketAiProvider` is deterministic and is the default local contract provider.
- `OpenAICompatibleTicketAiProvider` is an adapter for a real external provider. Its output is validated by Pydantic models (`TicketAnalysisResult`, `TicketReplyDraftResult`); natural-language substring parsing is not used.
- RAG reads the small `knowledge/` support corpus, chunks it, ranks lexical overlap, and returns source metadata. `AI_RAG_TOP_K` is configurable.
- The Agent has an allowlist of two read-only tools and an `AI_AGENT_MAX_TOOL_CALLS` loop guard.
- MCP exposes the read-only `search_ticket_knowledge` tool through the official Python SDK. It is a protocol demonstration isolated from the Java business write path.

## Configuration boundary

Java reads `APP_AI` settings from `application.yml` / environment variables (`AI_SERVICE_BASE_URL`, `AI_SERVICE_INTERNAL_TOKEN`, `AI_SERVICE_CONNECT_TIMEOUT`, `AI_SERVICE_READ_TIMEOUT`). Python reads `AI_` settings through Pydantic Settings (`AI_PROVIDER_MODE`, `AI_MODEL`, `AI_LLM_TIMEOUT_SECONDS`, `AI_LLM_RETRY_COUNT`, `AI_RAG_TOP_K`, `AI_AGENT_MAX_TOOL_CALLS`). Changes take effect after the corresponding process restarts; there is no dynamic configuration center.

Real provider credentials are environment-only. The repositories contain placeholders in `.env.example`, never API keys or local `.env` files.

## Failure isolation

The core Java ticket APIs do not require Python to be running. Only an explicit AI endpoint depends on the AI service call, and a failure produces an explicit business error rather than fake success. This keeps AI availability separate from MySQL/Redis business correctness.
