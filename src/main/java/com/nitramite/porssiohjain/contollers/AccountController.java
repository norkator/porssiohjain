package com.nitramite.porssiohjain.contollers;

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.services.AccountService;
import com.nitramite.porssiohjain.services.AuthService;
import com.nitramite.porssiohjain.services.models.LoginRequest;
import com.nitramite.porssiohjain.services.models.LoginResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/account")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;
    private final AuthService authService;

    @PostMapping("/create")
    public ResponseEntity<AccountEntity> createAccount() {
        AccountEntity account = accountService.createAccount();
        return ResponseEntity.ok(account);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @RequestBody LoginRequest request
    ) {
        return ResponseEntity.ok(authService.login(request.getUuid(), request.getSecret()));
    }

}