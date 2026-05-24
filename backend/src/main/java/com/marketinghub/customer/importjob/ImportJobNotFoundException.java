package com.marketinghub.customer.importjob;

import java.util.UUID;

public class ImportJobNotFoundException extends RuntimeException {
    public ImportJobNotFoundException(UUID id) {
        super("Import job not found: " + id);
    }
}
