# Ideas and feature requests

Authenticated users can see their own submitted ideas and requests, discuss each request with administrators, and send a new request through a guided form. The Vaadin desktop shortcut and Start entry open `FeatureRequestDialog`; the hybrid main menu tile opens its React counterpart. English and Finnish labels are included.

The three steps ask for the user’s situation/use case, desired features or changes, and a review with optional contact email. Both answers are required and limited to 5,000 characters each. Contact email is prefilled from `AccountEntity`, editable, and optional. Clearing it opts out of contact for that submission; entering an address does not change the account email. Closing and reopening the modal retains its draft while the current desktop/menu is mounted. A successful submission clears the answers. Drafts are not persisted across page reloads.

`POST /api/feature-requests` accepts `useCase`, `requestedChanges`, and `contactEmail`. Authentication determines account ownership. An omitted/null contact email falls back to the account email; an explicit empty string means no contact email. `FeatureRequestService` normalizes and validates the same input for both clients. Demo accounts and user preview can read, but cannot submit, comment, change status, or mark comments as read.

Flyway migration `V107__create_feature_request.sql` stores submissions in `feature_request`, including account ID, contact email snapshot, and creation instant. Account deletion removes its submissions via the foreign key; the existing account export discovers the scalar account ID automatically.

Vaadin administrators open **Admin → Ideas & feature requests** at `/admin/feature-requests`. The list is paged in groups of 30, newest first. It defaults to open requests, with handled/all filters. Each entry opens the original answers and its chronological conversation. Administrators can mark requests as handled and reopen them; handled status does not prevent further discussion and remains under administrator control. Both route access and service listing require an effective admin account. Original submissions and posted comments cannot be edited. Administrators may permanently delete a request and its comments after confirmation. This feature does not send email or push notifications. The hybrid’s existing email feedback form remains separate.

Both user dialogs show only requests belonging to the effective account, in pages of 30, including handled requests. A new-comment badge appears when an administrator has replied since the user last opened that conversation. Opening the history alone does not consume unread state. Comment drafts survive closing/reopening and switching between requests while the current desktop/menu remains mounted. A successful request or comment clears only its own draft.

REST conversation contract:

- `GET /api/feature-requests?page=0`: own request summaries (`items`, `page`, `totalPages`, `totalElements`), including `handled` and `unreadComments`.
- `GET /api/feature-requests/{id}`: own immutable original submission and chronological comments. Each comment exposes `id`, `adminAuthor`, `message`, and `createdAt` without author account identifiers.
- `POST /api/feature-requests/{id}/comments`: append `{ "message": "..." }`, trimmed and limited to 1–5,000 characters. Account ownership and author role come from the server.
- `POST /api/feature-requests/{id}/read`: acknowledge `{ "seenCommentId": 123 }`, the last comment actually displayed (0 for an empty conversation). The marker only moves forward, and concurrent later replies remain unread. Reading through demo or user preview has no side effects.

Admin actions are exposed through the Vaadin view and require current effective administrator permissions in the shared service. Comment, read-marker, and status mutations lock their request row to keep concurrent updates consistent.

`V109__feature_request_conversations.sql` adds handled status, unread markers, owner/status indexes, and `feature_request_comment`. Existing requests remain open. Deleting a request or its owning account cascades to comments; deleting a comment author's account preserves comments on other accounts' requests with a null author ID. The scalar `featureRequestId` lets account export include the complete discussion on the user's own requests.

Verification: run the Java test suite with Java 21 and the Gradle wrapper, and `npm run build` under `hybrid-web`. Focused coverage lives in `FeatureRequestServiceTest`, `FeatureRequestsControllerTest`, `FeatureRequestMigrationTest`, `FeatureRequestDialogTest`, `FeatureRequestConversationDialogTest`, and `AdminFeatureRequestsViewTest`, with desktop regression coverage in `MainLayoutTest`.
