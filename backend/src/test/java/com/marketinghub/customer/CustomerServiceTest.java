package com.marketinghub.customer;

import com.marketinghub.customer.dto.CreateCustomerRequest;
import com.marketinghub.customer.dto.CustomerDto;
import com.marketinghub.customer.dto.UpdateCustomerRequest;
import com.marketinghub.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock private CustomerRepository customerRepository;

    private CustomerService customerService;

    private static final UUID TENANT_A = UUID.randomUUID();
    private static final UUID TENANT_B = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        customerService = new CustomerService(customerRepository);
        TenantContext.setTenantId(TENANT_A);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void create_savesWithTenantScope() {
        when(customerRepository.existsByTenantIdAndPhoneE164(TENANT_A, "+14155550100")).thenReturn(false);
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> {
            Customer c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });

        CustomerDto dto = customerService.create(new CreateCustomerRequest(
            "+14155550100", "Alice", List.of("vip"), OptInStatus.OPTED_IN, null));

        assertThat(dto.tenantId()).isEqualTo(TENANT_A);
        assertThat(dto.phoneE164()).isEqualTo("+14155550100");
        assertThat(dto.tags()).containsExactly("vip");
        assertThat(dto.optInStatus()).isEqualTo(OptInStatus.OPTED_IN);
        ArgumentCaptor<Customer> cap = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(cap.capture());
        assertThat(cap.getValue().getTenantId()).isEqualTo(TENANT_A);
    }

    @Test
    void create_throwsOnDuplicatePhoneInTenant() {
        when(customerRepository.existsByTenantIdAndPhoneE164(TENANT_A, "+14155550100")).thenReturn(true);
        assertThatThrownBy(() -> customerService.create(new CreateCustomerRequest(
            "+14155550100", "Alice", null, null, null)))
            .isInstanceOf(DuplicatePhoneException.class);
        verify(customerRepository, never()).save(any());
    }

    @Test
    void create_requiresTenantContext() {
        TenantContext.clear();
        assertThatThrownBy(() -> customerService.create(new CreateCustomerRequest(
            "+14155550100", "Alice", null, null, null)))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void get_otherTenantCustomer_throwsNotFound() {
        UUID otherId = UUID.randomUUID();
        when(customerRepository.findByIdAndTenantId(otherId, TENANT_A)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> customerService.get(otherId))
            .isInstanceOf(CustomerNotFoundException.class);
    }

    @Test
    void update_phoneChange_checksDupe() {
        UUID id = UUID.randomUUID();
        Customer existing = stub(id, "+14155550100");
        when(customerRepository.findByIdAndTenantId(id, TENANT_A)).thenReturn(Optional.of(existing));
        when(customerRepository.existsByTenantIdAndPhoneE164(TENANT_A, "+14155550200")).thenReturn(true);

        assertThatThrownBy(() -> customerService.update(id,
            new UpdateCustomerRequest("+14155550200", null, null, null, null)))
            .isInstanceOf(DuplicatePhoneException.class);
    }

    @Test
    void update_partial_updatesOnlyProvidedFields() {
        UUID id = UUID.randomUUID();
        Customer existing = stub(id, "+14155550100");
        existing.setFullName("Old");
        existing.setOptInStatus(OptInStatus.UNKNOWN);
        when(customerRepository.findByIdAndTenantId(id, TENANT_A)).thenReturn(Optional.of(existing));

        CustomerDto dto = customerService.update(id,
            new UpdateCustomerRequest(null, "New", null, OptInStatus.OPTED_IN, null));

        assertThat(dto.fullName()).isEqualTo("New");
        assertThat(dto.optInStatus()).isEqualTo(OptInStatus.OPTED_IN);
        assertThat(dto.phoneE164()).isEqualTo("+14155550100");
    }

    @Test
    void delete_otherTenant_throwsNotFound() {
        UUID otherId = UUID.randomUUID();
        when(customerRepository.findByIdAndTenantId(otherId, TENANT_A)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> customerService.delete(otherId))
            .isInstanceOf(CustomerNotFoundException.class);
        verify(customerRepository, never()).delete(any());
    }

    @Test
    void tenantA_doesNotLeakToTenantB() {
        // Sanity: switching context changes the tenantId used for create
        TenantContext.setTenantId(TENANT_B);
        when(customerRepository.existsByTenantIdAndPhoneE164(TENANT_B, "+14155550100")).thenReturn(false);
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> {
            Customer c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });

        CustomerDto dto = customerService.create(new CreateCustomerRequest(
            "+14155550100", "Bob", null, null, null));

        assertThat(dto.tenantId()).isEqualTo(TENANT_B);
    }

    private static Customer stub(UUID id, String phone) {
        Customer c = new Customer();
        c.setId(id);
        c.setTenantId(TENANT_A);
        c.setPhoneE164(phone);
        c.setOptInStatus(OptInStatus.UNKNOWN);
        c.setTags(List.of());
        return c;
    }
}
