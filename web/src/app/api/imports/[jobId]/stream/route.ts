import { NextRequest } from 'next/server';

export async function GET(
  req: NextRequest,
  { params }: { params: Promise<{ jobId: string }> }
) {
  const token = req.headers.get('x-auth-token');
  if (!token) {
    return new Response('Unauthorized', { status: 401 });
  }

  const { jobId } = await params;
  const bankingServiceUrl = process.env.BANKING_SERVICE_INTERNAL_URL ?? '';
  if (!bankingServiceUrl) {
    return new Response('Service URL not configured', { status: 503 });
  }

  const upstream = await fetch(
    `${bankingServiceUrl}/imports/${jobId}/events`,
    {
      headers: { Authorization: `Bearer ${token}` },
      // @ts-expect-error — Node.js fetch duplex required for streaming
      duplex: 'half',
    }
  );

  if (!upstream.ok) {
    return new Response(upstream.statusText, { status: upstream.status });
  }

  return new Response(upstream.body, {
    headers: {
      'Content-Type': 'text/event-stream',
      'Cache-Control': 'no-cache, no-transform',
      Connection: 'keep-alive',
      'X-Accel-Buffering': 'no', // Disable Nginx buffering for SSE
    },
  });
}
