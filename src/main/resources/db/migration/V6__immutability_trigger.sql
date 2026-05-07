-- Prevent tampering with core event fields after insert (SIL2 audit integrity)
CREATE OR REPLACE FUNCTION fn_prevent_event_tampering()
RETURNS TRIGGER AS $$
BEGIN
    IF OLD.event_time    IS DISTINCT FROM NEW.event_time    OR
       OLD.receive_time  IS DISTINCT FROM NEW.receive_time  OR
       OLD.source_addr   IS DISTINCT FROM NEW.source_addr   OR
       OLD.message_type  IS DISTINCT FROM NEW.message_type  OR
       OLD.severity      IS DISTINCT FROM NEW.severity      OR
       OLD.raw_payload   IS DISTINCT FROM NEW.raw_payload   OR
       OLD.description   IS DISTINCT FROM NEW.description
    THEN
        RAISE EXCEPTION
            'Safety event core fields are immutable after insert [id=%]', OLD.id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_safety_event_immutability
BEFORE UPDATE ON safety_events
FOR EACH ROW EXECUTE FUNCTION fn_prevent_event_tampering();
