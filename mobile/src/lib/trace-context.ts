/**
 * W3C Trace Context helper for mobile GraphQL requests.
 *
 * Adds `traceparent` so Apollo Router can propagate a single distributed
 * trace to downstream services.
 */

const TRACEPARENT_VERSION = '00';
const TRACE_FLAGS_SAMPLED = '01';

function getRandomBytes(size: number): Uint8Array {
  const bytes = new Uint8Array(size);
  const cryptoApi = (globalThis as { crypto?: { getRandomValues?: (array: Uint8Array) => Uint8Array } }).crypto;

  if (cryptoApi?.getRandomValues) {
    return cryptoApi.getRandomValues(bytes);
  }

  for (let i = 0; i < size; i += 1) {
    bytes[i] = Math.floor(Math.random() * 256);
  }
  return bytes;
}

function toHex(bytes: Uint8Array): string {
  return Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('');
}

function isAllZeros(value: string): boolean {
  return /^0+$/.test(value);
}

function generateTraceId(): string {
  let traceId = toHex(getRandomBytes(16));
  while (isAllZeros(traceId)) {
    traceId = toHex(getRandomBytes(16));
  }
  return traceId;
}

function generateSpanId(): string {
  let spanId = toHex(getRandomBytes(8));
  while (isAllZeros(spanId)) {
    spanId = toHex(getRandomBytes(8));
  }
  return spanId;
}

export function buildTraceparent(): string {
  return `${TRACEPARENT_VERSION}-${generateTraceId()}-${generateSpanId()}-${TRACE_FLAGS_SAMPLED}`;
}

