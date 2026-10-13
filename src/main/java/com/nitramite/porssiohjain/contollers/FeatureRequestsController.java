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
import com.nitramite.porssiohjain.services.models.FeatureRequestSummary;
import java.util.List;
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

    @GetMapping
    public ResponseEntity<?> list(@RequestParam(defaultValue = "0") int page) {
        var result = service.listOwn(authContext.getAccountId(), page);
        return ResponseEntity.ok(new RequestPage(result.getContent(), result.getNumber(), result.getTotalPages(), result.getTotalElements()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> detail(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(service.detail(authContext.getAccountId(), id, false));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{id}/comments")
    public ResponseEntity<?> comment(@PathVariable Long id, @RequestBody CommentInput input) {
        try {
            service.comment(authContext.getAccountId(), id, input.message(), false);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(exception.getMessage());
        }
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<?> read(@PathVariable Long id, @RequestBody ReadInput input) {
        try {
            service.markRead(authContext.getAccountId(), id, input.seenCommentId());
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(exception.getMessage());
        }
    }

    public record RequestPage(List<FeatureRequestSummary> items,
                              int page, int totalPages, long totalElements) {}
    public record CommentInput(String message) {}
    public record ReadInput(long seenCommentId) {}

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
