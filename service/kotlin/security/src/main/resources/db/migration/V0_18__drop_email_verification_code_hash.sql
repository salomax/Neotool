-- Remove code-based verification; keep only magic link (token).
-- See docs/03-features/security/authentication/signup/email-verification-design.md

SET search_path TO security, public;

ALTER TABLE security.email_verifications
  DROP COLUMN IF EXISTS code_hash;
