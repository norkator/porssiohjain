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

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.entity.repository.AccountRepository;
import com.nitramite.porssiohjain.services.AccountLimitService;
import com.nitramite.porssiohjain.services.AuthService;
import com.nitramite.porssiohjain.services.I18nService;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static com.nitramite.porssiohjain.views.components.Divider.createDivider;

@PageTitle("Pörssiohjain - Admin Users")
@Route("admin/users")
@PermitAll
public class AdminUsersView extends VerticalLayout implements BeforeEnterObserver {

    private final AuthService authService;
    private final I18nService i18n;
    private final AccountRepository accountRepository;
    private final AccountLimitService accountLimitService;
    private final Grid<AccountEntity> grid = new Grid<>(AccountEntity.class, false);
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.of("Europe/Helsinki"));

    public AdminUsersView(
            AuthService authService,
            I18nService i18n,
            AccountRepository accountRepository,
            AccountLimitService accountLimitService
    ) {
        this.authService = authService;
        this.i18n = i18n;
        this.accountRepository = accountRepository;
        this.accountLimitService = accountLimitService;

        var account = ViewAuthUtils.findAuthenticatedAccount(authService);
        if (account == null || !account.isAdmin()) {
            return;
        }

        setWidthFull();
        setPadding(true);
        setSpacing(true);
        setAlignItems(Alignment.STRETCH);

        VerticalLayout card = new VerticalLayout();
        card.setWidthFull();
        card.addClassName("responsive-card");

        Button backButton = new Button("← " + t("admin.back"), e -> UI.getCurrent().navigate(AdminView.class));
        H1 title = new H1(t("admin.users.title"));

        configureGrid();
        refreshGrid();

        card.add(backButton, title, createDivider(), grid);
        add(card);
    }

    private void configureGrid() {
        grid.setWidthFull();
        grid.addColumn(AccountEntity::getId).setHeader(t("admin.users.id")).setAutoWidth(true);
        grid.addColumn(account -> account.getUuid() != null ? account.getUuid().toString() : "")
                .setHeader(t("admin.users.uuid"))
                .setAutoWidth(true)
                .setFlexGrow(1);
        grid.addColumn(account -> account.getEmail() != null ? account.getEmail() : "")
                .setHeader(t("admin.users.email"))
                .setAutoWidth(true);
        grid.addColumn(account -> account.getTier() != null ? account.getTier().name() : "")
                .setHeader(t("admin.users.tier"))
                .setAutoWidth(true);
        grid.addColumn(account -> account.isAdmin() ? t("admin.users.yes") : t("admin.users.no"))
                .setHeader(t("admin.users.admin"))
                .setAutoWidth(true);
        grid.addColumn(account -> account.isDemo() ? t("admin.users.yes") : t("admin.users.no"))
                .setHeader(t("admin.users.demo"))
                .setAutoWidth(true);
        grid.addColumn(AccountEntity::getLocale)
                .setHeader(t("admin.users.locale"))
                .setAutoWidth(true);
        grid.addColumn(account -> accountLimitService.getDeviceCount(account.getId()))
                .setHeader(t("admin.users.devices"))
                .setAutoWidth(true);
        grid.addColumn(account -> accountLimitService.getControlCount(account.getId()))
                .setHeader(t("admin.users.controls"))
                .setAutoWidth(true);
        grid.addColumn(account -> accountLimitService.getProductionSourceCount(account.getId()))
                .setHeader(t("admin.users.productionSources"))
                .setAutoWidth(true);
        grid.addColumn(account -> accountLimitService.getWeatherControlCount(account.getId()))
                .setHeader(t("admin.users.weatherControls"))
                .setAutoWidth(true);
        grid.addColumn(account -> account.getCreatedAt() != null ? formatter.format(account.getCreatedAt()) : "")
                .setHeader(t("admin.users.created"))
                .setAutoWidth(true);
        grid.addColumn(account -> account.getUpdatedAt() != null ? formatter.format(account.getUpdatedAt()) : "")
                .setHeader(t("admin.users.updated"))
                .setAutoWidth(true);
    }

    private void refreshGrid() {
        List<AccountEntity> accounts = accountRepository.findAll();
        grid.setItems(accounts);
    }

    private String t(String key, Object... args) {
        return i18n.t(key, args);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        ViewAuthUtils.rerouteToHomeIfNotAdmin(event, authService);
    }
}
