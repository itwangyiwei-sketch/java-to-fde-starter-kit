# Java to FDE Starter Kit

[中文](README.md) · [Roadmap](ROADMAP.md) · [Contributing](CONTRIBUTING.md) · [Apache-2.0 License](LICENSE)

A local Java learning project for an invoice reconciliation workflow. It demonstrates read-only MCP tools, an explicit execution path, deterministic business guards, fault injection, and an evaluation harness that checks business outcomes. The default model is a local mock; no API key, database, or Docker is needed.

## Quick start

Install Java 17, Maven 3.6.3+, and Python 3. This project targets Spring Boot 3.5.16 and Spring AI 1.1.8.

```bash
mvn test
mvn spring-boot:run
```

In another terminal:

```bash
curl -sS http://127.0.0.1:8080/api/reconcile \
  -H 'Content-Type: application/json' \
  -d '{"tenantId":"acme","query":"请核对发票 INV-1001","demoFault":"NONE"}'

python3 eval/run.py
python3 scripts/mcp_smoke.py
```

The response includes a final `decision`, `reasonCode`, `states`, and `traceId`. The MCP Streamable HTTP endpoint is `http://127.0.0.1:8080/mcp`; its tools only read local fixtures. The smoke script checks MCP initialization, tool discovery, a fixture tool call, and rejection of an untrusted `Origin`. `demoFault` accepts `NONE`, `TIMEOUT`, `MALFORMED`, or `OVERAPPROVE` for local failure exercises.

## Optional remote model

Set `FDE_MODEL_PROVIDER=openai`, `FDE_LLM_BASE_URL`, `FDE_LLM_MODEL`, and `FDE_LLM_API_KEY` in the environment before starting the app. `FDE_LLM_TIMEOUT_MS` is optional. This invokes an OpenAI-compatible HTTP endpoint and may incur charges. Use the default mock for reproducible evaluation.

## Scope

This is an educational local demo, not a production deployment template. Before connecting real data, bind tenant identity to authenticated server-side context, authorize each MCP tool, persist decisions and idempotency records, and add observability and evaluations using sanitized real cases. The demo binds to loopback and checks an `Origin` allowlist (`FDE_MCP_ALLOWED_ORIGINS`) across all HTTP routes; it does not authenticate or authorize MCP clients. See the [MCP transport security requirements](https://modelcontextprotocol.io/specification/2025-11-25/basic/transports) and [Spring AI MCP starter docs](https://docs.spring.io/spring-ai/reference/1.1/api/mcp/mcp-streamable-http-server-boot-starter-docs.html).
