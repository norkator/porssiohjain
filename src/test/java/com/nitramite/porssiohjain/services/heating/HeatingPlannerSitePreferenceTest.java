package com.nitramite.porssiohjain.services.heating;

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.entity.HeatingPlannerSettingsEntity;
import com.nitramite.porssiohjain.entity.SiteEntity;
import com.nitramite.porssiohjain.entity.repository.AccountRepository;
import com.nitramite.porssiohjain.entity.repository.HeatingPlannerSettingsRepository;
import com.nitramite.porssiohjain.entity.repository.SiteRepository;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.DriverManager;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class HeatingPlannerSitePreferenceTest {
    private final AccountRepository accounts = mock();
    private final SiteRepository sites = mock();
    private final HeatingPlannerSettingsRepository settings = mock();
    private final HeatingPlannerConfigurationService service = new HeatingPlannerConfigurationService(
            accounts, sites, mock(), mock(), settings, mock(), mock());

    @Test
    void explicitDefaultWinsOverEnabledPlannersAndCanBeDisabled() {
        var home = site(8L, true);
        var cottage = site(9L, false);
        when(accounts.findById(7L)).thenReturn(Optional.of(AccountEntity.builder()
                .id(7L).defaultHeatingPlannerSite(cottage).build()));
        when(sites.findByIdAndAccountId(9L, 7L)).thenReturn(Optional.of(cottage));
        assertThat(service.preferredSiteId(7L)).contains(9L);
        verifyNoInteractions(settings);
        verify(accounts, never()).save(any());
    }

    @Test
    void fallbackPrefersConfiguredPlannersThenEnabledSitesInStableIdOrder() {
        var home = site(8L, false);
        var cottage = site(9L, true);
        var apartment = site(10L, true);
        when(sites.findByAccountId(7L)).thenReturn(List.of(apartment, cottage, home));
        var homeSettings = HeatingPlannerSettingsEntity.builder().id(1L).site(home).enabled(false).build();
        var cottageSettings = HeatingPlannerSettingsEntity.builder().id(2L).site(cottage).enabled(true).build();
        when(settings.findByAccountIdOrderByIdAsc(7L)).thenReturn(List.of(homeSettings, cottageSettings));
        assertThat(service.preferredSiteId(7L)).contains(9L);
        cottageSettings.setEnabled(false);
        assertThat(service.preferredSiteId(7L)).contains(8L);
        when(settings.findByAccountIdOrderByIdAsc(7L)).thenReturn(List.of());
        assertThat(service.preferredSiteId(7L)).contains(9L);
        cottage.setEnabled(false);
        apartment.setEnabled(false);
        assertThat(service.preferredSiteId(7L)).contains(8L);
        when(sites.findByAccountId(7L)).thenReturn(List.of());
        assertThat(service.preferredSiteId(7L)).isEmpty();
        verify(accounts, never()).save(any());
        verify(settings, never()).save(any());
    }

    @Test
    void savedDefaultThatNoLongerBelongsToTheAccountIsIgnored() {
        when(accounts.findById(7L)).thenReturn(Optional.of(AccountEntity.builder().id(7L)
                .defaultHeatingPlannerSite(site(99L, true)).build()));
        when(sites.findByAccountId(7L)).thenReturn(List.of(site(8L, true)));
        assertThat(service.defaultSiteId(7L)).isEmpty();
        assertThat(service.preferredSiteId(7L)).contains(8L);
    }

    @Test
    void savingDefaultValidatesOwnershipWithoutCreatingOrEnablingPlannerSettings() {
        var account = AccountEntity.builder().id(7L).build();
        var cottage = site(9L, false);
        when(sites.findByIdAndAccountId(9L, 7L)).thenReturn(Optional.of(cottage));
        when(accounts.findWithLockById(7L)).thenReturn(Optional.of(account));
        service.setDefaultSite(7L, 9L);
        assertThat(account.getDefaultHeatingPlannerSite()).isSameAs(cottage);
        verify(accounts).save(account);
        verifyNoInteractions(settings);
        assertThatThrownBy(() -> service.setDefaultSite(7L, 99L))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("heatingPlanner.site.notFound");
        verify(accounts, times(1)).findWithLockById(7L);
        assertThat(account.getDefaultHeatingPlannerSite()).isSameAs(cottage);
    }

    @Test
    void migrationPreservesExistingAccountsAndClearsOnlyTheDeletedSitesDefault() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:planner-default-" + UUID.randomUUID()
                + ";MODE=PostgreSQL"); var sql = connection.createStatement()) {
            sql.execute("create table account (id bigint primary key)");
            sql.execute("create table site (id bigint primary key, account_id bigint references account(id) on delete cascade)");
            sql.execute("insert into account values (7), (8)");
            sql.execute("insert into site values (70, 7), (80, 8)");
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V118__add_default_heating_planner_site.sql"));
            try (var rows = sql.executeQuery("select * from account")) {
                while (rows.next()) assertThat(rows.getObject("default_heating_planner_site_id")).isNull();
            }
            sql.execute("update account set default_heating_planner_site_id = 70 where id = 7");
            sql.execute("update account set default_heating_planner_site_id = 80 where id = 8");
            sql.execute("delete from site where id = 70");
            try (var rows = sql.executeQuery("select * from account order by id")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getObject("default_heating_planner_site_id")).isNull();
                assertThat(rows.next()).isTrue();
                assertThat(rows.getLong("default_heating_planner_site_id")).isEqualTo(80L);
            }
            assertThatThrownBy(() -> sql.execute("update account set default_heating_planner_site_id = 999 where id = 7"))
                    .isInstanceOf(java.sql.SQLException.class);
        }
    }

    private SiteEntity site(Long id, boolean enabled) {
        return SiteEntity.builder().id(id).enabled(enabled).build();
    }
}
