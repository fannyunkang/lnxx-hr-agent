package com.lnxx.hr.domain;

import java.time.LocalDateTime;

public record ApprovalSummary(String approvalId, String employeeId, String approvalType,
                              String title, String status, LocalDateTime updatedAt) {
}

