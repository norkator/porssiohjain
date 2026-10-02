/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 * Licensed under the Pörssiohjain Personal Use License v1.0.
 */
package com.nitramite.porssiohjain.services.models;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FeatureRequestInput(
        @NotBlank @Size(max = 5000) String useCase,
        @NotBlank @Size(max = 5000) String requestedChanges,
        @Email @Size(max = 254) String contactEmail
) {}
