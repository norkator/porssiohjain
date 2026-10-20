package com.nitramite.porssiohjain.services.heating;

import com.nitramite.porssiohjain.entity.*;
import com.nitramite.porssiohjain.entity.enums.HeatingPlannerHeatSourceType;
import com.nitramite.porssiohjain.entity.enums.SiteType;
import com.nitramite.porssiohjain.entity.repository.*;
import com.nitramite.porssiohjain.services.heating.HeatingPlannerConfigurationService.ComfortScheduleConfiguration;
import com.nitramite.porssiohjain.services.models.HeatingComfortSchedule;
import jakarta.persistence.Entity;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.orm.jpa.hibernate.SpringBeanContainer;

import java.math.BigDecimal;
import java.sql.DriverManager;
import java.time.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

class HeatingComfortSchedulePersistenceTest {
    @Test
    void migrationKeepsExistingRoomsConstantAndAddsScheduleAndPlanPointStorage() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:comfort-migration-" + UUID.randomUUID()
                + ";MODE=PostgreSQL"); var sql = connection.createStatement()) {
            sql.execute("create table account (id bigint primary key)");
            sql.execute("create table site (id bigint primary key)");
            sql.execute("create table heating_planner_room (id bigint primary key, target_room_temperature numeric(10,2))");
            sql.execute("create table heating_planner_plan_point (id bigint primary key)");
            sql.execute("insert into heating_planner_room values (1, 21)");
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V116__add_heating_comfort_schedules.sql"));
            try (var result = sql.executeQuery("select target_room_temperature, comfort_schedule_id from heating_planner_room")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getBigDecimal(1)).isEqualByComparingTo("21");
                assertThat(result.getObject(2)).isNull();
            }
        }
    }

    @Test
    void savesSharedSchedulesRoundTripsRoomSelectionAndIsolatesOwnershipAndSites() throws Exception {
        var configuration = new Configuration();
        var beans = new DefaultListableBeanFactory();
        beans.registerSingleton("testCryptoKey", "comfort-test-key");
        configuration.getProperties().put("hibernate.resource.beans.container", new SpringBeanContainer(beans));
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        for (var entity : scanner.findCandidateComponents("com.nitramite.porssiohjain.entity")) {
            configuration.addAnnotatedClass(Class.forName(entity.getBeanClassName()));
        }
        configuration.setProperty("hibernate.connection.driver_class", "org.h2.Driver");
        configuration.setProperty("hibernate.connection.url", "jdbc:h2:mem:comfort-" + UUID.randomUUID() + ";MODE=PostgreSQL");
        configuration.setProperty("hibernate.hbm2ddl.auto", "create-drop");
        try (var factory = configuration.buildSessionFactory(); var session = factory.openSession()) {
            var tx = session.beginTransaction();
            var account = AccountEntity.builder().uuid(UUID.randomUUID()).secret(UUID.randomUUID().toString()).build();
            session.persist(account);
            var site = SiteEntity.builder().account(account).name("Home").type(SiteType.HOME).timezone("Europe/Helsinki").build();
            var otherSite = SiteEntity.builder().account(account).name("Cottage").type(SiteType.HOME).build();
            session.persist(site);
            session.persist(otherSite);
            var repositories = new JpaRepositoryFactory(session);
            var rooms = repositories.getRepository(HeatingPlannerRoomRepository.class);
            var settings = repositories.getRepository(HeatingPlannerSettingsRepository.class);
            var schedules = repositories.getRepository(HeatingPlannerComfortScheduleRepository.class);
            var service = new HeatingPlannerConfigurationService(repositories.getRepository(AccountRepository.class),
                    repositories.getRepository(SiteRepository.class), mock(), mock(), settings, rooms, schedules);
            var daily = new HeatingComfortSchedule(new BigDecimal("21"), List.of(new HeatingComfortSchedule.Period(
                    LocalTime.of(6, 0), LocalTime.of(9, 0), new BigDecimal("22"))));
            var saved = service.saveComfortSchedule(account.getId(), site.getId(), new ComfortScheduleConfiguration(null, "Morning", daily));
            var room = new HeatingPlannerConfigurationService.RoomConfiguration("Living room", HeatingPlannerHeatSourceType.HEAT_PUMP,
                    false, new BigDecimal("2"), new BigDecimal("21"), new BigDecimal("20"), new BigDecimal("23"),
                    new BigDecimal("27"), new BigDecimal("29"), new BigDecimal("19"), null, null, null, saved);
            service.save(account.getId(), site.getId(), settingsConfiguration(), List.of(room));
            session.flush();
            session.clear();

            var restored = service.configuration(account.getId(), site.getId()).rooms().getFirst();
            assertThat(restored.comfortSchedule()).isEqualTo(new ComfortScheduleConfiguration(saved.id(), "Morning",
                    new HeatingComfortSchedule(new BigDecimal("21.00"), List.of(new HeatingComfortSchedule.Period(
                            LocalTime.of(6, 0), LocalTime.of(9, 0), new BigDecimal("22.00"))))));
            assertThat(schedules.findByIdAndAccountIdAndSiteId(saved.id(), account.getId() + 1000, site.getId())).isEmpty();
            assertThatThrownBy(() -> service.saveComfortSchedule(account.getId(), otherSite.getId(), saved))
                    .hasMessage("heatingPlanner.comfort.notFound");
            assertThatThrownBy(() -> service.deleteComfortSchedule(account.getId(), site.getId(), saved.id()))
                    .hasMessage("heatingPlanner.comfort.inUse");
            assertThatThrownBy(() -> service.saveComfortSchedule(account.getId(), site.getId(),
                    new ComfortScheduleConfiguration(null, "morning", daily)))
                    .hasMessage("heatingPlanner.comfort.duplicateName");

            // The planned point's actual target is retained independently of later schedule edits.
            var points = repositories.getRepository(HeatingPlannerPlanPointRepository.class);
            var plans = new HeatingPlannerPlanService(settings, rooms, repositories.getRepository(HeatingPlannerPlanRepository.class),
                    points, repositories.getRepository(HeatingPlannerWoodRecommendationRepository.class));
            var point = new HeatingPlanSimulationService.SimulationPoint(Instant.parse("2026-01-15T04:00:00Z"),
                    BigDecimal.TEN, BigDecimal.ZERO, new BigDecimal("23"), new BigDecimal("21"), new BigDecimal("24"),
                    BigDecimal.ZERO, true, HeatingPlanSimulationService.OperatingMode.NORMAL, "Scheduled morning", new BigDecimal("22"));
            assertThat(plans.persistSimulatedPlan(account.getId(), site.getId(), Map.of("Living room",
                    new HeatingPlanSimulationService.SimulationResult(List.of(point), BigDecimal.ZERO, BigDecimal.ZERO,
                            null, true, "Active")))).isTrue();
            session.flush();
            session.clear();
            var persisted = points.findAll().getFirst();
            assertThat(persisted.getComfortTarget()).isEqualByComparingTo("22");
            assertThat(persisted.getPlannedHeatPumpSetpoint()).isEqualByComparingTo("24");

            service.saveComfortSchedule(account.getId(), site.getId(), new ComfortScheduleConfiguration(saved.id(), "Morning",
                    new HeatingComfortSchedule(new BigDecimal("21"), List.of(new HeatingComfortSchedule.Period(
                            LocalTime.of(6, 0), LocalTime.of(9, 0), new BigDecimal("23"))))));
            session.flush();
            session.clear();
            assertThat(service.configuration(account.getId(), site.getId()).rooms().getFirst().comfortSchedule().schedule()
                    .targetAt(point.time(), ZoneId.of("Europe/Helsinki"))).isEqualByComparingTo("23");
            assertThat(points.findAll().getFirst().getComfortTarget()).isEqualByComparingTo("22");
            tx.rollback();
        }
    }

    private HeatingPlannerConfigurationService.SettingsConfiguration settingsConfiguration() {
        return new HeatingPlannerConfigurationService.SettingsConfiguration(true, new BigDecimal("5"), BigDecimal.ZERO,
                new BigDecimal("25.5"), null, false, LocalTime.of(6, 0), LocalTime.of(22, 0), new BigDecimal("8"),
                45, 360, new BigDecimal("5"), new BigDecimal("20"), new BigDecimal("0.25"), new BigDecimal("0.75"),
                false, LocalTime.of(22, 0), LocalTime.of(5, 0));
    }
}
