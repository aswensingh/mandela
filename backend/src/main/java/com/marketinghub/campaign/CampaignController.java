package com.marketinghub.campaign;

import com.marketinghub.campaign.dto.CampaignDto;
import com.marketinghub.campaign.dto.CreateCampaignRequest;
import com.marketinghub.campaign.dto.RecipientDto;
import com.marketinghub.campaign.dto.UpdateCampaignRequest;
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
@RequestMapping("/api/campaigns")
public class CampaignController {

    private final CampaignService service;
    private final CampaignLaunchService launchService;

    public CampaignController(CampaignService service, CampaignLaunchService launchService) {
        this.service = service;
        this.launchService = launchService;
    }

    @GetMapping
    public Page<CampaignDto> list(@PageableDefault(size = 50) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    public CampaignDto get(@PathVariable UUID id) {
        return service.get(id);
    }

    @GetMapping("/{id}/recipients")
    public Page<RecipientDto> listRecipients(
        @PathVariable UUID id,
        @PageableDefault(size = 100) Pageable pageable
    ) {
        return service.listRecipients(id, pageable);
    }

    @PostMapping
    public ResponseEntity<CampaignDto> create(@Valid @RequestBody CreateCampaignRequest request) {
        CampaignDto created = service.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PatchMapping("/{id}")
    public CampaignDto update(@PathVariable UUID id, @Valid @RequestBody UpdateCampaignRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/cancel")
    public CampaignDto cancel(@PathVariable UUID id) {
        return service.cancel(id);
    }

    @PostMapping("/{id}/launch")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public CampaignDto launch(@PathVariable UUID id) {
        return launchService.launch(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
