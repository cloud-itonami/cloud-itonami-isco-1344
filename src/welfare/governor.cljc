(ns welfare.governor
  "WelfareGovernor — the independent safety/traceability layer for the
  ISCO-08 1344 social welfare manager actor. Wired as its own `:govern` node
  in `welfare.actor`'s StateGraph, downstream of `:advise` — the Advisor has
  no notion of agency provenance, worker safety, or the irreversible nature
  of case/eligibility decisions, so this MUST be a separate system able to
  reject a proposal (itonami actor pattern, per ADR-2607011000 / CLAUDE.md
  Actors section).

  `check` is a pure function of (request, context, proposal, store) ->
  verdict; it never mutates the store. The StateGraph's `:decide` node
  routes on the verdict:
    :hard? true                → :hold  (irreversible, no write)
    :escalate? true            → :request-approval (interrupt-before)
    otherwise                  → :commit

  ═══════════════════════════════════════════════════════════════════════════
  CRITICAL SCOPE BOUNDARY — WHAT THIS DOES NOT DO
  ═══════════════════════════════════════════════════════════════════════════

  This actor supports a welfare manager's ADMINISTRATIVE operations only:
  staffing schedules, program-delivery data logging, safety concerns for
  escalation to caseworkers.

  THIS ACTOR DOES NOT AND CANNOT:
    • Make case-eligibility determinations
    • Authorize or deny benefits
    • Make case-management decisions
    • Discharge caseworker authority or judgment

  Those decisions remain EXCLUSIVELY the human caseworker's/manager's authority.
  Any proposal that would constitute case-eligibility determination,
  benefits authorization, or case-management judgment is a HARD BLOCK.

  ═══════════════════════════════════════════════════════════════════════════

  HARD invariants (:hard? true, ALWAYS :hold, never overridable):
    1. agency provenance  — the request's agency must be registered.
    2. no-actuation       — proposal :effect must be :propose.
    3. no-case-authority  — proposal :op must NOT be case-eligibility,
                            benefits-decision, or case-management authority
                            (hardcoded allowlist: schedule-staffing,
                            log-program-report, draft-correspondence,
                            flag-case-concern only).

  ESCALATION invariants (:escalate? true, ALWAYS human sign-off):
    4. :op :flag-case-concern (worker safety always escalates to caseworker).
    5. low confidence (< `confidence-floor`)."
  (:require [welfare.store :as store]))

(def confidence-floor 0.6)
(def ^:private allowed-ops #{:schedule-staffing :log-program-report
                             :draft-correspondence :flag-case-concern})

(defn- hard-violations [{:keys [proposal]} agency-record]
  (cond-> []
    (nil? agency-record)
    (conj {:rule :no-agency :detail "未登録 agency"})

    (not= :propose (:effect proposal))
    (conj {:rule :no-actuation :detail "effect は :propose のみ許可（直接書込禁止）"})

    (not (contains? allowed-ops (:op proposal)))
    (conj {:rule :no-case-authority
           :detail "この操作は管理者の権限外（ケース資格決定・給付決定は人間 caseworker のみ）"})))

(defn check
  "Assess a proposal against `request`/`context`/`proposal` and a
  `store` implementing `welfare.store/Store`. Returns
  `{:ok? bool :violations [...] :confidence n :hard? bool :escalate? bool}`."
  [request context proposal store]
  (let [agency-record (store/agency store (:agency-id request))
        hard (hard-violations {:proposal proposal} agency-record)
        hard? (boolean (seq hard))
        conf (or (:confidence proposal) 0.0)
        low? (< conf confidence-floor)
        escalating-op? (= :flag-case-concern (:op proposal))]
    {:ok? (and (not hard?) (not low?) (not escalating-op?))
     :violations hard
     :confidence conf
     :hard? hard?
     :escalate? (and (not hard?) (or low? escalating-op?))}))
