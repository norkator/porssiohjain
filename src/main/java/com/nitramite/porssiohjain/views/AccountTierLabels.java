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

package com.nitramite.porssiohjain.views;

import com.nitramite.porssiohjain.entity.enums.AccountTier;
import com.nitramite.porssiohjain.services.I18nService;

final class AccountTierLabels {

    private AccountTierLabels() {
    }

    static String label(I18nService i18n, AccountTier tier) {
        return switch (tier) {
            case FREE -> i18n.t("accountTier.free");
            case PRO -> i18n.t("accountTier.pro");
            case BUSINESS -> i18n.t("accountTier.business");
        };
    }
}
