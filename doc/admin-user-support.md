# Admin support through View as user

Admins can use **View as user** in Vaadin and hybrid-web to manage a non-admin
account's resources for support, including creation, edits, deletion and device
commands. All ownership checks and account quotas apply to the selected account.
An authenticated, non-demo admin can also maintain a demo account's resources
through **View as user** in both clients. Direct demo logins remain read-only;
the exception is scoped to the admin's selected account and never changes its
stored demo flag. This does not grant the selected account admin
access or allow edits to another account's resources.

Hybrid-web keeps the admin's token and sends `X-View-As-Account` on resource
requests. `AuthInterceptor` rechecks the token owner's admin status and resolves
the selected account on every request. `/me` returns the selected account with
`admin=false` and `impersonating=true`. Saving that account's settings must not
replace the admin's browser session account ID or token. Returning to admin
clears the selected account and cached resource data.

`DemoAccountGuard` uses `AdminSupportAccess` to verify this exception in shared
resource services. REST uses only the interceptor's verified request context.
Vaadin reauthenticates the original token and checks the stored admin and target
IDs, so forged session markers, expired tokens and revoked admin access cannot
authorize demo edits. Background Tuya operations carry the captured identity and
recheck authorization when they reach the service guard. The hybrid Settings
read-only state uses the server's `demo` and `impersonating` response flags.
Feature-request submission and conversations retain their demo restrictions.

Password changes, account deletion, account export, authentication/QR-login
operations, billing purchases and admin routes remain unavailable while viewing
as another user. Native Android screens still use the signed-in admin's account,
so their actions remain unavailable in this mode.

Vaadin uses `ViewAuthUtils` to revalidate the admin session and resolve the
effective account before resource operations. Its password and account-deletion
guards remain in place. The desktop shell shows a persistent banner with the
selected user's email/account ID and a return-to-admin action. It remains above
maximized and mobile windows; dragging/resizing cannot cover it. Returning to
admin from the banner or Start menu uses the same unsaved-draft confirmation.
The admin user grid grows with its rows, leaving vertical scrolling to the
desktop window instead of using a second scroll area with a fixed height.
Its Devices online column shows online/total owned devices (for example `1/10`,
or `0/0` for an account with no devices). A device counts as online when API or
MQTT connectivity is online, counted once even when both are online.

The hybrid-web user list uses compact table rows with expandable UUID and date
details. On narrow screens less-used columns move into the expandable details
and action labels become shorter. The single-line support banner truncates long account names and
keeps the return-to-admin action visible.

Verification: `DemoAccountGuardTest`, `AdminUsersControllerTest`, `FeatureRequestsControllerTest`,
`ViewAuthUtilsTest`, `WaterMetersViewTest`, `MeasurementsViewTest`,
`FeatureRequestDialogTest`, `FeatureRequestConversationDialogTest`, and
`hybrid-web`'s production build.
