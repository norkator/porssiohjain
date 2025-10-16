package com.nitramite.porssiohjain.contollers;

import com.nitramite.porssiohjain.services.AccountService;
import com.nitramite.porssiohjain.services.AuthService;
import com.nitramite.porssiohjain.services.RateLimitService;
import com.nitramite.porssiohjain.services.models.LoginRequest;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/account")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;
    private final AuthService authService;
    private final RateLimitService rateLimitService;
    private final HttpServletRequest request;

    private String getClientIp() {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded != null ? forwarded.split(",")[0] : request.getRemoteAddr();
    }

    @PostMapping("/create")
    public ResponseEntity<?> createAccount() {
        String ip = getClientIp();
        if (!rateLimitService.allowAccountCreation(ip)) {
            return ResponseEntity.status(429).body("Too many account creations. Try again later.");
        }

        return ResponseEntity.ok(accountService.createAccount(ip));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest requestBody) {
        String ip = getClientIp();
        if (!rateLimitService.allowLogin(ip)) {
            return ResponseEntity.status(429).body("Too many login attempts. Try again later.");
        }

        return ResponseEntity.ok(authService.login(ip, requestBody.getUuid(), requestBody.getSecret()));
    }

}