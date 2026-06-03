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

package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.entity.BillingSubscriptionEntity;
import com.nitramite.porssiohjain.entity.enums.AccountTier;
import com.nitramite.porssiohjain.entity.enums.BillingProvider;
import com.nitramite.porssiohjain.entity.enums.BillingSubscriptionStatus;
import com.nitramite.porssiohjain.entity.repository.AccountRepository;
import com.nitramite.porssiohjain.entity.repository.BillingSubscriptionRepository;
import com.nitramite.porssiohjain.services.models.BillingProductResponse;
import com.nitramite.porssiohjain.services.models.BillingStatusResponse;
import com.nitramite.porssiohjain.services.models.VerifiedGooglePlaySubscription;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BillingService {

    public static final String PRO_MONTHLY_PRODUCT_ID = "porssiohjain_pro_monthly";
    public static final String BUSINESS_MONTHLY_PRODUCT_ID = "porssiohjain_business_monthly";

    private static final Map<String, AccountTier> SUBSCRIPTION_PRODUCTS = Map.of(
            PRO_MONTHLY_PRODUCT_ID, AccountTier.PRO,
            BUSINESS_MONTHLY_PRODUCT_ID, AccountTier.BUSINESS
    );

    private final AccountRepository accountRepository;
    private final BillingSubscriptionRepository billingSubscriptionRepository;
    private final GooglePlaySubscriptionVerifier googlePlaySubscriptionVerifier;

    public List<BillingProductResponse> getProducts() {
        return SUBSCRIPTION_PRODUCTS.entrySet().stream()
                .map(entry -> BillingProductResponse.builder()
                        .productId(entry.getKey())
                        .tier(entry.getValue())
                        .type("SUBSCRIPTION")
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public BillingStatusResponse getBillingStatus(Long accountId) {
        AccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));

        BillingSubscriptionEntity activeSubscription = billingSubscriptionRepository
                .findByAccountIdAndStatusOrderByUpdatedAtDesc(accountId, BillingSubscriptionStatus.ACTIVE)
                .stream()
                .findFirst()
                .orElse(null);

        return BillingStatusResponse.builder()
                .tier(account.getTier())
                .subscriptionStatus(activeSubscription != null ? activeSubscription.getStatus() : null)
                .productId(activeSubscription != null ? activeSubscription.getProductId() : null)
                .expiresAt(activeSubscription != null ? activeSubscription.getExpiresAt() : null)
                .products(getProducts())
                .build();
    }

    @Transactional
    public BillingStatusResponse handleGooglePlayPurchase(Long accountId, String productId, String purchaseToken) {
        if (productId == null || productId.isBlank()) {
            throw new IllegalArgumentException("productId is required.");
        }
        if (purchaseToken == null || purchaseToken.isBlank()) {
            throw new IllegalArgumentException("purchaseToken is required.");
        }

        AccountTier tier = SUBSCRIPTION_PRODUCTS.get(productId);
        if (tier == null) {
            throw new IllegalArgumentException("Unsupported subscription product: " + productId);
        }

        AccountEntity account = accountRepository.findWithLockById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));
        if (account.isDemo()) {
            throw new DemoAccountMutationException();
        }

        VerifiedGooglePlaySubscription verified = googlePlaySubscriptionVerifier.verifySubscription(productId, purchaseToken);
        if (!productId.equals(verified.getProductId())) {
            throw new IllegalArgumentException("Verified product does not match requested product.");
        }
        if (!purchaseToken.equals(verified.getPurchaseToken())) {
            throw new IllegalArgumentException("Verified purchase token does not match requested purchase token.");
        }

        BillingSubscriptionStatus status = verified.isActive()
                ? BillingSubscriptionStatus.ACTIVE
                : BillingSubscriptionStatus.EXPIRED;

        BillingSubscriptionEntity entity = billingSubscriptionRepository
                .findByProviderAndPurchaseToken(BillingProvider.GOOGLE_PLAY, purchaseToken)
                .orElseGet(BillingSubscriptionEntity::new);

        entity.setAccount(account);
        entity.setProvider(BillingProvider.GOOGLE_PLAY);
        entity.setProductId(productId);
        entity.setPurchaseToken(purchaseToken);
        entity.setTier(tier);
        entity.setStatus(status);
        entity.setExpiresAt(verified.getExpiresAt());
        entity.setAutoRenewing(verified.isAutoRenewing());
        entity.setAcknowledged(verified.isAcknowledged());
        entity.setLinkedPurchaseToken(verified.getLinkedPurchaseToken());
        entity.setLastVerifiedAt(Instant.now());
        billingSubscriptionRepository.save(entity);

        if (status == BillingSubscriptionStatus.ACTIVE) {
            account.setTier(tier);
            accountRepository.save(account);
        }

        return getBillingStatus(accountId);
    }
}
