/**
 * Invistus Slack Bot — Cloudflare Worker
 *
 * Fluxo async:
 * 1. Recebe evento Slack → responde 200 imediatamente
 * 2. waitUntil: posta "⏳ Trabalhando..." → dispara bridge (fire-and-forget)
 * 3. Bridge processa Goose em background e chama Slack diretamente ao terminar
 */

export interface Env {
  SLACK_BOT_TOKEN: string;
  SLACK_SIGNING_SECRET: string;
  GOOSE_SERVER_SECRET: string;
  SESSIONS: KVNamespace;
  CHANNEL_DEVPOD_MAP: string;
}

// ── Verificação de assinatura Slack ──────────────────────────────────────────

async function verifySlackSignature(
  request: Request,
  rawBody: string,
  signingSecret: string
): Promise<boolean> {
  const timestamp = request.headers.get('x-slack-request-timestamp');
  const slackSig = request.headers.get('x-slack-signature');
  if (!timestamp || !slackSig) return false;
  if (Math.abs(Date.now() / 1000 - parseInt(timestamp, 10)) > 300) return false;

  const sigBase = `v0:${timestamp}:${rawBody}`;
  const key = await crypto.subtle.importKey(
    'raw', new TextEncoder().encode(signingSecret),
    { name: 'HMAC', hash: 'SHA-256' }, false, ['sign']
  );
  const mac = await crypto.subtle.sign('HMAC', key, new TextEncoder().encode(sigBase));
  const computed = 'v0=' + Array.from(new Uint8Array(mac))
    .map(b => b.toString(16).padStart(2, '0')).join('');
  return computed === slackSig;
}

// ── Slack API ─────────────────────────────────────────────────────────────────

async function slackPost(token: string, channel: string, text: string, threadTs?: string): Promise<string> {
  const body: Record<string, string> = { channel, text };
  if (threadTs) body.thread_ts = threadTs;
  const res = await fetch('https://slack.com/api/chat.postMessage', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify(body),
  });
  const data = await res.json() as { ok: boolean; ts: string; error?: string };
  if (!data.ok) console.error('[slack] postMessage error:', data.error);
  return data.ts;
}

// ── Canal → DevPod URL ────────────────────────────────────────────────────────

function parseChannelMap(raw: string): Map<string, string> {
  const map = new Map<string, string>();
  for (const entry of raw.split(',')) {
    const idx = entry.indexOf(':');
    if (idx < 0) continue;
    const channelId = entry.slice(0, idx).trim();
    let url = entry.slice(idx + 1).trim();
    if (!url.startsWith('http')) url = `https://${url}`;
    if (channelId) map.set(channelId, url);
  }
  return map;
}

// ── Handler principal ─────────────────────────────────────────────────────────

export default {
  async fetch(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
    if (request.method !== 'POST') return new Response('Method Not Allowed', { status: 405 });

    const rawBody = await request.text();
    if (!(await verifySlackSignature(request, rawBody, env.SLACK_SIGNING_SECRET))) {
      return new Response('Unauthorized', { status: 401 });
    }

    const payload = JSON.parse(rawBody) as Record<string, unknown>;

    if (payload.type === 'url_verification') {
      return Response.json({ challenge: payload.challenge });
    }

    if (payload.type !== 'event_callback') return new Response('OK');

    const event = payload.event as Record<string, unknown>;
    if (event.type !== 'message' || event.bot_id || event.subtype) return new Response('OK');

    const channelMap = parseChannelMap(env.CHANNEL_DEVPOD_MAP);
    const devpodUrl = channelMap.get(event.channel as string);
    if (!devpodUrl) return new Response('OK');

    ctx.waitUntil(handleGooseRequest(event, devpodUrl, env));
    return new Response('OK');
  },
};

// ── Processamento assíncrono ──────────────────────────────────────────────────
// waitUntil só precisa: postar "⏳" + disparar bridge (fire-and-forget).
// O bridge chama o Slack diretamente quando o Goose terminar.

async function handleGooseRequest(
  event: Record<string, unknown>,
  devpodUrl: string,
  env: Env
): Promise<void> {
  const channel = event.channel as string;
  const ts      = event.ts as string;
  const threadTs = event.thread_ts as string | undefined;
  const text    = event.text as string;

  const isNewTask = !threadTs || threadTs === ts;
  const parentTs  = isNewTask ? ts : threadTs!;

  const sessionName = `slack-${channel}-${parentTs}`.replace(/\./g, '-');
  const sessionKey  = `session:${channel}:${parentTs}`;

  console.log('[handler] channel:', channel, '| isNewTask:', isNewTask, '| session:', sessionName);

  // 1. Posta "⏳ Trabalhando..." e obtém o ts da mensagem para o bridge atualizar depois
  const workingTs = await slackPost(env.SLACK_BOT_TOKEN, channel, '⏳ Trabalhando...', parentTs);
  console.log('[handler] workingTs:', workingTs);

  // 2. Resolve resume
  const resume = !isNewTask && !!(await env.SESSIONS.get(sessionKey));
  if (isNewTask) {
    await env.SESSIONS.put(sessionKey, sessionName, { expirationTtl: 60 * 60 * 24 * 7 });
  }
  console.log('[handler] resume:', resume);

  // 3. Dispara bridge — não aguarda resposta do Goose
  //    O bridge responde 202 imediatamente e atualiza o Slack quando terminar.
  try {
    const res = await fetch(`${devpodUrl}/chat`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(env.GOOSE_SERVER_SECRET ? { Authorization: `Bearer ${env.GOOSE_SERVER_SECRET}` } : {}),
      },
      body: JSON.stringify({
        message:     text,
        session:     sessionName,
        resume,
        channel,
        ts:          workingTs,
        slack_token: env.SLACK_BOT_TOKEN,
      }),
    });
    console.log('[handler] bridge ACK:', res.status);
  } catch (err) {
    console.error('[handler] erro ao chamar bridge:', err);
  }
}
