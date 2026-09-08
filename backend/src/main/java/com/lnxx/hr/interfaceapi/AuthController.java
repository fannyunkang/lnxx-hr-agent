package com.lnxx.hr.interfaceapi;

import com.lnxx.hr.security.DemoUserService;
import com.lnxx.hr.security.TokenService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final DemoUserService userService;
    private final TokenService tokenService;

    public AuthController(DemoUserService userService, TokenService tokenService) {
        this.userService = userService;
        this.tokenService = tokenService;
    }

    @PostMapping("/login")
    ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return userService.authenticate(request.username(), request.password())
                .map(user -> ResponseEntity.ok(new LoginResponse(tokenService.issue(user), user.displayName(),
                        user.employeeId(), user.role().name())))
                .orElseGet(() -> ResponseEntity.status(401).build());
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
    public record LoginResponse(String token, String displayName, String employeeId, String role) {}
}
