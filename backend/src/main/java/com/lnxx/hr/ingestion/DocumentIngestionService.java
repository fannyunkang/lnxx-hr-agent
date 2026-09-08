package com.lnxx.hr.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lnxx.hr.domain.KnowledgeChunk;
import com.lnxx.hr.rag.VectorKnowledgeIndex;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
public class DocumentIngestionService {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final SemanticChunker chunker;
    private final OcrClient ocr;
    private final ObjectMapper json;
    private final VectorKnowledgeIndex vectors;

    public DocumentIngestionService(JdbcTemplate jdbc, TransactionTemplate transactions,
                                    SemanticChunker chunker, OcrClient ocr, ObjectMapper json,
                                    VectorKnowledgeIndex vectors) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.chunker = chunker;
        this.ocr = ocr;
        this.json = json; this.vectors = vectors;
    }

    public IngestionJob submit(String documentId, String title, KnowledgeChunk.KnowledgeScope scope,
                               Set<String> departments, Set<String> employeeIds, MultipartFile file) {
        validateAccess(scope, departments, employeeIds);
        try {
            byte[] bytes = file.getBytes();
            String sourceName = file.getOriginalFilename() == null ? "upload" : file.getOriginalFilename();
            String sourceType = sourceType(sourceName);
            int version = jdbc.queryForObject("SELECT COALESCE(MAX(version), 0) + 1 FROM knowledge_documents WHERE document_id = ?", Integer.class, documentId);
            String jobId = UUID.randomUUID().toString();
            String hash = sha256(bytes);
            jdbc.update("INSERT INTO knowledge_documents(document_id, version, title, source_name, source_type, scope, content_hash, status, active, deleted) VALUES(?,?,?,?,?,?,?,'PENDING',FALSE,FALSE)",
                    documentId, version, title, sourceName, sourceType, scope.name(), hash);
            jdbc.update("INSERT INTO knowledge_ingestion_jobs(job_id, document_id, version, status) VALUES(?,?,?,'PENDING')", jobId, documentId, version);
            CompletableFuture.runAsync(() -> process(jobId, documentId, version, title, sourceName,
                    sourceType, scope, departments, employeeIds, hash, bytes));
            return get(jobId);
        } catch (IOException exception) {
            throw new IllegalArgumentException("UPLOAD_READ_FAILED", exception);
        }
    }

    public IngestionJob get(String jobId) {
        return jdbc.queryForObject("SELECT * FROM knowledge_ingestion_jobs WHERE job_id = ?", (rs, row) ->
                new IngestionJob(rs.getString("job_id"), rs.getString("document_id"), rs.getInt("version"),
                        IngestionJob.Status.valueOf(rs.getString("status")), rs.getInt("chunk_count"),
                        rs.getString("error_code"), rs.getString("error_message"),
                        rs.getTimestamp("created_at").toLocalDateTime(), rs.getTimestamp("updated_at").toLocalDateTime()), jobId);
    }

    public IngestionJob reindex(String documentId) {
        Integer version = jdbc.queryForObject("SELECT MAX(version) FROM knowledge_documents WHERE document_id = ? AND deleted = FALSE", Integer.class, documentId);
        if (version == null) throw new IllegalArgumentException("DOCUMENT_NOT_FOUND");
        String jobId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO knowledge_ingestion_jobs(job_id, document_id, version, status) VALUES(?,?,?,'EMBEDDING')", jobId, documentId, version);
        jdbc.update("UPDATE knowledge_ingestion_jobs SET status='INDEXED', chunk_count=(SELECT COUNT(*) FROM knowledge_chunks WHERE document_id=? AND version=?), updated_at=CURRENT_TIMESTAMP WHERE job_id=?", documentId, version, jobId);
        return get(jobId);
    }

    public void delete(String documentId) {
        transactions.executeWithoutResult(status -> {
            jdbc.update("UPDATE knowledge_documents SET deleted=TRUE, active=FALSE, status='DELETED' WHERE document_id=?", documentId);
            jdbc.update("UPDATE knowledge_chunks SET active=FALSE WHERE document_id=?", documentId);
        });
    }

    private void process(String jobId, String documentId, int version, String title, String sourceName,
                         String sourceType, KnowledgeChunk.KnowledgeScope scope, Set<String> departments,
                         Set<String> employeeIds, String hash, byte[] bytes) {
        try {
            updateJob(jobId, "PARSING", 0, null, null);
            String text = extract(bytes, sourceName, sourceType);
            List<String> chunks = chunker.split(text);
            if (chunks.isEmpty()) throw new IllegalArgumentException("DOCUMENT_EMPTY");
            updateJob(jobId, "EMBEDDING", chunks.size(), null, null);
            vectors.add(documentId, version, scope, List.copyOf(departments), List.copyOf(employeeIds), chunks);
            transactions.executeWithoutResult(status -> {
                jdbc.update("UPDATE knowledge_documents SET active=FALSE WHERE document_id=?", documentId);
                jdbc.update("UPDATE knowledge_chunks SET active=FALSE WHERE document_id=?", documentId);
                for (int index = 0; index < chunks.size(); index++) {
                    jdbc.update("INSERT INTO knowledge_chunks(document_id,version,chunk_number,title,content,scope,source_type,content_hash,active) VALUES(?,?,?,?,?,?,?,?,TRUE)",
                            documentId, version, index + 1, title, chunks.get(index), scope.name(), sourceType, hash);
                }
                departments.forEach(department -> jdbc.update("INSERT INTO knowledge_departments(document_id,department) SELECT ?,? WHERE NOT EXISTS (SELECT 1 FROM knowledge_departments WHERE document_id=? AND department=?)",
                        documentId, department, documentId, department));
                employeeIds.forEach(employeeId -> jdbc.update("INSERT INTO knowledge_personal_access(document_id,employee_id) SELECT ?,? WHERE NOT EXISTS (SELECT 1 FROM knowledge_personal_access WHERE document_id=? AND employee_id=?)",
                        documentId, employeeId, documentId, employeeId));
                jdbc.update("UPDATE knowledge_documents SET status='INDEXED', active=TRUE WHERE document_id=? AND version=?", documentId, version);
                updateJob(jobId, "INDEXED", chunks.size(), null, null);
            });
        } catch (Exception exception) {
            updateJob(jobId, "FAILED", 0, errorCode(exception), truncate(exception.getMessage(), 900));
            jdbc.update("UPDATE knowledge_documents SET status='FAILED' WHERE document_id=? AND version=?", documentId, version);
        }
    }

    private String extract(byte[] bytes, String filename, String type) throws IOException {
        return switch (type) {
            case "PDF" -> {
                try (var document = Loader.loadPDF(bytes)) {
                    String text = new PDFTextStripper().getText(document);
                    yield text.replaceAll("\\s+", " ").length() < 80 ? ocr.extract(bytes, filename) : text;
                }
            }
            case "JSON" -> json.writerWithDefaultPrettyPrinter().writeValueAsString(json.readTree(bytes));
            default -> new String(bytes, StandardCharsets.UTF_8);
        };
    }

    private String sourceType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".pdf")) return "PDF";
        if (lower.endsWith(".md") || lower.endsWith(".markdown")) return "MARKDOWN";
        if (lower.endsWith(".json")) return "JSON";
        throw new IllegalArgumentException("UNSUPPORTED_DOCUMENT_TYPE");
    }

    private void validateAccess(KnowledgeChunk.KnowledgeScope scope, Set<String> departments, Set<String> employeeIds) {
        if (scope == KnowledgeChunk.KnowledgeScope.DEPARTMENT && departments.isEmpty()) throw new IllegalArgumentException("DEPARTMENT_REQUIRED");
        if (scope == KnowledgeChunk.KnowledgeScope.PERSONAL && employeeIds.isEmpty()) throw new IllegalArgumentException("EMPLOYEE_REQUIRED");
    }

    private void updateJob(String jobId, String status, int count, String code, String message) {
        jdbc.update("UPDATE knowledge_ingestion_jobs SET status=?, chunk_count=?, error_code=?, error_message=?, updated_at=CURRENT_TIMESTAMP WHERE job_id=?",
                status, count, code, message, jobId);
    }

    private String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (Exception exception) { throw new IllegalStateException(exception); }
    }

    private String errorCode(Exception exception) {
        String message = exception.getMessage();
        return message != null && message.matches("[A-Z0-9_]+") ? message : "INGESTION_FAILED";
    }

    private String truncate(String value, int limit) {
        if (value == null) return null;
        return value.length() <= limit ? value : value.substring(0, limit);
    }
}
