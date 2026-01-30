package com.nitramite.porssiohjain.services.models;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

@Getter
@AllArgsConstructor
public class SolarmanTokenCache {

    private String token;
    private Instant expiresAt;

}