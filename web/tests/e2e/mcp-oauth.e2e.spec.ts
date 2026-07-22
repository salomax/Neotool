import { createHash, randomBytes } from 'node:crypto';
import { expect, test } from '@playwright/test';
import type { APIRequestContext, BrowserContext } from '@playwright/test';
import { currentConfig } from './config/environments';
import { TEST_USERS } from './fixtures/users';

const securityUrl = process.env.E2E_SECURITY_URL ?? 'http://localhost:8080';
const mcpUrl = process.env.E2E_MCP_URL ?? 'http://localhost:8086';
const mcpResource = process.env.E2E_MCP_RESOURCE ?? mcpUrl;
const redirectUri =
  process.env.E2E_MCP_REDIRECT_URI ?? `${currentConfig.baseURL.replace(/\/$/, '')}/oauth2-test-callback`;
const transactionsReadScope = 'finance:transactions:read';

test.describe('MCP OAuth2 authorization code flow', () => {
  test.skip(
    process.env.E2E_MCP_OAUTH !== 'true',
    'Set E2E_MCP_OAUTH=true to run the live security -> mcp OAuth E2E.',
  );

  test('registers a public client, exchanges PKCE code, and calls MCP tools', async ({ page, request }) => {
    const verifier = base64Url(randomBytes(64)).slice(0, 96);
    const challenge = base64Url(createHash('sha256').update(verifier).digest());
    const state = randomBytes(16).toString('hex');

    const registrationResponse = await request.post(`${securityUrl}/oauth2/register`, {
      data: {
        client_name: 'E2E MCP OAuth Test',
        redirect_uris: [redirectUri],
        scope: transactionsReadScope,
        resources: [mcpResource],
        grant_types: ['authorization_code', 'refresh_token'],
        response_types: ['code'],
        token_endpoint_auth_method: 'none',
      },
    });
    expect(
      registrationResponse.ok(),
      await responseDiagnostic('dynamic client registration', registrationResponse),
    ).toBe(true);
    const registration = (await registrationResponse.json()) as OAuthClientRegistrationResponse;

    const authorizeUrl = new URL('/oauth2/authorize', securityUrl);
    authorizeUrl.searchParams.set('response_type', 'code');
    authorizeUrl.searchParams.set('client_id', registration.client_id);
    authorizeUrl.searchParams.set('redirect_uri', redirectUri);
    authorizeUrl.searchParams.set('scope', transactionsReadScope);
    authorizeUrl.searchParams.set('resource', mcpResource);
    authorizeUrl.searchParams.set('state', state);
    authorizeUrl.searchParams.set('code_challenge', challenge);
    authorizeUrl.searchParams.set('code_challenge_method', 'S256');

    await page.goto(authorizeUrl.toString(), { waitUntil: 'domcontentloaded' });
    await page.locator('input[name="email"]').fill(TEST_USERS.admin.email);
    await page.locator('input[name="password"]').fill(TEST_USERS.admin.password);
    await page.getByRole('button', { name: 'Continue' }).click();

    await expect(page.getByRole('heading', { name: /Authorize E2E MCP OAuth Test/i })).toBeVisible();
    await expect(page.locator(`input[name="scope"][value="${transactionsReadScope}"]`)).toBeChecked();

    const requestId = await page.locator('input[name="request_id"]').inputValue();
    const csrfToken = await page.locator('input[name="csrf_token"]').inputValue();
    const cookieHeader = await securityCookieHeader(page.context());
    const consentResponse = await request.post(`${securityUrl}/oauth2/consent`, {
      headers: {
        Cookie: cookieHeader,
      },
      form: {
        request_id: requestId,
        csrf_token: csrfToken,
        scope: transactionsReadScope,
        decision: 'allow',
      },
      maxRedirects: 0,
    });
    expect(consentResponse.status(), await responseDiagnostic('OAuth consent approval', consentResponse)).toBe(303);
    const callbackLocation = consentResponse.headers().location;
    expect(callbackLocation).toBeTruthy();

    const callbackUrl = new URL(callbackLocation!);
    expect(callbackUrl.searchParams.get('state')).toBe(state);
    const code = callbackUrl.searchParams.get('code');
    expect(code).toBeTruthy();

    const tokenResponse = await request.post(`${securityUrl}/oauth2/token`, {
      form: {
        grant_type: 'authorization_code',
        code: code!,
        redirect_uri: redirectUri,
        client_id: registration.client_id,
        code_verifier: verifier,
      },
    });
    expect(tokenResponse.ok(), await responseDiagnostic('authorization code token exchange', tokenResponse)).toBe(true);
    const token = (await tokenResponse.json()) as OAuthTokenResponse;
    expect(token.scope).toBe(transactionsReadScope);
    expect(token.access_token).toBeTruthy();

    const toolsList = await mcpJsonRpc(request, token.access_token, {
      jsonrpc: '2.0',
      id: 'tools-list',
      method: 'tools/list',
      params: {},
    });
    const toolNames = toolsList.result.tools.map((tool) => tool.name);
    expect(toolNames).toContain('finance.transactions.list');

    const toolCall = await mcpJsonRpc(request, token.access_token, {
      jsonrpc: '2.0',
      id: 'transactions-list',
      method: 'tools/call',
      params: {
        name: 'finance.transactions.list',
        arguments: {
          first: 1,
        },
      },
    });

    expect(toolCall.error).toBeUndefined();
    expect(toolCall.result.isError).toBe(false);
    expect(toolCall.result.content[0].type).toBe('text');
    expect(toolCall.result.content[0].text).toContain('transactions');
  });
});

async function securityCookieHeader(context: BrowserContext) {
  const cookies = await context.cookies(securityUrl);
  return cookies.map((cookie) => `${cookie.name}=${cookie.value}`).join('; ');
}

async function mcpJsonRpc(
  request: APIRequestContext,
  accessToken: string,
  body: Record<string, unknown>,
): Promise<McpJsonRpcResponse> {
  const response = await request.post(`${mcpUrl}/mcp`, {
    headers: {
      Authorization: `Bearer ${accessToken}`,
      'Content-Type': 'application/json',
    },
    data: body,
  });
  expect(response.ok(), await responseDiagnostic(`MCP ${body.method}`, response)).toBe(true);
  return (await response.json()) as McpJsonRpcResponse;
}

async function responseDiagnostic(label: string, response: { status(): number; text(): Promise<string> }) {
  const text = await response.text().catch(() => '<unreadable body>');
  return `${label} failed with HTTP ${response.status()}: ${text.slice(0, 1000)}`;
}

function base64Url(input: Buffer): string {
  return input.toString('base64').replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '');
}

interface OAuthClientRegistrationResponse {
  client_id: string;
}

interface OAuthTokenResponse {
  access_token: string;
  scope: string;
}

interface McpJsonRpcResponse {
  result: {
    tools: Array<{ name: string }>;
    isError: boolean;
    content: Array<{ type: string; text: string }>;
  };
  error?: unknown;
}
