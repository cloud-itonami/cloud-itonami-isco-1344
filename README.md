# cloud-itonami-isco-1344

Open Occupation Blueprint for **ISCO-08 1344**: Social Welfare Managers.

This repository designs a forkable OSS business for a social welfare manager: an operations robot performs staffing scheduling, program-data logging, and case-safety escalation support under a governor-gated actor, so the agency keeps its own operation records and audit trail instead of renting a closed case-management SaaS.

## Scope boundary: administration only, case authority remains human

**This actor supports a social welfare manager's ADMINISTRATIVE operations only:**
- Staffing roster scheduling
- Program/service delivery data logging
- Correspondence drafting (internal reports, inter-agency comms)
- Case safety concern flagging for caseworker review

**THIS ACTOR DOES NOT AND CANNOT:**
- Make case-eligibility determinations
- Authorize or deny benefits
- Make case-management decisions
- Discharge caseworker authority or judgment

Those decisions remain **exclusively the human caseworker's/manager's authority**. Any proposal that attempts case-eligibility determination, benefits authorization, or case-management judgment is a hard, permanent block.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot performs
the physical domain work**. Here an operations robot performs staffing scheduling,
program-record entry, and case-concern escalation support under an actor that
proposes actions and an independent **Welfare Governor** that gates them. The
governor never dispatches hardware itself; worker-safety operations (such as
flagging a case concern requiring caseworker escalation, or low-confidence
recommendations) require human sign-off.

A live sample of the operator console (robotics safety console, shared template)
is rendered in [docs/samples/operator-console.html](docs/samples/operator-console.html) — pure-data HTML output of `kotoba.robotics.ui`.

## Core Contract

```text
agency registration + service mandate + operational context
        |
        v
Welfare Advisor -> Welfare Governor -> propose staffing/log/correspondence, or human sign-off
        |
        v
robot actions (gated) + operating records + audit ledger
```

No automated advice can dispatch a robot action the governor refuses, suppress
an operating record, disclose sensitive data, or make a case-eligibility
determination without governor approval and audit evidence. All case-authority
decisions remain with the human caseworker.

## Capability layer

Resolves via [`kotoba-lang/occupation`](https://github.com/kotoba-lang/occupation)
(ISCO-08 `1344`). Required capabilities:

- :robotics
- :identity
- :forms
- :dmn
- :bpmn
- :audit-ledger

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## Reference implementation (`:maturity :implemented`)

Full itonami Actor pattern (per ADR-2607011000 / CLAUDE.md's Actors section,
alongside `cloud-itonami-isco-6130`, `-8160`, `-2166`, `-2641`, `-2651`,
`-2652`, `-2654`, `-1219`, `-1223`, `-1330`, `-1341`, `-1349`, `-1412`,
`-1439`, `-2144`, `-2320`, and `-2411`): a real
[`kotoba-lang/langgraph`](https://github.com/kotoba-lang/langgraph)
`StateGraph`, with the Advisor and Governor as distinct graph nodes and
human-in-the-loop interrupt/resume via checkpointing.

```text
:intake -> :advise -> :govern -> :decide -+-> :commit            (:ok? true)
                                           +-> :request-approval   (:escalate? true, interrupt-before)
                                           +-> :hold               (:hard? true)
```

- `src/welfare/store.kotoba` — `Store` protocol + `MemStore`:
  registered agencies, committed records, an append-only audit ledger.
- `src/welfare/advisor.kotoba` — `Advisor` protocol; `mock-advisor`
  (deterministic, default) proposes a welfare management operation from a
  request; `llm-advisor` wraps a `langchain.model/ChatModel` — either
  way the advisor only ever produces a `:propose`-effect proposal,
  never a committed record, never a case-eligibility determination,
  and LLM parse failures always yield `confidence 0.0` (forces escalation,
  never fabricated confidence).
- `src/welfare/governor.kotoba` — `WelfareGovernor/check`: a pure
  function, wired as its own `:govern` node. Hard invariants
  (unregistered agency, a proposal whose `:effect` isn't `:propose`,
  or a proposal attempting case-authority outside the allowlist)
  always route to `:hold`. Escalation invariants (`:flag-case-concern`,
  or low advisor confidence) always route to `:request-approval` — an
  `interrupt-before` node that the graph checkpoints and only resumes
  on explicit human approval (`actor/approve!`), matching the README's
  robotics-premise statement that worker-safety escalations and low-confidence
  recommendations always require human sign-off.
- `src/welfare/actor.kotoba` — `build-graph`, `run-request!`,
  `approve!`: the `langgraph.graph/state-graph` wiring itself.

```bash
kbb -M:test
```

This is what backs this repo's `:maturity :implemented` entry in
[`kotoba-lang/occupation`](https://github.com/kotoba-lang/occupation).

## License

AGPL-3.0-or-later.
