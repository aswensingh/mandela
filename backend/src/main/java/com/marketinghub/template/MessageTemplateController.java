package com.marketinghub.template;

import com.marketinghub.template.dto.CreateTemplateRequest;
import com.marketinghub.template.dto.TemplateDto;
import com.marketinghub.template.dto.TemplateSyncResultDto;
import com.marketinghub.template.dto.UpdateTemplateRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/templates")
public class MessageTemplateController {

    private final MessageTemplateService service;
    private final TemplateSyncService syncService;

    public MessageTemplateController(MessageTemplateService service, TemplateSyncService syncService) {
        this.service = service;
        this.syncService = syncService;
    }

    @GetMapping
    public Page<TemplateDto> list(@PageableDefault(size = 50) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    public TemplateDto get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    public ResponseEntity<TemplateDto> create(@Valid @RequestBody CreateTemplateRequest request) {
        TemplateDto created = service.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /** Pull real approval status from Meta and write it onto this tenant's templates. */
    @PostMapping("/sync")
    public TemplateSyncResultDto sync() {
        return syncService.syncFromMeta();
    }

    @PatchMapping("/{id}")
    public TemplateDto update(@PathVariable UUID id, @Valid @RequestBody UpdateTemplateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
