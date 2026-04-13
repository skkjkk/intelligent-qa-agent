# AGENTS.md

This file applies to the entire repository.

## Project Summary
- This is a `Java 17` + `Spring Boot 3` backend project for an intelligent QA agent.
- Core capabilities include authentication, chat/session management, SSE streaming responses, Function Calling, tool execution, and **Knowledge Base RAG**.
- Main code lives under `src/main/java/com/jujiu/agent`.

## Tech Stack
- `Spring Boot 3.4.x`
- `Spring Security`
- `MyBatis-Plus`
- `MySQL`
- `Redis`
- `Spring WebFlux` and `SseEmitter`
- `DeepSeek` API integration
- **Elasticsearch** - Vector storage and hybrid search for knowledge base
- **Apache Kafka** - Async document processing pipeline
- **MinIO** - Object storage for document files
- **PDFBox / Apache POI / Jsoup** - Document parsing (PDF, Word, HTML, etc.)
- **Prometheus Micrometer** - Metrics and observability

## Architecture Expectations
- Keep the existing layered structure: `controller` -> `service` -> `repository`.
- Put HTTP entry logic in controllers, business logic in services, and persistence logic in repositories.
- **Knowledge Base modules follow additional conventions:**
  - Document parsing: extend `DocumentParser`, register via `DocumentParserFactory` auto-discovery
  - Async processing: use Kafka for document ingestion pipeline
  - Vector search: use Elasticsearch for embedding storage and similarity search
- New tools should extend `AbstractTool` and register through `ToolRegistry`.
- Function-calling related changes should stay consistent with the current `DeepSeekMessage` / `ToolDefinition` / `ToolCallDTO` model design.

## Coding Style
- Follow the existing project style and naming conventions.
- Prefer small, focused changes over broad refactors.
- Do not rename files, classes, fields, or APIs unless required by the task.
- Avoid adding unnecessary abstractions.
- Do not add inline comments unless the user explicitly asks.
- Do not use one-letter variable names.
- Keep log messages structured and consistent with the existing style.
- Use `[BRACKET_PREFIX]` for log messages in complex modules (e.g., `[TOOL_REGISTRY]`, `[RAG_PIPELINE]`).

## Spring Conventions
- Prefer constructor injection for new code.
- Reuse existing DTO, entity, and result wrapper patterns.
- Use `Result<T>` for controller responses unless the endpoint already uses a different established pattern like SSE.
- Keep validation in request DTOs and controller boundaries where appropriate.
- Preserve current security behavior unless the task explicitly requires changing it.
- Use `@ConfigurationProperties` classes for grouped configuration (see `KnowledgeBaseProperties` as reference).

## Database and Persistence
- Use `MyBatis-Plus` style already present in the codebase.
- Prefer `LambdaQueryWrapper` for query conditions.
- Do not change table structures or SQL scripts unless the task explicitly requires schema changes.
- If schema changes are necessary, update the relevant SQL file under `sql/` and mention it clearly.
- **Knowledge Base entities:** `KbDocument`, `KbChunk`, `KbQueryLog`, `KbRetrievalTrace` - handle with care.

## Document Parser Development
- Implement `DocumentParser` interface for new file format support.
- Implement `supports(String fileType)` and `parse(InputStream inputStream)` methods.
- The `DocumentParserFactory` auto-discovers parsers via constructor injection - just add `@Component`.
- Supported formats: `txt`, `md`, `pdf`, `docx`, `html`.
- Throw `BusinessException` with `ResultCode.UNSUPPORTED_DOCUMENT_TYPE` for unsupported types.

## Agent / Tooling Rules
- When adding a new tool:
  - implement it under `src/main/java/com/jujiu/agent/tool/impl`
  - extend `AbstractTool`
  - provide a clear `getName()`, `execute()`, and `getParameters()`
  - register automatically via `ToolRegistry` scanning
- Keep tool outputs concise, deterministic where possible, and safe for LLM consumption.
- If a tool depends on external APIs, read configuration from `application*.yml` and fail gracefully when config is missing.

## Knowledge Base / RAG Rules
- **Document ingestion flow:** Upload -> MinIO storage -> Kafka event -> Async processing -> Text extraction -> Chunking -> Embedding -> Elasticsearch indexing.
- **RAG query flow:** Query -> Embedding -> Elasticsearch retrieval (hybrid search) -> Re-rank (optional) -> Context assembly -> LLM generation.
- **Configuration:** Use `KnowledgeBaseProperties` for all KB-related config (embedding, ES, MinIO, Kafka, chunking, RAG settings).
- **Security:** KB has ACL support via `KbDocumentAcl` - respect `security.enableAcl` setting.
- **File storage:** All uploaded documents go to MinIO, not local filesystem.
- **Async processing:** Document processing is event-driven via Kafka; handle failures with retry and dead-letter logic.

## Config and Environment
- Respect the existing split between `application.yml`, `application-dev.yml`, and `application-prod.yml`.
- Do not hardcode secrets, API keys, passwords, or tokens.
- Use configuration properties classes where a config group already exists or where a new grouped config is introduced.
- **Key config groups:** `deepseek`, `jwt`, `cors`, `knowledge-base`, `spring.kafka`, `spring.elasticsearch`, `minio`.

## Documentation
- Update `README.md` when changing setup steps, APIs, configuration, or major behavior.
- If the task introduces a substantial new module, add or update a doc in `docs/`.
- Keep documentation practical and aligned with the code that actually exists.

## Testing and Validation
- Prefer targeted validation first.
- If adding or changing business logic, add or update tests when there is already an adjacent test pattern.
- Do not introduce a new testing framework.
- Do not fix unrelated failing tests.

## Safety and Scope Control
- Fix root causes, not just symptoms.
- Avoid unrelated cleanup during focused tasks.
- Preserve public API compatibility unless the user asks for a breaking change.
- For external API calls, keep timeout/error handling behavior reasonable and explicit.

## High-Value Areas
Be especially careful when modifying:
- `security/` - Authentication and authorization logic
- `config/SecurityConfig.java` - Security filter chain
- `service/impl/ChatServiceImpl.java` - Core chat orchestration
- `service/impl/FunctionCallingServiceImpl.java` - Function calling logic
- `client/DeepSeekClient.java` - LLM API client
- **NEW:** `service/KbDocumentServiceImpl.java` - Document upload and management
- **NEW:** `service/KbRagServiceImpl.java` - RAG retrieval and generation
- **NEW:** `service/DocumentProcessService.java` - Async document processing
- **NEW:** `parser/DocumentParserFactory.java` and parser implementations

Changes in these areas should be minimal, well-reasoned, and consistent with the current flow.

## Preferred Workflow for Agents
- First understand the impacted request flow end-to-end.
- Then change the smallest correct set of files.
- Validate the affected path as specifically as possible.
- Summarize what changed, why, and any follow-up the user may want.
