/**
 * GH-420 — /mcp/events rate-limit functional validation (k6)
 *
 * Validates:
 * 5. /mcp/events returns JSON 429 (not an invalid SSE stream) with Retry-After
 * 6. recovery after window rollover (requires short WINDOW_MS)
 * Also checks a second token is not starved by the limited token.
 *
 * Required env:
 *   MCP_BASE_URL     (default http://localhost:8086)
 *   MCP_READ_TOKEN   bearer access token (audience must match MCP)
 *   MCP_OTHER_TOKEN  optional second token for isolation (defaults to MCP_READ_TOKEN2)
 *   TOKEN_LIMIT      (must match MCP_RATE_LIMIT_TOKEN_READ_LIMIT)
 *   WINDOW_MS        (must match MCP_RATE_LIMIT_TOKEN_READ_WINDOW_MS)
 */
import http from 'k6/http'
import { check, group, sleep } from 'k6'
import { Counter, Rate } from 'k6/metrics'

export const options = {
  scenarios: {
    mcp_events_rate_limit: {
      executor: 'shared-iterations',
      vus: 1,
      iterations: 1,
      maxDuration: '5m',
    },
  },
  thresholds: {
    mcp_checks_passed: ['rate==1'],
    mcp_unexpected_status: ['count==0'],
  },
}

const baseUrl = __ENV.MCP_BASE_URL || 'http://localhost:8086'
const token = __ENV.MCP_READ_TOKEN
const otherToken = __ENV.MCP_OTHER_TOKEN || __ENV.MCP_READ_TOKEN2
const tokenLimit = Number(__ENV.TOKEN_LIMIT || '5')
const windowMs = Number(__ENV.WINDOW_MS || '10000')

const checksPassed = new Rate('mcp_checks_passed')
const unexpectedStatus = new Counter('mcp_unexpected_status')

if (!token) {
  throw new Error('MCP_READ_TOKEN is required')
}

function eventsRequest(bearer) {
  return http.post(
    `${baseUrl}/mcp/events`,
    JSON.stringify({
      jsonrpc: '2.0',
      id: `k6-events-${Math.random()}`,
      method: 'tools/list',
      params: {},
    }),
    {
      headers: {
        'Content-Type': 'application/json',
        Accept: 'text/event-stream, application/json',
        Authorization: `Bearer ${bearer}`,
      },
      tags: { endpoint: 'mcp_events' },
    },
  )
}

function assert(name, ok, detail) {
  const passed = !!ok
  checksPassed.add(passed)
  check(null, { [name]: () => passed })
  if (!passed) {
    console.error(`FAIL: ${name}${detail ? ` — ${detail}` : ''}`)
  } else {
    console.log(`PASS: ${name}${detail ? ` — ${detail}` : ''}`)
  }
  return passed
}

function contentTypeIsJson(res) {
  const ct = (res.headers['Content-Type'] || res.headers['content-type'] || '').toLowerCase()
  return ct.includes('application/json')
}

function bodyLooksLikeJsonError(res) {
  try {
    const body = typeof res.body === 'string' ? JSON.parse(res.body) : res.json()
    return body && (body.error === 'rate_limit_exceeded' || body.error_description || body.error)
  } catch (e) {
    return false
  }
}

export default function () {
  console.log(`mcp/events rate-limit probe base=${baseUrl} limit=${tokenLimit} windowMs=${windowMs}`)

  group('5 /mcp/events JSON 429 + Retry-After', () => {
    const statuses = []
    let limited = null

    for (let i = 1; i <= tokenLimit + 3; i++) {
      const res = eventsRequest(token)
      statuses.push(res.status)

      // Under limit: protocol may return 200 SSE/JSON or 200 empty accepted depending on tools/list.
      if (res.status === 429) {
        limited = res
        break
      }

      if (![200, 202].includes(res.status)) {
        unexpectedStatus.add(1)
        console.error(
          `unexpected status before limit: ${res.status} ct=${res.headers['Content-Type']} body=${String(res.body).slice(0, 200)}`,
        )
      }
    }

    assert('mcp/events eventually returns 429', limited !== null, `statuses=${statuses.join(',')}`)
    assert(
      'mcp/events 429 Content-Type is JSON',
      limited && contentTypeIsJson(limited),
      limited ? `Content-Type=${limited.headers['Content-Type']}` : 'no 429',
    )
    assert(
      'mcp/events 429 body is JSON oauth error',
      limited && bodyLooksLikeJsonError(limited),
      limited ? `body=${String(limited.body).slice(0, 200)}` : 'no 429',
    )
    assert(
      'mcp/events 429 has Retry-After',
      limited && !!limited.headers['Retry-After'],
      limited ? `Retry-After=${limited.headers['Retry-After']}` : 'no 429',
    )
    assert(
      'mcp/events exceeded after configured limit',
      limited !== null && statuses.length === tokenLimit + 1,
      `attempts=${statuses.length} expected=${tokenLimit + 1}`,
    )
  })

  if (otherToken && otherToken !== token) {
    group('token isolation', () => {
      const res = eventsRequest(otherToken)
      assert(
        'different token is not blocked by limited token',
        [200, 202].includes(res.status),
        `status=${res.status} body=${String(res.body).slice(0, 160)}`,
      )
    })
  } else {
    console.log('SKIP: token isolation (MCP_OTHER_TOKEN not provided)')
  }

  if (windowMs > 0 && windowMs <= 60_000) {
    group('6 window recovery', () => {
      const waitSeconds = Math.ceil(windowMs / 1000) + 1
      console.log(`waiting ${waitSeconds}s for window rollover`)
      sleep(waitSeconds)
      const res = eventsRequest(token)
      assert(
        'mcp/events recovers after window rollover',
        [200, 202].includes(res.status),
        `status=${res.status} body=${String(res.body).slice(0, 160)}`,
      )
    })
  } else {
    console.log('SKIP: window recovery (WINDOW_MS too large for practical k6 wait)')
  }
}
