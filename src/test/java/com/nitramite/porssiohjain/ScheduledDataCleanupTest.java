package com.nitramite.porssiohjain;

import com.nitramite.porssiohjain.entity.FingridDataEntity;
import com.nitramite.porssiohjain.entity.NordpoolEntity;
import com.nitramite.porssiohjain.entity.PricePredictionEntity;
import com.nitramite.porssiohjain.entity.repository.FingridDataRepository;
import com.nitramite.porssiohjain.entity.repository.NordpoolRepository;
import com.nitramite.porssiohjain.entity.repository.PricePredictionRepository;
import com.nitramite.porssiohjain.mqtt.MqttService;
import com.nitramite.porssiohjain.services.PricePredictionDataService;
import com.nitramite.porssiohjain.services.fingrid.FingridDataService;
import com.nitramite.porssiohjain.services.nordpool.NordpoolDataPortalService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// No test transaction: cleanup must open and commit its own transaction, as the scheduler does.
@SpringBootTest
@ActiveProfiles("test")
class ScheduledDataCleanupTest {
    @MockitoBean MqttService mqttService;
    @Autowired FingridDataRepository fingridRepository;
    @Autowired NordpoolRepository nordpoolRepository;
    @Autowired PricePredictionRepository predictionRepository;
    @Autowired FingridDataService fingridService;
    @Autowired NordpoolDataPortalService nordpoolService;
    @Autowired PricePredictionDataService predictionService;

    @Test
    void fingridCleanupCommitsDeletionAndKeepsRowsAtAndAfterCutoff() {
        fingridRepository.deleteAll();
        Instant cutoff = cutoffMonths(5);
        fingridRepository.saveAll(List.of(fingrid(cutoff.minusSeconds(1)), fingrid(cutoff),
                fingrid(cutoff.plusSeconds(3600))));

        fingridService.deleteOldFingridData();

        assertThat(fingridRepository.findAll()).extracting(FingridDataEntity::getStartTime)
                .containsExactlyInAnyOrder(cutoff, cutoff.plusSeconds(3600));
    }

    @Test
    void nordpoolCleanupCommitsDeletionAndKeepsRowsAtAndAfterCutoff() {
        nordpoolRepository.deleteAll();
        Instant cutoff = cutoffMonths(5);
        nordpoolRepository.saveAll(List.of(price(cutoff.minusSeconds(1)), price(cutoff),
                price(cutoff.plusSeconds(3600))));

        nordpoolService.deleteOldNordpoolData();

        assertThat(nordpoolRepository.findAll()).extracting(NordpoolEntity::getDeliveryStart)
                .containsExactlyInAnyOrder(cutoff, cutoff.plusSeconds(3600));
    }

    @Test
    void predictionCleanupCommitsDeletionAndKeepsRowsAtAndAfterCutoff() {
        predictionRepository.deleteAll();
        Instant cutoff = LocalDate.now().minusDays(90).atStartOfDay(ZoneId.systemDefault()).toInstant();
        predictionRepository.saveAll(List.of(prediction(cutoff.minusSeconds(1)), prediction(cutoff),
                prediction(cutoff.plusSeconds(3600))));

        predictionService.deleteOldData();

        assertThat(predictionRepository.findAll()).extracting(PricePredictionEntity::getTimestamp)
                .containsExactlyInAnyOrder(cutoff, cutoff.plusSeconds(3600));
    }

    private Instant cutoffMonths(int months) {
        return LocalDate.now().minusMonths(months).atStartOfDay(ZoneId.systemDefault()).toInstant();
    }

    private FingridDataEntity fingrid(Instant start) {
        var row = new FingridDataEntity();
        row.setDatasetId(245);
        row.setStartTime(start);
        row.setEndTime(start.plusSeconds(900));
        row.setValue(BigDecimal.ONE);
        return row;
    }

    private NordpoolEntity price(Instant start) {
        var row = new NordpoolEntity();
        row.setMarketIndexName("FI");
        row.setDeliveryStart(start);
        row.setDeliveryEnd(start.plusSeconds(900));
        row.setPriceFi(BigDecimal.ONE);
        return row;
    }

    private PricePredictionEntity prediction(Instant start) {
        return PricePredictionEntity.builder().timestamp(start).priceCents(BigDecimal.ONE).build();
    }
}
