package com.nitramite.porssiohjain.services.models;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class LoginResponse {
    private String token;
    private Instant expiresAt;
}