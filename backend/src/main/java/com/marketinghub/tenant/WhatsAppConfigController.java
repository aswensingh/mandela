package com.marketinghub.tenant;

import com.marketinghub.tenant.dto.UpdateWhatsAppConfigRequest;
import com.marketinghub.tenant.dto.WhatsAppConfigStatusDto;
import com.marketinghub.tenant.dto.WhatsAppConnectionTestResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tenants/me/whatsapp")
@PreAuthorize("hasRole('TENANT_ADMIN')")
public class WhatsAppConfigController {

    private final WhatsAppConfigService service;

    public WhatsAppConfigController(WhatsAppConfigService service) {
        this.service = service;
    }

    @GetMapping
    public WhatsAppConfigStatusDto get() {
        return service.getMyConfig();
    }

    @PutMapping
    public WhatsAppConfigStatusDto update(@Valid @RequestBody UpdateWhatsAppConfigRequest request) {
        return service.updateMyConfig(request);
    }

    /**
     * Diagnostic — hits Meta with the stored credentials and reports back whether
     * authentication works + what business profile Meta sees. Always returns 200
     * (even on auth failure) so the frontend can render a useful inline message
     * instead of having to parse a 4xx/5xx body. Read the {@code ok} field.
     */
    @PostMapping("/test")
    public WhatsAppConnectionTestResponse test() {
        return service.testConnection();
    }
}
