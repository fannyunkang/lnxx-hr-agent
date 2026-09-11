package com.lnxx.hr.security;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

@Service
public class DemoUserService {
    private final Map<String, DemoUser> users = Map.of(
            "employee", new DemoUser("employee", "employee123", "E1001", "张伟", UserRole.EMPLOYEE),
            "employee2", new DemoUser("employee2", "employee234", "E1002", "王敏", UserRole.EMPLOYEE),
            "hr", new DemoUser("hr", "hr123456", "E9001", "李娜", UserRole.HR),
            "admin", new DemoUser("admin", "admin123456", "E9002", "赵强", UserRole.ADMIN)
    );

    public Optional<DemoUser> authenticate(String username, String password) {
        return find(username).filter(user -> user.password().equals(password));
    }

    public Optional<DemoUser> find(String username) {
        return Optional.ofNullable(users.get(username));
    }
}
