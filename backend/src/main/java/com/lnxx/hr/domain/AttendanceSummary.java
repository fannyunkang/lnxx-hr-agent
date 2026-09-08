package com.lnxx.hr.domain;

public record AttendanceSummary(String employeeId, String attendanceMonth, int workDays, int attendedDays,
                                int lateCount, int absentCount) {
}
