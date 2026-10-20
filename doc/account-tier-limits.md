# Account tier limits

`AccountLimitService` is the backend source of truth for resource and notification limits. Keep Vaadin Settings, hybrid-web Account Settings, creation views and the authenticated `/me` response aligned with it when changing tiers.

| Limit per account | Free | Pro | Business |
| --- | ---: | ---: | ---: |
| Sites (`SiteEntity`) | 1 | 5 | Unlimited |
| Devices, unless an account-specific device override exists | 4 | 50 | 99 |
| Price controls | 4 | Unlimited | Unlimited |
| Production sources | 1 | Unlimited | Unlimited |
| Weather controls | 2 | Unlimited | Unlimited |
| Email notifications per week | 3 | 100 | 100 |
| Push notifications per week | 50 | 200 | 200 |

Unlimited resource limits use `null` in Java and JSON, not a large sentinel number. Business has unlimited sites, controls, production sources and weather controls; its default device and notification limits remain finite. `AccountEntity.deviceLimit` overrides only the device allowance. Weekly notification counters reset on Monday at 00:00 UTC, with separate counters for email and push.

## Site enforcement and existing accounts

- Count all sites owned by the effective account with `SiteRepository.countByAccountId`, including disabled sites and sites without weather configuration. Another account's sites do not count.
- `SiteService.createSite` serves both Vaadin and `POST /api/sites`. It checks demo-account restrictions, locks the account row for the creation transaction, then calls `AccountLimitService.assertCanCreateSite` before inserting. The shared account lock serializes concurrent creates so the last available slot cannot be used twice. A `TransactionTemplate` commits that work before the existing weather-forecast fetch, keeping network access outside the account lock in the normal creation path.
- Site-limit failures return HTTP 400 with an explanatory message through the existing `IllegalArgumentException` handler. UI checks are advisory; the backend must always enforce the quota.
- Existing sites are preserved if an account is downgraded or is already above a newly introduced limit. Reading, editing and disabling existing sites remain available. Creating another site is blocked while the count is at or above the allowance; disabling a site does not release a slot.
- Do not create sites through a new application path without the same service enforcement. Direct repository inserts in migration/test fixtures are not the normal creation path.

## Client presentation

`MeResponse.siteLimit` exposes the effective site allowance through both GET and PUT `/me`. Vaadin `SettingsView` and hybrid-web `AccountSettingsView` show each current allowance with a Free / Pro / Business comparison beneath it, covering devices, sites, controls, production sources, weather controls, weekly email notifications and weekly push notifications in English and Finnish. Device comparisons are labeled as defaults because an account-specific override may change the effective device allowance displayed above them. Keep these comparison descriptions aligned with the table and `AccountLimitService` when limits change.

Both Sites views show current usage, allowance and account tier. Vaadin follows the existing resource-limit badge and `DesktopCreateDialog.setLimitInfo` conventions; hybrid-web shows the same usage above the list and in its form dialog. At the limit, the create form remains accessible with a disabled create action and a Settings link explaining that upgrading the tier allows more sites. Existing-site edits must stay enabled. Hybrid-web reads the quota from `/me` rather than maintaining a second tier-rule implementation.

No database migration is required for these site limits: the account tier and site ownership already exist. Heating Planner's default-site preference is a separate setting and does not alter the number of allowed sites.

Focused checks:

```sh
./gradlew compileJava test --tests com.nitramite.porssiohjain.services.AccountLimitServiceTest --tests com.nitramite.porssiohjain.services.SiteServiceTest --tests com.nitramite.porssiohjain.SiteLimitsIntegrationTest --tests com.nitramite.porssiohjain.views.ListCreationViewsTest
cd hybrid-web
npm run build
```
