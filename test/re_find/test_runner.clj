(ns re-find.test-runner
  (:require
    [clojure.test :as t]
    [re-find.web-test]))

(defn -main [& args]
  (t/run-tests 're-find.web-test))
