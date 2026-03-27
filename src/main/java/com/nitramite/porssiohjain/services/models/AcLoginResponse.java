package com.nitramite.porssiohjain.services.models;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AcLoginResponse {
    private boolean success;
    private String accessToken;
}
