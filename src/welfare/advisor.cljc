(ns welfare.advisor
  "WelfareAdvisor — proposes a welfare management operation (schedule
  staffing, log program report, draft correspondence, flag case concern) for
  a registered agency. The advisor is swappable: `mock-advisor` (deterministic,
  default in dev/tests/CI) or `llm-advisor` (wraps a real `langchain.model/ChatModel`).
  Either way the advisor ONLY produces a PROPOSAL — it never writes to the store
  and has no notion of agency provenance, case eligibility, or benefits-decision
  authority; `welfare.governor` is the independent system that decides whether
  the proposal may proceed, per the itonami actor pattern.

  A proposal is a map:
    {:op :schedule-staffing|:log-program-report|:draft-correspondence|:flag-case-concern
     :effect :propose        ; the advisor NEVER emits a raw store write
     :stake :low|:medium|:high
     :confidence 0.0-1.0
     :rationale str}

  CRITICAL SCOPE EXCLUSION: This advisor NEVER proposes case-eligibility
  determination, benefits decisions, or any action that would constitute
  making a case-management judgment in lieu of the caseworker. Those remain
  exclusively the human caseworker's authority.

  LLM parse failures always yield `:confidence 0.0` (never fabricate
  confidence), which forces the governor to escalate/hold."
  (:require [clojure.edn :as edn]
            [clojure.string :as str]))

(defprotocol Advisor
  (-advise [advisor store request] "request -> proposal map"))

(defn- infer
  "Deterministic mock inference: reads the request's declared op/stake
  straight through (a stand-in for what an LLM would extract from free
  text), with a stake-derived confidence."
  [_store {:keys [op stake] :as request}]
  {:op op
   :effect :propose
   :stake (or stake :low)
   :confidence (case (or stake :low) :high 0.7 :medium 0.85 :low 0.95)
   :rationale (str "proposed " (name op) " for agency " (:agency-id request))})

(defn mock-advisor []
  (reify Advisor
    (-advise [_ store request] (infer store request))))

(def ^:private system-prompt
  "You are a welfare management advisor supporting agency operations
   (staffing schedules, program delivery logging, correspondence drafting,
   case safety concerns). Given a management operation request, propose an
   :op, an honest :confidence (0.0-1.0), and a :stake (:low/:medium/:high).
   Never fabricate confidence you don't have.

   CRITICAL SCOPE EXCLUSION: DO NOT propose case-eligibility determinations,
   benefits decisions, or any action that would constitute making a
   case-management judgment. Those remain the human caseworker's exclusive
   authority.")

(defn- parse-proposal [content]
  (try
    (let [p (edn/read-string content)]
      (if (map? p)
        (assoc p :effect :propose)
        {:op :unknown :effect :propose :confidence 0.0 :stake :high
         :rationale "unparseable LLM response"}))
    (catch #?(:clj Exception :cljs js/Error) _
      {:op :unknown :effect :propose :confidence 0.0 :stake :high
       :rationale "LLM response parse failure"})))

(defn llm-advisor
  "Wraps a `langchain.model/ChatModel`. `gen-opts` is passed through to
  `model/-generate`. Kept decoupled from any concrete model so this ns
  has no hard dependency beyond `langchain.model`'s protocol."
  [chat-model model-generate-fn gen-opts]
  (reify Advisor
    (-advise [_ _store request]
      (let [msgs [{:role :system :content system-prompt}
                  {:role :user :content (str "operation request: " (pr-str request))}]
            resp (model-generate-fn chat-model msgs gen-opts)]
        (parse-proposal (:content resp))))))
