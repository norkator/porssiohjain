package com.nitramite.porssiohjain.services.models;

import lombok.Data;

@Data
public class Pagination {

    private int total;
    private int lastPage;
    private Integer nextPage;
    private Integer prevPage;
    private int currentPage;
    private int perPage;
    private int from;
    private int to;

}
