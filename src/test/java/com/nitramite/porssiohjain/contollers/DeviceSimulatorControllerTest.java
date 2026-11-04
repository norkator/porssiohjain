package com.nitramite.porssiohjain.contollers;

import com.nitramite.porssiohjain.services.simulation.DeviceSimulationService;
import com.nitramite.porssiohjain.services.simulation.DeviceSimulatorProperties;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeviceSimulatorControllerTest {
    @Test
    void inventoryRequiresConfiguredPrivateServiceToken() {
        var properties = new DeviceSimulatorProperties();
        var service = mock(DeviceSimulationService.class);
        var controller = new DeviceSimulatorController(properties, service);
        assertThatThrownBy(() -> controller.inventory(null)).isInstanceOf(ResponseStatusException.class);
        properties.setToken("private-service-secret");
        assertThatThrownBy(() -> controller.inventory("Bearer demo-account-token")).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(service);
        controller.inventory("Bearer private-service-secret");
        verify(service).inventory();
    }
}
