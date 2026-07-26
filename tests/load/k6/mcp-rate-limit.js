import http from 'k6/http'
import { check, sleep } from 'k6'

export const options = {
  scenarios: {
    well_behaved_clients: {
      executor: 'per-vu-iterations',
      vus: 5,
      iterations: 10,
      maxDuration: '2m',
    },
    runaway_client: {
      executor: 'constant-arrival-rate',
      rate: 30,
      timeUnit: '1s',
      duration: '30s',
      preAllocatedVUs: 5,
      maxVUs: 10,
      exec: 'runaway',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.5'],
    http_req_duration: ['p(95)<1000'],
  },
}

const baseUrl = __ENV.MCP_BASE_URL || 'http://localhost:8086'
const readToken = __ENV.MCP_READ_TOKEN
const writeToken = __ENV.MCP_WRITE_TOKEN || readToken

function jsonRpcCall(token, method, params) {
  const response = http.post(
    `${baseUrl}/mcp`,
    JSON.stringify({
      jsonrpc: '2.0',
      id: `${method}-${Math.random()}`,
      method,
      params,
    }),
    {
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${token}`,
      },
    },
  )

  check(response, {
    'status is 200 or 429': (r) => r.status === 200 || r.status === 429,
  })

  return response
}

export default function () {
  const listResponse = jsonRpcCall(readToken, 'tools/list')
  check(listResponse, {
    'well-behaved tools/list stays under limit': (r) => r.status === 200,
  })

  const readResponse = jsonRpcCall(readToken, 'tools/call', {
    name: 'list_transactions',
    arguments: { first: 5 },
  })
  check(readResponse, {
    'well-behaved read stays under limit': (r) => r.status === 200,
  })

  sleep(1)
}

export function runaway() {
  const response = jsonRpcCall(writeToken, 'tools/call', {
    name: 'create_transaction',
    arguments: {
      idempotency_key: `k6-${Math.random()}`,
      financialAccountId: __ENV.MCP_FINANCIAL_ACCOUNT_ID,
      creditDebitType: 'DEBIT',
      type: 'OTHER_EXPENSE',
      amount: 10,
      date: '2026-07-26',
      description: 'k6 runaway request',
    },
  })

  check(response, {
    'runaway client eventually sees 429': (r) => r.status === 200 || (r.status === 429 && r.headers['Retry-After']),
  })
}
