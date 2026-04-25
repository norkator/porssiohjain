/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 *
 * This source code is licensed under the Pörssiohjain Personal Use License v1.0.
 * Private self-hosting for personal household use is permitted.
 * Commercial use, resale, managed hosting, or offering the software as a
 * service to third parties requires separate written permission.
 * See LICENSE for details.
 */

package com.nitramite.porssiohjain.contollers;

import com.nitramite.porssiohjain.auth.AuthContext;
import com.nitramite.porssiohjain.auth.RequireAuth;
import com.nitramite.porssiohjain.entity.enums.ContractType;
import com.nitramite.porssiohjain.entity.repository.ElectricityContractRepository;
import com.nitramite.porssiohjain.services.models.ElectricityContractResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/electricity-contracts")
@RequiredArgsConstructor
@RequireAuth
public class ElectricityContractsController {

    private final AuthContext authContext;
    private final ElectricityContractRepository electricityContractRepository;

    @GetMapping
    public List<ElectricityContractResponse> listContracts(
            @RequestParam(required = false) ContractType type
    ) {
        return electricityContractRepository.findByAccountId(authContext.getAccountId()).stream()
                .filter(contract -> type == null || contract.getType() == type)
                .map(contract -> ElectricityContractResponse.builder()
                        .id(contract.getId())
                        .name(contract.getName())
                        .type(contract.getType())
                        .build())
                .toList();
    }
}
