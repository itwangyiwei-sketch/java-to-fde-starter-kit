#!/usr/bin/env python3
"""Verify the local Streamable HTTP MCP server with synthetic fixture data.

Usage: python3 scripts/mcp_smoke.py --base-url http://127.0.0.1:8080
Only Python's standard library is required.
"""

import argparse
import json
import sys
import urllib.error
import urllib.request


PROTOCOL_VERSION = "2025-03-26"


class SmokeError(Exception):
    pass


def post(endpoint, payload, timeout, session_id=None):
    headers = {
        "Accept": "application/json, text/event-stream",
        "Content-Type": "application/json",
    }
    if session_id is not None:
        headers["Mcp-Session-Id"] = session_id
        headers["Mcp-Protocol-Version"] = PROTOCOL_VERSION
    request = urllib.request.Request(
        endpoint,
        data=json.dumps(payload).encode("utf-8"),
        headers=headers,
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            return response.status, response.headers, response.read()
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace")[:300]
        raise SmokeError(f"MCP HTTP {exc.code}: {detail}") from exc
    except (urllib.error.URLError, TimeoutError, OSError) as exc:
        raise SmokeError(f"MCP connection failed: {exc}") from exc


def rpc_result(headers, raw_body, expected_id):
    try:
        text = raw_body.decode("utf-8")
        if headers.get_content_type() == "text/event-stream":
            messages = []
            for event in text.replace("\r\n", "\n").split("\n\n"):
                data = "\n".join(
                    line[5:].lstrip() for line in event.splitlines() if line.startswith("data:")
                )
                if data:
                    messages.append(json.loads(data))
            matches = [message for message in messages if message.get("id") == expected_id]
            if not matches:
                raise SmokeError(f"SSE response lacks JSON-RPC id {expected_id}")
            message = matches[0]
        else:
            message = json.loads(text)
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise SmokeError(f"Invalid MCP response: {exc}") from exc

    if message.get("id") != expected_id:
        raise SmokeError(f"Expected JSON-RPC id {expected_id}, got {message.get('id')!r}")
    if "error" in message:
        raise SmokeError(f"MCP JSON-RPC error: {message['error']}")
    result = message.get("result")
    if not isinstance(result, dict):
        raise SmokeError("MCP response has no result object")
    return result


def reject_bad_origin(endpoint, timeout):
    payload = {
        "jsonrpc": "2.0",
        "id": 4,
        "method": "initialize",
        "params": {
            "protocolVersion": PROTOCOL_VERSION,
            "capabilities": {},
            "clientInfo": {"name": "origin-check", "version": "0.1.0"},
        },
    }
    request = urllib.request.Request(
        endpoint,
        data=json.dumps(payload).encode("utf-8"),
        headers={
            "Accept": "application/json, text/event-stream",
            "Content-Type": "application/json",
            "Origin": "https://evil.example",
        },
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            raise SmokeError(f"untrusted Origin was accepted with HTTP {response.status}")
    except urllib.error.HTTPError as exc:
        if exc.code != 403:
            raise SmokeError(f"untrusted Origin returned HTTP {exc.code}, expected 403") from exc
    except (urllib.error.URLError, TimeoutError, OSError) as exc:
        raise SmokeError(f"Origin check connection failed: {exc}") from exc


def smoke(endpoint, timeout):
    status, headers, body = post(
        endpoint,
        {
            "jsonrpc": "2.0",
            "id": 1,
            "method": "initialize",
            "params": {
                "protocolVersion": PROTOCOL_VERSION,
                "capabilities": {},
                "clientInfo": {"name": "java-to-fde-smoke", "version": "0.1.0"},
            },
        },
        timeout,
    )
    if status != 200:
        raise SmokeError(f"initialize returned HTTP {status}, expected 200")
    initialized = rpc_result(headers, body, 1)
    session_id = headers.get("Mcp-Session-Id")
    if not session_id:
        raise SmokeError("initialize did not provide Mcp-Session-Id")

    status, _, _ = post(
        endpoint,
        {"jsonrpc": "2.0", "method": "notifications/initialized"},
        timeout,
        session_id,
    )
    if status != 202:
        raise SmokeError(f"notifications/initialized returned HTTP {status}, expected 202")

    status, headers, body = post(
        endpoint,
        {"jsonrpc": "2.0", "id": 2, "method": "tools/list", "params": {}},
        timeout,
        session_id,
    )
    if status != 200:
        raise SmokeError(f"tools/list returned HTTP {status}, expected 200")
    listed = rpc_result(headers, body, 2)
    tools = listed.get("tools")
    if not isinstance(tools, list):
        raise SmokeError("tools/list has no tools array")
    names = {tool.get("name") for tool in tools if isinstance(tool, dict)}
    required = {"get_invoice", "get_purchase_order"}
    if not required.issubset(names):
        raise SmokeError(f"missing tools: {sorted(required - names)}")

    status, headers, body = post(
        endpoint,
        {
            "jsonrpc": "2.0",
            "id": 3,
            "method": "tools/call",
            "params": {
                "name": "get_invoice",
                "arguments": {"tenantId": "acme", "invoiceId": "INV-1001"},
            },
        },
        timeout,
        session_id,
    )
    if status != 200:
        raise SmokeError(f"tools/call returned HTTP {status}, expected 200")
    called = rpc_result(headers, body, 3)
    if called.get("isError") is not False:
        raise SmokeError("get_invoice returned an MCP tool error")
    content = called.get("content")
    if not isinstance(content, list):
        raise SmokeError("get_invoice has no content array")
    invoices = []
    for item in content:
        if isinstance(item, dict) and item.get("type") == "text":
            try:
                invoices.append(json.loads(item.get("text", "")))
            except json.JSONDecodeError as exc:
                raise SmokeError(f"get_invoice returned invalid JSON text: {exc}") from exc
    if not any(
        isinstance(invoice, dict)
        and invoice.get("id") == "INV-1001"
        and invoice.get("tenantId") == "acme"
        for invoice in invoices
    ):
        raise SmokeError("get_invoice did not return the expected synthetic invoice")

    for alternate_endpoint in (
        endpoint,
        endpoint + ";foo=bar",
        endpoint.rsplit("/", 1)[0] + "/%6dcp",
    ):
        reject_bad_origin(alternate_endpoint, timeout)
    server_name = initialized.get("serverInfo", {}).get("name", "MCP server")
    print(f"PASS {server_name}: initialize, tools/list, get_invoice, Origin guard")


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default="http://127.0.0.1:8080")
    parser.add_argument("--timeout", type=float, default=10.0)
    args = parser.parse_args(argv)
    if args.timeout <= 0:
        parser.error("--timeout must be positive")
    endpoint = args.base_url.rstrip("/") + "/mcp"
    try:
        smoke(endpoint, args.timeout)
    except SmokeError as exc:
        print(f"FAIL {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
