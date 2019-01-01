(ns re-find.web-test
  (:require
   [leiningen.simpleton :as simpleton]
   [etaoin.api :as eta]
   [clojure.string :as str]
   [clojure.test :as t :refer [is deftest run-tests]]
   [re-find.jsoup :as jsoup]
   [clojure.set :as set]
   [clojure.java.io :as io]))

(defonce server (atom nil))
(defonce driver (atom nil))

(def port (or (when-let [p (System/getenv "PORT")]
                (Integer/parseInt p)) 9500))
(def serve (System/getenv "SERVE"))

(def base-url (str "http://localhost:" port))

(def pm-opt {:dir "out/screenshots"})

(defn format-query-string [s]
  (java.net.URLEncoder/encode
   (str/join " " (str/split (str/trim s) #"\s+"))))

(defn args->str [args-vec]
  (str/join " " (map pr-str args-vec)))

(defn link-from-example [{:keys [:args :ret :exact-ret-match? :permutations?]}]
  (let [args-str (args->str args)
        qs (if (and (str/blank? ret)
                    (str/blank? args-str)) nil
               (str/join "&"
                         (filter identity
                                 [(when (not-empty args-str) (str "args=" (format-query-string args-str)))
                                  (when (not-empty ret) (str "ret=" (format-query-string ret)))
                                  (when (true? exact-ret-match?)
                                    (str "exact=" exact-ret-match?))
                                  (when (true? permutations?)
                                    (str "perms=" permutations?))])))]
    (-> (str base-url "/?" qs)
        (str/replace #"\(" "%28")
        (str/replace #"\)" "%29"))))

(defn get-outer-html [driver css]
  (eta/js-execute driver
                  (format "return document.querySelector('%s').outerHTML" css)))

(defn permutations [s]
  (lazy-seq
   (if (seq (rest s))
     (apply concat
            (for [x s]
              (map #(cons x %) (permutations (remove #{x} s)))))
     [s])))

(defn test-table [example expected-syms expected-permutation-syms]
  (eta/with-postmortem @driver pm-opt
    (let [args-str (args->str (:args example))
          link (link-from-example example)]
      (eta/go @driver link)
      (is (= args-str
             (eta/get-element-value @driver {:css "#args"})))
      (eta/wait-exists @driver {:css "#re-find .results:not(.example)"})
      (let [html (get-outer-html @driver "#re-find .results")]
        ;; expected-syms
        (let [trs (jsoup/select (jsoup/parse html) "tr:not(.permutation):not(.duplicate)")
              texts (partition-all
                     3 (map jsoup/text (mapcat #(jsoup/select % "td") trs)))
              syms-displayed (set (map first texts))
              args-displayed (set (map second texts))]
          (is (set/subset? expected-syms syms-displayed))
          (is (= 1 (count args-displayed)))
          (is (= args-str (first args-displayed))))
        ;; expected-permutation-syms
        (let [args-permutations (set (map args->str (permutations (:args example))))
              trs (jsoup/select (jsoup/parse html) "tr.permutation")
              texts (partition-all
                     3 (map jsoup/text (mapcat #(jsoup/select % "td") trs)))
              syms-displayed (set (map first texts))
              args-displayed (set (map second texts))]
          (is (set/subset? args-displayed args-permutations))
          (is (set/subset? expected-permutation-syms syms-displayed)))))))

(deftest only-args-test
  (test-table {:args '[">>> re-find <<<" #"re-find"]} #{"str" "=" "get"} #{}))

(deftest nil-arg-test
  (test-table {:args '["nil"] :ret "boolean?"} #{"some?" "="} #{}))

(deftest nil-ret-test
  (test-table {:args '["nil"] :ret "nil"} #{"last" "into" "conj"} #{}))

(deftest empty-arg-test
  (test-table {:args '[] :ret "coll?"} #{"into" "conj" "clojure.set/union" "range"} #{}))

(defn stop-server []
  (when-let [s @server]
    (.stop s 0)))

(defn start-server []
  (stop-server)
  (when serve
    (reset! server
            (simpleton/new-server port "/"
                                  (simpleton/fs-handler serve)))))

(t/use-fixtures :once
  (fn [f]
    (io/make-parents (io/file "out" "screenshots" "."))
    (start-server)
    (reset! driver (eta/chrome))
    (eta/go @driver base-url)
    (f)
    (eta/quit @driver)
    (stop-server)))

;;;; Scratch

(comment
  (only-args-test)
  (t/run-tests)
  )
