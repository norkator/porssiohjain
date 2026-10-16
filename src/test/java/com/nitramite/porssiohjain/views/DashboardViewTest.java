package com.nitramite.porssiohjain.views;

import com.nitramite.porssiohjain.entity.enums.DevicePlatform;
import com.nitramite.porssiohjain.entity.enums.DeviceType;
import com.nitramite.porssiohjain.services.AuthService;
import com.nitramite.porssiohjain.services.models.DeviceResponse;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class DashboardViewTest {

    @ParameterizedTest
    @CsvSource({
            "TUYA_SMART, 30, true, true, green",
            "TUYA_SMART, 59, true, true, green",
            "TUYA_SMART, 61, true, true, red",
            "TUYA_SMART, 30, true, false, red",
            "TUYA_SMART, 30, false, true, red",
            "ANDROID_ZIGBEE, 9, true, true, green",
            "ANDROID_ZIGBEE, 11, true, true, red"
    })
    void sensorCardUsesPlatformContactTimeoutAndTuyaOnlineState(DevicePlatform platform, long minutesSinceContact,
                                                              boolean enabled, boolean apiOnline, String color) {
        try (var accounts = mockStatic(ViewAuthUtils.class)) {
            var view = new DashboardView(mock(AuthService.class), null, null, null, null, null, null, null);
            var device = DeviceResponse.builder().deviceName("Temperature sensor")
                    .deviceType(DeviceType.TEMPERATURE_SENSOR).devicePlatform(platform)
                    .enabled(enabled).apiOnline(apiOnline)
                    .lastCommunication(Instant.now().minusSeconds(minutesSinceContact * 60)).build();

            Component card = ReflectionTestUtils.invokeMethod(view, "createDeviceCard", device);

            Div indicator = card.getChildren().filter(Div.class::isInstance).map(Div.class::cast)
                    .findFirst().orElseThrow();
            assertThat(indicator.getStyle().get("background-color")).isEqualTo(color);
        }
    }
}
