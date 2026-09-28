#!/usr/bin/env python3
"""Run business-outcome checks against the starter kit's reconcile API.

Usage: python3 eval/run.py --base-url http://127.0.0.1:8080
Only Python's standard library is required.
"""

import argparse
import json
import sys
import time
import urllib.error
import urllib.request
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path


HERE = Path(__file__).resolve().parent
DEFAULT_CASES = HERE / "cases.jsonl"
DEFAULT_REPORT = HERE / "reports" / "last-run.json"


def load_cases(path):
    cases = []
    seen_ids = set()
    with path.open("r", encoding="utf-8") as handle:
        for line_number, line in enumerate(handle, 1):
            if not line.strip():
                continue
            try:
                case = json.loads(line)
            except json.JSONDecodeError as exc:
                raise ValueError(f"{path}:{line_number}: invalid JSON: {exc.msg}") from exc
            if not isinstance(case, dict):
                raise ValueError(f"{path}:{line_number}: case must be an object")
            case_id = case.get("id")
            if not isinstance(case_id, str) or not case_id.strip():
                raise ValueError(f"{path}:{line_number}: id must be a nonempty string")
            if case_id in seen_ids:
                raise ValueError(f"{path}:{line_number}: duplicate id {case_id!r}")
            if not isinstance(case.get("request"), dict):
                raise ValueError(f"{path}:{line_number}: request must be an object")
            if not isinstance(case.get("expect"), dict):
                raise ValueError(f"{path}:{line_number}: expect must be an object")
            seen_ids.add(case_id)
            cases.append(case)
    if not cases:
        raise ValueError(f"{path}: no cases found")
    return cases


def post_json(url, payload, timeout):
    request = urllib.request.Request(
        url,
        data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
        headers={"Accept": "application/json", "Content-Type": "application/json; charset=utf-8"},
        method="POST",
    )
    started = time.monotonic()
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            status = response.status
            raw_body = response.read()
    except urllib.error.HTTPError as exc:
        status = exc.code
        raw_body = exc.read()
    except (urllib.error.URLError, TimeoutError, OSError) as exc:
        return None, None, round((time.monotonic() - started) * 1000, 2), str(exc)

    elapsed_ms = round((time.monotonic() - started) * 1000, 2)
    try:
        body = json.loads(raw_body.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError):
        body = None
    return status, body, elapsed_ms, None


def check_case(case, status, body, transport_error):
    expected = case["expect"]
    issues = []
    expected_status = expected.get("httpStatus", 200)

    if transport_error is not None:
        return [f"transport error: {transport_error}"]
    if status != expected_status:
        issues.append(f"HTTP status: expected {expected_status}, got {status}")

    # Error responses need only meet their stated expectations. Successful
    # responses must carry enough evidence to verify the business outcome.
    if not isinstance(body, dict):
        if expected_status == 200 or any(
            key in expected for key in ("decision", "reasonCode", "invoiceId", "terminalState", "guardOverrodeModel")
        ):
            issues.append("response body must be a JSON object")
        return issues

    for field in ("decision", "reasonCode", "invoiceId", "guardOverrodeModel"):
        if field in expected and body.get(field) != expected[field]:
            issues.append(f"{field}: expected {expected[field]!r}, got {body.get(field)!r}")
    if expected.get("decisionAbsent") and body.get("decision") is not None:
        issues.append(f"decision must be absent for rejected input, got {body['decision']!r}")

    if expected_status == 200:
        states = body.get("states")
        if not isinstance(states, list) or not states or not all(isinstance(state, str) for state in states):
            issues.append("states must be a nonempty array of strings")
        elif "terminalState" in expected and states[-1] != expected["terminalState"]:
            issues.append(f"terminal state: expected {expected['terminalState']!r}, got {states[-1]!r}")

        trace_id = body.get("traceId")
        if not isinstance(trace_id, str) or not trace_id.strip():
            issues.append("traceId must be a nonempty string")

        duration_ms = body.get("durationMs")
        if (
            isinstance(duration_ms, bool)
            or not isinstance(duration_ms, (int, float))
            or duration_ms < 0
        ):
            issues.append("durationMs must be a nonnegative number")

    return issues


def run_case(case, url, timeout):
    status, body, elapsed_ms, transport_error = post_json(url, case["request"], timeout)
    issues = check_case(case, status, body, transport_error)
    return {
        "id": case["id"],
        "description": case.get("description", ""),
        "passed": not issues,
        "expected": case["expect"],
        "actual": {"httpStatus": status, "body": body, "elapsedMs": elapsed_ms},
        "issues": issues,
    }


def percentile(values, fraction):
    """Linear interpolation keeps p50/p95 defined even with one sample."""
    if not values:
        return None
    ordered = sorted(values)
    position = (len(ordered) - 1) * fraction
    lower = int(position)
    upper = min(lower + 1, len(ordered) - 1)
    return round(ordered[lower] + (ordered[upper] - ordered[lower]) * (position - lower), 2)


def summarize_results(results):
    decision_counts = Counter()
    fallback_count = 0
    guard_override_count = 0
    http_latencies = []

    for result in results:
        actual = result["actual"]
        if actual["httpStatus"] is not None:
            http_latencies.append(actual["elapsedMs"])
        body = actual["body"]
        if not isinstance(body, dict):
            continue
        decision = body.get("decision")
        if isinstance(decision, str) and decision:
            decision_counts[decision] += 1
        states = body.get("states")
        if isinstance(states, list) and states and states[-1] == "FALLBACK":
            fallback_count += 1
        if body.get("guardOverrodeModel") is True:
            guard_override_count += 1

    passed = sum(result["passed"] for result in results)
    return {
        "total": len(results),
        "passed": passed,
        "failed": len(results) - passed,
        "decisionCounts": dict(sorted(decision_counts.items())),
        "fallbackCount": fallback_count,
        "guardOverrideCount": guard_override_count,
        "httpLatencyMs": {
            "samples": len(http_latencies),
            "p50": percentile(http_latencies, 0.50),
            "p95": percentile(http_latencies, 0.95),
        },
    }


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default="http://127.0.0.1:8080", help="service root URL")
    parser.add_argument("--cases", type=Path, default=DEFAULT_CASES, help="JSONL case file")
    parser.add_argument("--output", type=Path, default=DEFAULT_REPORT, help="JSON report path")
    parser.add_argument("--timeout", type=float, default=10.0, help="seconds per HTTP request")
    args = parser.parse_args(argv)

    if args.timeout <= 0:
        parser.error("--timeout must be greater than zero")
    try:
        cases = load_cases(args.cases)
    except (OSError, ValueError) as exc:
        print(f"Cannot load cases: {exc}", file=sys.stderr)
        return 2

    url = args.base_url.rstrip("/") + "/api/reconcile"
    results = [run_case(case, url, args.timeout) for case in cases]
    summary = summarize_results(results)
    report = {
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "endpoint": url,
        "casesFile": str(args.cases.resolve()),
        "summary": summary,
        "results": results,
    }

    try:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    except OSError as exc:
        print(f"Cannot write report: {exc}", file=sys.stderr)
        return 2

    for result in results:
        mark = "PASS" if result["passed"] else "FAIL"
        print(f"[{mark}] {result['id']}")
        for issue in result["issues"]:
            print(f"       {issue}")
    print(f"\nResult: {summary['passed']}/{summary['total']} passed; {summary['failed']} failed")
    decisions = ", ".join(f"{name}={count}" for name, count in summary["decisionCounts"].items()) or "none"
    print(f"Decisions: {decisions}; fallbacks={summary['fallbackCount']}; "
          f"guard overrides={summary['guardOverrideCount']}")
    latency = summary["httpLatencyMs"]
    if latency["samples"]:
        print(f"HTTP latency ({latency['samples']} responses): "
              f"p50={latency['p50']:.2f} ms; p95={latency['p95']:.2f} ms")
    else:
        print("HTTP latency: n/a (no responses)")
    print(f"JSON report: {args.output.resolve()}")
    return 0 if summary["failed"] == 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())
