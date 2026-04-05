import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { GET } from './route';

const ACCOUNT_ID = '550e8400-e29b-41d4-a716-446655440000';
const BANKING_URL = 'http://banking-internal:8080';

function createRequest(headers: Record<string, string> = {}): Request {
  return new Request(`http://localhost/api/investments/portfolio/${ACCOUNT_ID}/recalculation-status/events`, {
    method: 'GET',
    headers,
  });
}

describe.sequential('GET /api/investments/portfolio/[accountId]/recalculation-status/events', () => {
  const originalFetch = globalThis.fetch;
  const originalEnv = process.env.BANKING_SERVICE_INTERNAL_URL;

  beforeEach(() => {
    process.env.BANKING_SERVICE_INTERNAL_URL = BANKING_URL;
  });

  afterEach(() => {
    process.env.BANKING_SERVICE_INTERNAL_URL = originalEnv;
    globalThis.fetch = originalFetch;
  });

  it('givenMissingToken_whenGet_then401', async () => {
    const req = createRequest();
    const params = Promise.resolve({ accountId: ACCOUNT_ID });

    const res = await GET(req as never, { params });

    expect(res.status).toBe(401);
    expect(await res.text()).toBe('Unauthorized');
  });

  it('givenValidToken_whenGet_thenSseHeadersSet', async () => {
    const token = 'Bearer eyJhbGc...';
    const req = createRequest({ 'x-auth-token': token });
    const params = Promise.resolve({ accountId: ACCOUNT_ID });

    const mockBody = new ReadableStream();
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      body: mockBody,
    });
    globalThis.fetch = fetchMock as typeof fetch;

    const res = await GET(req as never, { params });

    expect(res.status).toBe(200);
    expect(res.headers.get('Content-Type')).toBe('text/event-stream');
    expect(res.headers.get('Cache-Control')).toBe('no-cache, no-transform');
    expect(res.headers.get('Connection')).toBe('keep-alive');
    expect(res.headers.get('X-Accel-Buffering')).toBe('no');
    expect(fetchMock).toHaveBeenCalledWith(
      `${BANKING_URL}/investments/portfolio/${ACCOUNT_ID}/recalculation-status/events`,
      expect.objectContaining({
        headers: { Authorization: `Bearer ${token}` },
      })
    );
  });

  it('givenMicronautReturns404_whenGet_then404Proxied', async () => {
    const req = createRequest({ 'x-auth-token': 'token' });
    const params = Promise.resolve({ accountId: ACCOUNT_ID });

    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: false,
      status: 404,
      statusText: 'Not Found',
    }) as typeof fetch;

    const res = await GET(req as never, { params });

    expect(res.status).toBe(404);
    expect(await res.text()).toBe('Not Found');
  });
});
