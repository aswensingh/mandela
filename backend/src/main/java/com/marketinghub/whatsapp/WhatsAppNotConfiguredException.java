package com.marketinghub.whatsapp;

public class WhatsAppNotConfiguredException extends RuntimeException {
    public WhatsAppNotConfiguredException() {
        super("WhatsApp credentials are not configured for this tenant");
    }
}
