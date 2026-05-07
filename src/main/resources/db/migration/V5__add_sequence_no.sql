-- Sequence number for event integrity / gap detection
ALTER TABLE safety_events
    ADD COLUMN sequence_no BIGINT GENERATED ALWAYS AS IDENTITY;

CREATE UNIQUE INDEX idx_safety_events_seq ON safety_events (sequence_no);
