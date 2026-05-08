package com.nitramite.porssiohjain.contollers;

import com.nitramite.porssiohjain.auth.AuthContext;
import com.nitramite.porssiohjain.auth.RequireAuth;
import com.nitramite.porssiohjain.services.AdminAuthorizationService;
import com.nitramite.porssiohjain.services.FactoryProvisioningService;
import com.nitramite.porssiohjain.services.models.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/admin/factory")
@RequireAuth
@RequiredArgsConstructor
public class AdminFactoryController {

    private final AuthContext authContext;
    private final AdminAuthorizationService adminAuthorizationService;
    private final FactoryProvisioningService factoryProvisioningService;

    @GetMapping("/devices")
    public List<FactoryDeviceResponse> listFactoryDevices() throws IOException {
        adminAuthorizationService.requireAdmin(authContext.getAccountId());
        return factoryProvisioningService.listFactoryDevices();
    }

    @PostMapping("/devices")
    public FactoryDeviceResponse createFactoryDevice(@RequestBody CreateFactoryDeviceRequest request) throws IOException {
        adminAuthorizationService.requireAdmin(authContext.getAccountId());
        return factoryProvisioningService.createFactoryDevice(request);
    }

    @GetMapping("/devices/{id}")
    public FactoryDeviceResponse getFactoryDevice(@PathVariable Long id) throws IOException {
        adminAuthorizationService.requireAdmin(authContext.getAccountId());
        return factoryProvisioningService.getFactoryDevice(id);
    }

    @PutMapping("/devices/{id}")
    public FactoryDeviceResponse updateFactoryDevice(
            @PathVariable Long id,
            @RequestBody UpdateFactoryDeviceRequest request
    ) throws IOException {
        adminAuthorizationService.requireAdmin(authContext.getAccountId());
        return factoryProvisioningService.updateFactoryDevice(id, request);
    }

    @PostMapping("/devices/{id}/test-runs")
    public FactoryTestRunResponse startTestRun(
            @PathVariable Long id,
            @RequestBody CreateFactoryTestRunRequest request
    ) throws IOException {
        Long accountId = authContext.getAccountId();
        adminAuthorizationService.requireAdmin(accountId);
        return factoryProvisioningService.startTestRun(accountId, id, request);
    }

    @PostMapping("/test-runs/{runId}/steps")
    public FactoryTestRunResponse addTestStep(
            @PathVariable Long runId,
            @RequestBody CreateFactoryTestStepRequest request
    ) throws IOException {
        adminAuthorizationService.requireAdmin(authContext.getAccountId());
        return factoryProvisioningService.addTestStep(runId, request);
    }

    @PostMapping("/devices/{id}/claim")
    public DeviceResponse claimFactoryDevice(
            @PathVariable Long id,
            @RequestBody ClaimFactoryDeviceRequest request
    ) throws IOException {
        adminAuthorizationService.requireAdmin(authContext.getAccountId());
        return factoryProvisioningService.claimFactoryDevice(id, request);
    }

    @GetMapping("/ota-releases")
    public List<OtaReleaseResponse> listOtaReleases() throws IOException {
        adminAuthorizationService.requireAdmin(authContext.getAccountId());
        return factoryProvisioningService.listOtaReleases();
    }

    @PostMapping("/ota-releases")
    public OtaReleaseResponse createOtaRelease(@RequestBody CreateOtaReleaseRequest request) throws IOException {
        adminAuthorizationService.requireAdmin(authContext.getAccountId());
        return factoryProvisioningService.createOtaRelease(request);
    }

    @PostMapping("/devices/{id}/ota-deployments")
    public OtaDeploymentResponse createOtaDeployment(
            @PathVariable Long id,
            @RequestBody CreateOtaDeploymentRequest request
    ) throws IOException {
        Long accountId = authContext.getAccountId();
        adminAuthorizationService.requireAdmin(accountId);
        return factoryProvisioningService.createOtaDeployment(accountId, id, request);
    }
}
