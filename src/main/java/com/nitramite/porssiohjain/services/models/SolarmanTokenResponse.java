package com.nitramite.porssiohjain.services.models;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SolarmanTokenResponse {

    private String access_token;
    private long expires_in;

}