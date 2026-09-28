package com.nitramite.porssiohjain.contollers;

import com.nitramite.porssiohjain.auth.AuthContext;
import com.nitramite.porssiohjain.auth.RequireAuth;
import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.entity.enums.AccountTier;
import com.nitramite.porssiohjain.entity.enums.AccountActivitySource;
import com.nitramite.porssiohjain.entity.repository.AccountRepository;
import com.nitramite.porssiohjain.services.AccountLimitService;
import com.nitramite.porssiohjain.services.AdminAuthorizationService;
import com.nitramite.porssiohjain.services.AuthService;
import com.nitramite.porssiohjain.services.SystemLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.nio.file.AccessDeniedException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/users")
@RequireAuth
@RequiredArgsConstructor
public class AdminUsersController {
    private final AuthContext authContext;
    private final AdminAuthorizationService adminAuthorizationService;
    private final AccountRepository accountRepository;
    private final AccountLimitService accountLimitService;
    private final AuthService authService;
    private final SystemLogService systemLogService;

    @GetMapping
    public UsersPage listUsers(@RequestParam(defaultValue = "") String search,
                               @RequestParam(defaultValue = "0") int page) throws AccessDeniedException {
        adminAuthorizationService.requireAdmin(authContext.getAccountId());
        if (page < 0 || search.length() > 200) {
            throw new IllegalArgumentException("Invalid search or page");
        }
        var users = accountRepository.searchAdminUsers(search.trim(),
                PageRequest.of(page, 50, Sort.by(Sort.Direction.ASC, "id")));
        return new UsersPage(users.getContent().stream().map(this::userSummary).toList(),
                users.getNumber(), users.getTotalPages(), users.getTotalElements());
    }

    @PostMapping("/{accountId}/preview")
    public UserSummary previewUser(@PathVariable Long accountId) throws AccessDeniedException {
        var admin = adminAuthorizationService.requireAdmin(authContext.getAccountId());
        var target = authService.getAccount(accountId);
        if (target.isAdmin() || admin.getId().equals(target.getId())) {
            throw new IllegalArgumentException("Cannot view an admin account");
        }
        systemLogService.log("Admin account " + admin.getId() + " started hybrid-web preview of account " + accountId);
        return userSummary(target);
    }

    private UserSummary userSummary(AccountEntity account) {
        return new UserSummary(account.getId(), account.getUuid(), account.getEmail(), account.getTier(),
                account.isAdmin(), account.isBlocked(), account.getCreatedAt(), account.getUpdatedAt(),
                account.getLastActivitySource(), accountLimitService.getDeviceCount(account.getId()));
    }

    public record UsersPage(List<UserSummary> users, int page, int totalPages, long totalElements) {}

    // Never serialize AccountEntity: it contains the password hash and private account settings.
    public record UserSummary(Long id, UUID uuid, String email, AccountTier tier, boolean admin,
                              boolean blocked, Instant createdAt, Instant updatedAt,
                              AccountActivitySource lastActivitySource, long deviceCount) {}
}
