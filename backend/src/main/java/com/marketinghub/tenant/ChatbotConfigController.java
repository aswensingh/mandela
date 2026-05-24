package com.marketinghub.tenant;

import com.marketinghub.tenant.dto.ChatbotConfigDto;
import com.marketinghub.tenant.dto.UpdateChatbotConfigRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tenants/me/chatbot")
@PreAuthorize("hasRole('TENANT_ADMIN')")
public class ChatbotConfigController {

    private final ChatbotConfigService service;

    public ChatbotConfigController(ChatbotConfigService service) {
        this.service = service;
    }

    @GetMapping
    public ChatbotConfigDto get() {
        return service.getMyConfig();
    }

    @PutMapping
    public ChatbotConfigDto update(@Valid @RequestBody UpdateChatbotConfigRequest request) {
        return service.updateMyConfig(request);
    }
}
