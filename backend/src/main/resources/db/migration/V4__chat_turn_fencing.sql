-- A newer turn invalidates writes from a turn whose lock connection was lost.
ALTER TABLE chat_session ADD COLUMN turn_generation BIGINT NOT NULL DEFAULT 0;
