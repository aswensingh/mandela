package com.marketinghub.tenant;

import com.marketinghub.tenant.dto.CreateTenantRequest;
import com.marketinghub.tenant.dto.CreateTenantResponse;
import com.marketinghub.tenant.dto.PurgeTenantRequest;
import com.marketinghub.tenant.dto.TenantDto;
import com.marketinghub.tenant.dto.UpdateTenantRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/platform/tenants")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class TenantController {

    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @PostMapping
    public ResponseEntity<CreateTenantResponse> create(@Valid @RequestBody CreateTenantRequest request) {
        CreateTenantResponse response = tenantService.createTenant(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public Page<TenantDto> list(
        @RequestParam(name = "includeDeleted", defaultValue = "false") boolean includeDeleted,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        return tenantService.listTenants(pageable, includeDeleted);
    }

    @PatchMapping("/{id}")
    public TenantDto update(@PathVariable UUID id, @Valid @RequestBody UpdateTenantRequest request) {
        return tenantService.update(id, request);
    }

    @PostMapping("/{id}/suspend")
    public TenantDto suspend(@PathVariable UUID id) {
        return tenantService.suspend(id);
    }

    @PostMapping("/{id}/unsuspend")
    public TenantDto unsuspend(@PathVariable UUID id) {
        return tenantService.unsuspend(id);
    }

    /**
     * Soft delete — hides the tenant from the default list and blocks login for its users. Row
     * stays in the DB. Idempotent: calling again on an already-DELETED tenant is a no-op.
     */
    @DeleteMapping("/{id}")
    public TenantDto softDelete(@PathVariable UUID id) {
        return tenantService.softDelete(id);
    }

    /**
     * Hard purge — irreversible cascading delete of every row this tenant owns plus the tenant
     * row itself. Requires the tenant to already be in DELETED state (two-step gate) AND for the
     * caller to type the tenant's exact name in the request body (typo gate).
     */
    @DeleteMapping("/{id}/purge")
    public ResponseEntity<Void> purge(
        @PathVariable UUID id,
        @Valid @RequestBody PurgeTenantRequest request
    ) {
        tenantService.purge(id, request.confirmName());
        return ResponseEntity.noContent().build();
    }
}
