package com.marketinghub.customer.importjob;

import com.marketinghub.customer.Customer;
import com.marketinghub.customer.CustomerRepository;
import com.marketinghub.customer.OptInStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class CsvImportProcessor {

    private static final Logger log = LoggerFactory.getLogger(CsvImportProcessor.class);
    private static final Pattern E164 = Pattern.compile("^\\+[1-9]\\d{1,14}$");
    private static final int PROGRESS_FLUSH_EVERY = 50;
    private static final int MAX_ERROR_LOG_ENTRIES = 200;

    private final CsvImportJobRepository jobRepository;
    private final CustomerRepository customerRepository;
    private final TransactionTemplate tx;

    public CsvImportProcessor(
        CsvImportJobRepository jobRepository,
        CustomerRepository customerRepository,
        PlatformTransactionManager transactionManager
    ) {
        this.jobRepository = jobRepository;
        this.customerRepository = customerRepository;
        this.tx = new TransactionTemplate(transactionManager);
    }

    @Async("csvImportExecutor")
    public void process(UUID jobId, byte[] fileBytes) {
        log.info("CSV import {} starting", jobId);
        List<String> rows;
        try {
            rows = readLines(fileBytes);
        } catch (Exception e) {
            log.warn("CSV import {} failed to read file", jobId, e);
            markFailed(jobId, "Could not read file: " + e.getMessage());
            return;
        }

        // First non-empty line should be the header.
        if (rows.isEmpty()) {
            markFailed(jobId, "File is empty");
            return;
        }
        String header = rows.get(0).trim().toLowerCase();
        if (!header.startsWith("phone_e164")) {
            markFailed(jobId, "Header row required: phone_e164,full_name,tags,opt_in_status");
            return;
        }

        List<String> dataRows = rows.subList(1, rows.size());
        int total = dataRows.size();

        tx.executeWithoutResult(s -> {
            CsvImportJob job = jobRepository.findById(jobId).orElseThrow();
            job.setStatus(ImportJobStatus.PROCESSING);
            job.setTotalRows(total);
        });

        UUID tenantId = tx.execute(s -> jobRepository.findById(jobId).orElseThrow().getTenantId());

        int processed = 0;
        int failed = 0;
        List<ImportRowError> errors = new ArrayList<>();

        for (int i = 0; i < dataRows.size(); i++) {
            String line = dataRows.get(i);
            int rowNumber = i + 2; // 1-indexed including header
            if (line.isBlank()) {
                processed++;
                continue;
            }
            try {
                tx.executeWithoutResult(s -> upsertRow(tenantId, line));
                processed++;
            } catch (RowValidationException e) {
                failed++;
                if (errors.size() < MAX_ERROR_LOG_ENTRIES) {
                    errors.add(new ImportRowError(rowNumber, e.getMessage(), line));
                }
            } catch (Exception e) {
                failed++;
                if (errors.size() < MAX_ERROR_LOG_ENTRIES) {
                    errors.add(new ImportRowError(rowNumber, "Unexpected: " + e.getClass().getSimpleName() + ": " + e.getMessage(), line));
                }
                log.warn("CSV import {} row {} crashed", jobId, rowNumber, e);
            }

            if ((processed + failed) % PROGRESS_FLUSH_EVERY == 0) {
                flushProgress(jobId, processed, failed, errors);
            }
        }

        finishJob(jobId, processed, failed, errors);
        log.info("CSV import {} done — processed={}, failed={}", jobId, processed, failed);
    }

    private void upsertRow(UUID tenantId, String line) {
        String[] parts = splitCsvLine(line);
        if (parts.length < 1 || parts[0].isBlank()) {
            throw new RowValidationException("phone_e164 column is empty");
        }
        String phone = parts[0].trim();
        if (!E164.matcher(phone).matches()) {
            throw new RowValidationException("phone_e164 not E.164: " + phone);
        }
        String fullName = parts.length > 1 && !parts[1].isBlank() ? parts[1].trim() : null;
        List<String> tags = List.of();
        if (parts.length > 2 && !parts[2].isBlank()) {
            tags = Arrays.stream(parts[2].split(";")).map(String::trim).filter(s -> !s.isBlank()).toList();
        }
        OptInStatus optIn = OptInStatus.UNKNOWN;
        if (parts.length > 3 && !parts[3].isBlank()) {
            try {
                optIn = OptInStatus.valueOf(parts[3].trim().toUpperCase());
            } catch (IllegalArgumentException ex) {
                throw new RowValidationException("opt_in_status invalid: " + parts[3]);
            }
        }

        Customer existing = customerRepository.findByTenantIdAndPhoneE164(tenantId, phone).orElse(null);
        if (existing == null) {
            Customer c = new Customer();
            c.setTenantId(tenantId);
            c.setPhoneE164(phone);
            c.setFullName(fullName);
            c.setTags(new ArrayList<>(tags));
            c.setOptInStatus(optIn);
            customerRepository.save(c);
        } else {
            if (fullName != null) existing.setFullName(fullName);
            existing.setTags(new ArrayList<>(tags));
            existing.setOptInStatus(optIn);
        }
    }

    private void flushProgress(UUID jobId, int processed, int failed, List<ImportRowError> errors) {
        tx.executeWithoutResult(s -> {
            CsvImportJob j = jobRepository.findById(jobId).orElseThrow();
            j.setProcessedRows(processed);
            j.setFailedRows(failed);
            j.setErrorLog(new ArrayList<>(errors));
        });
    }

    private void finishJob(UUID jobId, int processed, int failed, List<ImportRowError> errors) {
        tx.executeWithoutResult(s -> {
            CsvImportJob j = jobRepository.findById(jobId).orElseThrow();
            j.setProcessedRows(processed);
            j.setFailedRows(failed);
            j.setErrorLog(new ArrayList<>(errors));
            j.setStatus(ImportJobStatus.COMPLETED);
            j.setCompletedAt(Instant.now());
        });
    }

    private void markFailed(UUID jobId, String reason) {
        tx.executeWithoutResult(s -> {
            CsvImportJob j = jobRepository.findById(jobId).orElseThrow();
            j.setStatus(ImportJobStatus.FAILED);
            j.setCompletedAt(Instant.now());
            List<ImportRowError> errs = new ArrayList<>(j.getErrorLog());
            errs.add(new ImportRowError(0, reason, null));
            j.setErrorLog(errs);
        });
    }

    private static List<String> readLines(byte[] fileBytes) throws Exception {
        try (BufferedReader r = new BufferedReader(
            new InputStreamReader(new ByteArrayInputStream(fileBytes), StandardCharsets.UTF_8))) {
            List<String> out = new ArrayList<>();
            String line;
            while ((line = r.readLine()) != null) {
                out.add(line);
            }
            return out;
        }
    }

    /**
     * Minimal CSV split. Supports unquoted commas only; quoted fields aren't needed for the
     * documented format (phone_e164,full_name,tags,opt_in_status) because tags use ';'.
     * If we later want commas in full_name, swap this for Apache Commons CSV.
     */
    private static String[] splitCsvLine(String line) {
        return line.split(",", -1);
    }

    private static final class RowValidationException extends RuntimeException {
        RowValidationException(String msg) { super(msg); }
    }
}
