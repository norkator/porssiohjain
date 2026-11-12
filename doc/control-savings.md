# Monthly control savings

`ControlSavingsService` calculates estimated savings against the same day's duration-weighted average combined price. Estimates use configured device/heat-pump power, rather than measured consumption. Combined prices include the control's tax and applicable transfer price. Price-day and transfer-tariff boundaries use each control's timezone, including DST days.

## Storage and retention

| Data | Retention | Daily job, Europe/Helsinki |
| --- | --- | --- |
| Raw calculated control timings (`control_table`) | 45 days after the period ends, provided a completed-month report covers the period | 03:15, after savings finalization |
| Completed-month savings (`control_savings_month`) | No age expiry; deleted with the account | Finalized at 03:15, with catch-up on every run |
| Expired QR challenges | One day after expiry, regardless of saved challenge status | 12:05 |
| Invalidated push registrations | 30 days after invalidation | 12:06 |

Usable push registrations are preserved regardless of last-seen age. QR cleanup includes abandoned `PENDING` challenges: expiration does not depend on a client returning to update the status.

Each completed-month savings row is unique by account, local month start and reporting timezone. It stores half-open local-calendar-month boundaries converted to instants, calculation time and a JSON snapshot of the existing `ControlSavingsSummaryResponse`, including the per-control breakdown, names, load estimates, energy, costs, savings and counts. It has an account foreign key with `ON DELETE CASCADE` and no foreign key to individual controls. Later configuration edits, control deletion and price-history cleanup do not change an existing snapshot.

The reporting timezone normally comes from the request or first control. The job covers all current control timezones and previously used snapshot timezones. Different reporting timezones can have different month boundaries and therefore have separate snapshots. If controls have been removed, the default reporting timezone falls back to a stored snapshot timezone.

## Finalization and cleanup

`DatabaseRetentionScheduler` processes accounts independently. `archiveAndCleanupAccount` locks the account and creates missing completed-month snapshots before deleting raw timings in the same transaction. A failed calculation rolls back that account's work and retains its source rows; other accounts continue. Baseline prices must cover each complete control-local day used by a nonzero load estimate before that report can be finalized.

The first run backfills months represented by available raw timings and at least the five completed months used by the React client's six-month chart. Months before account registration return zero without allocating snapshot rows. This preserves the estimates available at rollout; it cannot recover history already deleted under the previous six-month policy or reconstruct earlier configuration changes. Subsequent runs skip saved reports and catch up any missed months. A period is deleted only if it ended more than 45 days ago and is fully contained in a saved month for that account. Unusual periods crossing a reporting-month boundary are conservatively retained by this deletion guard.

The account's `control_history_deleted_before` records the source-history availability boundary. It prevents requests for expired detail from silently returning an incomplete calculation. The previous unguarded weekly six-month deletion job is removed.

## Reading savings

`GET /dashboard/control-savings` retains the existing response format and `from`, `to`, `timezone` parameters:

- A full completed local calendar month reads its saved snapshot, creating it from available raw history if necessary.
- The current month is calculated from live raw periods and uses the existing short response cache. Future end dates are capped at the current instant.
- Requests spanning several months combine saved completed months with any available live partial-month ranges. Energy, costs and schedule counts are added; load power and counts are not multiplied by the number of months.
- Old partial-month ranges, or a historical month in a newly requested timezone whose source detail has expired, return HTTP 410. Monthly totals cannot reconstruct arbitrary daily ranges or a different timezone's boundaries.

The React dashboard passes the browser timezone explicitly alongside its local month boundaries. This keeps its historical requests aligned with stored reporting months. No six-month retention limit is imposed on monthly savings snapshots themselves.

## Verification

`DatabaseRetentionTest` covers QR/push cutoffs, account isolation, current-month recalculation, completed-month immutability, deletion protection, catch-up/idempotency, mixed historical/live requests, control deletion, and Helsinki's 23/25-hour days. `ControlSavingsMigrationTest` checks the migration, uniqueness and account-deletion cascade. Existing QR-login and scheduler tests cover the surrounding lifecycle.
