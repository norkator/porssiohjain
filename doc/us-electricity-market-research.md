# U.S. electricity market support: research notes

Research date: 2026-10-27. Status: investigation for future use; no implementation has been selected.

This note covers the United States. Canada and other American countries would need separate research and integrations.

Pörssiohjain could support U.S. customers, but support would normally be defined by the customer's utility and retail tariff, rather than by adding another Nord Pool bidding area. The scheduler needs the price the customer actually pays for consumption at each time.

## Wholesale markets

There is no single nationwide equivalent of Nord Pool. Regional operators include PJM, MISO, ERCOT, CAISO, NYISO, ISO New England and SPP. These markets do not cover the entire country; other areas use different utility arrangements. See [FERC's electricity market overview](https://www.ferc.gov/electric-power-markets).

Organized markets generally have two linked energy markets:

- **Day-ahead:** prices and commitments for electricity delivered the following day.
- **Real-time:** prices and dispatch updated during the operating day as conditions change.

Prices vary by grid location, using nodes, zones or other market-specific pricing locations. Locational marginal prices can reflect energy, transmission congestion and losses. These wholesale components are not a substitute for the customer's retail delivery charges. Market intervals and rules also differ between operators. See [FERC's energy market explanation](https://www.ferc.gov/understanding-energy-markets).

## Household pricing

A regional wholesale price does not necessarily determine a household's bill. Most consumers do not directly experience daily wholesale fluctuations; some utilities offer time-dependent retail rates. See [EIA's explanation of electricity prices](https://www.eia.gov/energyexplained/electricity/prices-and-factors-affecting-prices.php).

| Retail arrangement | How the customer pays | Implication for Pörssiohjain |
| --- | --- | --- |
| Flat energy rate | Same energy rate throughout the day, potentially changing seasonally or between billing periods | Little benefit from shifting consumption solely by energy price |
| Time-of-use (TOU) | Preset peak/off-peak periods, potentially varying by season, weekday and holiday | Generate a price schedule from tariff rules |
| Day-ahead hourly pricing | Tomorrow's hourly prices are published in advance | Closest fit to existing Nord Pool planning |
| Real-time hourly pricing | Billed hourly prices are determined during the operating day | Use forecasts for planning and update as actual prices develop |

Retail supplier choice also varies. Where customers can choose their electricity supplier, the local distribution utility normally still delivers the electricity and charges for that service. Elsewhere, one utility provides both supply and delivery. See [EIA's retail choice explanation](https://www.eia.gov/tools/faqs/faq.php?id=627&t=2).

## Energy and transfer charges

The closest U.S. terminology to energy and transfer is **supply** and **delivery**. Bills can contain:

- Energy charges in USD/kWh or cents/kWh.
- Delivery charges, including consumption-based and fixed charges.
- Transmission, capacity and other adjustments, depending on the tariff.
- Taxes and fees.

For example, ComEd itemizes supply, transmission, capacity, delivery, and taxes/fees. Its hourly energy price is only part of the bill. Exact component placement varies by tariff; transmission can appear under supply rather than delivery. See [ComEd's billing explanation](https://hourlypricing.comed.com/faqs/).

For scheduling, calculate the cost affected by moving consumption between intervals. Fixed monthly charges do not change when the same consumption is moved within a billing period. If a quoted retail rate already includes delivery or another component, do not add that component again.

Some tariffs also have **demand charges**, measured in kW. Running several large devices simultaneously can increase the bill even when energy is cheap. Some residential SRP plans use demand measured over 30- or 60-minute intervals, with plan-specific billing rules. Supporting these requires load coordination and tariff-specific demand calculations beyond selecting the cheapest energy intervals. See [SRP's explanation of demand plans](https://www.srpnet.com/price-plans/residential-electric/solar-demand-plans-savings).

## Concrete integration candidates

### Ameren Illinois Power Smart Pricing

This residential program uses hourly market prices set the night before. Ameren publishes the next day's hourly prices each evening after 5 p.m. The underlying wholesale market is MISO. This is a close match to Pörssiohjain's existing today/tomorrow planning model. See [Ameren's program description](https://www.ameren.com/bill/rates/power-smart-pricing).

Before implementation, verify a supported data endpoint, price units, timezone and interval conventions, tariff adjustments, publication behavior and data-use terms. The existence of a public price chart alone does not establish an API contract.

### ComEd Hourly Pricing, Illinois

This residential program uses actual real-time hourly market prices. Wholesale day-ahead prices can inform planning, but they are not the final hourly prices charged under this program. The distinction from Ameren's next-day pricing is documented by the [Illinois regulator](https://plugin.illinois.gov/your-available-choices/utility-real-time-pricing.html).

ComEd documents a public API for five-minute prices and the current-hour average. These feeds are useful integration candidates, but their values must be distinguished from final billed hourly prices. Verify billing aggregation and data-use terms before implementation. See [ComEd's API documentation](https://hourlypricing.comed.com/hp-api/).

### Utility time-of-use plans

TOU support would allow scheduling without requiring a wholesale market feed. For example, PG&E's E-TOU-C plan has higher prices from 4–9 p.m. every day, with seasonal and usage-related differences. The household's applicable tariff determines its costs, rather than California's wholesale spot price. See [PG&E's TOU plans](https://www.pge.com/en/account/rate-plans/time-of-use-rate-plans.html).

Supporting arbitrary TOU plans requires configurable tariff schedules rather than assuming one universal day/night split.

## Implications for the current application

At the research date, [ControlPriceService](../src/main/java/com/nitramite/porssiohjain/services/ControlPriceService.java) combines Nord Pool prices, the control's tax percentage, and static or day/night transfer prices. Its day/night split is 22:00–07:00 in the control's timezone. [NordpoolMarket](../src/main/java/com/nitramite/porssiohjain/services/nordpool/NordpoolMarket.java) validates a fixed list of Nord Pool market identifiers.

A future implementation would need:

1. **Independent price providers.** Separate source ingestion and customer tariff calculation from Nord Pool-specific repositories and identifiers. Identify the utility, tariff and applicable pricing location.
2. **Explicit currencies and units.** Support USD and distinguish USD/MWh, USD/kWh and cents/kWh. For example, USD 50/MWh equals USD 0.05/kWh, or 5 cents/kWh. This is a unit conversion, not a currency conversion.
3. **Configurable retail tariffs.** Model peak periods, seasons, weekdays, holidays, effective dates and applicable charges. Usage tiers and demand charges need additional state and calculations.
4. **Price certainty.** Keep confirmed future tariff prices, forecasts, provisional real-time values and final actual prices distinguishable in storage, charts and scheduling.
5. **Time and interval handling.** Preserve the configured control timezone, explicit interval boundaries and local calendar days, including DST. Treat feed resolution and the customer's billed interval as separate concepts. Preserve existing scheduler mode guarantees when adapting hourly or shorter data to its scheduling steps.
6. **Correct combined costs.** Avoid assuming that one tax percentage and one transfer addition reproduce every tariff. Keep energy, delivery, adjustments and fixed charges distinguishable; prevent double counting bundled components.

The existing scheduling logic is a useful foundation for day-ahead and TOU plans once supplied with the correct interval costs. Real-time optimization and demand-charge optimization would need additional design; tomorrow's real-time billed prices cannot be treated as known in advance.

## Suggested starting scope

Investigate **Ameren Illinois Power Smart Pricing first** because its published next-day hourly prices resemble the current Nord Pool workflow. Confirm the data interface and full tariff calculation before committing to an integration.

A configurable TOU tariff would be another useful starting point. ComEd offers a documented price API, but its real-time billing requires careful treatment of forecast and final prices.

Supporting one verified utility tariff is a more concrete initial scope than promising nationwide U.S. market support. Recheck program rules, tariffs and API terms when implementation begins; the external details in this note describe the research date.
