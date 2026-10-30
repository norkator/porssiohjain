package com.nitramite.porssiohjain;

import com.nitramite.porssiohjain.entity.*;
import com.nitramite.porssiohjain.entity.enums.*;
import com.nitramite.porssiohjain.entity.repository.*;
import com.nitramite.porssiohjain.mqtt.MqttService;
import com.nitramite.porssiohjain.services.heating.HeatingPlanSimulationService.OperatingMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class HeatingPlannerControllerTest {
    @Autowired MockMvc mvc;
    @Autowired AccountRepository accounts;
    @Autowired TokenRepository tokens;
    @Autowired SiteRepository sites;
    @Autowired HeatingPlannerSettingsRepository settingsRepository;
    @Autowired HeatingPlannerRoomRepository rooms;
    @Autowired HeatingPlannerPlanRepository plans;
    @Autowired HeatingPlannerPlanPointRepository points;
    @Autowired HeatingPlannerWoodRecommendationRepository wood;
    @MockitoBean MqttService mqttService;

    AccountEntity owner;
    SiteEntity site;
    String token;

    @BeforeEach void setUp() {
        owner = accounts.saveAndFlush(AccountEntity.builder().uuid(UUID.randomUUID()).secret(UUID.randomUUID().toString()).build());
        token = UUID.randomUUID().toString();
        tokens.saveAndFlush(TokenEntity.builder().account(owner).token(token).build());
        site = sites.saveAndFlush(SiteEntity.builder().account(owner).name("Home").type(SiteType.HOME).build());
    }

    @Test void monitorUsesSavedDefaultWithoutCreatingSettings() throws Exception {
        var preferred = sites.saveAndFlush(SiteEntity.builder().account(owner).name("Default cabin")
                .type(SiteType.HOME).timezone("Europe/Helsinki").build());
        owner.setDefaultHeatingPlannerSite(preferred);
        accounts.saveAndFlush(owner);
        long before = settingsRepository.count();
        mvc.perform(get("/api/heating-planner/monitor")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/heating-planner/monitor").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.summary.site.id").value(preferred.getId()))
                .andExpect(jsonPath("$.rooms").isEmpty());
        assertThat(settingsRepository.count()).isEqualTo(before);
    }

    @Test void comfortUpdateIsNarrowAndRejectsInvalidTargetsAndOtherSites() throws Exception {
        var settings = settingsRepository.saveAndFlush(HeatingPlannerSettingsEntity.builder()
                .account(owner).site(site).build());
        var room = rooms.saveAndFlush(HeatingPlannerRoomEntity.builder().account(owner).site(site)
                .settings(settings).name("Kitchen").normalFloorTemperature(new BigDecimal("24"))
                .roomSensorMeasurementKey("custom-temperature").build());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                        "/api/heating-planner/rooms/" + room.getId() + "/comfort")
                .header("Authorization", token).contentType("application/json")
                .content("{\"target\":22,\"scheduleId\":null}"))
                .andExpect(status().isOk());
        assertThat(room.getTargetRoomTemperature()).isEqualByComparingTo("22");
        assertThat(room.getNormalFloorTemperature()).isEqualByComparingTo("24");
        assertThat(room.getRoomSensorMeasurementKey()).isEqualTo("custom-temperature");
        assertThat(settings.isActiveControlEnabled()).isFalse();
        for (String target : new String[] {"4", "17", "36"}) {
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                            "/api/heating-planner/rooms/" + room.getId() + "/comfort")
                    .header("Authorization", token).contentType("application/json")
                    .content("{\"target\":" + target + "}"))
                    .andExpect(status().isBadRequest());
        }
        var other = sites.saveAndFlush(SiteEntity.builder().account(owner).name("Other").type(SiteType.HOME).build());
        owner.setDefaultHeatingPlannerSite(other);
        accounts.saveAndFlush(owner);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                        "/api/heating-planner/rooms/" + room.getId() + "/comfort")
                .header("Authorization", token).contentType("application/json").content("{\"target\":23}"))
                .andExpect(status().isBadRequest());
        assertThat(room.getTargetRoomTemperature()).isEqualByComparingTo("22");
    }

    @Test void requiresRawTokenAndPositiveSiteId() throws Exception {
        mvc.perform(get("/api/heating-planner/widget-summary").param("siteId", site.getId().toString()))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/heating-planner/widget-summary").header("Authorization", "invalid")
                        .param("siteId", site.getId().toString())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/heating-planner/widget-summary").header("Authorization", token)
                        .param("siteId", "0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/heating-planner/widget-summary").header("Authorization", token))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/heating-planner/widget-summary").header("Authorization", token)
                        .param("siteId", "invalid")).andExpect(status().isBadRequest());
    }

    @Test void returnsUnconfiguredSiteWithoutCreatingPlannerSettings() throws Exception {
        long before = settingsRepository.count();
        mvc.perform(get("/api/heating-planner/widget-summary").header("Authorization", token)
                        .param("siteId", site.getId().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.schemaVersion").value(1))
                .andExpect(jsonPath("$.status.code").value("NOT_CONFIGURED"))
                .andExpect(jsonPath("$.timezone").value("Europe/Helsinki"))
                .andExpect(jsonPath("$.rooms").isEmpty());
        assertThat(settingsRepository.count()).isEqualTo(before);
    }

    @Test void foreignSiteAndUnknownSiteReturnSameError() throws Exception {
        AccountEntity other = accounts.saveAndFlush(AccountEntity.builder().uuid(UUID.randomUUID())
                .secret(UUID.randomUUID().toString()).build());
        SiteEntity foreign = sites.saveAndFlush(SiteEntity.builder().account(other).name("Private").type(SiteType.HOME).build());
        for (Long id : new Long[] {foreign.getId(), Long.MAX_VALUE}) {
            mvc.perform(get("/api/heating-planner/widget-summary").header("Authorization", token).param("siteId", id.toString()))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("Site not found"));
        }
    }

    @Test void readsPersistedPlanAndFiltersStoveRecommendationsWithoutChangingThem() throws Exception {
        var settings = settingsRepository.saveAndFlush(HeatingPlannerSettingsEntity.builder().account(owner).site(site)
                .enabled(true).stoveLoaded(true).build());
        var room = rooms.saveAndFlush(HeatingPlannerRoomEntity.builder().account(owner).site(site).settings(settings).name("Kitchen").build());
        Instant now = Instant.now();
        var plan = plans.saveAndFlush(HeatingPlannerPlanEntity.builder().account(owner).site(site).settings(settings)
                .horizonStart(now.minusSeconds(60)).horizonEnd(now.plusSeconds(9000)).triggerReason("Widget test").build());
        points.saveAndFlush(HeatingPlannerPlanPointEntity.builder().account(owner).site(site).room(room).plan(plan)
                .planVersion(plan.getPlanVersion()).plannedTime(now.minusSeconds(60)).operatingMode(OperatingMode.PREHEAT)
                .predictedRoomTemperature(BigDecimal.valueOf(22.3)).plannedFloorSetpoint(BigDecimal.valueOf(25))
                .reason("Cheap period").build());
        var expired = wood.saveAndFlush(recommendation(settings, room, plan, now.minusSeconds(1), HeatingPlannerWoodRecommendationStatus.PENDING));
        var skipped = wood.saveAndFlush(recommendation(settings, room, plan, now.plusSeconds(1200), HeatingPlannerWoodRecommendationStatus.SKIPPED));
        var due = wood.saveAndFlush(recommendation(settings, room, plan, now.plusSeconds(2400), HeatingPlannerWoodRecommendationStatus.SENT));
        var supersededPlan = plans.saveAndFlush(HeatingPlannerPlanEntity.builder().account(owner).site(site).settings(settings)
                .horizonStart(now.minusSeconds(60)).horizonEnd(now.plusSeconds(9000)).triggerReason("Old")
                .status(HeatingPlannerPlanStatus.SUPERSEDED).build());
        wood.saveAndFlush(recommendation(settings, room, supersededPlan, now.plusSeconds(1800), HeatingPlannerWoodRecommendationStatus.PENDING));
        Instant settingsUpdated = settings.getUpdatedAt();
        long planCount = plans.count();
        long pointCount = points.count();
        mvc.perform(get("/api/heating-planner/widget-summary").header("Authorization", token)
                        .param("siteId", site.getId().toString()).param("roomId", room.getId().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status.code").value("SIMULATED"))
                .andExpect(jsonPath("$.currentModeCounts.PREHEAT").value(1))
                .andExpect(jsonPath("$.rooms[0].roomTemperature.freshness").value("MISSING"))
                .andExpect(jsonPath("$.rooms[0].currentPoint.predictedRoomTemperature").value(22.3))
                .andExpect(jsonPath("$.nextWoodRecommendation.id").value(due.getId()))
                .andExpect(jsonPath("$.nextWoodRecommendation.due").value(true));
        assertThat(settingsRepository.findById(settings.getId()).orElseThrow().getUpdatedAt()).isEqualTo(settingsUpdated);
        assertThat(plans.count()).isEqualTo(planCount);
        assertThat(points.count()).isEqualTo(pointCount);
        assertThat(wood.findById(expired.getId()).orElseThrow().getStatus()).isEqualTo(HeatingPlannerWoodRecommendationStatus.PENDING);
        assertThat(wood.findById(skipped.getId()).orElseThrow().getStatus()).isEqualTo(HeatingPlannerWoodRecommendationStatus.SKIPPED);
        assertThat(wood.findById(due.getId()).orElseThrow().getStatus()).isEqualTo(HeatingPlannerWoodRecommendationStatus.SENT);
        mvc.perform(get("/api/heating-planner/widget-summary").header("Authorization", token)
                        .param("siteId", site.getId().toString()).param("roomId", Long.toString(Long.MAX_VALUE)))
                .andExpect(status().isNotFound());
    }

    private HeatingPlannerWoodRecommendationEntity recommendation(HeatingPlannerSettingsEntity settings,
            HeatingPlannerRoomEntity room, HeatingPlannerPlanEntity plan, Instant release, HeatingPlannerWoodRecommendationStatus status) {
        return HeatingPlannerWoodRecommendationEntity.builder().account(owner).site(site).settings(settings).room(room)
                .plan(plan).planVersion(plan.getPlanVersion()).loadName("Normal load").woodAmount(BigDecimal.valueOf(8))
                .notifyAt(release.minusSeconds(2700)).releaseStartsAt(release).releaseEndsAt(release.plusSeconds(21600))
                .initialRoomHeatingRate(BigDecimal.ONE).status(status).reason("Expensive period").build();
    }
}
