# Wind forecast and notifications

The source is [Fingrid dataset 245](https://data.fingrid.fi/en/datasets/245): a rolling 72-hour Finnish wind-power production forecast with 15-minute intervals. This can span four calendar dates; it does not guarantee four complete future days.

`FingridDataService` already requests the next 72 hours. The application fetches it daily at 14:00 Europe/Helsinki and at startup if tomorrow's data is missing. Existing stored intervals are retained rather than overwritten, so this is not a live feed of every Fingrid forecast revision. Historical records are kept for five months.

`GET /fingrid/wind-forecast` returns every stored dataset-245 interval starting at or after today's midnight in the requested timezone, with no tomorrow-only cutoff. Hybrid-web renders all returned days in both charts and displays the last interval's end time. Today's and tomorrow's averages and the drop percentage remain limited to those two local calendar days.

Each wind-notification rule has a local `notificationTime` in its configured timezone. Existing and new rules default to 09:00. Users can change it in the hybrid-web wind-notification dialog. The scheduler checks every minute and only evaluates a rule during its chosen minute. A matching rule sends through the account's enabled email/push channels, subject to existing quotas and once-per-local-day deduplication. A missing forecast, unmatched condition or missed execution does not cause a later overnight catch-up notification.

Local timezone rules handle DST: a nonexistent spring clock time moves forward by the DST gap, and an ambiguous autumn time uses the earlier occurrence.
