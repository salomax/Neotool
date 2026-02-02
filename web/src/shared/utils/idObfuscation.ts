import Hashids from 'hashids';

/**
 * ID Obfuscation Utility
 * 
 * Provides URL-safe encoding/decoding for IDs to obfuscate them in URLs.
 * Uses Hashids with hex encoding to support both numeric and string IDs.
 * 
 * This is for obfuscation only, not security. The encoded IDs can be easily decoded.
 * 
 * @example
 * ```ts
 * const encoded = encodeId("26264220");
 * // Returns obfuscated string e.g. "Nk7x9"
 * 
 * const decoded = decodeId("Nk7x9");
 * // Returns: "26264220"
 * ```
 */

const SALT = 'invistus-financial-data-salt';
const MIN_LENGTH = 8;
const hashids = new Hashids(SALT, MIN_LENGTH);

/**
 * Encodes an ID to a URL-safe hashid string
 * @param id - The ID to encode
 * @returns The encoded ID (hashid format)
 * @throws Error if encoding fails
 */
export function encodeId(id: string): string {
  if (!id) {
    throw new Error("ID cannot be empty");
  }

  try {
    // Convert string to hex to support non-numeric IDs and preserve leading zeros
    const hex = Buffer.from(id, 'utf8').toString('hex');
    return hashids.encodeHex(hex);
  } catch (error) {
    throw new Error(`Failed to encode ID: ${error instanceof Error ? error.message : 'Unknown error'}`);
  }
}

/**
 * Decodes a hashid-encoded ID back to the original ID
 * @param encodedId - The encoded ID (hashid format)
 * @returns The decoded ID
 * @throws Error if decoding fails or input is invalid
 */
export function decodeId(encodedId: string): string {
  if (!encodedId) {
    throw new Error("Encoded ID cannot be empty");
  }

  try {
    const hex = hashids.decodeHex(encodedId);
    if (!hex) {
       // hashids.decodeHex returns empty string if invalid
       throw new Error('Invalid encoded ID');
    }
    return Buffer.from(hex, 'hex').toString('utf8');
  } catch (error) {
    throw new Error(`Failed to decode ID: ${error instanceof Error ? error.message : 'Invalid encoded ID'}`);
  }
}

/**
 * Safely decodes an ID, returning null if decoding fails
 * Useful for handling potentially invalid encoded IDs from URLs
 * @param encodedId - The encoded ID (hashid format)
 * @returns The decoded ID, or null if decoding fails
 */
export function safeDecodeId(encodedId: string | null | undefined): string | null {
  if (!encodedId) {
    return null;
  }

  try {
    return decodeId(encodedId);
  } catch (error) {
    return null;
  }
}
