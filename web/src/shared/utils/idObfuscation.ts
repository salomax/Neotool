/**
 * ID Obfuscation Utility
 * 
 * Provides URL-safe encoding/decoding for IDs to obfuscate them in URLs.
 * Uses base64url encoding which is URL-safe (no special characters that need encoding).
 * 
 * This is for obfuscation only, not security. The encoded IDs can be easily decoded.
 * 
 * @example
 * ```ts
 * const encoded = encodeId("26264220");
 * // Returns: "MjYyNjQyMjA="
 * 
 * const decoded = decodeId("MjYyNjQyMjA=");
 * // Returns: "26264220"
 * ```
 */

/**
 * Encodes an ID to a URL-safe base64 string
 * @param id - The ID to encode
 * @returns The encoded ID (base64url format)
 * @throws Error if encoding fails
 */
export function encodeId(id: string): string {
  if (!id) {
    throw new Error("ID cannot be empty");
  }

  try {
    // Convert string to base64
    let base64: string;

    if (typeof Buffer !== 'undefined') {
      // Node.js environment
      base64 = Buffer.from(id, 'utf8').toString('base64');
    } else {
      // Browser environment
      base64 = btoa(unescape(encodeURIComponent(id)));
    }

    // Convert base64 to base64url (URL-safe)
    // Replace + with -, / with _, and remove padding =
    return base64
      .replace(/\+/g, '-')
      .replace(/\//g, '_')
      .replace(/=+$/, '');
  } catch (error) {
    throw new Error(`Failed to encode ID: ${error instanceof Error ? error.message : 'Unknown error'}`);
  }
}

/**
 * Decodes a base64url-encoded ID back to the original ID
 * @param encodedId - The encoded ID (base64url format)
 * @returns The decoded ID
 * @throws Error if decoding fails or input is invalid
 */
export function decodeId(encodedId: string): string {
  if (!encodedId) {
    throw new Error("Encoded ID cannot be empty");
  }

  // Validate characters (base64url or base64)
  if (!/^[a-zA-Z0-9\-_+/=]+$/.test(encodedId)) {
    throw new Error("Invalid encoded ID");
  }

  try {
    // Convert base64url back to base64
    // Replace - with +, _ with /, and add padding if needed
    let base64 = encodedId
      .replace(/-/g, '+')
      .replace(/_/g, '/');

    // Add padding if needed (base64 requires length to be multiple of 4)
    const padding = base64.length % 4;
    if (padding) {
      base64 += '='.repeat(4 - padding);
    }

    // Decode from base64
    if (typeof Buffer !== 'undefined') {
      // Node.js environment
      return Buffer.from(base64, 'base64').toString('utf8');
    } else {
      // Browser environment
      return decodeURIComponent(escape(atob(base64)));
    }
  } catch (error) {
    throw new Error(`Failed to decode ID: ${error instanceof Error ? error.message : 'Invalid encoded ID'}`);
  }
}

/**
 * Safely decodes an ID, returning null if decoding fails
 * Useful for handling potentially invalid encoded IDs from URLs
 * @param encodedId - The encoded ID (base64url format)
 * @returns The decoded ID, or null if decoding fails
 */
export function safeDecodeId(encodedId: string | null | undefined): string | null {
  if (!encodedId) {
    return null;
  }

  try {
    return decodeId(encodedId);
  } catch {
    return null;
  }
}
