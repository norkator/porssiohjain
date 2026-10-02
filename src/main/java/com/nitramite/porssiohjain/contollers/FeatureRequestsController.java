/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 * Licensed under the Pörssiohjain Personal Use License v1.0.
 */
package com.nitramite.porssiohjain.contollers;

import com.nitramite.porssiohjain.auth.AuthContext;
import com.nitramite.porssiohjain.auth.RequireAuth;
import com.nitramite.porssiohjain.services.FeatureRequestService;
import com.nitramite.porssiohjain.services.models.FeatureRequestInput;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/feature-requests")
@RequireAuth
@RequiredArgsConstructor
public class FeatureRequestsController {
    private final AuthContext authContext;
    private final FeatureRequestService service;

    @PostMapping
    public ResponseEntity<?> submit(@RequestBody FeatureRequestInput input) {
        try {
            service.submit(authContext.getAccountId(), input);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(exception.getMessage());
        }
    }
}
