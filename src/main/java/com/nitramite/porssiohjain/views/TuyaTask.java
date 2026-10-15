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

import com.nitramite.porssiohjain.services.I18nService;
import com.nitramite.porssiohjain.services.tuya.TuyaException;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.shared.Registration;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Cloud IO runs off the Vaadin session thread; short UI polling delivers its result without global push. */
final class TuyaTask {
    private final Component owner;
    private Registration poll;
    private CompletableFuture<?> running;
    private UI ui;
    private PollState pollState;

    TuyaTask(Component owner) {
        this.owner = owner;
        owner.addDetachListener(event -> cancel());
        if (owner instanceof Dialog dialog) dialog.addOpenedChangeListener(event -> {
            if (!event.isOpened()) cancel();
        });
    }

    <T> void run(Supplier<T> work, Consumer<T> success, Consumer<Throwable> failure) {
        cancel();
        ui = owner.getUI().orElseGet(UI::getCurrent);
        if (ui == null) return;
        pollState = ComponentUtil.getData(ui, PollState.class);
        if (pollState == null) {
            pollState = new PollState();
            ComponentUtil.setData(ui, PollState.class, pollState);
        }
        if (pollState.users++ == 0) {
            pollState.previousInterval = ui.getPollInterval();
            pollState.changed = pollState.previousInterval <= 0 || pollState.previousInterval > 500;
            if (pollState.changed) ui.setPollInterval(500);
        }
        CompletableFuture<T> future = CompletableFuture.supplyAsync(work, task -> Thread.ofVirtual().start(task));
        running = future;
        poll = ui.addPollListener(event -> {
            if (!future.isDone()) return;
            release();
            if (!owner.isAttached()) return;
            try { success.accept(future.join()); }
            catch (CompletionException e) { failure.accept(e.getCause()); }
            catch (RuntimeException e) { failure.accept(e); }
        });
    }

    void cancel() {
        // In-flight network work may finish, but a closed/detached UI never receives its result.
        release();
    }

    private void release() {
        if (poll != null) { poll.remove(); poll = null; }
        if (running != null && pollState != null && --pollState.users == 0
                && pollState.changed && ui.getPollInterval() == 500) ui.setPollInterval(pollState.previousInterval);
        running = null;
        pollState = null;
    }

    private static final class PollState {
        private int users;
        private int previousInterval;
        private boolean changed;
    }

    static String error(I18nService i18n, Throwable error) {
        if (error instanceof TuyaException tuya) return i18n.t(tuya.getMessage())
                + (tuya.code().isBlank() ? "" : " (" + tuya.code() + ")");
        if (error instanceof com.nitramite.porssiohjain.services.DemoAccountMutationException)
            return i18n.t("tuya.error.demo");
        return i18n.t("tuya.error.request");
    }
}
