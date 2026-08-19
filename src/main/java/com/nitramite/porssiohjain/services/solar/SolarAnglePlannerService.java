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

package com.nitramite.porssiohjain.services.solar;

import com.nitramite.porssiohjain.services.models.SolarAngleRecommendationResponse;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.ZonedDateTime;

@Service
public class SolarAnglePlannerService {

    private static final double DEG_TO_RAD = Math.PI / 180.0;
    private static final double RAD_TO_DEG = 180.0 / Math.PI;
    private static final int DEFAULT_NEXT_CHECK_MINUTES = 15;

    public SolarAngleRecommendationResponse calculateRecommendation(
            double latitude,
            double longitude,
            String timezone,
            double currentTilt,
            double currentAzimuth,
            double toleranceDegrees
    ) {
        ZoneId zoneId = ZoneId.of(timezone);
        ZonedDateTime calculatedAt = ZonedDateTime.now(zoneId);
        return calculateRecommendation(latitude, longitude, zoneId, calculatedAt, currentTilt, currentAzimuth, toleranceDegrees);
    }

    public SolarAngleRecommendationResponse calculateRecommendation(
            double latitude,
            double longitude,
            ZoneId zoneId,
            ZonedDateTime calculatedAt,
            double currentTilt,
            double currentAzimuth,
            double toleranceDegrees
    ) {
        SolarPosition position = calculateSolarPosition(latitude, longitude, calculatedAt.withZoneSameInstant(zoneId));
        boolean sunVisible = position.elevation() > 0.0;
        double targetTilt = sunVisible ? clamp(90.0 - position.elevation(), 0.0, 90.0) : clamp(currentTilt, 0.0, 90.0);
        double targetAzimuth = sunVisible ? normalizeAzimuth(position.azimuth()) : normalizeAzimuth(currentAzimuth);
        double tiltDelta = targetTilt - currentTilt;
        double azimuthDelta = shortestAzimuthDelta(currentAzimuth, targetAzimuth);
        double tolerance = Math.max(0.0, toleranceDegrees);

        return SolarAngleRecommendationResponse.builder()
                .latitude(latitude)
                .longitude(longitude)
                .timezone(zoneId.getId())
                .calculatedAt(calculatedAt.withZoneSameInstant(zoneId))
                .sunVisible(sunVisible)
                .sunElevation(round(position.elevation()))
                .sunAzimuth(round(normalizeAzimuth(position.azimuth())))
                .currentTilt(round(currentTilt))
                .currentAzimuth(round(normalizeAzimuth(currentAzimuth)))
                .targetTilt(round(targetTilt))
                .targetAzimuth(round(targetAzimuth))
                .tiltDelta(round(tiltDelta))
                .azimuthDelta(round(azimuthDelta))
                .tiltDirection(directionForSignedDelta(tiltDelta, "INCREASE", "DECREASE", tolerance))
                .azimuthDirection(directionForSignedDelta(azimuthDelta, "CLOCKWISE", "COUNTER_CLOCKWISE", tolerance))
                .shouldMoveTilt(sunVisible && Math.abs(tiltDelta) > tolerance)
                .shouldMoveAzimuth(sunVisible && Math.abs(azimuthDelta) > tolerance)
                .toleranceDegrees(round(tolerance))
                .nextCheckAt(calculatedAt.withZoneSameInstant(zoneId).plusMinutes(DEFAULT_NEXT_CHECK_MINUTES))
                .build();
    }

    private SolarPosition calculateSolarPosition(double latitude, double longitude, ZonedDateTime time) {
        double hour = time.getHour()
                + (time.getMinute() / 60.0)
                + (time.getSecond() / 3600.0);
        double gamma = 2.0 * Math.PI / 365.0 * (time.getDayOfYear() - 1.0 + ((hour - 12.0) / 24.0));
        double equationOfTime = 229.18 * (0.000075
                + 0.001868 * Math.cos(gamma)
                - 0.032077 * Math.sin(gamma)
                - 0.014615 * Math.cos(2.0 * gamma)
                - 0.040849 * Math.sin(2.0 * gamma));
        double declination = 0.006918
                - 0.399912 * Math.cos(gamma)
                + 0.070257 * Math.sin(gamma)
                - 0.006758 * Math.cos(2.0 * gamma)
                + 0.000907 * Math.sin(2.0 * gamma)
                - 0.002697 * Math.cos(3.0 * gamma)
                + 0.00148 * Math.sin(3.0 * gamma);
        double zoneOffsetHours = time.getOffset().getTotalSeconds() / 3600.0;
        double localMinutes = time.getHour() * 60.0 + time.getMinute() + time.getSecond() / 60.0;
        double trueSolarTime = positiveModulo(localMinutes + equationOfTime + (4.0 * longitude) - (60.0 * zoneOffsetHours), 1440.0);
        double hourAngle = (trueSolarTime / 4.0) - 180.0;
        if (hourAngle < -180.0) {
            hourAngle += 360.0;
        }

        double latitudeRad = latitude * DEG_TO_RAD;
        double hourAngleRad = hourAngle * DEG_TO_RAD;
        double cosZenith = Math.sin(latitudeRad) * Math.sin(declination)
                + Math.cos(latitudeRad) * Math.cos(declination) * Math.cos(hourAngleRad);
        double zenith = Math.acos(clamp(cosZenith, -1.0, 1.0)) * RAD_TO_DEG;
        double elevation = 90.0 - zenith;
        double azimuth = Math.atan2(
                Math.sin(hourAngleRad),
                Math.cos(hourAngleRad) * Math.sin(latitudeRad) - Math.tan(declination) * Math.cos(latitudeRad)
        ) * RAD_TO_DEG + 180.0;
        return new SolarPosition(elevation, azimuth);
    }

    private String directionForSignedDelta(double delta, String positive, String negative, double tolerance) {
        if (Math.abs(delta) <= tolerance) {
            return "HOLD";
        }
        return delta > 0.0 ? positive : negative;
    }

    private double shortestAzimuthDelta(double currentAzimuth, double targetAzimuth) {
        return positiveModulo(targetAzimuth - currentAzimuth + 540.0, 360.0) - 180.0;
    }

    private double normalizeAzimuth(double azimuth) {
        return positiveModulo(azimuth, 360.0);
    }

    private double positiveModulo(double value, double modulo) {
        return ((value % modulo) + modulo) % modulo;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private record SolarPosition(double elevation, double azimuth) {
    }
}
