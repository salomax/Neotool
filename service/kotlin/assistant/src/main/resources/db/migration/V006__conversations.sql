-- ============================================================================
-- Assistant Service - Conversation Persistence
-- Version: V006__conversations.sql
-- Purpose:
--   - Persist chat conversation state in database
--   - Replace in-memory conversation storage
-- ============================================================================

SET search_path TO assistant, public;

-- ============================================================================
-- Table: conversations
-- Purpose: Conversation state by session
-- ============================================================================

CREATE TABLE IF NOT EXISTS assistant.conversations (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    account_id UUID NOT NULL,
    session_id TEXT NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('ACTIVE', 'ESCALATED')),
    escalation_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_conversations_account
        FOREIGN KEY (account_id)
        REFERENCES security.accounts(id)
        ON DELETE CASCADE,

    CONSTRAINT uq_conversations_account_session_id UNIQUE (account_id, session_id)
);

CREATE INDEX IF NOT EXISTS idx_conversations_account_session
    ON assistant.conversations(account_id, session_id);

CREATE INDEX IF NOT EXISTS idx_conversations_status
    ON assistant.conversations(status);

-- ============================================================================
-- Table: conversation_messages
-- Purpose: Ordered messages for each conversation
-- ============================================================================

CREATE TABLE IF NOT EXISTS assistant.conversation_messages (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    conversation_id UUID NOT NULL,
    sequence_no INTEGER NOT NULL CHECK (sequence_no > 0),
    role TEXT NOT NULL CHECK (role IN ('SYSTEM', 'USER', 'ASSISTANT', 'FUNCTION')),
    content TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_conversation_messages_conversation
        FOREIGN KEY (conversation_id)
        REFERENCES assistant.conversations(id)
        ON DELETE CASCADE,

    CONSTRAINT uq_conversation_messages_order
        UNIQUE (conversation_id, sequence_no)
);

CREATE INDEX IF NOT EXISTS idx_conversation_messages_conversation_seq
    ON assistant.conversation_messages(conversation_id, sequence_no ASC);
