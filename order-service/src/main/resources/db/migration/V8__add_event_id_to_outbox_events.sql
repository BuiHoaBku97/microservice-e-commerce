ALTER TABLE outbox_events ADD COLUMN event_id VARCHAR(100);

UPDATE outbox_events
SET event_id = COALESCE(payload::jsonb ->> 'eventId', md5(random()::text || clock_timestamp()::text))
WHERE event_id IS NULL;

ALTER TABLE outbox_events ALTER COLUMN event_id SET NOT NULL;
CREATE UNIQUE INDEX uk_order_outbox_events_event_id ON outbox_events (event_id);
