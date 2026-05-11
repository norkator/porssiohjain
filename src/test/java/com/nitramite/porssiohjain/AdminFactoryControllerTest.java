package com.nitramite.porssiohjain;

import com.jayway.jsonpath.JsonPath;
import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.entity.enums.MqttDeviceProfile;
import com.nitramite.porssiohjain.entity.repository.AccountRepository;
import com.nitramite.porssiohjain.entity.repository.DeviceRepository;
import com.nitramite.porssiohjain.entity.repository.FactoryDeviceRepository;
import com.nitramite.porssiohjain.entity.repository.PowerLimitRepository;
import com.nitramite.porssiohjain.entity.repository.ProductionSourceRepository;
import com.nitramite.porssiohjain.entity.repository.SiteRepository;
import com.nitramite.porssiohjain.entity.repository.TokenRepository;
import com.nitramite.porssiohjain.mqtt.MqttService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminFactoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TokenRepository tokenRepository;

    @Autowired
    private FactoryDeviceRepository factoryDeviceRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private ProductionSourceRepository productionSourceRepository;

    @Autowired
    private PowerLimitRepository powerLimitRepository;

    @Autowired
    private SiteRepository siteRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private MqttService mqttService;

    private String adminAuthToken;
    private String userAuthToken;

    @BeforeEach
    void setUp() throws Exception {
        factoryDeviceRepository.deleteAll();
        deviceRepository.deleteAll();
        productionSourceRepository.deleteAll();
        powerLimitRepository.deleteAll();
        siteRepository.deleteAll();
        tokenRepository.deleteAll();
        accountRepository.deleteAll();

        AccountEntity admin = accountRepository.save(AccountEntity.builder()
                .uuid(UUID.randomUUID())
                .secret(passwordEncoder.encode("adminsecret"))
                .admin(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());

        AccountEntity user = accountRepository.save(AccountEntity.builder()
                .uuid(UUID.randomUUID())
                .secret(passwordEncoder.encode("usersecret"))
                .admin(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());

        adminAuthToken = login(admin.getUuid(), "adminsecret");
        userAuthToken = login(user.getUuid(), "usersecret");
    }

    @Test
    @DisplayName("Admin can create and list factory devices")
    void adminCanCreateAndListFactoryDevices() throws Exception {
        String request = """
                {
                    "serialNumber": "SER-001",
                    "platform": "OPENBEKEN",
                    "productModel": "Relay-2CH",
                    "mqttDeviceProfile": "OPENBEKEN_RELAY"
                }
                """;

        mockMvc.perform(post("/admin/factory/devices")
                        .header("Authorization", adminAuthToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.serialNumber").value("SER-001"))
                .andExpect(jsonPath("$.mqttTopicRoot").value("factory/bootstrap/SER-001"))
                .andExpect(jsonPath("$.mqttUsername").isString())
                .andExpect(jsonPath("$.mqttPassword").isString())
                .andExpect(jsonPath("$.mqttDeviceProfile").value(MqttDeviceProfile.OPENBEKEN_RELAY.name()))
                .andExpect(jsonPath("$.claimCode").isString());

        mockMvc.perform(get("/admin/factory/devices")
                        .header("Authorization", adminAuthToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].serialNumber").value("SER-001"))
                .andExpect(jsonPath("$[0].claimCode").isString());
    }

    @Test
    @DisplayName("Non-admin cannot access factory admin endpoints")
    void nonAdminCannotAccessFactoryEndpoints() throws Exception {
        mockMvc.perform(get("/admin/factory/devices")
                        .header("Authorization", userAuthToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Admin access required"));
    }

    private String login(UUID uuid, String secret) throws Exception {
        String loginJson = """
                {
                    "uuid": "%s",
                    "secret": "%s"
                }
                """.formatted(uuid, secret);

        String response = mockMvc.perform(post("/account/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return JsonPath.read(response, "$.token");
    }
}
