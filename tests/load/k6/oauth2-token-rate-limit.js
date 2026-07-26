/**
 * GH-420 — OAuth2 /oauth2/token rate-limit functional validation (k6)
 *
 * Validates:
 * 1. authorization_code returns 429 after client exceeds limit
 * 2. 429 includes Retry-After
 * 3. one limited client does not block another client
 * 4. token_exchange is rate-limited per client
 * 6. recovery after window rollover (requires short WINDOW_MS)
 *
 * Rate limiting is evaluated before grant processing when client_id is present,
 * so invalid codes/secrets still consume the counter — intentional for this probe.
 *
 * Required env:
 *   SECURITY_BASE_URL   (default http://localhost:8080)
 *   TOKEN_LIMIT         (must match server OAUTH2_TOKEN_RATE_LIMIT_LIMIT)
 *   WINDOW_MS           (must match server OAUTH2_TOKEN_RATE_LIMIT_WINDOW_MS)
 *
 * Optional:
 *   CLIENT_A / CLIENT_B  fixed client ids (default: random UUIDs per run)
 */
import http from 'k6/http'
import { check, group, sleep } from 'k6'
import { Counter, Rate } from 'k6/metrics'
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js'

export const options = {
  scenarios: {
    oauth2_rate_limit: {
      executor: 'shared-iterations',
      vus: 1,
      iterations: 1,
      maxDuration: '5m',
    },
  },
  thresholds: {
    oauth2_checks_passed: ['rate==1'],
    oauth2_unexpected_status: ['count==0'],
  },
}

const baseUrl = __ENV.SECURITY_BASE_URL || 'http://localhost:8080'
const tokenLimit = Number(__ENV.TOKEN_LIMIT || '5')
const windowMs = Number(__ENV.WINDOW_MS || '10000')
const clientA = __ENV.CLIENT_A || `k6-client-a-${uuidv4()}`
const clientB = __ENV.CLIENT_B || `k6-client-b-${uuidv4()}`

const checksPassed = new Rate('oauth2_checks_passed')
const unexpectedStatus = new Counter('oauth2_unexpected_status')

function postToken(body) {
  return http.post(`${baseUrl}/oauth2/token`, JSON.stringify(body), {
    headers: { 'Content-Type': 'application/json' },
    tags: { endpoint: 'oauth2_token' },
  })
}

function authorizationCodeBody(clientId) {
  return {
    grant_type: 'authorization_code',
    client_id: clientId,
    redirect_uri: 'https://runtime.example/callback',
    code: `invalid-code-${uuidv4()}`,
    code_verifier: 'correct-horse-battery-staple-0123456789',
  }
}

function tokenExchangeBody(clientId) {
  return {
    grant_type: 'urn:ietf:params:oauth:grant-type:token-exchange',
    client_id: clientId,
    client_secret: 'invalid-secret',
    audience: 'banking',
    subject_token: `invalid-subject-${uuidv4()}`,
    subject_token_type: 'urn:ietf:params:oauth:token-type:access_token',
  }
}

function refreshTokenBody() {
  return {
    grant_type: 'refresh_token',
    refresh_token: `invalid-refresh-${uuidv4()}`,
  }
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

function exhaustUntil429(label, bodyFactory, clientId, expectedUnderLimitStatuses) {
  const statuses = []
  let limited = null

  for (let i = 1; i <= tokenLimit + 3; i++) {
    const res = postToken(bodyFactory(clientId))
    statuses.push(res.status)
    if (res.status === 429) {
      limited = res
      break
    }
    if (!expectedUnderLimitStatuses.includes(res.status)) {
      unexpectedStatus.add(1)
      console.error(`${label} unexpected status before limit: ${res.status} body=${String(res.body).slice(0, 200)}`)
    }
  }

  return { statuses, limited }
}

export default function () {
  console.log(
    `oauth2 rate-limit probe base=${baseUrl} limit=${tokenLimit} windowMs=${windowMs} clientA=${clientA} clientB=${clientB}`,
  )

  group('1+2 authorization_code 429 + Retry-After', () => {
    const { statuses, limited } = exhaustUntil429(
      'authorization_code',
      authorizationCodeBody,
      clientA,
      [400],
    )

    assert(
      'authorization_code eventually returns 429',
      limited !== null,
      `statuses=${statuses.join(',')}`,
    )
    assert(
      'authorization_code 429 has Retry-After',
      limited && !!limited.headers['Retry-After'],
      limited ? `Retry-After=${limited.headers['Retry-After']}` : 'no 429',
    )
    assert(
      'authorization_code exceeded after configured limit',
      limited !== null && statuses.length === tokenLimit + 1,
      `attempts=${statuses.length} expected=${tokenLimit + 1}`,
    )
  })

  group('3 client isolation', () => {
    const res = postToken(authorizationCodeBody(clientB))
    assert(
      'different client is not blocked by limited client',
      res.status === 400,
      `status=${res.status} body=${String(res.body).slice(0, 160)}`,
    )
  })

  group('4 token_exchange rate limit', () => {
    const exchangeClient = `k6-exchange-${uuidv4()}`
    const { statuses, limited } = exhaustUntil429(
      'token_exchange',
      tokenExchangeBody,
      exchangeClient,
      [400, 401],
    )

    assert(
      'token_exchange eventually returns 429',
      limited !== null,
      `statuses=${statuses.join(',')}`,
    )
    assert(
      'token_exchange 429 has Retry-After',
      limited && !!limited.headers['Retry-After'],
      limited ? `Retry-After=${limited.headers['Retry-After']}` : 'no 429',
    )
  })

  group('refresh_token without resolvable client (expected skip)', () => {
    // Without a resolvable OAuth client from the refresh token, rate limiting is skipped.
    const res = postToken(refreshTokenBody())
    assert(
      'invalid refresh_token does not falsely rate-limit',
      res.status !== 429,
      `status=${res.status}`,
    )
  })

  if (windowMs > 0 && windowMs <= 60_000) {
    group('6 window recovery', () => {
      const waitSeconds = Math.ceil(windowMs / 1000) + 1
      console.log(`waiting ${waitSeconds}s for window rollover`)
      sleep(waitSeconds)
      const res = postToken(authorizationCodeBody(clientA))
      assert(
        'authorization_code recovers after window rollover',
        res.status === 400,
        `status=${res.status} body=${String(res.body).slice(0, 160)}`,
      )
    })
  } else {
    console.log('SKIP: window recovery (WINDOW_MS too large for practical k6 wait)')
  }
}
