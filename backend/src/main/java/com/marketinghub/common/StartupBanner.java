package com.marketinghub.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Prints an unmistakable banner once the application context is fully up
 * ({@link ApplicationReadyEvent}). Seeing it means startup succeeded; if startup fails you
 * get the error/stack trace instead, never this. ASCII-only so it renders correctly on the
 * Windows console (which isn't UTF-8 by default).
 */
@Component
public class StartupBanner {

    private static final Logger log = LoggerFactory.getLogger(StartupBanner.class);

    private final String port;

    public StartupBanner(@Value("${server.port:8080}") String port) {
        this.port = port;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        log.info("\n"
            + "============================================================\n"
            + "  MARKETINGHUB BACKEND READY  ->  http://localhost:" + port + "\n"
            + "============================================================");
    }
}
