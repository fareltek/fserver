-- EN 50129 / EN 50126 Compliant Safety Event Table
-- Railway Signalling Event Recording

CREATE TABLE IF NOT EXISTS safety_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Timing (EN 50159 requirement)
    event_time TIMESTAMP WITH TIME ZONE NOT NULL,
    receive_time TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Source tracking (SIL-2: her cihaz izlenebilir olmalı)
    source_addr VARCHAR(45),

    -- Frame identification
    sequence INTEGER,
    source_id INTEGER,

    -- Event classification
    message_type VARCHAR(32) NOT NULL,
    device_type VARCHAR(16),
    device_id INTEGER,

    -- Severity (SIL-2 classification)
    severity VARCHAR(16) NOT NULL DEFAULT 'INFO',

    -- Event details
    event_code INTEGER,
    event_data INTEGER,
    event_flags INTEGER,

    -- Raw data for audit
    raw_payload BYTEA,

    -- Human readable
    description TEXT,

    -- Alarm acknowledgment
    acknowledged BOOLEAN DEFAULT FALSE,
    acknowledged_by VARCHAR(64),
    acknowledged_time TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_safety_events_time     ON safety_events(event_time DESC);
CREATE INDEX IF NOT EXISTS idx_safety_events_device   ON safety_events(device_type, device_id);
CREATE INDEX IF NOT EXISTS idx_safety_events_severity ON safety_events(severity);
CREATE INDEX IF NOT EXISTS idx_safety_events_unack    ON safety_events(acknowledged) WHERE acknowledged = FALSE;
CREATE INDEX IF NOT EXISTS idx_safety_events_type     ON safety_events(message_type);
CREATE INDEX IF NOT EXISTS idx_safety_events_source   ON safety_events(source_addr);

COMMENT ON TABLE safety_events IS 'EN 50129 compliant safety event recording for railway signalling system';
COMMENT ON COLUMN safety_events.event_time   IS 'Event timestamp from CPU (UTC)';
COMMENT ON COLUMN safety_events.receive_time IS 'Server receive timestamp (UTC)';
COMMENT ON COLUMN safety_events.source_addr  IS 'Waveshare module IP:port';
COMMENT ON COLUMN safety_events.sequence     IS 'Frame sequence number for loss detection';
COMMENT ON COLUMN safety_events.severity     IS 'SIL-2 severity: INFO, WARNING, ALARM, CRITICAL';
