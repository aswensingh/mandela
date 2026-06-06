package com.marketinghub.customer;

import com.marketinghub.customer.dto.CreateCustomerRequest;
import com.marketinghub.customer.dto.CustomerDto;
import com.marketinghub.customer.dto.UpdateCustomerRequest;
import com.marketinghub.tenant.TenantContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Transactional(readOnly = true)
    public Page<CustomerDto> list(String search, String tag, OptInStatus optInStatus, Pageable pageable) {
        UUID tenantId = requireTenant();
        String searchNorm = (search == null || search.isBlank()) ? null : search.trim();
        String tagNorm = (tag == null || tag.isBlank()) ? null : tag.trim();
        String optInNorm = optInStatus == null ? null : optInStatus.name();
        return customerRepository.search(tenantId, searchNorm, tagNorm, optInNorm, pageable)
            .map(CustomerService::toDto);
    }

    @Transactional(readOnly = true)
    public CustomerDto get(UUID id) {
        UUID tenantId = requireTenant();
        return customerRepository.findByIdAndTenantId(id, tenantId)
            .map(CustomerService::toDto)
            .orElseThrow(() -> new CustomerNotFoundException(id));
    }

    @Transactional
    public CustomerDto create(CreateCustomerRequest request) {
        UUID tenantId = requireTenant();
        if (customerRepository.existsByTenantIdAndPhoneE164(tenantId, request.phoneE164())) {
            throw new DuplicatePhoneException(request.phoneE164());
        }
        Customer c = new Customer();
        c.setTenantId(tenantId);
        c.setPhoneE164(request.phoneE164());
        c.setFullName(request.fullName());
        c.setTags(request.tags() == null ? List.of() : new ArrayList<>(request.tags()));
        c.setOptInStatus(request.optInStatus() == null ? OptInStatus.UNKNOWN : request.optInStatus());
        c.setCustomAttributes(request.customAttributes() == null
            ? new HashMap<>()
            : new HashMap<>(request.customAttributes()));
        Customer saved = customerRepository.save(c);
        customerRepository.flush();
        return toDto(saved);
    }

    @Transactional
    public CustomerDto update(UUID id, UpdateCustomerRequest request) {
        UUID tenantId = requireTenant();
        Customer c = customerRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new CustomerNotFoundException(id));

        if (request.phoneE164() != null && !request.phoneE164().equals(c.getPhoneE164())) {
            if (customerRepository.existsByTenantIdAndPhoneE164(tenantId, request.phoneE164())) {
                throw new DuplicatePhoneException(request.phoneE164());
            }
            c.setPhoneE164(request.phoneE164());
        }
        if (request.fullName() != null) {
            c.setFullName(request.fullName());
        }
        if (request.tags() != null) {
            c.setTags(new ArrayList<>(request.tags()));
        }
        if (request.optInStatus() != null) {
            c.setOptInStatus(request.optInStatus());
        }
        if (request.customAttributes() != null) {
            c.setCustomAttributes(new HashMap<>(request.customAttributes()));
        }
        return toDto(c);
    }

    @Transactional
    public void delete(UUID id) {
        UUID tenantId = requireTenant();
        Customer c = customerRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new CustomerNotFoundException(id));
        customerRepository.delete(c);
    }

    /**
     * Deletes many customers in a single SQL statement. Silently skips ids that don't
     * belong to the current tenant (we don't want to leak which ids exist in other
     * tenants), and returns the count actually removed.
     */
    @Transactional
    public int bulkDelete(Collection<UUID> ids) {
        UUID tenantId = requireTenant();
        if (ids == null || ids.isEmpty()) return 0;
        return customerRepository.deleteByTenantIdAndIdIn(tenantId, ids);
    }

    private UUID requireTenant() {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new AccessDeniedException("No tenant context — platform admins cannot manage customers here");
        }
        return tenantId;
    }

    static CustomerDto toDto(Customer c) {
        return new CustomerDto(
            c.getId(),
            c.getTenantId(),
            c.getPhoneE164(),
            c.getFullName(),
            c.getTags(),
            c.getOptInStatus(),
            c.getCustomAttributes(),
            c.getCreatedAt(),
            c.getUpdatedAt()
        );
    }
}
