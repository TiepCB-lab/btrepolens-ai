-- Busy is derived from a transaction-scoped advisory lock, not persistent state.
ALTER TABLE chat_session DROP COLUMN busy;
