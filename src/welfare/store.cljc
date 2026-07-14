(ns welfare.store
  "SSoT for the ISCO-08 1344 social welfare manager actor. Store is a
  protocol injected into the `welfare.actor` StateGraph — `MemStore` is the
  default, deterministic, zero-dep backend; a Datomic/kotoba-server-backed
  implementation can be swapped in without touching the actor or governor
  (itonami actor pattern, per ADR-2607011000 / CLAUDE.md Actors section).

  Domain:

    agency       — a registered welfare agency (:agency-id, :name)
    record       — a committed operating record under an agency
                   (staffing schedule, program report, correspondence draft,
                   case concern flag) — written ONLY via commit-record!,
                   never mutated in place
    ledger       — an append-only audit trail of every proposal/verdict/
                   disposition, regardless of outcome (commit or hold)")

(defprotocol Store
  (agency [s agency-id])
  (records-of [s agency-id])
  (ledger [s])
  (register-agency! [s agency])
  (commit-record! [s record])
  (append-ledger! [s fact]))

(defrecord MemStore [a]
  Store
  (agency [_ agency-id] (get-in @a [:agencies agency-id]))
  (records-of [_ agency-id] (filter #(= agency-id (:agency-id %)) (:records @a)))
  (ledger [_] (:ledger @a))
  (register-agency! [s agency]
    (swap! a assoc-in [:agencies (:agency-id agency)] agency) s)
  (commit-record! [s record]
    (swap! a update :records (fnil conj []) record) s)
  (append-ledger! [s fact]
    (swap! a update :ledger (fnil conj []) fact) s))

(defn mem-store
  ([] (mem-store {}))
  ([seed] (->MemStore (atom (merge {:agencies {} :records [] :ledger []} seed)))))
