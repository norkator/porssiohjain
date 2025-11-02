package com.nitramite.porssiohjain;

import com.nitramite.porssiohjain.services.ControlService;
import com.nitramite.porssiohjain.services.models.TimeTableListResponse;
import com.nitramite.porssiohjain.services.models.TimeTableResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class ControlControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ControlService controlService;

    private UUID testDeviceUuid;

    @BeforeEach
    void setUp() {
        testDeviceUuid = UUID.randomUUID();
    }

    @Test
    @DisplayName("GET /control/{deviceUuid} should return control map")
    void controlsForDeviceShouldReturnMap() throws Exception {
        // todo make this actually test full logic

        Map<Integer, Integer> mockMap = Map.of(0, 1, 1, 0);
        Mockito.when(controlService.getControlsForDevice(anyString())).thenReturn(mockMap);

        mockMvc.perform(get("/control/{deviceUuid}", testDeviceUuid)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.['0']").value(1))
                .andExpect(jsonPath("$.['1']").value(0));
    }

    @Test
    @DisplayName("GET /control/{deviceUuid}/timetable should return schedule list")
    void timetableForDeviceShouldReturnSchedule() throws Exception {
        // todo make this actually test full logic
        List<TimeTableResponse> mockSchedule = List.of(
                TimeTableResponse.builder()
                        .time("2025-11-03T01:15:00+02:00")
                        .action(1)
                        .build(),
                TimeTableResponse.builder()
                        .time("2025-11-03T03:15:00+02:00")
                        .action(0)
                        .build()
        );

        TimeTableListResponse mockResponse = TimeTableListResponse.builder()
                .timezone("Europe/Helsinki")
                .schedule(mockSchedule)
                .build();

        Mockito.when(controlService.getTimetableForDevice(anyString()))
                .thenReturn(mockResponse);

        mockMvc.perform(get("/control/{deviceUuid}/timetable", testDeviceUuid)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timezone").value("Europe/Helsinki"))
                .andExpect(jsonPath("$.schedule[0].time").value("2025-11-03T01:15:00+02:00"))
                .andExpect(jsonPath("$.schedule[0].action").value(1))
                .andExpect(jsonPath("$.schedule[1].time").value("2025-11-03T03:15:00+02:00"))
                .andExpect(jsonPath("$.schedule[1].action").value(0));
    }

}
