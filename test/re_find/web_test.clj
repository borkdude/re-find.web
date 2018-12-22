(ns re-find.web-test
  (:require
   [leiningen.simpleton :as simpleton]
   [etaoin.api :as eta]
   [clojure.string :as str]
   [clojure.test :as t :refer [is deftest run-tests]]))

(defonce server (atom nil))
(defonce driver (atom nil))

(def port (or (when-let [p (System/getenv "PORT")]
                (Integer/parseInt p)) 9500))
(def serve (System/getenv "SERVE"))

(def base-url (str "http://localhost:" port))

(defn format-query-string [s]
  (java.net.URLEncoder/encode
   (str/join " " (str/split (str/trim s) #"\s+"))))

(defn link-from-example [{:keys [:args :ret :exact-ret-match? :permutations?]}]
  (let [qs (if (and (str/blank? ret)
                    (str/blank? args)) nil
               (str/join "&"
                         (filter identity
                                 [(when (not-empty args) (str "args=" (format-query-string args)))
                                  (when (not-empty ret) (str "ret=" (format-query-string ret)))
                                  (when (true? exact-ret-match?)
                                    (str "exact=" exact-ret-match?))
                                  (when (true? permutations?)
                                    (str "perms=" permutations?))])))]
    (-> (str base-url "/?" qs)
        (str/replace #"\(" "%28")
        (str/replace #"\)" "%29"))))

(defn get-inner-html [driver css]
  (eta/js-execute driver
                  (format "return document.querySelector('%s').innerHTML" css)))

(deftest only-args-test
  (let [example {:args "\">>> re-find <<<\" #\"re-find\""}
        link (link-from-example example)]
    (eta/go @driver link)
    (is (= (:args example)
           (eta/get-element-value @driver {:css "#args"})))
    (eta/wait-exists @driver {:css "table.results"})
    (is (not (empty? (get-inner-html @driver "#re-find .results"))))))

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
    (start-server)
    (reset! driver (eta/chrome))
    (eta/go @driver base-url)
    (f)
    (stop-server)))

;;;; Scratch

(comment
  (only-args-test)
  )
