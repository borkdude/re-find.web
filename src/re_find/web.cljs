(ns ^:figwheel-hooks re-find.web
  (:require
   [cljs.js :as cljs]
   [cljs.tools.reader :as reader]
   [cljs.tools.reader.reader-types :refer [string-push-back-reader]]
   [clojure.string :as str]
   [goog.functions :as functions]
   [re-find.core :as re-find]
   [reagent.core :as r]
   [clojure.spec.alpha :as s]
   [speculative.instrument] ;; loads all specs
   )
  (:import [goog Uri]))

(defprotocol ToFinite
  (to-finite [x]))

(def take-max 100)

(extend-protocol ToFinite

  default
  (to-finite [x] x)

  LazySeq
  (to-finite [x] (take take-max x))

  Cons
  (to-finite [x] (take take-max x))

  Range
  (to-finite [x] (take take-max x))

  Iterate
  (to-finite [x] (take take-max x))

  Repeat
  (to-finite [x] (take take-max x)))

(defn wrap-vector [s]
  (str "[" s "]"))

(defn eval-str [s]
  (try
    (let [args (wrap-vector s)
          result (atom nil)]
      (cljs.js/eval-str
       (cljs/empty-state) args
       nil
       {:eval cljs/js-eval
        ;; ns setting is needed to get errors when evaluating unknowns like `i`
        :ns "cljs.core"}
       (fn [res]
         (reset! result res)))
      (if-let [err (:error @result)]
        (do
          (.error js/console err)
          ::invalid)
        (mapv to-finite (:value @result))))
    (catch :default _ ::invalid)))

(def nbsp "\u00a0")

(def general-help
  [:div.help
   [:div.row
    [:p.col-12 "Re-find helps you find Clojure functions. It leverages specs provided by "
     [:a {:href "https://github.com/slipset/speculative"} "speculative"] ". If
      you are not finding the function you should be finding, it's either a bug
      in this app, or there is currently no spec for this function. Please
      report an issue about this at "
     [:a
      {:href "https://github.com/borkdude/re-find"} "re-find"]
     " or " [:a
             {:href "https://github.com/slipset/speculative"} "speculative"] "."]]])

(def args-help
  [:div.help
   [:div.row
    [:p.col-12 "The arguments are matched against the " [:span.mono ":args"] "
    spec. For matching on no arguments, the special
    value " [:span.mono "#_empty"] " can be used."]]
   [:div.row
    [:p.col-12 "Example: " [:span.mono "inc [1 2 3]"] ". This matches when the
    first argument can be a function and the second argument a vector of
    numbers."]]
   [:div.row
    [:p.col-12 "Example: "[:span.mono "#{1 2 3} #{4 5 6}"] ". This matches when
            the first and second argument can be a set, or more generally, a
            collection of numbers."]]])

(def returns-help
  [:div.help
   [:div.row
    [:p.col-12 "When value is not a function, it is matched against
            the " [:span.mono ":ret"] " spec. When value is a function, it is
            used as a predicate for checking the return value."]]
   [:div.row
    [:p.col-12 "Example: " [:span.mono "\"message\""] ". This matches when the
            spec is " [:span.mono "seqable?"] " or " [:span.mono "string?"] ",
            but not when the spec is " [:span.mono "int?"] "."]]
   [:div.row
    [:p.col-12 "Example:" nbsp [:span.mono "string?"] ". Only functions that
    return a string are listed."]]
   [:div.row
    [:p.col-12 "When exact match is enabled, the return value of a function
            applied to arguments must be equal to the given return value. This
            option only has effect when arguments are provided."]]])

(def examples [{:args "\">>> re-find <<<\" #\"re-find\""
                :ret "\"re-find\""
                :permutations? true
                :exact-ret-match? true}
               {:args "#{1 2 3} #{4 5 6}"
                :ret "set?"}
               {:args "0 [1 2 3]"
                :ret "[1 0 2 0 3]"
                :exact-ret-match? true}
               {:args "1" :ret "2" :exact-ret-match? true}
               {:args "odd? (range 10)"
                :ret "map?"}])

(def init-state {:args ""
                 :ret ""
                 :exact-ret-match? nil
                 :help false})

(defn state-from-query-params []
  (let [uri (-> js/window .-location .-href)
        uri (.parse Uri uri)
        qd (.getQueryData uri)
        args (first (.getValues qd "args"))
        returns (first (.getValues qd "ret"))
        exact? (first (.getValues qd "exact"))
        permutations? (first (.getValues qd "perms"))
        no-args? (first (.getValues qd "no-args"))]
    (cond-> {}
      args (assoc :args args)
      returns (assoc :ret returns)
      exact? (assoc :exact-ret-match? (= "true" exact?))
      permutations? (assoc :permutations? (= "true" permutations?))
      no-args? (assoc :no-args? (= "true" no-args?)))))

(defonce app-state (r/atom (merge init-state (state-from-query-params))))
(defonce example-state (r/atom {}))
(defonce delayed-state (r/atom init-state))

(defonce delayed-reset! (functions/debounce reset! 250))

(defn sync-delayed-state! []
  (when (not= @delayed-state @app-state)
    (delayed-reset! delayed-state @app-state)))

(r/track! sync-delayed-state!)

(defn rotate-examples! [examples]
  (if (and (empty? (:args @app-state))
           (empty? (:ret @app-state)))
    (do (reset! example-state (first examples))
        (js/setTimeout #(rotate-examples! (rest examples)) 10000))
    (js/setTimeout #(rotate-examples! examples) 10000)))

(rotate-examples! (cycle (shuffle examples)))

(defn format-query-string [s]
  (str/join " " (str/split (str/trim s) #"\s+")))

(defn shareable-link [state]
  (let [uri (.parse Uri (.. js/window -location -href))
        {:keys [:args :ret :exact-ret-match? :permutations? :no-args?]} state
        qs (if (and (str/blank? ret)
                    (str/blank? args)) nil
               (str/join "&"
                         (filter identity
                                 [(when (not-empty args) (str "args=" (format-query-string args)))
                                  (when (not-empty ret) (str "ret=" (format-query-string ret)))
                                  (when (true? exact-ret-match?)
                                    (str "exact=" exact-ret-match?))
                                  (when (true? permutations?)
                                    (str "perms=" permutations?))
                                  (when (true? no-args?)
                                    (str "no-args=" no-args?))])))
        _ (.setQuery uri qs)]
    (-> (str uri)
        (str/replace #"\(" "%28")
        (str/replace #"\)" "%29"))))

(defn sync-address-bar []
  (let [link (shareable-link @app-state)]
    (when true #_(not= link (.. js/window -location -href))
          (.replaceState js/window.history nil "" link))))

(r/track! sync-address-bar)

(defn show-sym [sym]
  (if (= "cljs.core" (namespace sym))
    (name sym)
    (pr-str sym)))

(defn mapply
  "Applies a function f to the argument list formed by concatenating
  everything but the last element of args with the last element of
  args.  This is useful for applying a function that accepts keyword
  arguments to a map."
  [f & args]
  (apply f (apply concat (butlast args) (last args))))

(defn read-string [s]
  (let [r (string-push-back-reader s)]
    (take-while #(not= ::eof %)
                (repeatedly #(reader/read {:eof ::eof} r)))))

(defn search-results []
  (binding [*print-length* 10]
    (let [{:keys [:args :ret :exact-ret-match? :permutations? :no-args?]}
          @delayed-state
          from-example? (and (empty? args)
                             (empty? ret))
          [args* ret*] (if from-example?
                         [(eval-str (:args @example-state))
                          (eval-str (:ret @example-state))]
                         [(when-not (and (str/blank? args) no-args?)
                            (eval-str args))
                          (when-not (str/blank? ret)
                            (eval-str ret))])
          [exact-ret-match? permutations?]
          (if from-example?
            [(:exact-ret-match? @example-state)
             (:permutations? @example-state)]
            [exact-ret-match? permutations?])]
      (when (and (not= ::invalid args*)
                 (not= ::invalid ret*))
        (let [printable-args (if from-example?
                               (read-string (:args @example-state))
                               (read-string args))
              args? (and args*
                         (not= args* ::invalid)
                         (some? args*))
              match-args (cond-> {:printable-args printable-args
                                  :permutations? true}
                           args?
                           (assoc :args args*)
                           (and
                            ret*
                            (not= ret* ::invalid)
                            (do #_(prn "RET" ret*) true))
                           (assoc :ret (first ret*))
                           (and args*
                                ret*
                                exact-ret-match?)
                           (assoc :exact-ret-match? true))
              results (try (mapply re-find/match match-args)
                           (catch :default e
                             (.error js/console e)
                             nil))]
          (when (seq results)
            (let [no-perm-syms (set (keep #(when (not (:permutation? %))
                                             (:sym %)) results))
                  results (map #(if (:permutation? %)
                                  (assoc % :duplicate? (contains? no-perm-syms (:sym %)))
                                  %) results)
                  results (sort-by (juxt :permutation? :duplicate?) results)]
              [:table.table.results
               {:class (when from-example? "example")}
               [:thead
                [:tr
                 [:th "function"]
                 (when args? [:th "arguments"])
                 [:th (if args? "return value" ":ret spec")]]]
               [:tbody.mono
                (doall
                 (for [{:keys [:printable-args :sym :ret-val :permutation? :duplicate? :ret-spec] :as r}
                       results]
                   ^{:key (pr-str (show-sym sym) "-" (:printable-args r))}
                   [:tr {:class [(when duplicate? "duplicate")
                                 (when permutation? "permutation")]}
                    [:td (show-sym sym)]
                    (when args? [:td (str/join " " (map pr-str printable-args))])
                    [:td (if args? (pr-str ret-val)
                             (pr-str (s/form ret-spec)))]]))]])))))))

(defn app []
  (let [{:keys [:args :ret :exact-ret-match?
                :help :permutations? :no-args?]} @app-state
        example-mode? (and (empty? args)
                           (empty? ret))]
    
    [:div#re-find.container
     [:div.row
      [:p.col-12.lead
       [:span
        "To find Clojure functions, start typing arguments and/or a return
         value/predicate."]]]
     general-help
     [:form
      [:div.form-group.row
       {:style {:cursor "default"}
        :on-click #(swap! app-state update :help not)}
       [:div.col-2 "Show help"]
       [:div.col-10
        [:input#exact {:type "checkbox"
                       :checked help
                       :auto-complete "off"
                       :auto-correct "off"
                       :auto-capitalize "off"
                       :spell-check "false"}]]]
      [:div.form-group.row
       [:label.col-md-2.col-sm-3.col-form-label {:for "args"} "Arguments"]
       [:div.col-md-7.col-sm-6
        [:input#args.form-control.mono
         {:placeholder (:args @example-state)
          :value args
          :on-change #(swap! app-state assoc :args (.. % -target -value))}]]]
      (when help args-help)
      [:div.form-group.row
       [:label.col-md-2.col-sm-3.col-form-label {:for "ret"} "Returns"]
       [:div.col-md-7.col-sm-6
        [:input#ret.form-control.mono
         {:placeholder (:ret @example-state)
          :value ret
          :on-change #(swap! app-state assoc :ret (.. % -target -value))}]]
       (let [exact-disabled? (or
                              no-args? #_(str/blank? (str/trim args))
                              (str/blank? (str/trim ret)))]
         [:div.col-md-3.col-sm-4
          {:style {:cursor "default"}
           :on-click #(when-not exact-disabled?
                        (swap! app-state update :exact-ret-match? not)
                        (swap! delayed-state update :exact-ret-match? not))}
          [:input#exact {:type "checkbox"
                         :disabled exact-disabled?
                         :checked (boolean
                                   (if-not example-mode?
                                     exact-ret-match?
                                     (:exact-ret-match? @example-state)))
                         :on-change (fn [])}]
          nbsp
          [:label.col-form-label
           {:class (when exact-disabled? "disabled")}
           "exact match?"]])]
      (when help
        returns-help)]
     [:div.row
      [:div.col-12
       [search-results]]]]))

(defn mount [el]
  (r/render-component [app] el))

(defn mount-app-element []
  (when-let [el (js/document.getElementById "app")]
    (mount el)))

;; conditionally start your application based on the presence of an "app" element
;; this is particularly helpful for testing this ns without launching the app
(mount-app-element)

;; specify reload hook with ^;after-load metadata
(defn ^:after-load on-reload []
  (mount-app-element)
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
  )
