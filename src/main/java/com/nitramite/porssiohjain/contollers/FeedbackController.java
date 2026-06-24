/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 *
 * This source code is licensed under the Pörssiohjain Personal Use License v1.0.
 * Private self-hosting for personal household use is permitted.
 * Commercial use, resale, managed hosting, or offering the software as a
 * service to third parties requires separate written permission.
 * See LICENSE for details.
 */

package com.nitramite.porssiohjain.contollers;

import com.nitramite.porssiohjain.auth.RequireAuth;
import com.nitramite.porssiohjain.services.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/feedback")
@RequiredArgsConstructor
@RequireAuth
public class FeedbackController {

    private final EmailService emailService;

    @PostMapping
    public ResponseEntity<?> sendFeedback(@RequestBody FeedbackRequest request) {
        String feedback = request.feedback() == null ? "" : request.feedback().trim();
        String contactEmail = request.contactEmail() == null ? "" : request.contactEmail().trim();

        if (feedback.isBlank()) {
            return ResponseEntity.badRequest().body("Feedback is required");
        }

        if (contactEmail.isBlank()) {
            return ResponseEntity.badRequest().body("Contact email is required");
        }

        emailService.sendFeedbackEmail(feedback, contactEmail);

        return ResponseEntity.noContent().build();
    }

    public record FeedbackRequest(String feedback, String contactEmail) {
    }
}
