package com.marketinghub.tenant;

import com.marketinghub.tenant.dto.ChatbotConfigDto;
import com.marketinghub.tenant.dto.UpdateChatbotConfigRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class ChatbotConfigService {

    private static final String THRESHOLD_KEY = "handoff_confidence_threshold";

    private final TenantRepository tenantRepository;

    public ChatbotConfigService(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    @Transactional(readOnly = true)
    public ChatbotConfigDto getMyConfig() {
        Tenant t = currentTenant();
        Double threshold = readThreshold(t.getChatConfig());
        return new ChatbotConfigDto(t.getAiSystemPrompt(), threshold);
    }

    @Transactional
    public ChatbotConfigDto updateMyConfig(UpdateChatbotConfigRequest request) {
        Tenant t = currentTenant();
        if (request.aiSystemPrompt() != null) {
            t.setAiSystemPrompt(request.aiSystemPrompt().isBlank() ? null : request.aiSystemPrompt());
        }
        if (request.handoffConfidenceThreshold() != null) {
            Map<String, Object> cfg = t.getChatConfig() == null
                ? new HashMap<>()
                : new HashMap<>(t.getChatConfig());
            cfg.put(THRESHOLD_KEY, request.handoffConfidenceThreshold());
            t.setChatConfig(cfg);
        }
        return new ChatbotConfigDto(t.getAiSystemPrompt(), readThreshold(t.getChatConfig()));
    }

    private static Double readThreshold(Map<String, Object> cfg) {
        if (cfg == null) return null;
        Object v = cfg.get(THRESHOLD_KEY);
        if (v instanceof Number n) return n.doubleValue();
        return null;
    }

    private Tenant currentTenant() {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new AccessDeniedException("No tenant context");
        }
        return tenantRepository.findById(tenantId)
            .orElseThrow(() -> new TenantNotFoundException(tenantId));
    }
}
