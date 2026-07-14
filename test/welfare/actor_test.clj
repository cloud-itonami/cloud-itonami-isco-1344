(ns welfare.actor-test
  (:require [clojure.test :refer [deftest is testing]]
            [welfare.actor :as actor]
            [welfare.store :as store]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-agency! st {:agency-id "agency-1" :name "City Welfare Department"})
    st))

(deftest commits-a-clean-low-risk-request
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:agency-id "agency-1" :op :schedule-staffing :stake :low}
        result (actor/run-request! graph request {} "thread-1")]
    (is (= :done (:status result)))
    (is (some? (get-in result [:state :record])))
    (is (= 1 (count (store/records-of st "agency-1"))))))

(deftest holds-on-unregistered-agency-without-committing
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:agency-id "no-such-agency" :op :schedule-staffing :stake :low}
        result (actor/run-request! graph request {} "thread-2")]
    (is (= :done (:status result)))
    (is (nil? (get-in result [:state :record])))
    (is (empty? (store/records-of st "no-such-agency")))
    (is (= :hold (:disposition (:state result))))))

(deftest interrupts-then-commits-on-human-approval
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        ;; flagging a case concern always escalates (governor invariant)
        request {:agency-id "agency-1" :op :flag-case-concern :stake :high}
        interrupted (actor/run-request! graph request {} "thread-3")]
    (is (= :interrupted (:status interrupted)))
    (is (empty? (store/records-of st "agency-1")))
    (let [resumed (actor/approve! graph "thread-3")]
      (is (= :done (:status resumed)))
      (is (some? (get-in resumed [:state :record])))
      (is (= 1 (count (store/records-of st "agency-1")))))))

(deftest holds-on-case-authority-violation
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        ;; attempting to determine case eligibility (forbidden operation)
        request {:agency-id "agency-1" :op :determine-eligibility :stake :high}
        result (actor/run-request! graph request {} "thread-4")]
    (is (= :done (:status result)))
    (is (nil? (get-in result [:state :record])))
    (is (empty? (store/records-of st "agency-1")))
    (is (= :hold (:disposition (:state result))))))
