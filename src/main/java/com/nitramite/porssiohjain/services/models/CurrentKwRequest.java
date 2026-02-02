package com.nitramite.porssiohjain.services.models;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CurrentKwRequest {
    private Double currentKw;
    private Double totalKwh;
    private Long measuredAt;
}
