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

package com.nitramite.porssiohjain.services.mitsubishi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MitsubishiAcStateResponse {

    @JsonProperty("EffectiveFlags")
    private Long effectiveFlags;

    @JsonProperty("LocalIPAddress")
    private String localIPAddress;

    @JsonProperty("RoomTemperature")
    private Double roomTemperature;

    @JsonProperty("SetTemperature")
    private Double setTemperature;

    @JsonProperty("SetFanSpeed")
    private Integer setFanSpeed;

    @JsonProperty("OperationMode")
    private Integer operationMode;

    @JsonProperty("VaneHorizontal")
    private Integer vaneHorizontal;

    @JsonProperty("VaneVertical")
    private Integer vaneVertical;

    @JsonProperty("Name")
    private String name;

    @JsonProperty("NumberOfFanSpeeds")
    private Integer numberOfFanSpeeds;

    @JsonProperty("WeatherObservations")
    private List<WeatherObservation> weatherObservations;

    @JsonProperty("ErrorMessage")
    private String errorMessage;

    @JsonProperty("ErrorCode")
    private Integer errorCode;

    @JsonProperty("DefaultHeatingSetTemperature")
    private Double defaultHeatingSetTemperature;

    @JsonProperty("DefaultCoolingSetTemperature")
    private Double defaultCoolingSetTemperature;

    @JsonProperty("HideVaneControls")
    private Boolean hideVaneControls;

    @JsonProperty("HideDryModeControl")
    private Boolean hideDryModeControl;

    @JsonProperty("RoomTemperatureLabel")
    private Integer roomTemperatureLabel;

    @JsonProperty("InStandbyMode")
    private Boolean inStandbyMode;

    @JsonProperty("TemperatureIncrementOverride")
    private Integer temperatureIncrementOverride;

    @JsonProperty("ProhibitSetTemperature")
    private Boolean prohibitSetTemperature;

    @JsonProperty("ProhibitOperationMode")
    private Boolean prohibitOperationMode;

    @JsonProperty("ProhibitPower")
    private Boolean prohibitPower;

    @JsonProperty("DemandPercentage")
    private Integer demandPercentage;

    @JsonProperty("DeviceID")
    private Long deviceId;

    @JsonProperty("DeviceType")
    private Integer deviceType;

    @JsonProperty("LastCommunication")
    private String lastCommunication;

    @JsonProperty("NextCommunication")
    private String nextCommunication;

    @JsonProperty("Power")
    private Boolean power;

    @JsonProperty("HasPendingCommand")
    private Boolean hasPendingCommand;

    @JsonProperty("Offline")
    private Boolean offline;

    @JsonProperty("Scene")
    private Object scene;

    @JsonProperty("SceneOwner")
    private Object sceneOwner;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WeatherObservation {
        @JsonProperty("Date")
        private String date;

        @JsonProperty("Sunrise")
        private String sunrise;

        @JsonProperty("Sunset")
        private String sunset;

        @JsonProperty("Condition")
        private Integer condition;

        @JsonProperty("Humidity")
        private Integer humidity;

        @JsonProperty("Temperature")
        private Integer temperature;

        @JsonProperty("Icon")
        private String icon;

        @JsonProperty("ConditionName")
        private String conditionName;

        @JsonProperty("Day")
        private Integer day;

        @JsonProperty("WeatherType")
        private Integer weatherType;
    }

}
