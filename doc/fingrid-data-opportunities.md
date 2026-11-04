# Fingrid data opportunities

Research date: 2026-11-02. This is an assessment and proposed implementation order; it does not change application behaviour. Sources are Fingrid's current [catalogue](https://data.fingrid.fi/en/datasets), individual dataset descriptions, API instructions, and the application code. No model was trained, forecast accuracy measured, or authenticated API ingestion tested during this review.

The best starting point is to refresh the existing wind forecast properly, add demand and solar forecasts, and build a transparent 72-hour Finnish energy outlook. Those inputs also support an experimental price model. A seven-day price forecast needs additional sources because these Fingrid forecasts cover only the next 72 hours.

## Existing application capabilities

- [FingridDataService](../src/main/java/com/nitramite/porssiohjain/services/fingrid/FingridDataService.java) already requests dataset 245 for the next 72 hours. [Scheduler](../src/main/java/com/nitramite/porssiohjain/scheduled/Scheduler.java) fetches it daily at 14:00 Europe/Helsinki and at startup when tomorrow's data is missing. Existing intervals are never updated. See [wind-forecast.md](wind-forecast.md).
- [PricePredictionDataService](../src/main/java/com/nitramite/porssiohjain/services/PricePredictionDataService.java) already imports `vividfog/nordpool-predict-fi/deploy/prediction.json`, daily at 09:00 and conditionally at startup. It also retains the first value seen for each target timestamp, ignoring later revisions. `PricePredictionEntity.createdAt` records insertion time, not the source's forecast issue time.
- The external project's [README](https://github.com/vividfog/nordpool-predict-fi#readme) describes a seven-day XGBoost forecast using weather, generation, transmission and calendar inputs. It is an existing comparison candidate for an in-house model; owning a new model would not itself demonstrate better accuracy.
- [DashboardView](../src/main/java/com/nitramite/porssiohjain/views/DashboardView.java) displays price predictions alongside wind, but constructs that chart's timeline from wind intervals. The displayed horizon can therefore be shorter than the imported price forecast. The chart interpolates prediction values; a future overlay should preserve each source's actual time resolution and coverage.
- Regular controls use published Nord Pool prices through the scheduler and [ControlPriceService](../src/main/java/com/nitramite/porssiohjain/services/ControlPriceService.java). [HeatingPlannerAutomationService](../src/main/java/com/nitramite/porssiohjain/services/heating/HeatingPlannerAutomationService.java) also reads Nord Pool prices, within its today/tomorrow horizon. A research forecast should initially feed separate advisory views and simulations.
- The app supports multiple Nord Pool markets. A model trained on these Finnish national inputs should initially be explicitly scoped to `FI`, independently of the user's display timezone.

## Useful datasets and concrete uses

All feature suggestions below are proposals, not validated predictive relationships.

| Dataset | Published coverage / interval | Proposed application use | Main limitation |
| --- | --- | --- | --- |
| [245: wind forecast](https://data.fingrid.fi/en/datasets/245) | Next 72 hours; updated every 15 minutes | Refresh existing charts and wind alerts; identify upcoming wind drops; price-model input | Already integrated, but revisions are currently discarded. Forecast incorporates known day-ahead prices from 15:30 on the previous day. |
| [166: consumption forecast](https://data.fingrid.fi/en/datasets/166) | Next 72 hours; updated every 15 minutes | Add demand context to wind alerts and price forecasting | National demand, not the consumption of a particular home. |
| [248: solar forecast](https://data.fingrid.fi/en/datasets/248) | Next 72 hours; updated every 15 minutes | Explain sunny midday conditions alongside wind and demand | National production estimate with approximate PV locations; cannot predict a specific roof's production. |
| [241: production forecast](https://data.fingrid.fi/en/datasets/241) | 15-minute periods and updates; description does not guarantee a 72-hour horizon | Supplement an energy outlook with reported generation plans where available | Check actual coverage and when plans became available; plans can reflect already-known market outcomes. |
| [75: wind generation](https://data.fingrid.fi/en/datasets/75) | Historical/recent generation in 15-minute periods | Compare saved forecasts with generation and measure error by forecast lead time | Includes estimates for unmeasured wind parks; a mismatch alone does not identify its cause. |
| [188: nuclear production](https://data.fingrid.fi/en/datasets/188) | Real-time, updated every 3 minutes | Display current generation changes and use lagged observations as model context | A production drop does not establish an outage or its duration; this is not a future availability schedule. |
| [268: wind capacity used in forecasting](https://data.fingrid.fi/en/datasets/268) and [267: solar capacity used in forecasting](https://data.fingrid.fi/en/datasets/267) | Capacity series in MW; manually maintained estimates | Show forecast production relative to modelled capacity; account for growth when comparing years | Not official installed-capacity statistics. Preserve the capacity information available at the time for evaluation. |
| [396: consumption emission factor](https://data.fingrid.fi/en/datasets/396) | 15-minute estimates, gCO2/kWh | Add estimated emissions to retained electricity-use history | Includes imports/exports; it is a historical/current factor, not a future carbon forecast or proof of emissions avoided. |
| [395: non-fossil generation share](https://data.fingrid.fi/en/datasets/395) | 15-minute estimate | Compact Finnish electricity-mix indicator | Domestic generation share, not the mix of electricity consumed after imports; includes nuclear and biomass, so do not label it renewable share. |
| [336: shortage status](https://data.fingrid.fi/en/datasets/336) and [209: system state](https://data.fingrid.fi/en/datasets/209) | National status, updated every 3 minutes | Official-status banner and optional notifications when the status changes | Different status code systems. Neither predicts a household's local distribution outage. Show stale/unknown when the feed is unavailable. |
| [364: average consumption by user group](https://data.fingrid.fi/en/datasets/364), [365: standard deviation](https://data.fingrid.fi/en/datasets/365), [360: grouped consumption totals](https://data.fingrid.fi/en/datasets/360) | Hourly profiles; latest data around four days behind; revisions possible | Optional comparison with electrically heated homes, other homes or holiday homes | Broad accounting-point groups, not matched homes. Account for season, coverage and household differences; standard deviation does not establish percentile rank. |

The Datahub group metadata includes `BE02` for electrically heated small residential buildings, `BE03` for other small residential buildings and `BE04` for holiday homes. These are a plausible fit for the existing site usage charts. Compare whole-site measurements with the selected group; a single controlled heater is not comparable to a household total. For dataset 360, use the reported accounting-point count when deriving a per-point mean. Its records contain `additionalJson` dimensions, so multiple groups can share the same dataset and interval. [Source: dataset 360](https://data.fingrid.fi/en/datasets/360).

## A useful first product: 72-hour energy outlook

Add wind, solar and national demand to the existing forecast card. A transparent derived series is:

```text
residual demand proxy (MW) = forecast consumption - forecast wind - forecast solar
```

This describes demand left for other generation and net imports to meet. It is not an electricity-shortage calculation or a price formula: nuclear, hydro, other generation, cross-border conditions and neighbouring markets also matter. Verify the datasets' compatible accounting boundaries before interpreting the result quantitatively.

Useful advisory output could say: "Wind is forecast to fall during tomorrow evening while demand rises", with the actual source curves, last successful fetch time and coverage end. This adds context to the existing wind-drop notifications without requiring a numerical price prediction. Start with factual changes in the input forecasts; only label periods as likely cheap or expensive after validating that classification.

For a later price model, this panel can also explain which inputs contributed to a prediction. A feature's contribution to a model is an association, not proof that it caused a market-price change.

## An in-house price forecast is feasible as an experiment

Fingrid supplies candidate explanatory inputs, not an official future day-ahead price series. Its [FAQ](https://data.fingrid.fi/en/faq) explains that day-ahead prices are not distributed in its open data. Continue obtaining the target prices from the existing market-price integration.

Proposed display contract:

1. Published Nord Pool intervals always take precedence.
2. Show estimates only where published prices are unavailable, using a distinct forecast series and an uncertainty band calibrated against held-out observations.
3. Include market, delivery start/end, forecast generation time, input observation times, source/model version and forecast coverage. Preserve negative price predictions where the model supports them.
4. Stop at valid input/model coverage. A rolling 72 hours can touch four local dates; it does not mean four complete future days. Do not extend a flat tail or invent 15-minute detail from hourly model output.
5. Keep raw price forecasts on a declared basis, preferably EUR/MWh before tax, then explicitly convert to c/kWh and apply the user's configured tax/transfer terms for estimated delivered cost. Verify the external feed's price basis before comparing it with a new model.

Potential model inputs are demand/wind/solar forecasts, calendar and holiday effects, price lags that were already published, capacity context, and generation availability known at the forecast time. Begin with a seasonal baseline and a small tabular regression model; a tree-based model is a reasonable experiment, not a requirement. Compare against both the seasonal baseline and the existing external predictions.

Seven-day forecasting would require a separate extension of the weather/generation/load inputs and generation-availability information. Cross-border constraints also need another current source: Fingrid's [SE1-FI planned capacity dataset 142](https://data.fingrid.fi/en/datasets/142) ended in October 2024 and [EE-FI official capacity dataset 112](https://data.fingrid.fi/en/datasets/112) ended in September 2026. Do not build on those retired feeds. Investigate current JAO/ENTSO-E sources separately before promising their coverage or implementation.

### Evaluation before operational use

- Save immutable input and prediction snapshots now. Record source issue time only when provided, plus our fetch time; do not equate a delivery timestamp with a forecast issue time. Fetch time is when our system observed the value, not necessarily when it was first published.
- Do not assume a historical forecast download reconstructs what was available at a past decision time. Verify archive revision semantics. Existing insert-only rows have mixed lead times and do not reconstruct a complete revision history.
- Dataset 245 incorporates the known day-ahead price at 15:30 on the previous day. A backtest using later forecast revisions to predict that same day's auction price would leak information. Report pre-publication and later forecast performance separately and enforce each decision's information cutoff.
- Evaluate chronologically using only earlier training data; do not randomly split quarters from the same market periods. Measure errors separately for 0–24, 24–48 and 48–72 hours, seasons, negative prices and price spikes.
- Measure usefulness as well as price error: cheapest-window ranking, threshold decisions and simulated extra cost compared with a schedule using eventual published prices. Use the actual mode rules and combined tax/transfer price for those simulations. Report uncertainty-band coverage and the frequency of withheld predictions.
- Historical Nord Pool retention defaults to 12 months, wind to five months and imported price predictions to 90 days. A dedicated research archive is needed for multiple seasons and forecast versions; increasing production-table retention alone would not restore missing revisions.

Run the model in shadow mode and then as an advisory chart. Any later use in Heating Planner or relay scheduling is a separate feature design, requiring explicit forecast provenance and evaluation. Preserve the current published-price behaviour, control-local day boundaries and existing planner constraints. In particular, do not make forecast rows look like published Nord Pool rows to bypass tomorrow-price availability checks. The current Heating Planner specification remains today/tomorrow; a longer horizon would be a separate scope change.

## Other product improvements

**Historical emissions reporting:** join retained, time-aligned site/device kWh with dataset 396 and sum `kWh * gCO2/kWh / 1000` for estimated kgCO2. Daily totals alone cannot establish quarter-hour allocation. Use measurements where available and label any runtime/power-derived energy as estimated. A comparison with an uncontrolled schedule can be an estimated counterfactual, not a claim of measured emissions saved. Prefer the consumption factor to the domestic-production factor for this use.

**Grid notices:** map dataset 336's 0–3 shortage levels and dataset 209's separate 1–5 system states explicitly. Display the official category, timestamp and source; deduplicate notifications on transitions and respect existing notification preferences. Reuse the desktop's notice presentation where appropriate without confusing an external grid condition with an application service outage. Automatic device actions are unnecessary for the initial feature.

**Wind forecast quality:** compare saved lead-time snapshots with dataset 75, expose revisions and improve the existing notifications with demand/solar context. Use dataset 268 for capacity-normalized views across years. Keep forecasts and generation observations visibly distinct.

**Consumption comparisons:** add an optional group/profile to site usage reporting, with delayed data clearly labelled. Broad national profiles can provide context but cannot diagnose poor insulation or quantify a particular home's attainable savings. The mean and standard deviation do not provide a full distribution for percentile claims.

**National energy monitor:** an optional dashboard card could combine generation, imports, nuclear output and the non-fossil share. Display-only external indicators could later fit Powerplant, but that needs a deliberate new source type; its existing rules currently bind to owned Zigbee measurements. National solar data is useful here and in market forecasting, rather than as a replacement for site weather or the panel-angle calculation.

Balancing and reserve prices are lower priority. For example, [319 is the imbalance settlement price for balance-responsible parties](https://data.fingrid.fi/en/datasets/319), not a household's Nord Pool tariff. Such data could provide specialist market context, but should not silently replace retail pricing or imply household reserve-market earnings. High-frequency grid data likewise has limited direct value to the current price-based controls.

## Ingestion work required before adding datasets

- Use a dataset-aware client and query by `(datasetId, startTime, endTime)`. The database constraint already includes the dataset ID, but `FingridDataService.saveEntries` currently deduplicates only by start/end, which would incorrectly discard simultaneous intervals from additional datasets.
- Keep a latest-value projection for charts and a separate immutable snapshot history for evaluation. The existing Fingrid entity lacks fetch/issue times; the prediction entity lacks source/model identity and delivery end. Update latest values when forecasts change while retaining the snapshots that earlier decisions used.
- Datahub grouped series require dimension-aware storage and DTOs, including `additionalJson`; they cannot use the existing one-value-per-dataset/interval constraint unchanged.
- Preserve authoritative UTC start/end instants. Form calendar-day summaries with the site's/control's configured timezone and half-open local-day boundaries, including 23/25-hour DST days. Weight averages by covered duration and distinguish partial coverage from complete days.
- Distinguish MW/MWh-per-hour average power from energy: an average of 1 MW over 15 minutes is 0.25 MWh. Dataset 360 already reports kWh. Do not confuse data period with the update cadence or apply energy conversion twice.
- Retrieve complete responses with pagination and verify actual interval coverage. Include the active interval when needed; a query starting at the current second should not be assumed to include it. Keep missing values separate from zero and report stale data explicitly.
- Collect centrally on the backend and cache for all users. Fingrid's [API instructions](https://data.fingrid.fi/en/instructions) specify a free registered API key, at most 10,000 requests per 24 hours and one request every two seconds per key. Stagger calls through a shared limiter with retries/backoff; keep the key on the server. Three forecast requests every 15 minutes would be 288 requests/day before pagination and retries.
- Provide dataset/source attribution. The checked dataset pages link to [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/); retain the source and license links and identify derived estimates as our calculations.

## Suggested implementation order

1. **Forecast freshness and provenance:** support revised wind values, frequent polling and snapshots; refresh imported external price predictions with a source-aware history. This improves existing functionality and begins collecting evaluation evidence.
2. **72-hour energy outlook:** ingest 166 and 248 alongside 245, expose coverage/freshness, and enrich existing charts and factual notifications.
3. **Price forecasting experiment:** establish baselines, collect comparable external forecasts, train and evaluate a Finnish model in shadow mode, then consider a clearly marked advisory overlay.
4. **Independent additions:** emissions history and official grid notices are useful without waiting for a successful price model. Consumption comparisons need suitable whole-site measurements and grouped-series storage.

Open questions for implementation are historical forecast-version availability, actual coverage/publication timing of dataset 241, the external price feed's tax basis, long-horizon generation/transmission inputs, and demonstrated forecast accuracy. Dataset descriptions establish availability and semantics, not the quality of an in-house price forecast.
