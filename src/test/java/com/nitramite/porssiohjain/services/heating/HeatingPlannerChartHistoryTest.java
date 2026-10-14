package com.nitramite.porssiohjain.services.heating;

import com.nitramite.porssiohjain.entity.*;
import com.nitramite.porssiohjain.entity.enums.HeatingPlannerPlanStatus;
import com.nitramite.porssiohjain.entity.enums.SiteType;
import com.nitramite.porssiohjain.entity.repository.HeatingPlannerPlanPointRepository;
import jakarta.persistence.Entity;
import org.hibernate.Session;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.orm.jpa.hibernate.SpringBeanContainer;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class HeatingPlannerChartHistoryTest {
    @Test
    void retainsLatestPredictionKnownAtPeriodStartAcrossVersionsAndIsolatesAccountSiteAndLocalDay() throws Exception {
        var configuration = new Configuration();
        var beans = new DefaultListableBeanFactory();
        beans.registerSingleton("testCryptoKey", "chart-history-test-key");
        configuration.getProperties().put("hibernate.resource.beans.container", new SpringBeanContainer(beans));
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        for (var entity : scanner.findCandidateComponents("com.nitramite.porssiohjain.entity")) {
            configuration.addAnnotatedClass(Class.forName(entity.getBeanClassName()));
        }
        configuration.setProperty("hibernate.connection.driver_class", "org.h2.Driver");
        configuration.setProperty("hibernate.connection.url", "jdbc:h2:mem:chart-history-" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        configuration.setProperty("hibernate.hbm2ddl.auto", "create-drop");
        try (var factory = configuration.buildSessionFactory(); var session = factory.openSession()) {
            var transaction = session.beginTransaction();
            ZoneId zone = ZoneId.of("Europe/Helsinki");
            Instant start = LocalDate.of(2026, 10, 25).atStartOfDay(zone).toInstant();
            Instant end = LocalDate.of(2026, 10, 26).atStartOfDay(zone).toInstant();
            var room = room(session, null);
            var older = plan(session, room, start.minusSeconds(7200));
            var newer = plan(session, room, start.minusSeconds(3600));
            var tiedNewer = plan(session, room, start.minusSeconds(3600));
            var retrospective = plan(session, room, start.plusSeconds(3600));
            point(session, older, room, start, "20.00");
            point(session, newer, room, start, "21.00");
            point(session, tiedNewer, room, start, "22.00");
            point(session, retrospective, room, start, "99.00");
            point(session, newer, room, start.minusSeconds(900), "99.00");
            point(session, newer, room, end.minusSeconds(900), "23.00");
            point(session, newer, room, end, "99.00");
            var otherSiteRoom = room(session, room.getAccount());
            point(session, plan(session, otherSiteRoom, start.minusSeconds(60)), otherSiteRoom, start, "99.00");
            var otherAccountRoom = room(session, null);
            point(session, plan(session, otherAccountRoom, start.minusSeconds(60)), otherAccountRoom, start, "99.00");
            session.flush();
            session.clear();
            var repository = new JpaRepositoryFactory(session).getRepository(HeatingPlannerPlanPointRepository.class);
            var service = new HeatingPlannerPlanService(mock(), mock(), mock(), repository, mock());

            var history = service.chartHistory(room.getAccount().getId(), room.getSite().getId(), start, end);

            assertThat(history).containsOnlyKeys("Kitchen");
            assertThat(history.get("Kitchen")).extracting(HeatingPlanSimulationService.SimulationPoint::time)
                    .containsExactly(start, end.minusSeconds(900));
            assertThat(history.get("Kitchen")).extracting(HeatingPlanSimulationService.SimulationPoint::roomTemperature)
                    .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                    .containsExactly(new BigDecimal("22.00"), new BigDecimal("23.00"));
            assertThat(service.chartHistory(null, room.getSite().getId(), start, end)).isEmpty();
            transaction.rollback();
        }
    }

    private HeatingPlannerRoomEntity room(Session session, AccountEntity account) {
        if (account == null) {
            account = AccountEntity.builder().uuid(UUID.randomUUID()).secret(UUID.randomUUID().toString()).build();
            session.persist(account);
        }
        var site = SiteEntity.builder().account(account).name("Home").type(SiteType.HOME).build();
        session.persist(site);
        var settings = HeatingPlannerSettingsEntity.builder().account(account).site(site).build();
        session.persist(settings);
        var room = HeatingPlannerRoomEntity.builder().account(account).site(site).settings(settings).name("Kitchen").build();
        session.persist(room);
        return room;
    }

    private HeatingPlannerPlanEntity plan(Session session, HeatingPlannerRoomEntity room, Instant createdAt) {
        var plan = HeatingPlannerPlanEntity.builder().account(room.getAccount()).site(room.getSite())
                .settings(room.getSettings()).horizonStart(createdAt).horizonEnd(createdAt.plusSeconds(172800))
                .status(HeatingPlannerPlanStatus.SUPERSEDED).triggerReason("Chart history test").build();
        session.persist(plan);
        session.createMutationQuery("update HeatingPlannerPlanEntity set createdAt = :createdAt where id = :id")
                .setParameter("createdAt", createdAt).setParameter("id", plan.getId()).executeUpdate();
        return plan;
    }

    private void point(Session session, HeatingPlannerPlanEntity plan, HeatingPlannerRoomEntity room,
                       Instant time, String temperature) {
        session.persist(HeatingPlannerPlanPointEntity.builder().plan(plan).planVersion(plan.getPlanVersion())
                .room(room).account(room.getAccount()).site(room.getSite()).plannedTime(time)
                .predictedRoomTemperature(new BigDecimal(temperature))
                .operatingMode(HeatingPlanSimulationService.OperatingMode.NORMAL).reason("Saved prediction").build());
    }
}
