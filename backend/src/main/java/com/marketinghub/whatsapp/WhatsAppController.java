package com.marketinghub.whatsapp;

import com.marketinghub.common.TestToolsGuard;
import com.marketinghub.message.dto.MessageDto;
import com.marketinghub.whatsapp.dto.SendTestRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/whatsapp")
@PreAuthorize("hasRole('TENANT_ADMIN')")
public class WhatsAppController {

    private final WhatsAppMessageService service;
    private final TestToolsGuard testToolsGuard;

    public WhatsAppController(WhatsAppMessageService service, TestToolsGuard testToolsGuard) {
        this.service = service;
        this.testToolsGuard = testToolsGuard;
    }

    @PostMapping("/send-test")
    public ResponseEntity<MessageDto> sendTest(@Valid @RequestBody SendTestRequest request) {
        testToolsGuard.requireEnabled();
        MessageDto dto = service.sendTest(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(dto);
    }
}
