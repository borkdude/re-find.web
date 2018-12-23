(ns re-find.test-runner
  (:require
    [clojure.test :as t]
    [re-find.web-test]))

(defn exit
  "Exit with the given status."
  [status]
  (do
    (shutdown-agents)
    (System/exit status)))

(defmethod clojure.test/report :summary [m]
  (clojure.test/with-test-out
    (println "\nRan" (:test m) "tests containing"
             (+ (:pass m) (:fail m) (:error m)) "assertions.")
    (println (:fail m) "failures," (:error m) "errors."))
  (if-not (clojure.test/successful? m)
    (exit 1)
    (exit 0)))

(defn -main [& args]
  (t/run-tests 're-find.web-test))
