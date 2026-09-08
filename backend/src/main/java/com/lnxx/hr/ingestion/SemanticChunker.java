package com.lnxx.hr.ingestion;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class SemanticChunker {
    private final int targetSize;
    private final int overlap;

    public SemanticChunker(@Value("${hr-agent.ingestion.chunk-size:500}") int targetSize,
                           @Value("${hr-agent.ingestion.chunk-overlap:80}") int overlap) {
        if (targetSize < 100 || overlap < 0 || overlap >= targetSize) {
            throw new IllegalArgumentException("Invalid chunk size/overlap configuration");
        }
        this.targetSize = targetSize;
        this.overlap = overlap;
    }

    public List<String> split(String input) {
        String text = input == null ? "" : input.replace("\r\n", "\n").trim();
        if (text.isEmpty()) return List.of();
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + targetSize, text.length());
            if (end < text.length()) {
                int boundary = bestBoundary(text, start, end);
                if (boundary > start + targetSize / 2) end = boundary;
            }
            chunks.add(text.substring(start, end).trim());
            if (end == text.length()) break;
            start = Math.max(start + 1, end - overlap);
        }
        return chunks.stream().filter(value -> !value.isBlank()).toList();
    }

    private int bestBoundary(String text, int start, int end) {
        for (String separator : List.of("\n## ", "\n# ", "\n\n", "。", "！", "？", ". ")) {
            int found = text.lastIndexOf(separator, end);
            if (found > start) return found + separator.length();
        }
        return end;
    }
}
