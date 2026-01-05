-- Security Service - UUID v7 Function
-- Version: 0.0
-- Description: Creates the uuidv7() function for generating time-sortable UUIDs
-- Reference: https://datatracker.ietf.org/doc/draft-ietf-uuidrev-rfc4122bis/

-- Create uuidv7 function if it doesn't exist
-- This function generates UUID version 7 which is time-sortable
-- Format: timestamp (48 bits) + version (4 bits) + random (12 bits) + variant (2 bits) + random (62 bits)
CREATE OR REPLACE FUNCTION uuidv7() RETURNS uuid
AS $$
DECLARE
    unix_ts_ms bytea;
    uuid_bytes bytea;
BEGIN
    -- Get current timestamp in milliseconds since Unix epoch
    unix_ts_ms = substring(int8send(floor(extract(epoch from clock_timestamp()) * 1000)::bigint) from 3);

    -- Construct UUID v7 bytes
    -- Bytes 0-5: 48-bit timestamp
    -- Byte 6: version (0x7) in high nibble + 12-bit random in low nibble and byte 7
    -- Byte 8: variant (0b10) in high 2 bits + 62-bit random in remaining bits
    uuid_bytes = unix_ts_ms ||
                 gen_random_bytes(2) ||
                 set_byte(gen_random_bytes(1), 0, (b'0111' || substring(get_byte(gen_random_bytes(1), 0)::bit(8) from 5))::bit(8)::int) ||
                 gen_random_bytes(1) ||
                 set_byte(gen_random_bytes(1), 0, (b'10' || substring(get_byte(gen_random_bytes(1), 0)::bit(8) from 3))::bit(8)::int) ||
                 gen_random_bytes(7);

    RETURN encode(uuid_bytes, 'hex')::uuid;
END;
$$ LANGUAGE plpgsql VOLATILE;

COMMENT ON FUNCTION uuidv7() IS 'Generates a UUID v7 (time-sortable UUID with millisecond precision timestamp)';
