package com.marketinghub.template;

public enum TemplateStatus {
    APPROVED,
    PENDING,
    REJECTED,
    PAUSED,
    DISABLED,
    /** Set by the Meta sync when a local template has no matching name+language in the WABA. */
    NOT_FOUND
}
