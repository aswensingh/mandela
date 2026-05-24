package com.marketinghub.customer.importjob;

import com.marketinghub.customer.importjob.dto.ImportJobDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/customers/import")
public class CsvImportController {

    private final CsvImportService importService;

    public CsvImportController(CsvImportService importService) {
        this.importService = importService;
    }

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<ImportJobDto> start(@RequestParam("file") MultipartFile file) {
        ImportJobDto job = importService.startImport(file);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(job);
    }

    @GetMapping("/{jobId}")
    public ImportJobDto get(@PathVariable UUID jobId) {
        return importService.get(jobId);
    }
}
