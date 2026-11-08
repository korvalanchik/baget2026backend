package com.example.baget.prro;

import com.example.baget.prro.service.PrroFiscalDocumentArchiveService;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Objects;

@RestController
@RequestMapping("/api/prro/documents")
@ConditionalOnProperty(prefix = "baget.prro", name = "enabled", havingValue = "true")
public class PrroFiscalDocumentArchiveController {
    private final PrroFiscalDocumentArchiveService archive;

    public PrroFiscalDocumentArchiveController(PrroFiscalDocumentArchiveService archive) {
        this.archive = Objects.requireNonNull(archive);
    }

    @GetMapping
    public PrroFiscalDocumentArchiveService.SearchResult search(
            @RequestParam @Positive long branchNo,
            @RequestParam(required = false) String query,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size,
            Authentication authentication) {
        return archive.search(branchNo, from, to, query, page, size, authentication);
    }

    @GetMapping("/{documentId}")
    public PrroFiscalDocumentArchiveService.ArchivedDocument get(
            @PathVariable @Positive long documentId,
            Authentication authentication) {
        return archive.get(documentId, authentication);
    }
}
