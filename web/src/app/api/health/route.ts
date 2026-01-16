import { NextResponse } from 'next/server';

/**
 * Health check endpoint for Kubernetes liveness and readiness probes
 *
 * This endpoint is used by Kubernetes to determine if the container is:
 * - Alive (livenessProbe): If this fails, Kubernetes will restart the container
 * - Ready (readinessProbe): If this fails, Kubernetes will stop sending traffic to the pod
 *
 * @returns 200 OK if the service is healthy
 */
export async function GET() {
  try {
    // Add any additional health checks here if needed
    // For example: database connectivity, external service checks, etc.

    return NextResponse.json(
      {
        status: 'ok',
        timestamp: new Date().toISOString(),
        uptime: process.uptime(),
      },
      { status: 200 }
    );
  } catch (error) {
    // If there's an error, return 503 Service Unavailable
    return NextResponse.json(
      {
        status: 'error',
        timestamp: new Date().toISOString(),
        error: error instanceof Error ? error.message : 'Unknown error',
      },
      { status: 503 }
    );
  }
}
