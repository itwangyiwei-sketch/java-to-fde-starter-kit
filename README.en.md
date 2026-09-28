# Java to FDE Starter Kit

[![Open in GitHub Codespaces](https://github.com/codespaces/badge.svg)](https://codespaces.new/itwangyiwei-sketch/java-to-fde-starter-kit)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![Java 17](https://img.shields.io/badge/Java-17-orange.svg)](https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html)
[![Spring Boot 3.5](https://img.shields.io/badge/Spring_Boot-3.5-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Spring AI 1.1](https://img.shields.io/badge/Spring_AI-1.1-green.svg)](https://spring.io/projects/spring-ai)

[中文](README.md) · [Roadmap](ROADMAP.md) · [Contributing](CONTRIBUTING.md) · [Apache-2.0 License](LICENSE)

A local Java learning project for an invoice reconciliation workflow. It demonstrates read-only MCP tools, an explicit execution path, deterministic business guards, fault injection, and an evaluation harness that checks business outcomes. The default model is a local mock; no API key, database, or Docker is needed.

## Quick start

> **💡 Run in Browser with Codespaces**: If you do not have local Java 17 or Maven, click the **[Open in GitHub Codespaces](https://codespaces.new/itwangyiwei-sketch/java-to-fde-starter-kit)** badge above to launch a ready-to-run cloud development container in 30 seconds.

Install Java 17, Maven 3.6.3+, and Python 3. This project targets Spring Boot 3.5.16 and Spring AI 1.1.8.

### Option A: One-command demo (Recommended)
```bash
./run_demo.sh
```
`run_demo.sh` starts the Spring Boot app in the background, waits for port 8080, runs both `eval/run.py` and `scripts/mcp_smoke.py`, and shuts down cleanly.

### Option B: Manual dual-terminal execution
Terminal 1:
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
