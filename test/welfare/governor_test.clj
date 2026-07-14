(ns welfare.governor-test
  (:require [clojure.test :refer [deftest is testing]]
            [welfare.store :as store]
            [welfare.governor :as governor]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-agency! st {:agency-id "agency-1" :name "City Welfare Department"})
    st))

(deftest ok-on-clean-schedule-staffing
  (let [st (fresh-store)
        proposal {:op :schedule-staffing :effect :propose :confidence 0.9 :stake :low}
        v (governor/check {:agency-id "agency-1"} {} proposal st)]
    (is (:ok? v))
    (is (not (:hard? v)))
    (is (not (:escalate? v)))))

(deftest hard-on-unregistered-agency
  (let [st (fresh-store)
        proposal {:op :schedule-staffing :effect :propose :confidence 0.9 :stake :low}
        v (governor/check {:agency-id "no-such-agency"} {} proposal st)]
    (is (:hard? v))
    (is (some #(= :no-agency (:rule %)) (:violations v)))))

(deftest hard-on-no-actuation-violation
  (let [st (fresh-store)
        proposal {:op :schedule-staffing :effect :direct-write :confidence 0.9 :stake :low}
        v (governor/check {:agency-id "agency-1"} {} proposal st)]
    (is (:hard? v))
    (is (some #(= :no-actuation (:rule %)) (:violations v)))))

(deftest hard-on-case-authority-violation
  (let [st (fresh-store)
        ;; attempting to propose a case-eligibility determination (forbidden)
        proposal {:op :determine-eligibility :effect :propose :confidence 0.9 :stake :high}
        v (governor/check {:agency-id "agency-1"} {} proposal st)]
    (is (:hard? v))
    (is (some #(= :no-case-authority (:rule %)) (:violations v)))))

(deftest escalates-on-flag-case-concern
  (let [st (fresh-store)
        proposal {:op :flag-case-concern :effect :propose :confidence 0.9 :stake :high}
        v (governor/check {:agency-id "agency-1"} {} proposal st)]
    (is (:escalate? v))
    (is (not (:hard? v)))))

(deftest escalates-on-low-confidence
  (let [st (fresh-store)
        proposal {:op :schedule-staffing :effect :propose :confidence 0.2 :stake :low}
        v (governor/check {:agency-id "agency-1"} {} proposal st)]
    (is (:escalate? v))
    (is (not (:hard? v)))))

(deftest store-records-and-ledger-append-only
  (let [st (fresh-store)]
    (store/commit-record! st {:agency-id "agency-1" :op :schedule-staffing})
    (store/append-ledger! st {:disposition :commit})
    (is (= 1 (count (store/records-of st "agency-1"))))
    (is (= 1 (count (store/ledger st))))))
