SET search_path TO assistant, public;

ALTER TABLE assistant.tools
    ADD COLUMN IF NOT EXISTS sensitive_input_fields JSONB NOT NULL DEFAULT '[]'::jsonb;
