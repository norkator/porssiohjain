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

package com.nitramite.porssiohjain.services.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SolarAngleRecommendationResponse {
    private double latitude;
    private double longitude;
    private String timezone;
    private ZonedDateTime calculatedAt;
    private boolean sunVisible;
    private double sunElevation;
    private double sunAzimuth;
    private double currentTilt;
    private double currentAzimuth;
    private double targetTilt;
    private double targetAzimuth;
    private double tiltDelta;
    private double azimuthDelta;
    private String tiltDirection;
    private String azimuthDirection;
    private boolean shouldMoveTilt;
    private boolean shouldMoveAzimuth;
    private double toleranceDegrees;
    private ZonedDateTime nextCheckAt;
}
