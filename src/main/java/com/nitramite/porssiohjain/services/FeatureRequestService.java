/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 * Licensed under the Pörssiohjain Personal Use License v1.0.
 */
package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.entity.FeatureRequestEntity;
import com.nitramite.porssiohjain.entity.FeatureRequestCommentEntity;
import com.nitramite.porssiohjain.entity.repository.FeatureRequestCommentRepository;
import com.nitramite.porssiohjain.services.models.FeatureRequestDetail;
import com.nitramite.porssiohjain.services.models.FeatureRequestSummary;
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
    private final FeatureRequestCommentRepository comments;

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

    @Transactional
    public void deleteForAdmin(Long accountId, Long requestId) {
        AccountEntity admin = account(accountId);
        if (!admin.isAdmin()) throw new IllegalArgumentException("Admin access required");
        if (admin.isDemo()) throw new IllegalArgumentException("Demo account is read-only");
        if (requestId == null) throw new IllegalArgumentException("Request is required");
        FeatureRequestEntity request = repository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found"));
        repository.delete(request);
    }

    @Transactional(readOnly = true)
    public Page<FeatureRequestSummary> listOwn(Long accountId, int page) {
        account(accountId);
        return repository.findByAccountId(accountId, pageRequest(page)).map(FeatureRequestSummary::from);
    }

    @Transactional(readOnly = true)
    public Page<FeatureRequestEntity> listForAdmin(Long accountId, int page, Boolean handled) {
        requireAdmin(account(accountId));
        return handled == null ? repository.findAll(pageRequest(page)) : repository.findByHandled(handled, pageRequest(page));
    }

    @Transactional(readOnly = true)
    public FeatureRequestDetail detail(Long accountId, Long requestId, boolean adminView) {
        AccountEntity account = account(accountId);
        FeatureRequestEntity request = accessible(account, requestId, adminView, false);
        return new FeatureRequestDetail(request.getId(), request.getUseCase(), request.getRequestedChanges(),
                request.getContactEmail(), request.getCreatedAt(), request.isHandled(),
                comments.findByFeatureRequestIdOrderByIdAsc(requestId).stream().map(comment -> new FeatureRequestDetail.Comment(
                        comment.getId(), comment.isAdminAuthor(), comment.getMessage(), comment.getCreatedAt())).toList());
    }

    @Transactional
    public void comment(Long accountId, Long requestId, String message, boolean adminView) {
        AccountEntity account = account(accountId);
        requireWritable(account);
        String normalized = trim(message);
        if (normalized.isEmpty() || normalized.length() > 5000) throw new IllegalArgumentException("Comment must contain 1–5000 characters");
        FeatureRequestEntity request = accessible(account, requestId, adminView, true);
        FeatureRequestCommentEntity comment = comments.saveAndFlush(FeatureRequestCommentEntity.builder()
                .featureRequestId(requestId).accountId(accountId).adminAuthor(adminView).message(normalized).build());
        if (adminView) request.setLatestAdminCommentId(comment.getId());
    }

    @Transactional
    public void markRead(Long accountId, Long requestId, long seenCommentId) {
        AccountEntity account = account(accountId);
        requireWritable(account);
        FeatureRequestEntity request = accessible(account, requestId, false, true);
        if (seenCommentId < 0 || seenCommentId > 0 && !comments.existsByIdAndFeatureRequestId(seenCommentId, requestId)) {
            throw new IllegalArgumentException("Comment not found");
        }
        // A reply arriving after the displayed conversation must remain unread.
        request.setUserReadCommentId(Math.max(request.getUserReadCommentId(), seenCommentId));
    }

    @Transactional
    public void setHandled(Long accountId, Long requestId, boolean handled) {
        AccountEntity account = account(accountId);
        requireWritable(account);
        accessible(account, requestId, true, true).setHandled(handled);
    }

    private FeatureRequestEntity accessible(AccountEntity account, Long requestId, boolean adminView, boolean lock) {
        if (adminView) requireAdmin(account);
        if (requestId == null) throw new IllegalArgumentException("Request not found");
        FeatureRequestEntity request = (lock ? repository.findLockedById(requestId) : repository.findById(requestId))
                .orElseThrow(() -> new IllegalArgumentException("Request not found"));
        if (!adminView && !request.getAccountId().equals(account.getId())) throw new IllegalArgumentException("Request not found");
        return request;
    }

    private void requireAdmin(AccountEntity account) {
        if (!account.isAdmin()) throw new IllegalArgumentException("Admin access required");
    }

    private void requireWritable(AccountEntity account) {
        if (account.isDemo()) throw new IllegalArgumentException("Demo account is read-only");
    }

    private PageRequest pageRequest(int page) {
        return PageRequest.of(Math.max(0, page), 30, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
    }

    private AccountEntity account(Long id) {
        if (id == null) throw new IllegalArgumentException("Authentication required");
        return accounts.findById(id).orElseThrow(() -> new IllegalArgumentException("Account not found"));
    }

    private static String trim(String value) { return value == null ? "" : value.trim(); }
}
