package com.nitramite.porssiohjain.services.models;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RabbitMqUserAuthRequest {
    private String username;
    private String password;
    private String vhost;
}