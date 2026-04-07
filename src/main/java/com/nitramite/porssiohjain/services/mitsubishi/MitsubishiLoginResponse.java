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

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class MitsubishiLoginResponse {

    @JsonProperty("ErrorId")
    private Integer errorId;

    @JsonProperty("ErrorMessage")
    private String errorMessage;

    @JsonProperty("LoginStatus")
    private Integer loginStatus;

    @JsonProperty("UserId")
    private Integer userId;

    @JsonProperty("RandomKey")
    private String randomKey;

    @JsonProperty("AppVersionAnnouncement")
    private String appVersionAnnouncement;

    @JsonProperty("LoginData")
    private LoginData loginData;

    @JsonProperty("ListPendingInvite")
    private List<Object> listPendingInvite;

    @JsonProperty("ListOwnershipChangeRequest")
    private List<Object> listOwnershipChangeRequest;

    @JsonProperty("ListPendingAnnouncement")
    private List<Object> listPendingAnnouncement;

    @JsonProperty("LoginMinutes")
    private Integer loginMinutes;

    @JsonProperty("LoginAttempts")
    private Integer loginAttempts;

    @JsonProperty("EnableRegistration")
    private Boolean enableRegistration;

    @JsonProperty("EnableEditStructure")
    private Boolean enableEditStructure;

    @JsonProperty("EnableFrostProtection")
    private Boolean enableFrostProtection;

    @JsonProperty("EnableHolidayMode")
    private Boolean enableHolidayMode;

    @JsonProperty("EnableTimer")
    private Boolean enableTimer;

    @JsonProperty("EnableScenes")
    private Boolean enableScenes;

    @JsonProperty("EnableInviteNewGuests")
    private Boolean enableInviteNewGuests;

    @JsonProperty("EnableManageGuestAccess")
    private Boolean enableManageGuestAccess;

    @JsonProperty("EnableControlOfGuestDevices")
    private Boolean enableControlOfGuestDevices;

    @Data
    public static class LoginData {

        @JsonProperty("ContextKey")
        private String contextKey;

        @JsonProperty("Client")
        private Integer client;

        @JsonProperty("Terms")
        private Integer terms;

        @JsonProperty("AL")
        private Integer al;

        @JsonProperty("ML")
        private Integer ml;

        @JsonProperty("CMI")
        private Boolean cmi;

        @JsonProperty("IsStaff")
        private Boolean isStaff;

        @JsonProperty("CUTF")
        private Boolean cutf;

        @JsonProperty("CAA")
        private Boolean caa;

        @JsonProperty("ReceiveCountryNotifications")
        private Boolean receiveCountryNotifications;

        @JsonProperty("ReceiveAllNotifications")
        private Boolean receiveAllNotifications;

        @JsonProperty("CACA")
        private Boolean caca;

        @JsonProperty("CAGA")
        private Boolean caga;

        @JsonProperty("MaximumDevices")
        private Integer maximumDevices;

        @JsonProperty("ShowDiagnostics")
        private Boolean showDiagnostics;

        @JsonProperty("Language")
        private Integer language;

        @JsonProperty("Country")
        private Integer country;

        @JsonProperty("RealClient")
        private Integer realClient;

        @JsonProperty("Name")
        private String name;

        @JsonProperty("UseFahrenheit")
        private Boolean useFahrenheit;

        @JsonProperty("Duration")
        private Integer duration;

        @JsonProperty("Expiry")
        private String expiry;

        @JsonProperty("CMSC")
        private Boolean cmsc;

        @JsonProperty("PartnerApplicationVersion")
        private String partnerApplicationVersion;

        @JsonProperty("EmailSettingsReminderShown")
        private Boolean emailSettingsReminderShown;

        @JsonProperty("EmailUnitErrors")
        private Integer emailUnitErrors;

        @JsonProperty("EmailCommsErrors")
        private Integer emailCommsErrors;

        @JsonProperty("ChartSeriesHidden")
        private Integer chartSeriesHidden;

        @JsonProperty("DeletePending")
        private Boolean deletePending;

        @JsonProperty("Throttle")
        private Boolean throttle;

        @JsonProperty("PrivacyPolicy")
        private Integer privacyPolicy;

        @JsonProperty("ConfirmedDataProcessing")
        private Boolean confirmedDataProcessing;

        @JsonProperty("SendMarketingMessages")
        private Boolean sendMarketingMessages;

        @JsonProperty("IsImpersonated")
        private Boolean isImpersonated;

        @JsonProperty("LanguageCode")
        private String languageCode;

        @JsonProperty("CountryName")
        private String countryName;

        @JsonProperty("CurrencySymbol")
        private String currencySymbol;

        @JsonProperty("SupportEmailAddress")
        private String supportEmailAddress;

        @JsonProperty("DateSeperator")
        private String dateSeperator;

        @JsonProperty("TimeSeperator")
        private String timeSeperator;

        @JsonProperty("AtwLogoFile")
        private String atwLogoFile;

        @JsonProperty("DECCReport")
        private Boolean deccReport;

        @JsonProperty("CSVReport1min")
        private Boolean csvReport1min;

        @JsonProperty("HidePresetPanel")
        private Boolean hidePresetPanel;

        @JsonProperty("EmailSettingsReminderRequired")
        private Boolean emailSettingsReminderRequired;

        @JsonProperty("TermsText")
        private String termsText;

        @JsonProperty("PrivacyPolicyText")
        private String privacyPolicyText;

        @JsonProperty("MustConfirmDataProcessing")
        private Boolean mustConfirmDataProcessing;

        @JsonProperty("MapView")
        private Boolean mapView;

        @JsonProperty("MapZoom")
        private Integer mapZoom;

        @JsonProperty("MapLongitude")
        private Double mapLongitude;

        @JsonProperty("MapLatitude")
        private Double mapLatitude;

        @JsonProperty("Throttled")
        private Boolean throttled;
    }

}
