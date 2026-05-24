package com.marketinghub.customer.importjob;

import com.marketinghub.customer.Customer;
import com.marketinghub.customer.CustomerRepository;
import com.marketinghub.customer.OptInStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CsvImportProcessorTest {

    @Mock private CsvImportJobRepository jobRepository;
    @Mock private CustomerRepository customerRepository;

    /** A TransactionManager that does nothing so TransactionTemplate just runs the lambda. */
    private final PlatformTransactionManager noopTm = new PlatformTransactionManager() {
        @Override
        public TransactionStatus getTransaction(TransactionDefinition def) {
            return new SimpleTransactionStatus();
        }
        @Override public void commit(TransactionStatus s) {}
        @Override public void rollback(TransactionStatus s) {}
    };

    private CsvImportProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new CsvImportProcessor(jobRepository, customerRepository, noopTm);
    }

    @Test
    void process_happyPath_insertsCustomers() {
        UUID jobId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        CsvImportJob job = stubJob(jobId, tenantId);
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(customerRepository.findByTenantIdAndPhoneE164(eq(tenantId), anyString()))
            .thenReturn(Optional.empty());
        Map<String, Customer> saved = new HashMap<>();
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> {
            Customer c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            saved.put(c.getPhoneE164(), c);
            return c;
        });

        String csv = """
            phone_e164,full_name,tags,opt_in_status
            +14155550101,Alice,vip;promo,OPTED_IN
            +14155550102,Bob,,UNKNOWN
            +14155550103,,,
            """;

        processor.process(jobId, csv.getBytes(StandardCharsets.UTF_8));

        assertThat(job.getStatus()).isEqualTo(ImportJobStatus.COMPLETED);
        assertThat(job.getTotalRows()).isEqualTo(3);
        assertThat(job.getProcessedRows()).isEqualTo(3);
        assertThat(job.getFailedRows()).isEqualTo(0);
        assertThat(saved).hasSize(3);
        assertThat(saved.get("+14155550101").getTags()).containsExactly("vip", "promo");
        assertThat(saved.get("+14155550101").getOptInStatus()).isEqualTo(OptInStatus.OPTED_IN);
        assertThat(saved.get("+14155550103").getFullName()).isNull();
    }

    @Test
    void process_invalidPhone_recordsErrorAndContinues() {
        UUID jobId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        CsvImportJob job = stubJob(jobId, tenantId);
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(customerRepository.findByTenantIdAndPhoneE164(eq(tenantId), anyString()))
            .thenReturn(Optional.empty());
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        String csv = """
            phone_e164,full_name,tags,opt_in_status
            +14155550101,Alice,,
            123,Bob,,
            14155550103,Charlie,,
            +14155550104,Dave,,WRONG_OPT_IN
            """;

        processor.process(jobId, csv.getBytes(StandardCharsets.UTF_8));

        assertThat(job.getStatus()).isEqualTo(ImportJobStatus.COMPLETED);
        assertThat(job.getTotalRows()).isEqualTo(4);
        assertThat(job.getProcessedRows()).isEqualTo(1);
        assertThat(job.getFailedRows()).isEqualTo(3);
        assertThat(job.getErrorLog())
            .extracting(ImportRowError::reason)
            .anySatisfy(r -> assertThat(r).contains("phone_e164 not E.164"))
            .anySatisfy(r -> assertThat(r).contains("opt_in_status invalid"));
    }

    @Test
    void process_upsertsExistingCustomer() {
        UUID jobId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        CsvImportJob job = stubJob(jobId, tenantId);
        Customer existing = new Customer();
        existing.setId(UUID.randomUUID());
        existing.setTenantId(tenantId);
        existing.setPhoneE164("+14155550101");
        existing.setFullName("Old Name");
        existing.setTags(new ArrayList<>(List.of("old")));
        existing.setOptInStatus(OptInStatus.UNKNOWN);

        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(customerRepository.findByTenantIdAndPhoneE164(tenantId, "+14155550101"))
            .thenReturn(Optional.of(existing));

        String csv = """
            phone_e164,full_name,tags,opt_in_status
            +14155550101,New Name,vip;new,OPTED_IN
            """;

        processor.process(jobId, csv.getBytes(StandardCharsets.UTF_8));

        // No new insert happened (Mockito strict: would fail if save() were called)
        verify(customerRepository, never()).save(any());
        // Existing row was mutated in place
        assertThat(existing.getFullName()).isEqualTo("New Name");
        assertThat(existing.getTags()).containsExactly("vip", "new");
        assertThat(existing.getOptInStatus()).isEqualTo(OptInStatus.OPTED_IN);
        assertThat(job.getProcessedRows()).isEqualTo(1);
        assertThat(job.getFailedRows()).isEqualTo(0);
    }

    @Test
    void process_missingHeader_marksJobFailed() {
        UUID jobId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        CsvImportJob job = stubJob(jobId, tenantId);
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));

        String csv = """
            +14155550101,Alice,,
            +14155550102,Bob,,
            """;

        processor.process(jobId, csv.getBytes(StandardCharsets.UTF_8));

        assertThat(job.getStatus()).isEqualTo(ImportJobStatus.FAILED);
        assertThat(job.getErrorLog())
            .extracting(ImportRowError::reason)
            .anySatisfy(r -> assertThat(r).contains("Header row required"));
    }

    private static CsvImportJob stubJob(UUID id, UUID tenantId) {
        CsvImportJob j = new CsvImportJob();
        j.setId(id);
        j.setTenantId(tenantId);
        j.setCreatedByUserId(UUID.randomUUID());
        j.setFileName("test.csv");
        j.setStatus(ImportJobStatus.PENDING);
        j.setErrorLog(new ArrayList<>());
        return j;
    }
}
