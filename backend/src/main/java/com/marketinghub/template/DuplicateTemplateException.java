package com.marketinghub.template;

public class DuplicateTemplateException extends RuntimeException {
    public DuplicateTemplateException(String whatsappTemplateName, String language) {
        super("A template named '" + whatsappTemplateName
            + "' in language '" + language + "' already exists in this tenant");
    }
}
