-- Campaign send mode: TEMPLATE (approved Meta template — reaches any customer) vs
-- FREE_TEXT (free-form text — only delivered inside the 24h customer-service window).
-- Existing rows are all template-based, so default to TEMPLATE and keep template_id on them.
ALTER TABLE campaigns
    ADD COLUMN send_mode text NOT NULL DEFAULT 'TEMPLATE',
    ADD COLUMN body_text text;

-- Free-text campaigns carry no template, so template_id can no longer be mandatory.
ALTER TABLE campaigns
    ALTER COLUMN template_id DROP NOT NULL;

ALTER TABLE campaigns
    ADD CONSTRAINT campaigns_send_mode_chk
        CHECK (send_mode IN ('TEMPLATE', 'FREE_TEXT')),
    ADD CONSTRAINT campaigns_mode_payload_chk
        CHECK (
            (send_mode = 'TEMPLATE' AND template_id IS NOT NULL)
            OR (send_mode = 'FREE_TEXT' AND body_text IS NOT NULL)
        );

-- WhatsApp Business Account (WABA) ID — needed to query Meta's
-- GET /{waba_id}/message_templates endpoint for real template approval status.
ALTER TABLE tenants
    ADD COLUMN whatsapp_business_account_id text;
