-- Remove legacy sequence/source_id columns; device_type and device_id are the canonical identifiers
ALTER TABLE safety_events DROP COLUMN IF EXISTS sequence;
ALTER TABLE safety_events DROP COLUMN IF EXISTS source_id;

DROP INDEX IF EXISTS idx_safety_events_device;
CREATE INDEX IF NOT EXISTS idx_safety_events_device ON safety_events(device_type, device_id);
