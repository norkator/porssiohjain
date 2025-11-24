package com.nitramite.porssiohjain.services.models;

import lombok.Data;

import java.util.List;

@Data
public class WindForecastResponse {

    private List<WindDataEntry> data;
    private Pagination pagination;

}
