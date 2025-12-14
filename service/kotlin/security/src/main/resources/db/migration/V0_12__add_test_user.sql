-- Set search path to security schema
SET search_path TO security, public;

-- Insert test user (testuser/test)
-- This user has NO group assigned and is intended for negative testing
-- (testing access control when user has no permissions)
-- 
-- Password: test
-- To generate a new hash, run:
-- ./gradlew :security:test --tests "io.github.salomax.neotool.security.test.GenerateTestUserHash.generateTestUserHash"
INSERT INTO security.users (id, email, display_name, password_hash)
VALUES (
    uuidv7(),
    'testuser@example.com',
    'Test User',
    '$argon2id$v=19$m=65536,t=3,p=1$Q+wZTS3oHnk2tGO/8JoTiw$BC9LIR0RiY++YnVr0R/Ij15HAEL4HtjEM/DRcivtDJs'
)
ON CONFLICT (email) DO NOTHING;

-- Note: The test user is intentionally NOT assigned to any group
-- This allows testing of access control scenarios where users have no permissions
