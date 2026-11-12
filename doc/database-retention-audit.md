# Database retention audit

Source audit: 2026-11-12, originally based on commit `de9470d`, updated for monthly savings and token cleanup. Covers all 68 currently mapped database tables, including the Heating Planner comfort-period collection table. This describes application cleanup in the repository, not a measurement of the production database or confirmation of deployed environment overrides/external maintenance jobs.

**Measurement-history assessment: six calendar months** was the requested comparison target. Those measurement changes and the other optional recommendations below have not been applied. The follow-up implementation changes only QR/push cleanup and control-timing retention with monthly savings snapshots; see [monthly savings](control-savings.md).

## History, measurements and generated plans

| Feature / data | Database tables | Current holding time | Cleanup | What the feature currently needs | Proposed holding time |
| --- | --- | --- | --- | --- | --- |
| Zigbee sensor and thermostat readings | `zigbee_device_measurement` | **1 calendar month**, based on `measured_at` | Daily, 12:00 Helsinki | Heating Planner learns from the last **14 days**. Public charts expose up to 7 days per request. Longer retention serves user history rather than a longer current learning window. | **6 months** |
| Tuya / generic integration readings | `device_measurement` | **1 calendar month**, based on `received_at` | Daily, 12:00 Helsinki, separate Tuya job | Same shared measurement readers and 14-day Heating Planner learning. Tuya normally polls every 30 minutes; each measurement key is a separate row. | **6 months** |
| Power-limit electricity consumption history | `power_limit_history` | **90 days**, based on `created_at` | Daily, 12:00 Helsinki | Current control uses the active interval. Historical day/week/month charts and usage-cost calculations need retained readings and matching electricity prices. Writes reuse the same minute's row. | **6 months** |
| Solar / production readings | `production_history` | **90 days**, based on `created_at` | Daily, 12:00 Helsinki | Control uses current production state; the desktop production chart requests 24 hours. Older rows serve user history. Default Solarman poll interval is 15 minutes. | **6 months** |
| Water-meter cumulative readings | `water_measurement` | **No automatic age cleanup** | None; deleting the meter cascades to readings | The chart uses retained readings. Daily/weekly pushes use the latest cached total and fetch time on the meter, rather than historical calculations. Successful daily and manual reads append rows. | **6 months**; preserve a boundary reading if future period-consumption summaries need it |
| Calculated control on-periods | `control_table` | **45 days after `end_time`**, only after a saved monthly report covers the period | Daily, 03:15 Helsinki | Scheduling operates on today/tomorrow and execution has a 30-minute lookback. The current month's savings use these rows; completed months read saved snapshots. | **45 days**, with report coverage required before deletion |
| Completed-month control savings | `control_savings_month` | **No age expiry**; removed with the account | Missing completed months finalized daily, 03:15 Helsinki | One account/month/reporting-timezone row stores totals and per-control detail, independent of later control changes or raw-history cleanup. | Keep monthly summaries |
| Heating Planner generated plans and per-room prediction points | `heating_planner_plan`, `heating_planner_plan_point` | **14 days after the plan's `horizon_end`**, except `ACTIVE` plans are preserved regardless of age | Daily, 03:37 Helsinki | Runtime planning covers today/tomorrow; command readers look back 2 hours and reject stale points. Current history charts recover today's predictions from older plan versions. Thermal learning uses measurements/weather, **not old plan versions**. | **Keep 14 days initially** for diagnostics. Consider compacting superseded versions before increasing this period. Preserve the current plan and today's historical predictions. |
| Heating Planner wood recommendations / outcomes | `heating_planner_wood_recommendation` | Same **14 days after parent plan horizon**, with the same `ACTIVE` exemption | Same Heating Planner job | Upcoming advice, pending notifications and operator actions are linked to a plan. No six-month algorithmic history requirement was found. | **Keep 14 days**, coordinated with parent-plan cleanup |
| Per-site weather forecast history | `site_weather` | **14 days**, based on `forecast_time`; configurable | Daily, 12:00 Helsinki | Current forecasts support weather controls and planning. Heating Planner matches the last **14 days** of sensor history against retained weather for learning. Forecast updates overwrite the same site/time row. | **30 days** gives learning some buffer. Six-month sensor retention alone does not extend learning. |
| Sent heat-pump commands | `device_ac_command_log` | **Latest 100 rows per device**, with no maximum age | Pruned after each logged command | Recent troubleshooting; not used as six-month measurement history. An inactive device can retain its last 100 commands indefinitely. | **Keep 100 rows per device** |

Sources: [general cleanup scheduler](../src/main/java/com/nitramite/porssiohjain/scheduled/Scheduler.java), [Tuya cleanup](../src/main/java/com/nitramite/porssiohjain/scheduled/TuyaScheduler.java), [Heating Planner cleanup scheduler](../src/main/java/com/nitramite/porssiohjain/scheduled/HeatingPlannerAutomationScheduler.java), [plan retention](../src/main/java/com/nitramite/porssiohjain/services/heating/HeatingPlannerPlanService.java), [thermal learning](../src/main/java/com/nitramite/porssiohjain/services/heating/HeatingPlannerThermalModelService.java), [control savings](../src/main/java/com/nitramite/porssiohjain/services/ControlSavingsService.java), [power-limit history](../src/main/java/com/nitramite/porssiohjain/services/PowerLimitService.java), [production history](../src/main/java/com/nitramite/porssiohjain/services/ProductionSourceService.java), [water history](../src/main/java/com/nitramite/porssiohjain/services/WaterMeterService.java), [weather retention](../src/main/java/com/nitramite/porssiohjain/services/SiteWeatherService.java), [command-log cap](../src/main/java/com/nitramite/porssiohjain/services/DeviceAcCommandLogService.java).

## Shared market data

These rows are shared across users. Their storage scales with retained dates, markets and datasets, rather than adding a separate copy for every account.

| Data | Table | Current holding time | Cleanup | Need / proposed policy |
| --- | --- | --- | --- | --- |
| Nord Pool prices | `nordpool` | **12 calendar months**, based on `delivery_start`; configurable | Daily, 12:00 Helsinki | Keep **12 months**. Prices support historical electricity costs and control-savings baselines, in addition to today's/tomorrow's scheduling. At minimum, retain prices throughout the histories whose costs are calculated. |
| Fingrid wind data | `fingrid_data` | **5 calendar months**, based on `start_time`; configurable | Daily, 12:00 Helsinki | Keep **5 months** unless a longer user-facing history is wanted. Operational forecasting uses current/future data; existing historical retention does not multiply by user count. |
| Imported price predictions | `price_prediction` | **90 days**, based on prediction `timestamp` | Daily, 12:00 Helsinki | Current price-forecast readers use current/future periods. **30 days** would be a reasonable diagnostic policy if reducing this history is desired; no current calculation requiring 90 historical days was found. |

Sources: [configured defaults](../src/main/resources/application.properties), [Nord Pool cleanup](../src/main/java/com/nitramite/porssiohjain/services/nordpool/NordpoolDataPortalService.java), [Fingrid cleanup](../src/main/java/com/nitramite/porssiohjain/services/fingrid/FingridDataService.java), [prediction cleanup](../src/main/java/com/nitramite/porssiohjain/services/PricePredictionDataService.java), [prediction readers](../src/main/java/com/nitramite/porssiohjain/services/PricePredictionService.java).

## Authentication and other accumulating records

| Data | Tables | Current holding time | Need / proposed policy |
| --- | --- | --- | --- |
| Access tokens | `account_token` | **1-day validity**, then deletion at the daily 12:00 Helsinki job | Keep current policy. Expired rows can remain until the next cleanup. |
| Refresh tokens | `account_refresh_token` | **7-day validity** by current configuration, then daily expiry cleanup; revoked tokens remain until their original expiry | Keep current policy. Expiry is configurable. |
| QR login challenges | `qr_login_challenge` | Deleted **1 day after expiry**, daily at 12:05 Helsinki; validity remains **120 seconds** | Includes abandoned challenges regardless of saved status. |
| Push registration tokens | `push_notification_token` | Invalidated rows deleted **30 days after invalidation**, daily at 12:06 Helsinki. Usable registrations have no age expiry. | The existing delete action invalidates a token; scheduled cleanup later removes its row. |
| Feature requests and discussions | `feature_request`, `feature_request_comment` | **No age cleanup**; explicit request deletion removes the conversation | Keep while the request/conversation is wanted; use an explicit archive/delete policy for closed requests rather than a measurement TTL. Comments on another account's retained request can survive author deletion with a null author reference. |
| Factory test runs and step results | `factory_test_run`, `factory_test_step_result` | **No age cleanup**; tied to the tested device | Keep while device support needs the test record. Archive/delete old completed runs under a separate support policy; no runtime control calculation needs this history. |
| OTA releases and deployment records | `ota_release`, `ota_deployment` | **No age cleanup** | Keep supported releases and pending deployments. Consider **6 months for completed deployment diagnostics**, subject to support needs. Firmware binaries are referenced by URL in the release row. |
| Billing subscription records | `billing_subscription` | **No age cleanup**; updated by provider/purchase identity | Keep the records needed for entitlement and subscription lifecycle. A billing archival policy is separate from measurement retention. |

Sources: [access-token lifetime](../src/main/java/com/nitramite/porssiohjain/entity/TokenEntity.java), [refresh-token creation and cleanup](../src/main/java/com/nitramite/porssiohjain/services/AuthService.java), [QR lifecycle](../src/main/java/com/nitramite/porssiohjain/services/QrLoginService.java), [push-token invalidation](../src/main/java/com/nitramite/porssiohjain/services/PushNotificationTokenService.java), [feature-request schema](../src/main/resources/db/migration/V109__feature_request_conversations.sql), [factory/OTA schema](../src/main/resources/db/migration/V59__create_factory_provisioning_and_ota_tables.sql), [factory-device migration](../src/main/resources/db/migration/V73__move_factory_devices_to_device.sql), [billing](../src/main/java/com/nitramite/porssiohjain/services/BillingService.java).

## Configuration and latest state

These tables have **no scheduled age cleanup**. They mostly grow with configured accounts/resources, rather than appending readings continuously. Keep them while their owning feature/resource exists. Disabling a feature or leaving an account inactive does not itself delete its configuration.

| Feature | Tables | Storage behavior / lifetime |
| --- | --- | --- |
| Accounts, sites, electricity contracts | `account`, `site`, `electricity_contract` | Persistent configuration until explicitly deleted. No inactive-account expiry was found. |
| Controls and linked equipment | `control`, `control_device`, `control_heat_pump`, `control_thermostat` | Current settings and links; calculated historical periods are separately retained in `control_table`. |
| Weather controls | `weather_control`, `weather_control_device`, `weather_control_heat_pump` | Settings and links. Uses shared per-site forecasts; no separate accumulating calculated-timing table. |
| Power limits | `power_limit`, `power_limit_device` | Settings, mappings, latest read-back and cumulative counters; history is separate. |
| Production sources | `production_source`, `production_source_device`, `production_source_heat_pump` | Configuration, links and latest production values; history is separate. |
| Devices and heat-pump connections | `device`, `device_ac_data` | Current configuration, latest telemetry/read-back and connection state are updated in place. |
| Tuya connections and imported devices | `tuya_connection`, `tuya_device` | Connection/import metadata and latest state; measurement history is separate. |
| Zigbee gateways and linked devices | `zigbee_gateway_status`, `zigbee_gateway_device` | Latest gateway status, linked-device details, desired state and acknowledgements; updated in place. |
| Zigbee gateway backups | `zigbee_gateway_backup` | **One current backup per account/coordinator**, overwritten on save with a higher revision. No accumulating revision history. Each backup accepts up to 500 devices. |
| Heating Planner configuration and learned model | `heating_planner_settings`, `heating_planner_room`, `heating_planner_room_heat_source`, `heating_planner_comfort_schedule`, `heating_planner_comfort_period` | Keep configured rooms/schedules. Learned coefficients and evidence metadata are saved on room rows, not appended as a separate model history. Evidence becomes unusable after 14 days; that does not delete the room or model fields. |
| Powerplant board | `powerplant_settings`, `powerplant_element`, `powerplant_rule` | Current board, configured values and rule state. Historical measurement sources use the measurement tables. |
| Load shedding | `load_shedding_node`, `load_shedding_link` | Current graph and settings. |
| Notification definitions and delivery state | `control_notification`, `production_notification`, `power_limit_notification`, `market_notification`, `wind_notification` | Definitions and last/next-delivery fields. No separate per-send database journal was found. Water push settings live on `water_meter`; wood recommendations are covered above. |
| Water-meter setup and latest reading | `water_meter` | Configuration, provider credentials, cached latest total and push state; history is separate. |
| Sharing and published measurements | `resource_sharing`, `public_measurement` | Persistent links/selections until removed. Published measurements read existing measurement history; they do not copy it to another history store. |
| Stored files and service notice | `file_entity`, `service_notice` | Files keyed by name; service notice uses one fixed row. No automatic revision history. |

Most account/resource-owned tables use foreign-key cascades, so explicit deletion can remove data earlier than a retention cutoff. Some references use `SET NULL`, including feature-comment authors and factory/OTA actor references; account deletion is not a universal purge of every record mentioning that account.

Solar Angle Planner is advisory calculation without its own persisted feature tables. The standalone Nuclear Power app uses browser-local simulation and does not add backend database history. System/client-call diagnostic lists examined in the backend are bounded in-memory collections rather than database tables.

Sources: [entity mappings](../src/main/java/com/nitramite/porssiohjain/entity), [schema migrations](../src/main/resources/db/migration), [backup overwrite behavior](../src/main/java/com/nitramite/porssiohjain/services/ZigbeeGatewayBackupService.java), [public measurements](../src/main/java/com/nitramite/porssiohjain/services/PublicMeasurementService.java), [service notice](../src/main/java/com/nitramite/porssiohjain/services/ServiceNoticeService.java).

## Storage implications

Six months of sensor readings does not mean six months of every recalculated forecast. Keep the real measurement history and size generated-plan history separately.

| Data / assumed frequency | Approximate rows per resource over 180 days |
| --- | ---: |
| One measurement key every minute | 259,200 |
| One measurement key every 5 minutes | 51,840 |
| One measurement key every 30 minutes, e.g. Tuya polling | 8,640 |
| Power-limit history with continuous one-minute reporting | 259,200 per limit |
| Production polling every 15 minutes | 17,280 per source |
| Control selected for every quarter-hour | About 4,320 retained per control under the new 45-day policy, plus cleanup lag / protected unarchived periods |
| One successful water reading per day | 180 per meter, plus manual reads |

These are capacity examples, not observed production counts. Six calendar months varies in length. Multiple measurement keys multiply rows: temperature, humidity and battery are three separate streams. Retaining six months instead of one month is roughly a sixfold increase in those histories; moving 90 days to six months is roughly twofold.

**Heating Planner can be larger than expected:** every enabled site replans every 15 minutes, up to 96 scheduled replans per day. Each successful calculation saves a new plan and one point per room per available market period. For an illustrative full 48-hour horizon with 15-minute prices:

```text
192 periods × 96 replans = 18,432 new prediction rows per room per day
18,432 × 14 days = 258,048 rows per room before extra horizon/cleanup lag
```

Actual counts are lower when only today's prices or fewer future quarters are available. Manual recalculations can add versions. The retention clock starts at the horizon end, so a plan can survive roughly 14–16 days from creation plus the daily cleanup delay. This is not a measured row-count estimate.

The best candidate for reducing generated-plan storage is to retain the latest/current plan, preserve the predictions actually in effect when each elapsed period began, and shorten retention for other superseded future predictions. **That is a future implementation change**: deleting all superseded plans immediately would break the existing historical-prediction reader used for today's chart.

Six-month measurement retention also does not automatically expose six-month charts. Private measurement history currently displays the newest 500 values, and public requests are limited to seven-day windows. Production history currently fetches all retained source rows and filters them in Java; extending retention should be accompanied by a database time-range query there.

Cleanup timing matters: daily jobs can leave eligible data for nearly another day. Control timing cleanup is now daily and requires archived coverage. The general daily job still calls its other cleanup services sequentially without isolating failures, so an earlier exception can skip later cleanup during that run. Monitor successful deletions when sizing steady-state storage. `ACTIVE` Heating Planner plans are exempt from cleanup even when their horizon has expired.

## Measuring actual database size

This read-only PostgreSQL query provides approximate live-row counts and current table/index allocation. Run it against the intended database before translating retention periods into a storage budget:

```sql
SELECT schemaname,
       relname AS table_name,
       n_live_tup AS approximate_live_rows,
       pg_size_pretty(pg_table_size(relid)) AS table_size,
       pg_size_pretty(pg_indexes_size(relid)) AS index_size,
       pg_size_pretty(pg_total_relation_size(relid)) AS total_size
FROM pg_stat_user_tables
WHERE schemaname = 'public'
ORDER BY pg_total_relation_size(relid) DESC;
```

No production database was queried or cleaned during this work. The new token cleanup and monthly-savings retention jobs take effect when the implementation is deployed with migration V124.
