package com.marketinghub.tenant;

import com.marketinghub.common.crypto.EncryptionService;
import com.marketinghub.tenant.dto.UpdateWhatsAppConfigRequest;
import com.marketinghub.tenant.dto.WhatsAppConfigStatusDto;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class WhatsAppConfigService {

    private final TenantRepository tenantRepository;
    private final EncryptionService encryptionService;

    public WhatsAppConfigService(TenantRepository tenantRepository, EncryptionService encryptionService) {
        this.tenantRepository = tenantRepository;
        this.encryptionService = encryptionService;
    }

    @Transactional(readOnly = true)
    public WhatsAppConfigStatusDto getMyConfig() {
        Tenant tenant = currentTenant();
        boolean configured = tenant.getWhatsappPhoneNumberId() != null
            && tenant.getWhatsappAccessTokenEncrypted() != null;
        String lastFour = null;
        if (configured) {
            String plaintext = encryptionService.decrypt(tenant.getWhatsappAccessTokenEncrypted());
            int n = plaintext.length();
            lastFour = n <= 4 ? plaintext : plaintext.substring(n - 4);
        }
        return new WhatsAppConfigStatusDto(configured, tenant.getWhatsappPhoneNumberId(), lastFour);
    }

    @Transactional
    public WhatsAppConfigStatusDto updateMyConfig(UpdateWhatsAppConfigRequest request) {
        Tenant tenant = currentTenant();
        tenant.setWhatsappPhoneNumberId(request.phoneNumberId());
        tenant.setWhatsappAccessTokenEncrypted(encryptionService.encrypt(request.accessToken()));
        int n = request.accessToken().length();
        String lastFour = n <= 4 ? request.accessToken() : request.accessToken().substring(n - 4);
        return new WhatsAppConfigStatusDto(true, tenant.getWhatsappPhoneNumberId(), lastFour);
    }

    private Tenant currentTenant() {
        UUID tenantId = com.marketinghub.tenant.TenantContext.getTenantId();
        if (tenantId == null) {
            throw new AccessDeniedException("No tenant context");
        }
        return tenantRepository.findById(tenantId)
            .orElseThrow(() -> new TenantNotFoundException(tenantId));
    }
}
