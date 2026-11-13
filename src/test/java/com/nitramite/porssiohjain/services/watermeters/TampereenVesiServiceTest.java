package com.nitramite.porssiohjain.services.watermeters;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

class TampereenVesiServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private String response(String pairs) {
        return """
                {"MeteringPointCode":"123", "Data":{"IsValid":true,"Days":{"Consumption":{
                "Resolution":"Day","Unit":"m³","Data":%s}}}}
                """.formatted(pairs);
    }
    @Test void readsDailyUsageAndSkipsMissingValues() throws Exception {
        var values = TampereenVesiService.parseDaily(mapper.readTree(response(
                "[[1792627200000,0.7490000000000001],[1792713600000,null],[1792800000000,0]]")), "123");
        assertEquals(new BigDecimal("0.749000"), values.get(LocalDate.of(2026,10,22)));
        assertFalse(values.containsKey(LocalDate.of(2026,10,23)));
        assertEquals(new BigDecimal("0.000000"), values.get(LocalDate.of(2026,10,24)));
    }
    @Test void rejectsOtherMeterNegativeUsageAndDuplicateDates() throws Exception {
        assertThrows(WaterMeterException.class, () -> TampereenVesiService.parseDaily(
                mapper.readTree(response("[]")), "other"));
        assertThrows(WaterMeterException.class, () -> TampereenVesiService.parseDaily(
                mapper.readTree(response("[[1792627200000,-1]]")), "123"));
        assertThrows(WaterMeterException.class, () -> TampereenVesiService.parseDaily(
                mapper.readTree(response("[[1792627200000,1],[1792627200000,2]]")), "123"));
    }
    @Test void restrictsPortalToTrustedEndpoint() {
        var service = new TampereenVesiService();
        assertEquals(TampereenVesiService.PORTAL, service.normalizePortalUrl(TampereenVesiService.PORTAL));
        assertThrows(WaterMeterException.class, () -> service.normalizePortalUrl("https://example.com"));
    }
}
