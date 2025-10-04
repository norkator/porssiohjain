package com.nitramite.porssiohjain.services.models;

import lombok.Data;

import java.util.UUID;

@Data
public class LoginRequest {
    private UUID uuid;
    private String secret;
}