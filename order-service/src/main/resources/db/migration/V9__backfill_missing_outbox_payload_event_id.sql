-- V8 generated an ID for legacy rows without one. Publish that same ID on retries.
UPDATE outbox_events
SET payload = jsonb_set(payload::jsonb, '{eventId}', to_jsonb(event_id))::text
WHERE payload::jsonb ->> 'eventId' IS NULL;
