/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026 Martin Kankaanranta / Nitramite Tmi
 */
package com.nitramite.porssiohjain.services.heating;

import com.nitramite.porssiohjain.entity.*;
import com.nitramite.porssiohjain.entity.enums.DeviceType;
import com.nitramite.porssiohjain.entity.enums.HeatingPlannerHeatSourceType;
import com.nitramite.porssiohjain.entity.repository.*;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class HeatingPlannerModelPersistenceTest {

    @Test
    void evidenceMigrationPreservesLegacyConfidenceAndDoesNotRestoreAnUnlearnedModel() throws Exception {
        var dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:model-migration-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL");
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                create table heating_planner_room (
                    id bigint primary key,
                    model_parameters_learned boolean not null,
                    model_confidence numeric(5, 4) not null,
                    model_trained_at timestamp with time zone
                )
                """);
        jdbc.execute("""
                insert into heating_planner_room values
                    (1, true, 0.6000, timestamp with time zone '2026-01-15 11:00:00+00'),
                    (2, false, 0.0000, timestamp with time zone '2026-01-15 11:00:00+00'),
                    (3, true, 0.2000, timestamp with time zone '2026-01-15 11:00:00+00')
                """);

        try (var connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/V110__add_heating_planner_model_evidence_time.sql"));
        }

        assertThat(jdbc.queryForObject("select model_evidence_at = model_trained_at from heating_planner_room where id = 1",
                Boolean.class)).isTrue();
        assertThat(jdbc.queryForList("select id from heating_planner_room where model_evidence_at is null", Long.class))
                .containsExactlyInAnyOrder(2L, 3L);
        assertThat(jdbc.queryForObject("select model_confidence from heating_planner_room where id = 1", BigDecimal.class))
                .isEqualByComparingTo("0.6");
        assertThat(jdbc.queryForObject("select model_parameters_learned from heating_planner_room where id = 2", Boolean.class))
                .isFalse();
    }

    @Test
    void changingRoomSensorInvalidatesThePreviousSensorsLearnedModel() {
        var room = saveWithSensor(22L);

        assertThat(room.getRoomSensorDevice().getId()).isEqualTo(22L);
        assertThat(room.isModelParametersLearned()).isFalse();
        assertThat(room.getModelConfidence()).isZero();
        assertThat(room.getModelSampleCount()).isZero();
        assertThat(room.getModelTrainedAt()).isNull();
        assertThat(room.getModelEvidenceAt()).isNull();
        assertThat(room.getRoomOutdoorLossRate()).isNull();
        assertThat(room.getWindLossRate()).isNull();
    }

    @Test
    void savingTheSameRoomSensorKeepsItsLearnedModel() {
        var room = saveWithSensor(21L);

        assertThat(room.isModelParametersLearned()).isTrue();
        assertThat(room.getModelConfidence()).isEqualByComparingTo("0.6");
        assertThat(room.getModelSampleCount()).isEqualTo(72);
        assertThat(room.getModelEvidenceAt()).isEqualTo(Instant.parse("2026-01-15T11:00:00Z"));
        assertThat(room.getRoomOutdoorLossRate()).isEqualByComparingTo("0.012");
    }

    private HeatingPlannerRoomEntity saveWithSensor(Long sensorId) {
        AccountRepository accounts = mock();
        SiteRepository sites = mock();
        DeviceRepository devices = mock();
        HeatingPlannerSettingsRepository settingsRepository = mock();
        HeatingPlannerRoomRepository rooms = mock();
        var service = new HeatingPlannerConfigurationService(accounts, sites, devices, mock(), settingsRepository, rooms);
        var account = AccountEntity.builder().id(7L).build();
        var site = SiteEntity.builder().id(8L).account(account).timezone("Europe/Helsinki").build();
        var settings = HeatingPlannerSettingsEntity.builder().id(1L).account(account).site(site).build();
        var previousSensor = DeviceEntity.builder().id(21L).account(account).deviceType(DeviceType.TEMPERATURE_SENSOR).build();
        var selectedSensor = DeviceEntity.builder().id(sensorId).account(account).deviceType(DeviceType.TEMPERATURE_SENSOR).build();
        var room = HeatingPlannerRoomEntity.builder().id(10L).settings(settings).account(account).site(site)
                .name("Kitchen").roomSensorDevice(previousSensor).roomSensorMeasurementKey("temperature")
                .modelParametersLearned(true).modelSampleCount(72).modelConfidence(new BigDecimal("0.6"))
                .modelTrainedAt(Instant.parse("2026-01-15T11:30:00Z"))
                .modelEvidenceAt(Instant.parse("2026-01-15T11:00:00Z"))
                .roomOutdoorLossRate(new BigDecimal("0.012")).windLossRate(new BigDecimal("0.001")).build();
        when(accounts.findById(7L)).thenReturn(Optional.of(account));
        when(sites.findByIdAndAccountId(8L, 7L)).thenReturn(Optional.of(site));
        when(settingsRepository.findByAccountIdAndSiteId(7L, 8L)).thenReturn(Optional.of(settings));
        when(settingsRepository.save(settings)).thenReturn(settings);
        when(rooms.findBySettingsIdOrderBySortOrderAscIdAsc(1L)).thenReturn(List.of(room));
        when(devices.findByIdAndAccount(sensorId, account)).thenReturn(Optional.of(selectedSensor));
        var configuration = new HeatingPlannerConfigurationService.SettingsConfiguration(true,
                new BigDecimal("5"), BigDecimal.ZERO, new BigDecimal("25.5"), null,
                false, LocalTime.of(6, 0), LocalTime.of(22, 0), new BigDecimal("8"), 45, 360,
                new BigDecimal("5"), new BigDecimal("20"), new BigDecimal("0.25"), new BigDecimal("0.75"),
                false, LocalTime.of(22, 0), LocalTime.of(5, 0));
        var roomConfiguration = new HeatingPlannerConfigurationService.RoomConfiguration("Kitchen",
                HeatingPlannerHeatSourceType.FLOOR_HEATING, false, new BigDecimal("2"),
                new BigDecimal("21"), new BigDecimal("20"), new BigDecimal("23"), new BigDecimal("27"),
                new BigDecimal("29"), new BigDecimal("19"), null, sensorId, null);

        service.save(7L, 8L, configuration, List.of(roomConfiguration));

        verify(rooms).save(room);
        return room;
    }
}
