package com.lnxx.hr.trace;

import java.time.Instant;

public record TraceNode(String name, Instant startedAt, Instant finishedAt, long durationMs,
                        String inputSummary, String outputSummary, String status, String errorCode) {}
