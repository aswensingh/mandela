package com.marketinghub.customer.importjob;

/** One failed row in a CSV import. Stored in csv_import_jobs.error_log (jsonb). */
public record ImportRowError(int rowNumber, String reason, String rawLine) {
    public ImportRowError() {
        this(0, null, null);
    }
}
