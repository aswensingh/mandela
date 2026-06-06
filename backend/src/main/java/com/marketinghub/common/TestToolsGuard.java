package com.marketinghub.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Central gate for the WhatsApp "test tools" — the Send Test Message and Simulate Inbound
 * features. These are debugging aids that are hidden by default. Toggle with the
 * {@code WHATSAPP_TEST_TOOLS_ENABLED} env var -> {@code whatsapp.test-tools.enabled} property.
 *
 * The frontend reads {@link #enabled()} (surfaced via WhatsAppConfigStatusDto) to hide the
 * corresponding cards; this guard is the matching server-side enforcement so the endpoints
 * cannot be called when the tools are turned off.
 */
@Component
public class TestToolsGuard {

    private final boolean enabled;

    public TestToolsGuard(@Value("${whatsapp.test-tools.enabled:false}") boolean enabled) {
        this.enabled = enabled;
    }

    /** Whether the WhatsApp test/debug tools are currently enabled. */
    public boolean enabled() {
        return enabled;
    }

    /** Reject the call with 403 when the test tools are disabled. */
    public void requireEnabled() {
        if (!enabled) {
            throw new AccessDeniedException(
                "WhatsApp test tools are disabled. Set WHATSAPP_TEST_TOOLS_ENABLED=true to enable them.");
        }
    }
}
