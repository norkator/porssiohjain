package com.nitramite.porssiohjain;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import com.vaadin.flow.theme.Theme;
import com.vaadin.flow.component.page.AppShellConfigurator;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@Theme("my-theme")
public class PorssiohjainApplication implements AppShellConfigurator {

    public static void main(String[] args) {
        SpringApplication.run(PorssiohjainApplication.class, args);
    }
}
