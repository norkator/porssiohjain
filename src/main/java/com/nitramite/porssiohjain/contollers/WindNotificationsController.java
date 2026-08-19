package com.nitramite.porssiohjain.contollers;

import com.nitramite.porssiohjain.auth.*;
import com.nitramite.porssiohjain.services.WindNotificationService;
import com.nitramite.porssiohjain.services.models.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/wind-notifications") @RequiredArgsConstructor @RequireAuth
public class WindNotificationsController {
    private final AuthContext authContext;
    private final WindNotificationService service;
    @GetMapping public List<WindNotificationResponse> list() { return service.list(authContext.getAccountId()); }
    @PostMapping public WindNotificationResponse create(@RequestBody WindNotificationRequest request) { return service.create(authContext.getAccountId(), request); }
    @PutMapping("/{id}") public WindNotificationResponse update(@PathVariable Long id, @RequestBody WindNotificationRequest request) { return service.update(authContext.getAccountId(), id, request); }
    @DeleteMapping("/{id}") public void delete(@PathVariable Long id) { service.delete(authContext.getAccountId(), id); }
}
