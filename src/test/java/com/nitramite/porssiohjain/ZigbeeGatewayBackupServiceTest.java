package com.nitramite.porssiohjain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nitramite.porssiohjain.entity.*;
import com.nitramite.porssiohjain.entity.repository.*;
import com.nitramite.porssiohjain.services.ZigbeeGatewayBackupService;
import com.nitramite.porssiohjain.services.models.ZigbeeGatewayBackup;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ZigbeeGatewayBackupServiceTest {
    @Mock AccountRepository accounts;
    @Mock ZigbeeGatewayBackupRepository backups;
    ZigbeeGatewayBackupService service;
    AccountEntity account;

    @BeforeEach void setUp() {
        service = new ZigbeeGatewayBackupService(accounts, backups, new ObjectMapper());
        account = new AccountEntity(); account.setId(7L);
        lenient().when(accounts.findById(7L)).thenReturn(Optional.of(account));
        lenient().when(backups.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test void storesSeparateBackupsForTwoDonglesOnOneAccount() {
        ZigbeeGatewayBackup first = request("00124b0000000001", UUID.randomUUID());
        ZigbeeGatewayBackup second = request("00124b0000000002", UUID.randomUUID());
        when(backups.findByGatewayId(any())).thenReturn(Optional.empty());
        when(backups.findByAccountIdAndCoordinatorIeee(eq(7L), any())).thenReturn(Optional.empty());

        assertEquals(first.getCoordinatorIeee(), service.save(7L, first.getCoordinatorIeee(), first).getCoordinatorIeee());
        assertEquals(second.getCoordinatorIeee(), service.save(7L, second.getCoordinatorIeee(), second).getCoordinatorIeee());

        ArgumentCaptor<ZigbeeGatewayBackupEntity> captor = ArgumentCaptor.forClass(ZigbeeGatewayBackupEntity.class);
        verify(backups, times(2)).save(captor.capture());
        assertNotEquals(captor.getAllValues().get(0).getCoordinatorIeee(),
                captor.getAllValues().get(1).getCoordinatorIeee());
    }

    @Test void rejectsGatewayOwnedByAnotherAccount() {
        ZigbeeGatewayBackup request = request("00124b0000000001", UUID.randomUUID());
        AccountEntity other = new AccountEntity(); other.setId(8L);
        when(backups.findByGatewayId(request.getGatewayId())).thenReturn(Optional.of(
                ZigbeeGatewayBackupEntity.builder().account(other).gatewayId(request.getGatewayId())
                        .coordinatorIeee(request.getCoordinatorIeee()).build()));
        assertThrows(ResponseStatusException.class,
                () -> service.save(7L, request.getCoordinatorIeee(), request));
    }

    @Test void rejectsCoordinatorPathMismatchAndDuplicateDevices() {
        ZigbeeGatewayBackup request = request("00124b0000000001", UUID.randomUUID());
        assertThrows(ResponseStatusException.class, () -> service.save(7L, "00124b0000000002", request));
        request.setDevices(List.of(Map.of("ieeeAddress", "8c6fb9fffe2d5cdb"),
                Map.of("ieeeAddress", "8C6FB9FFFE2D5CDB")));
        assertThrows(ResponseStatusException.class,
                () -> service.save(7L, request.getCoordinatorIeee(), request));
    }

    private ZigbeeGatewayBackup request(String coordinatorIeee, UUID gatewayId) {
        ZigbeeGatewayBackup value = new ZigbeeGatewayBackup();
        value.setGatewayId(gatewayId); value.setCoordinatorIeee(coordinatorIeee);
        value.setPanId(1234); value.setExtendedPanId("1122334455667788"); value.setChannel(20);
        value.setDevices(List.of(Map.of("ieeeAddress", "8c6fb9fffe2d5cdb", "customName", "Bathroom")));
        return value;
    }
}
