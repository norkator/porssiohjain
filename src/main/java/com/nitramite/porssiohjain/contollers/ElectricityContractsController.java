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
import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.entity.ElectricityContractEntity;
import com.nitramite.porssiohjain.entity.enums.ContractType;
import com.nitramite.porssiohjain.entity.repository.AccountRepository;
import com.nitramite.porssiohjain.entity.repository.ElectricityContractRepository;
import com.nitramite.porssiohjain.services.models.ElectricityContractResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/electricity-contracts")
@RequiredArgsConstructor
@RequireAuth
public class ElectricityContractsController {

    private final AuthContext authContext;
    private final ElectricityContractRepository electricityContractRepository;
    private final AccountRepository accountRepository;

    @GetMapping
    public List<ElectricityContractResponse> listContracts(
            @RequestParam(required = false) ContractType type
    ) {
        return electricityContractRepository.findByAccountId(authContext.getAccountId()).stream()
                .filter(contract -> type == null || contract.getType() == type)
                .map(this::toResponse)
                .toList();
    }

    @PostMapping
    public ElectricityContractResponse createContract(@RequestBody ElectricityContractRequest request) {
        AccountEntity account = accountRepository.findById(authContext.getAccountId())
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));

        ElectricityContractEntity contract = ElectricityContractEntity.builder()
                .account(account)
                .name(request.name())
                .type(request.type())
                .basicFee(request.basicFee())
                .nightPrice(request.nightPrice())
                .dayPrice(request.dayPrice())
                .staticPrice(request.staticPrice())
                .taxPercent(request.taxPercent())
                .taxAmount(request.taxAmount())
                .build();

        return toResponse(electricityContractRepository.save(contract));
    }

    @PutMapping("/{contractId}")
    public ElectricityContractResponse updateContract(
            @PathVariable Long contractId,
            @RequestBody ElectricityContractRequest request
    ) {
        ElectricityContractEntity contract = electricityContractRepository.findByIdAndAccountId(contractId, authContext.getAccountId())
                .orElseThrow(() -> new IllegalArgumentException("Electricity contract not found"));
        contract.setName(request.name());
        contract.setType(request.type());
        contract.setBasicFee(request.basicFee());
        contract.setNightPrice(request.nightPrice());
        contract.setDayPrice(request.dayPrice());
        contract.setStaticPrice(request.staticPrice());
        contract.setTaxPercent(request.taxPercent());
        contract.setTaxAmount(request.taxAmount());
        return toResponse(electricityContractRepository.save(contract));
    }

    private ElectricityContractResponse toResponse(ElectricityContractEntity contract) {
        return ElectricityContractResponse.builder()
                .id(contract.getId())
                .name(contract.getName())
                .type(contract.getType())
                .basicFee(contract.getBasicFee())
                .nightPrice(contract.getNightPrice())
                .dayPrice(contract.getDayPrice())
                .staticPrice(contract.getStaticPrice())
                .taxPercent(contract.getTaxPercent())
                .taxAmount(contract.getTaxAmount())
                .createdAt(contract.getCreatedAt())
                .updatedAt(contract.getUpdatedAt())
                .build();
    }

    public record ElectricityContractRequest(
            String name,
            ContractType type,
            BigDecimal basicFee,
            BigDecimal nightPrice,
            BigDecimal dayPrice,
            BigDecimal staticPrice,
            BigDecimal taxPercent,
            BigDecimal taxAmount
    ) {
    }
}
