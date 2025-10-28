package com.nitramite.porssiohjain;

import com.jayway.jsonpath.JsonPath;
import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.entity.DeviceEntity;
import com.nitramite.porssiohjain.entity.repository.AccountRepository;
import com.nitramite.porssiohjain.entity.repository.DeviceRepository;
import com.nitramite.porssiohjain.entity.repository.TokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DeviceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TokenRepository tokenRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private AccountEntity testAccount;
    private String authToken;

    @BeforeEach
    void setUp() throws Exception {
        deviceRepository.deleteAll();
        tokenRepository.deleteAll();
        accountRepository.deleteAll();

        AccountEntity account = AccountEntity.builder()
                .uuid(UUID.randomUUID())
                .secret(passwordEncoder.encode("supersecret"))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        testAccount = accountRepository.save(account);

        String loginJson = String.format("""
                {
                    "uuid": "%s",
                    "secret": "supersecret"
                }
                """, testAccount.getUuid());

        String response = mockMvc.perform(post("/account/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        authToken = JsonPath.read(response, "$.token");
    }

    @Test
    @DisplayName("Create a new device")
    void createDeviceShouldReturnDeviceJson() throws Exception {
        String json = """
                {
                    "deviceName": "MyDevice",
                    "timezone": "Europe/Helsinki"
                }
                """;

        mockMvc.perform(post("/device/create/" + testAccount.getId())
                        .header("Authorization", authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.uuid").isString())
                .andExpect(jsonPath("$.deviceName").value("MyDevice"))
                .andExpect(jsonPath("$.timezone").value("Europe/Helsinki"))
                .andExpect(jsonPath("$.createdAt").isString());
    }

    @Test
    @DisplayName("List devices for account")
    void listDevicesShouldReturnDeviceList() throws Exception {
        DeviceEntity device = DeviceEntity.builder()
                .account(testAccount)
                .deviceName("Device1")
                .timezone("Europe/Helsinki")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        DeviceEntity saved = deviceRepository.save(device);

        mockMvc.perform(get("/device/list/" + testAccount.getId())
                        .header("Authorization", authToken))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].id").value(saved.getId()))
                .andExpect(jsonPath("$[0].deviceName").value("Device1"));
    }

    @Test
    @DisplayName("Get single device by ID")
    void getDeviceShouldReturnDevice() throws Exception {
        DeviceEntity device = DeviceEntity.builder()
                .account(testAccount)
                .deviceName("Device1")
                .timezone("Europe/Helsinki")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        DeviceEntity saved = deviceRepository.save(device);

        mockMvc.perform(get("/device/" + saved.getId())
                        .header("Authorization", authToken))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(saved.getId()))
                .andExpect(jsonPath("$.deviceName").value("Device1"));
    }

}
