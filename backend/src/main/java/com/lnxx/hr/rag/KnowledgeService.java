package com.lnxx.hr.rag;

import com.lnxx.hr.domain.KnowledgeChunk;
import com.lnxx.hr.domain.HrRepository;
import com.lnxx.hr.security.AuthPrincipal;
import com.lnxx.hr.security.UserRole;
import org.springframework.stereotype.Service;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class KnowledgeService {
    private final HrRepository repository;
    private VectorKnowledgeIndex vectors;

    public KnowledgeService(HrRepository repository) {
        this.repository = repository;
    }

    @Autowired
    void vectorIndex(VectorKnowledgeIndex vectors) { this.vectors = vectors; }

    public List<KnowledgeChunk> search(String query, AuthPrincipal principal, String department) {
        List<String> terms = Arrays.stream(query.replaceAll("[，。？！,.?!]", " ").split("\\s+"))
                .filter(term -> !term.isBlank()).toList();
        List<KnowledgeChunk> keyword = repository.knowledge().stream()
                .filter(chunk -> allowed(chunk, principal, department))
                .map(chunk -> new ScoredChunk(chunk, score(chunk, query, terms)))
                .filter(result -> result.score > 0)
                .sorted(Comparator.comparingInt(ScoredChunk::score).reversed())
                .limit(30)
                .map(ScoredChunk::chunk)
                .toList();
        if (vectors == null) return keyword.stream().limit(5).toList();
        String filter = permissionFilter(principal, department);
        List<String> vectorIds = vectors.search(query, filter).stream().map(Document::getId).toList();
        java.util.Map<String, KnowledgeChunk> byId = repository.knowledge().stream()
                .filter(chunk -> allowed(chunk, principal, department))
                .collect(java.util.stream.Collectors.toMap(chunk -> VectorKnowledgeIndex.id(chunk.documentId(), chunk.version(), chunk.chunkNumber()), chunk -> chunk, (a, b) -> a));
        List<KnowledgeChunk> semantic = vectorIds.stream().map(byId::get).filter(java.util.Objects::nonNull).toList();
        return RrfFusion.fuse(List.of(keyword, semantic),
                chunk -> VectorKnowledgeIndex.id(chunk.documentId(), chunk.version(), chunk.chunkNumber()), 5);
    }

    public List<KnowledgeChunk> visibleTo(AuthPrincipal principal, String department) {
        return repository.knowledge().stream().filter(chunk -> allowed(chunk, principal, department)).toList();
    }

    public KnowledgeChunk create(String documentId, String title, String content,
                                 KnowledgeChunk.KnowledgeScope scope, java.util.Set<String> departments) {
        Set<String> normalizedDepartments = departments.stream().map(String::trim)
                .filter(value -> !value.isBlank()).collect(Collectors.toUnmodifiableSet());
        if (scope == KnowledgeChunk.KnowledgeScope.DEPARTMENT && normalizedDepartments.isEmpty()) {
            throw new IllegalArgumentException("部门知识必须指定至少一个可见部门");
        }
        if (scope != KnowledgeChunk.KnowledgeScope.DEPARTMENT) normalizedDepartments = Set.of();
        return repository.addKnowledge(documentId.trim(), title.trim(), content.trim(), scope, normalizedDepartments);
    }

    private boolean allowed(KnowledgeChunk chunk, AuthPrincipal principal, String department) {
        return switch (chunk.scope()) {
            case PUBLIC -> true;
            case DEPARTMENT -> chunk.departments().contains(department);
            case PERSONAL -> chunk.employeeIds().contains(principal.employeeId())
                    || principal.role() == UserRole.HR || principal.role() == UserRole.ADMIN;
            case HR_ONLY -> principal.role() == UserRole.HR || principal.role() == UserRole.ADMIN;
        };
    }

    private int score(KnowledgeChunk chunk, String query, List<String> terms) {
        String text = chunk.title() + chunk.content();
        int score = terms.stream().mapToInt(term -> text.contains(term) ? 1 : 0).sum();
        if (query.contains("年假") && text.contains("年假")) score += 3;
        if ((query.contains("考勤") || query.contains("迟到")) && text.contains("考勤")) score += 3;
        if ((query.contains("弹性") || query.contains("上班")) && text.contains("弹性")) score += 3;
        return score;
    }

    private String permissionFilter(AuthPrincipal principal, String department) {
        String safeDepartment = department.replace("'", "");
        String safeEmployee = principal.employeeId().replace("'", "");
        String expression = "scope == 'PUBLIC' OR (scope == 'DEPARTMENT' AND departments LIKE '%" + safeDepartment + "%')"
                + " OR (scope == 'PERSONAL' AND employeeIds LIKE '%" + safeEmployee + "%')";
        if (principal.role() == UserRole.HR || principal.role() == UserRole.ADMIN) expression += " OR scope == 'HR_ONLY'";
        return expression;
    }

    private record ScoredChunk(KnowledgeChunk chunk, int score) {}
}
