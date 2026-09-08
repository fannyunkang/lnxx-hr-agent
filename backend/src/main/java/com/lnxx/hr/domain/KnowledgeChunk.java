package com.lnxx.hr.domain;

import java.util.Set;

public record KnowledgeChunk(String documentId, int version, int chunkNumber, String title, String content,
                             KnowledgeScope scope, Set<String> departments, Set<String> employeeIds) {
    public KnowledgeChunk(String documentId, int chunkNumber, String title, String content,
                          KnowledgeScope scope, Set<String> departments) {
        this(documentId, 1, chunkNumber, title, content, scope, departments, Set.of());
    }

    public String citation() {
        return "[KB-" + documentId + "-" + version + "-" + chunkNumber + "]";
    }

    public enum KnowledgeScope { PUBLIC, DEPARTMENT, PERSONAL, HR_ONLY }
}
