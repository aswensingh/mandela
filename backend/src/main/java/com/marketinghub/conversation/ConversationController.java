package com.marketinghub.conversation;

import com.marketinghub.conversation.dto.ConversationListItemDto;
import com.marketinghub.conversation.dto.ConversationMessageDto;
import com.marketinghub.conversation.dto.SendAgentMessageRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationService service;

    public ConversationController(ConversationService service) {
        this.service = service;
    }

    @GetMapping
    public Page<ConversationListItemDto> list(
        @RequestParam(name = "filter", required = false) ConversationService.Filter filter,
        @PageableDefault(size = 50) Pageable pageable
    ) {
        return service.list(filter, pageable);
    }

    @GetMapping("/{id}")
    public ConversationListItemDto get(@PathVariable UUID id) {
        return service.get(id);
    }

    @GetMapping("/{id}/messages")
    public Page<ConversationMessageDto> messages(
        @PathVariable UUID id,
        @PageableDefault(size = 100) Pageable pageable
    ) {
        return service.listMessages(id, pageable);
    }

    @PostMapping("/{id}/take-over")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','AGENT')")
    public ConversationListItemDto takeOver(@PathVariable UUID id) {
        return service.takeOver(id);
    }

    @PostMapping("/{id}/release")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','AGENT')")
    public ConversationListItemDto release(@PathVariable UUID id) {
        return service.release(id);
    }

    @PostMapping("/{id}/messages")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','AGENT')")
    public ResponseEntity<ConversationMessageDto> sendMessage(
        @PathVariable UUID id,
        @Valid @RequestBody SendAgentMessageRequest request
    ) {
        ConversationMessageDto created = service.sendAgentMessage(id, request.body());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
