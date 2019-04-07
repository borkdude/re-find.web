(ns re-find.web-test
  (:require
   [leiningen.simpleton :as simpleton]
   [etaoin.api :as eta]
   [clojure.string :as str]
   [clojure.test :as t :refer [is deftest run-tests
                               testing]]
   [clojure.math.combinatorics :refer [permutations]]
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

(defn link-from-example [{:keys [:args :ret :exact-ret-match? :permutations? :more?]}]
  (let [args-str (args->str args)
        ret-str (when ret (pr-str ret))
        qs (if (and (str/blank? ret-str)
                    (str/blank? args-str)) nil
               (str/join "&"
                         (filter identity
                                 [(when (not-empty args-str) (str "args=" (format-query-string args-str)))
                                  (when (not-empty ret-str) (str "ret=" (format-query-string ret-str)))
                                  (when (true? exact-ret-match?)
                                    (str "exact=" exact-ret-match?))
                                  (when (true? permutations?)
                                    (str "perms=" permutations?))
                                  (when (true? more?)
                                    (str "more=" more?))])))]
    (-> (str base-url "/?" qs)
        (str/replace #"\(" "%28")
        (str/replace #"\)" "%29"))))

(defn get-outer-html [driver css]
  (eta/js-execute driver
                  (format "return document.querySelector('%s').outerHTML" css)))

(defn splice-last-arg [args]
  (let [l (eval (last args))]
    (if (and some? (seqable? l))
      [args (into (vec (butlast args)) l)]
      [args])))

(defn test-table [{:keys [:args :ret :more?] :as example}
                  expected expected-permutation-syms]
  (eta/with-postmortem @driver pm-opt
    (let [expected-syms (if (every? map? expected)
                          (set (map :sym expected))
                          expected)
          expected-sym-args-ret (when (every? map? expected)
                                  (map (juxt :sym :args :ret) expected)) 
          args-str (args->str args)
          ret-str (pr-str ret)
          link (link-from-example example)]
      (eta/go @driver link)
      (is (= args-str
             (eta/get-element-value @driver {:css "#args"})))
      (eta/wait-exists @driver {:css "#re-find .results:not(.example)"})
      (let [html (get-outer-html @driver "#re-find .results")
            trs (jsoup/select (jsoup/parse html) "tr:not(.permutation):not(.duplicate)")
            texts (partition-all
                   3 (map jsoup/text (mapcat #(jsoup/select % "td") trs)))
            syms-displayed (set (map first texts))
            args-displayed (set (map second texts))]
        (testing "combination of sym + args + ret is unique"
          (is (= (distinct texts) texts)))
        (testing "expected sym + arg +ret combinations"
          (is (set/subset? (set expected-sym-args-ret) (set texts))))
        (is (set/subset? expected-syms syms-displayed))
        (is (pos? (count args-displayed)))
        (is (or (= args-str (first args-displayed))
                (when more?
                  (= (args->str (second (splice-last-arg args)))
                     (first args-displayed)))))
        ;; expected-permutation-syms
        (let [args-variations (set (map args->str (mapcat splice-last-arg (permutations (:args example)))))
              trs (jsoup/select (jsoup/parse html) "tr.permutation")
              texts (partition-all
                     3 (map jsoup/text (mapcat #(jsoup/select % "td") trs)))
              syms-displayed (set (map first texts))
              args-displayed (set (map second texts))]
          (is (set/subset? args-displayed args-variations))
          (is (set/subset? expected-permutation-syms syms-displayed)))))))

(deftest only-args-test
  (test-table '{:args [">>> re-find <<<" #"re-find"]} #{"str" "=" "get"} #{}))

(deftest nil-arg-test
  (test-table '{:args [nil] :ret boolean?} #{"some?" "="} #{}))

(deftest nil-ret-test
  (test-table '{:args [nil] :ret nil} #{"last" "into" "conj"} #{}))

(deftest empty-args-test
  (test-table '{:args [] :ret coll?} #{"into" "conj" "clojure.set/union" "range"} #{}))

(deftest sequential-ret-test
  (test-table '{:args [1 [2 3 4]] :ret [1 2 3 4]
                :more? true}
              #{"cons"} #{}))

(deftest finitize-ret-test
  (test-table '{:args [] :ret #(every? number? %)
                :more? true}
              #{"range"} #{}))

(deftest splice-last-arg-test
  (test-table '{:args [{:a 1 :b 2 :c 3} [:b :c]]
                :ret {:a 1}
                :more? true}
              #{"dissoc"} #{})
  (test-table '{:args [(list 1 2 3)]
                :ret 6
                :more? true}
              #{"*" "+"} #{}))

(deftest arg-ret-printing
  (test-table '{:args [inc inc]
                :ret (fn [f] (= 2 (f 0)))
                :more? true}
              #{{:sym "comp"
                 :args "inc inc"
                 :ret "function"}} #{})
  (test-table '{:args [(list 1 2 3)]
                :ret 6
                :more? true}
              #{{:sym "*"
                 :args "1 2 3"
                 :ret "6"}} #{}))

(deftest select-keys-test
  ;; example reported at ClojureDays 2019
  (test-table '{:args [[:a] {:a 1 :b 2}]
                :ret {:a 1}
                :more? true}
              #{"select-keys"} #{}))

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
