package com.marketinghub.knowledge;

import java.util.UUID;

public class KnowledgeDocumentNotFoundException extends RuntimeException {
    public KnowledgeDocumentNotFoundException(UUID id) {
        super("Knowledge document not found: " + id);
    }
}
