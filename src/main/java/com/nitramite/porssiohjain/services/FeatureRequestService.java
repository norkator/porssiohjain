/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 * Licensed under the Pörssiohjain Personal Use License v1.0.
 */
package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.entity.FeatureRequestEntity;
import com.nitramite.porssiohjain.entity.repository.AccountRepository;
import com.nitramite.porssiohjain.entity.repository.FeatureRequestRepository;
import com.nitramite.porssiohjain.services.models.FeatureRequestInput;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FeatureRequestService {
    private final FeatureRequestRepository repository;
    private final AccountRepository accounts;
    private final Validator validator;

    @Transactional
    public void submit(Long accountId, FeatureRequestInput input) {
        AccountEntity account = account(accountId);
        if (account.isDemo()) throw new IllegalArgumentException("Demo account is read-only");
        if (input == null) throw new IllegalArgumentException("Request is required");
        // Omitted email uses the account email; an explicitly empty value opts out of contact.
        String email = trim(input.contactEmail() == null ? account.getEmail() : input.contactEmail());
        FeatureRequestInput normalized = new FeatureRequestInput(trim(input.useCase()), trim(input.requestedChanges()), email);
        if (!validator.validate(normalized).isEmpty()) {
            throw new IllegalArgumentException("Provide your use case and requested changes (up to 5000 characters each), and a valid optional email");
        }
        repository.save(FeatureRequestEntity.builder().accountId(accountId)
                .useCase(normalized.useCase()).requestedChanges(normalized.requestedChanges())
                .contactEmail(email.isEmpty() ? null : email).build());
    }

    @Transactional(readOnly = true)
    public Page<FeatureRequestEntity> listForAdmin(Long accountId, int page) {
        if (!account(accountId).isAdmin()) throw new IllegalArgumentException("Admin access required");
        return repository.findAll(PageRequest.of(Math.max(0, page), 30,
                Sort.by(Sort.Direction.DESC, "createdAt", "id")));
    }

    private AccountEntity account(Long id) {
        if (id == null) throw new IllegalArgumentException("Authentication required");
        return accounts.findById(id).orElseThrow(() -> new IllegalArgumentException("Account not found"));
    }

    private static String trim(String value) { return value == null ? "" : value.trim(); }
}
