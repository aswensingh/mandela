package com.marketinghub.template;

import java.util.UUID;

public class TemplateNotFoundException extends RuntimeException {
    public TemplateNotFoundException(UUID id) {
        super("Template not found: " + id);
    }
}
