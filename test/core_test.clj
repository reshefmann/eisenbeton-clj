(ns eisenbeton-clj.core-test
  (:require [clojure.test :refer :all]
            [eisenbeton.core :refer :all]))

(deftest completable-future-test
  (testing "Test future completion"
    (let [res (atom nil)
          cf (java.util.concurrent.CompletableFuture.)]
      (handle-completion cf (fn [_] (reset! res true)) nil)
      (.complete cf nil)
      (is (true? @res)))))

