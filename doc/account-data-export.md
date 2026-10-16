# Account data export

Vaadin Settings and Hybrid-web Account settings use `AccountDataExportService`. The REST download is `GET /me/export`; both interfaces download a ZIP archive.

The archive contains `README.txt` and `data.jsonl`, a compact UTF-8 JSON Lines file. Each line is one stored record labeled with a short data category:

```json
{"device":{"id":42,"account":7,"deviceName":"Kitchen"}}
```

Field names and values are preserved, with related entities represented by their IDs. Child collections are separate records rather than repeated collection placeholders. Empty datasets add no records. Keeping records in one ZIP entry avoids per-file overhead and lets compression reuse repeated fields across categories.

This replaces the previous pretty-printed `account-data.json` document with its schema, table, row-count, and Java entity-name wrappers. Consumers of that document must read the labeled JSON Lines records instead.

Records are serialized directly into the ZIP in batches of 500, avoiding a complete intermediate document and uncompressed JSON byte array. Ownership discovery still holds record IDs in memory, and the final compressed download is returned as a byte array.

The existing history exclusions remain: AC command logs, Heating Planner plan points, power-limit history, production history, and Zigbee measurements. When present, `history-summary` records contain their record counts and time ranges. These histories are not included record by record. `README.txt` explains the archive format.

Focused verification:

```sh
JAVA_HOME=/path/to/java21 ./gradlew compileJava test --tests com.nitramite.porssiohjain.AccountControllerTest --tests com.nitramite.porssiohjain.FeatureRequestsControllerTest --tests com.nitramite.porssiohjain.AdminUsersControllerTest
cd hybrid-web
npm run build
```
