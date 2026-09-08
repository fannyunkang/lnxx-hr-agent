package com.lnxx.hr.rag;

import com.lnxx.hr.domain.KnowledgeChunk;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class VectorKnowledgeIndex {
    private final ObjectProvider<VectorStore> stores;
    private final MeterRegistry meters;

    public VectorKnowledgeIndex(ObjectProvider<VectorStore> stores, MeterRegistry meters) {
        this.stores = stores; this.meters = meters;
    }

    public boolean add(String documentId, int version, KnowledgeChunk.KnowledgeScope scope,
                       List<String> departments, List<String> employeeIds, List<String> chunks) {
        VectorStore store = stores.getIfAvailable();
        if (store == null) return false;
        try {
            List<Document> documents = java.util.stream.IntStream.range(0, chunks.size()).mapToObj(index ->
                    Document.builder().id(id(documentId, version, index + 1)).text(chunks.get(index))
                            .metadata(Map.of("documentId", documentId, "version", version,
                                    "chunkNumber", index + 1, "scope", scope.name(),
                                    "departments", String.join(",", departments),
                                    "employeeIds", String.join(",", employeeIds))).build()).toList();
            store.add(documents);
            return true;
        } catch (RuntimeException exception) {
            meters.counter("hr.rag.fallback", "reason", "vector_index_failed").increment();
            return false;
        }
    }

    public List<Document> search(String query, String filter) {
        VectorStore store = stores.getIfAvailable();
        if (store == null) return List.of();
        try {
            return store.similaritySearch(SearchRequest.builder().query(query).topK(30)
                    .similarityThreshold(0.0).filterExpression(filter).build());
        } catch (RuntimeException exception) {
            meters.counter("hr.rag.fallback", "reason", "vector_search_failed").increment();
            return List.of();
        }
    }

    public static String id(String documentId, int version, int chunk) {
        return documentId + ":" + version + ":" + chunk;
    }
}
