package com.marketinghub.user;

import com.marketinghub.auth.AuthenticatedPrincipal;
import com.marketinghub.user.dto.CreateUserRequest;
import com.marketinghub.user.dto.ResetPasswordRequest;
import com.marketinghub.user.dto.ResetPasswordResponse;
import com.marketinghub.user.dto.UpdateUserRequest;
import com.marketinghub.user.dto.UserSummaryDto;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public Page<UserSummaryDto> list(
        @AuthenticationPrincipal AuthenticatedPrincipal caller,
        @PageableDefault(size = 50) Pageable pageable
    ) {
        return userService.listUsers(caller, pageable);
    }

    @PostMapping
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<UserSummaryDto> create(@Valid @RequestBody CreateUserRequest request) {
        UserSummaryDto created = userService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PatchMapping("/{id}")
    public UserSummaryDto update(
        @AuthenticationPrincipal AuthenticatedPrincipal caller,
        @PathVariable UUID id,
        @Valid @RequestBody UpdateUserRequest request
    ) {
        return userService.updateUser(caller, id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public UserSummaryDto disable(@PathVariable UUID id) {
        return userService.disableUser(id);
    }

    /**
     * Admin-assisted password reset. PLATFORM_ADMIN can hit this for any user (any tenant or
     * another platform admin); TENANT_ADMIN can hit it for users in their tenant only — the
     * service enforces the tenant scope. Body's {@code newPassword} is optional: when blank
     * the server generates a 16-char password and returns it.
     */
    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','TENANT_ADMIN')")
    public ResetPasswordResponse resetPassword(
        @AuthenticationPrincipal AuthenticatedPrincipal caller,
        @PathVariable UUID id,
        @Valid @RequestBody ResetPasswordRequest request
    ) {
        return userService.resetPassword(caller, id, request.newPassword());
    }
}
