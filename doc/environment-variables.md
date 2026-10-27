# Environment variables

This reference was moved from the main README and is retained for a later update.
For the current variable names, defaults and application settings, see
[application.properties](../src/main/resources/application.properties).

## Existing configuration example

```env
export DB_HOST=localhost
export DB_PORT=5432
export DB_NAME=porssiohjain
export DB_USER=porssiohjain
export DB_PASSWORD=porssiohjain
export FINGRID_API_ENABLED=true
export FINGRID_API_KEY=xxxxxx
export RESEND_API_KEY=re_xxxxxx
export ALERTS_MAIL_ADDRESS=xxxxxx
export ADMIN_MAIL_ADDRESS=xxxxxx@xxxxxx.xx
export APP_CRYPTO_KEY=(run node app_crypto_key.js)
export MQTT_BROKER_ADDRESS=localhost
export MQTT_BROKER_PASSWORD=xxxxxx
export MQTT_CLIENT_ID=xxxxxx
export APP_PUSH_FCM_ENABLED=false
export APP_PUSH_FCM_SERVICE_ACCOUNT_FILE=/path/to/file.xyz
export APP_PUSH_ERROR_ALERTS_ENABLED=true
export APP_PUSH_ERROR_ALERTS_DUPLICATE_COOLDOWN=5m
export APP_PUSH_ERROR_ALERTS_MAX_PER_MINUTE=10
``` 

When FCM is enabled, a Logback appender sends runtime `ERROR` logs (including Spring and other
libraries) to active admin push tokens using the existing `SYSTEM_ERROR` notification. Alerts
include the logger, formatted message, exception summary when present, and event time. Full
stack traces remain in the backend logs. Delivery runs on a separate worker with a bounded
queue, so requests and scheduled jobs do not wait for push delivery.

Repeated errors from the same logger, message template, and exception type are suppressed for
five minutes by default. At most ten alerts per minute are queued per backend instance;
duplicates and excess alerts remain in the normal logs. Push-delivery errors do not trigger
further alerts. Limits reset on restart. Set `APP_PUSH_ERROR_ALERTS_ENABLED=false` to disable
this listener. Existing Nord Pool combined-market fallback warnings still use their explicit
admin push. Other `WARN`/`INFO` logs do not generate alerts.

The listener starts once Spring reports the application ready and detaches during shutdown.
It cannot report failed startup, process crashes, or an outage that prevents FCM delivery.
