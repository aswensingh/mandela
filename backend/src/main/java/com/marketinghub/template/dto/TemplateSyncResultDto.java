package com.marketinghub.template.dto;

/**
 * Summary of a "sync template status from Meta" run.
 *
 * @param checked   how many local templates were compared against Meta
 * @param updated   how many had their status changed by the sync
 * @param notFound  how many local templates had no matching name+language in the WABA
 * @param metaCount how many templates Meta returned for the WABA
 * @param message   human-readable summary (also covers the mock-mode / no-WABA short-circuits)
 */
public record TemplateSyncResultDto(
    int checked,
    int updated,
    int notFound,
    int metaCount,
    String message
) {}
