# Ideas and feature requests

Authenticated users can send one guided form for survey answers and feature requests. The Vaadin desktop shortcut and Start entry open `FeatureRequestDialog`; the hybrid main menu tile opens its React counterpart. English and Finnish labels are included.

The three steps ask for the user’s situation/use case, desired features or changes, and a review with optional contact email. Both answers are required and limited to 5,000 characters each. Contact email is prefilled from `AccountEntity`, editable, and optional. Clearing it opts out of contact for that submission; entering an address does not change the account email. Closing and reopening the modal retains its draft while the current desktop/menu is mounted. A successful submission clears the answers. Drafts are not persisted across page reloads.

`POST /api/feature-requests` accepts `useCase`, `requestedChanges`, and `contactEmail`. Authentication determines account ownership. An omitted/null contact email falls back to the account email; an explicit empty string means no contact email. `FeatureRequestService` normalizes and validates the same input for both clients. Demo accounts and user preview cannot submit.

Flyway migration `V107__create_feature_request.sql` stores submissions in `feature_request`, including account ID, contact email snapshot, and creation instant. Account deletion removes its submissions via the foreign key; the existing account export discovers the scalar account ID automatically.

Vaadin administrators open **Admin → Ideas & feature requests** at `/admin/feature-requests`. The list is paged in groups of 30, newest first, and each entry opens a modal containing both complete answers. Both route access and service listing require an effective admin account. This feature stores responses for manual admin review and contact; it does not send email notifications. The hybrid’s existing email feedback form remains separate.

Verification: run the Java test suite with Java 21 and the Gradle wrapper, and `npm run build` under `hybrid-web`. Focused coverage lives in `FeatureRequestServiceTest`, `FeatureRequestsControllerTest`, `FeatureRequestMigrationTest`, and `FeatureRequestDialogTest`, with desktop regression coverage in `MainLayoutTest`.
