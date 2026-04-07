/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
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
