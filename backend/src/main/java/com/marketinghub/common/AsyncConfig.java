package com.marketinghub.common;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Dedicated executor for CSV import jobs. Sized small on purpose for Phase 8 —
     * imports are I/O-bound (DB upserts) and we don't want a CSV upload spike to
     * starve other request threads.
     */
    @Bean(name = "csvImportExecutor")
    public Executor csvImportExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(2);
        exec.setMaxPoolSize(4);
        exec.setQueueCapacity(50);
        exec.setThreadNamePrefix("csv-import-");
        exec.initialize();
        return exec;
    }
}
