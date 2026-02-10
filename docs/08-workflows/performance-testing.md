---
title: API Performance and Stress Testing
type: workflow
category: testing
status: current
version: 1.0.0
tags: [testing, performance, load-testing, stress-testing, api, workflow]
ai_optimized: true
search_keywords: [performance testing, load testing, stress testing, spike testing, soak testing, k6, jmeter, gatling, locust]
related:
  - 08-workflows/testing-workflow.md
  - 10-observability/observability-overview.md
  - 11-infrastructure/README.md
last_updated: 2026-02-05
---

# API Performance and Stress Testing

> **Purpose**: Industry-aligned guidance for planning and running API performance tests (load, stress, spike, soak), with tooling options and recommendations for NeoTool.

## Where this document lives

This is a **testing workflow** because it defines how we design and run non-functional tests. If we later need a dedicated load-testing environment runbook, place it under `11-infrastructure/` and link back here.

## Industry terms (performance test types)

- **Load testing**: validates performance under expected/anticipated load and helps surface performance issues.
- **Stress testing**: evaluates behavior under extreme load to find breaking points and failure behavior.
- **Spike testing**: checks response to sudden increases in load.
- **Soak testing**: checks stability under sustained load for an extended period.

## Common industry practices (incl. big tech)

- **Plan first**: define goals, success criteria, key user/API scenarios, metrics to measure, test data, and test environment.
- **Use APM/observability** to correlate load with latency, error rate, and resource consumption during tests.
- **Complement pre-prod with production tests** when safe: SRE practices distinguish traditional pre-production tests from production tests, where production tests run on the live service and must be carefully controlled.

## Test design for API stress testing

1. **Define objectives**: e.g., max RPS, p95 latency thresholds, error rate budget, and breaking point.
2. **Build a workload model**:
   - Mix of API operations by real usage frequency.
   - Concurrency model (users/clients) with realistic think time.
   - Ramp-up / steady state / ramp-down phases.
3. **Choose test types**:
   - **Baseline** (small load) to validate scripts and monitoring.
   - **Load** (expected peak) to verify targets.
   - **Stress** (beyond peak) to identify breaking points.
   - **Spike** (sudden surges) to validate autoscaling and backpressure.
   - **Soak** (long duration) to detect leaks or degradation.
4. **Prepare environment and data**:
   - Use a production-like environment.
   - Seed realistic test data; avoid data mutations that pollute prod.
5. **Run and analyze**:
   - Capture latency percentiles, error rates, and saturation metrics.
   - Record failure modes and remediation actions.

## Tooling options (frameworks)

| Option | Notes | Good fit for |
|---|---|---|
| **k6** | Open-source load testing tool for developers/testers; tests reliability and performance of services. | Code-first tests in JS; easy CI integration. |
| **Gatling** | Load testing tool with scenarios as code; supports Java/Scala/Kotlin/JavaScript/TypeScript. | JVM teams that want type-safe, code-first scripts. |
| **Apache JMeter** | Open-source Java application for load testing and performance measurement. | Teams needing a GUI-driven tool or existing JMeter assets. |
| **Locust** | Open-source load testing tool; tests are written in Python code. | Python-centric teams that want code-based scenarios. |
| **Azure Load Testing** (managed) | Managed service for large-scale load testing; runs Apache JMeter or Locust tests and integrates with CI/CD. | When you need managed scale and Azure-native integration. |

## Recommendations for NeoTool

**Short-term (fast start)**
- Pick **one code-first tool** and standardize: **k6** (JS) or **Gatling** (JVM/Kotlin).
- Add a **baseline + load + stress** suite to CI for critical APIs.

**Mid-term (scale and confidence)**
- Add **spike** and **soak** tests for critical workflows.
- If we need distributed scale or managed infrastructure, consider **Azure Load Testing**.

**When to run**
- Before major releases or schema changes.
- After significant performance-sensitive changes (DB, caching, infra).
- On a cadence for critical APIs (e.g., monthly soak test).

## Outputs and artifacts

- `test-plan.md` with goals, scenarios, data, environment, and pass/fail criteria.
- Load test scripts and configs in version control.
- Results report with charts and regressions.
