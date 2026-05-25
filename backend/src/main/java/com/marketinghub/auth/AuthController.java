package com.marketinghub.auth;

import com.marketinghub.auth.dto.AuthResponse;
import com.marketinghub.auth.dto.LoginRequest;
import com.marketinghub.auth.dto.RefreshRequest;
import com.marketinghub.auth.dto.UserDto;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final UserRepository userRepository;

    public AuthController(AuthService authService, UserRepository userRepository) {
        this.authService = authService;
        this.userRepository = userRepository;
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public UserDto me() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        AuthenticatedPrincipal principal = (AuthenticatedPrincipal) authentication.getPrincipal();
        User user = userRepository.findById(principal.userId())
            .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));
        return authService.toDto(user);
    }
}
