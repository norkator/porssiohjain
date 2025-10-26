package com.nitramite.porssiohjain;

import com.nitramite.porssiohjain.services.SystemLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import com.vaadin.flow.theme.Theme;
import com.vaadin.flow.component.page.AppShellConfigurator;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@Theme("my-theme")
public class PorssiohjainApplication implements AppShellConfigurator {

    @Autowired
    SystemLogService systemLogService;

    public static void main(String[] args) {
        SpringApplication.run(PorssiohjainApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        systemLogService.log("Application started successfully.");
    }

}
