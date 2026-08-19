package com.nitramite.porssiohjain.services.models;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data @Builder
public class WindForecastChartResponse {
    private String timezone;
    private BigDecimal todayAverage;
    private BigDecimal tomorrowAverage;
    private BigDecimal tomorrowDropPercent;
    private List<Point> points;
    @Data @Builder public static class Point { private Instant startTime; private Instant endTime; private BigDecimal megawatts; }
}
