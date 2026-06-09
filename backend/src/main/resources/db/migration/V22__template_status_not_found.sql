-- The V8 inline CHECK on message_templates.status predates the NOT_FOUND value, which the
-- "Sync from Meta" feature writes when a local template has no matching name+language in the
-- WABA. Widen the constraint so the sync can persist that status.
ALTER TABLE message_templates
    DROP CONSTRAINT message_templates_status_check;

ALTER TABLE message_templates
    ADD CONSTRAINT message_templates_status_check
        CHECK (status IN ('APPROVED', 'PENDING', 'REJECTED', 'PAUSED', 'DISABLED', 'NOT_FOUND'));
