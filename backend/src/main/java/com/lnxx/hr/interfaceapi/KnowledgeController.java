package com.lnxx.hr.interfaceapi;

import com.lnxx.hr.domain.HrRepository;
import com.lnxx.hr.domain.KnowledgeChunk;
import com.lnxx.hr.rag.KnowledgeService;
import com.lnxx.hr.ingestion.DocumentIngestionService;
import com.lnxx.hr.ingestion.IngestionJob;
import com.lnxx.hr.security.AuthPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {
    private final KnowledgeService knowledgeService;
    private final HrRepository repository;
    private final DocumentIngestionService ingestion;

    public KnowledgeController(KnowledgeService knowledgeService, HrRepository repository,
                               DocumentIngestionService ingestion) {
        this.knowledgeService = knowledgeService;
        this.repository = repository;
        this.ingestion = ingestion;
    }

    @GetMapping
    List<KnowledgeChunk> visible(@AuthenticationPrincipal AuthPrincipal principal) {
        String department = repository.employee(principal.employeeId())
                .map(employee -> employee.department()).orElse("");
        return knowledgeService.visibleTo(principal, department);
    }

    @PostMapping
    KnowledgeChunk create(@Valid @RequestBody CreateKnowledgeRequest request) {
        return knowledgeService.create(request.documentId(), request.title(), request.content(),
                request.scope(), request.departments() == null ? Set.of() : request.departments());
    }

    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    IngestionJob upload(@RequestParam @Pattern(regexp = "[A-Z0-9_-]{3,64}") String documentId,
                        @RequestParam @NotBlank @Size(max = 256) String title,
                        @RequestParam KnowledgeChunk.KnowledgeScope scope,
                        @RequestParam(required = false) Set<String> departments,
                        @RequestParam(required = false) Set<String> employeeIds,
                        @RequestPart MultipartFile file) {
        if (file.isEmpty() || file.getSize() > 20L * 1024 * 1024) {
            throw new IllegalArgumentException("FILE_SIZE_INVALID");
        }
        return ingestion.submit(documentId, title, scope,
                departments == null ? Set.of() : departments,
                employeeIds == null ? Set.of() : employeeIds, file);
    }

    @GetMapping("/jobs/{jobId}")
    IngestionJob job(@PathVariable @Pattern(regexp = "[a-f0-9-]{36}") String jobId) {
        return ingestion.get(jobId);
    }

    @PostMapping("/documents/{documentId}/reindex")
    IngestionJob reindex(@PathVariable @Pattern(regexp = "[A-Z0-9_-]{3,64}") String documentId) {
        return ingestion.reindex(documentId);
    }

    @DeleteMapping("/documents/{documentId}")
    void delete(@PathVariable @Pattern(regexp = "[A-Z0-9_-]{3,64}") String documentId) {
        ingestion.delete(documentId);
    }

    public record CreateKnowledgeRequest(
            @NotBlank @Pattern(regexp = "[A-Z0-9_-]{3,64}") String documentId,
            @NotBlank @Size(max = 256) String title,
            @NotBlank @Size(max = 4000) String content,
            @NotNull KnowledgeChunk.KnowledgeScope scope,
            @Size(max = 20) Set<@NotBlank @Size(max = 64) String> departments) {}
}
