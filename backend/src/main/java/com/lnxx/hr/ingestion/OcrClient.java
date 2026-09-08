package com.lnxx.hr.ingestion;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;

@Component
public class OcrClient {
    private final RestClient client;

    public OcrClient(@Value("${hr-agent.ocr.base-url:http://localhost:8866}") String baseUrl) {
        this.client = RestClient.builder().baseUrl(baseUrl).build();
    }

    public String extract(byte[] bytes, String filename) {
        LinkedMultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(bytes) {
            @Override public String getFilename() { return filename; }
        });
        OcrResponse response = client.post().uri("/ocr")
                .contentType(MediaType.MULTIPART_FORM_DATA).body(body).retrieve().body(OcrResponse.class);
        if (response == null || response.text() == null || response.text().isBlank()) {
            throw new IllegalStateException("OCR_EMPTY_RESULT");
        }
        return response.text();
    }

    record OcrResponse(String text) {}
}
