package com.lnxx.hr.interfaceapi;

import com.lnxx.hr.security.AuthPrincipal;
import com.lnxx.hr.trace.AgentTrace;
import com.lnxx.hr.trace.TraceStore;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/traces")
public class TraceController {
    private final TraceStore traceStore;

    public TraceController(TraceStore traceStore) { this.traceStore = traceStore; }

    @GetMapping
    List<AgentTrace> recent(@AuthenticationPrincipal AuthPrincipal principal) {
        return traceStore.recentFor(principal.username());
    }
}
