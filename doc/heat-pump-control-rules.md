# Heat-pump rule sets in Controls

Vaadin and hybrid-web display one link per heat pump in each control. Opening the
link edits its complete rule set alongside an operating preview for today and
tomorrow. Existing separate rows are grouped automatically without rewriting
stored rules. Reordering, adding and removing actions is saved in one transaction.

Rules are evaluated from top to bottom, with the first matching action winning.
A rule may use a strict combined-price comparison (`<` or `>`, snt/kWh) or the
existing FINAL control timeline / manual control state. The combined price includes
the control's tax and transfer contract. Equal prices do not satisfy either strict
comparison. When no action matches, no command is sent; the preview marks a
previous projected setting as assumed, or shows an unknown state if none exists.
The preview recalculates the whole local day today and tomorrow, including elapsed
periods, using the current rule set. Earlier periods illustrate those rules and do
not reconstruct recorded device operation.

Each action retains a separately polled and edited command state, including the
outside-window action. The action label describes the intention; it does not
rewrite the captured device state. Polling, editing and previewing never dispatch
commands. Saved rules use the existing heat-pump scheduler and command deduplication.
Weather, Heating Planner and production controls retain their existing priority.

An optional daily window applies to the entire set in the control's configured
ZoneId. Start is inclusive and end exclusive. End earlier than start means an
overnight window. A timed set requires exactly one outside-window action, such as
an independently captured OFF state. That action does not need prices or an active
FINAL timeline. Inside the window, the ordered normal rules apply. Days without a
window retain the existing rule behavior. DST gaps and repeated clock hours are
resolved using the control timezone, with both occurrences of repeated hours
represented in the preview.

## Persistence and API

V119 adds nullable `active_from`, `active_until` and `rule_order`, plus
`outside_active_time` defaulting to false, to `control_heat_pump`. A set is identified
by `(control_id, device_id)`; shared window values are saved consistently on all
its action rows. Existing IDs are retained. Legacy rows with no explicit order
retain their original ascending-ID precedence until edited. Explicitly ordered
sets use their earliest persisted ID for precedence relative to other controls,
then their configured order internally.

- `PUT /api/controls/{controlId}/heat-pumps/{deviceId}/rules` replaces the whole set.
  Body: `{ activeFrom, activeUntil, rules: [{ id, stateHex, controlAction,
  comparisonType, priceLimit, estimatedPowerKw, outsideActiveTime }] }`.
  Times use local `HH:mm`; null times mean all day. Null rule IDs create actions;
  existing IDs must belong to that control and pump. An empty rule list removes
  the pump's link. Validation and ownership checks precede all mutations.
- `POST /api/controls/{controlId}/heat-pumps/{deviceId}/preview` accepts the same
  body and projects the draft without writes or commands.
- `GET /api/controls/{controlId}/heat-pumps/preview` projects saved rules and
  follows the existing control read authorization.

Preview and live scheduling share `ControlHeatPumpRuleEvaluator`. The preview
uses control-local day boundaries, authoritative market delivery intervals,
quarter-hour points, exact minute window boundaries and timezone transitions.
Missing prices suppress price rules rather than fabricating an OFF command.
Legacy individual-link endpoints remain available for existing clients.

## Cloud state queries

`GET /devices/{deviceId}/heat-pump/state` still checks device ownership before
querying the cloud. Cloud failures return `currentState: null`, `queryFailed: true`
and an optional vendor HTTP `queryStatus`, while preserving the device type and
`lastPolledState`. A saved state is never presented as a successful fresh query.
Hybrid editors show the query warning and keep the saved state available for
explicit reuse and editing, as in Vaadin. Application authorization errors still
fail normally; they are not converted into cloud-query warnings.
