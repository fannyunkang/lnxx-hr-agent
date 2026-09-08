package com.lnxx.hr.domain;

public record EmployeeProfile(String employeeId, String name, String department, String position,
                              String emailMasked, String phoneMasked) {
}

