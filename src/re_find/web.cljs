(ns ^:figwheel-hooks re-find.web
  (:require
   [cljs.js :as cljs]
   [cljs.tools.reader :as reader]
   [cljs.tools.reader.reader-types :refer [string-push-back-reader]]
   [cljsjs.codemirror]
   [cljsjs.codemirror.addon.display.placeholder]
   [cljsjs.codemirror.addon.edit.matchbrackets]
   [cljsjs.codemirror.mode.clojure]
   [cljsjs.parinfer]
   [cljsjs.parinfer-codemirror]
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [goog.functions :as functions]
   [re-find.core :as re-find]
   [reagent.core :as r]
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

(defn highlight* [cm-ref text]
  (r/create-class
   {:render (fn [] [:textarea
                    {:class "mono"
                     :type "text"
                     :default-value text
                     :auto-complete "off"}])
    :component-did-mount
    (fn [this]
      (let [opts #js {:mode "clojure"
                      :readOnly true}
            cm (.fromTextArea js/CodeMirror
                              (r/dom-node this)
                              opts)]
        (vreset! cm-ref cm)))}))

(defn highlight [text]
  (let [cm-ref (volatile! nil)]
    (r/create-class
     {:render (fn [] [:span.cm-s-default.mono.inline
                      [highlight* cm-ref text]])
      :component-did-mount
      (fn [this]
        (let [dn (r/dom-node this)
              lines (.-firstChild (.querySelector dn ".CodeMirror-line"))
              lines (.cloneNode lines true)]
          ;; toTextArea will destroy and clean up cm
          (.toTextArea @cm-ref)
          (while (.-firstChild dn)
            (.removeChild dn (.-firstChild dn)))
          (.prepend dn lines)))})))

(def general-help
  [:div.help
   [:div.row
    [:p.col-12 "Re-find helps you find Clojure functions. It leverages specs provided by "
     [:a {:href "https://github.com/borkdude/speculative"} "speculative"] ". If
      you are not finding the function you should be finding, it's either a bug
      in this app, or there is currently no spec for this function. Please
      report an issue about this at "
     [:a
      {:href "https://github.com/borkdude/re-find"} "re-find"]
     " or " [:a
             {:href "https://github.com/borkdude/speculative"} "speculative"] "."]]])

(def args-help
  [:div.help
   [:div.row
    [:p.col-12 "The arguments are matched against the " [highlight ":args"] "
    spec."]]
   [:div.row
    [:p.col-12 "Example input: " [highlight "inc [1 2 3]"] ". This matches when the
    first argument can be a function and the second argument a vector of
    numbers."]]
   [:div.row
    [:p.col-12 "Example input: " [highlight "#{1 2 3} #{4 5 6}"] ". This matches when
            the first and second argument can be a set, or more generally, a
            collection of numbers."]]])

(def returns-help
  [:div.help
   [:div.row
    [:p.col-12 "When this field has a value that is not a function, it is
            matched against the " [:span.mono ":ret"] " spec. When a return
            value equals this field, the function is shown first,
            followed by non-exact matches. When this field has a function, it is
            used as a predicate for checking the return value."]]
   [:div.row
    [:p.col-12 "Example input: " [highlight "\"message\""] ". This value is not a
            function. This matches when the spec is
            e.g. " [highlight "seqable?"] " or " [highlight "string?"] ", but
            not when the spec is " [highlight "int?"] ". When the argument
            is " [highlight "\"message\""] ", the function " [highlight "str"] "
            is shown at the top, because " [highlight "(str \"message\")"] " equals
            " [highlight "\"message\""] "."]]
   [:div.row
    [:p.col-12 "Example:" nbsp [highlight "string?"] ". Only functions that
    return a string with the given arguments are listed."]]])

(def examples [{:args "\">>> re-find <<<\" #\"re-find\""
                :ret "\"re-find\""
                :more? true
                ;;:permutations? true
                ;;:exact-ret-match? true
                }
               {:args "#{1 2 3} #{4 5 6}"
                :ret "set?"}
               {:args "0 [1 2 3]"
                :ret "[1 0 2 0 3]"
                :exact-ret-match? true}
               {:args "1" :ret "2" :exact-ret-match? true}
               {:args "odd? (range 10)"
                :ret "map?"}
               {:args "{:a 1 :b 2} [:a]"
                :ret "{:a 1}"}
               {:args "[1 [2 [3 [4] 5] 6] 7]"
                :ret "[1 2 3 4 5 6 7]"}
               {:args "{:a 1} {:b 2}"
                :ret "{:a 1 :b 2}"}
               {:args "{} [:a :b :c] :d"
                :ret "{:a {:b {:c :d}}}"}
               {:args "{:a nil :b nil} :a"
                :ret "(MapEntry. :a nil)"}
               {:args "val {:a 1 :b 2 :c nil}"
                :ret "[1 2]"}])

(def init-state {:args ""
                 :ret ""
                 :exact-ret-match? nil
                 :help? false
                 :more? false})

(defn state-from-query-params []
  (let [uri (-> js/window .-location .-href)
        uri (.parse Uri uri)
        qd (.getQueryData uri)
        args (first (.getValues qd "args"))
        returns (first (.getValues qd "ret"))
        exact? (first (.getValues qd "exact"))
        permutations? (first (.getValues qd "perms"))
        no-args? (first (.getValues qd "no-args"))
        more? (first (.getValues qd "more"))]
    (cond-> {}
      args (assoc :args args)
      returns (assoc :ret returns)
      exact? (assoc :exact-ret-match? (= "true" exact?))
      permutations? (assoc :permutations? (= "true" permutations?))
      no-args? (assoc :no-args? (= "true" no-args?))
      more? (assoc :more? (= "true" more?)))))

(defonce app-state (r/atom (merge init-state (state-from-query-params))))
(defonce example-state (r/atom {}))
(defonce delayed-state (r/atom @app-state))

(defonce delayed-reset! (functions/debounce reset! 500))

(defn sync-delayed-state! []
  (when (not= @delayed-state @app-state)
    (delayed-reset! delayed-state @app-state)))

(r/track! sync-delayed-state!)

(defonce editors (atom {}))

(defn set-editor-placeholder! [path text]
  (when-let [cm (get-in @editors path)]
    (.setOption cm "placeholder" text)))

(defn sync-editor-placeholders []
  (let [{:keys [:args :ret]} @example-state]
    (set-editor-placeholder! [:args] args)
    (set-editor-placeholder! [:ret] ret)))

(r/track! sync-editor-placeholders)

(defn rotate-examples! [examples]
  (if (and (empty? (:args @app-state))
           (empty? (:ret @app-state)))
    (do (reset! example-state (first examples))
        (js/setTimeout #(rotate-examples! (rest examples)) 10000))
    (js/setTimeout #(rotate-examples! examples) 10000)))

(defonce do-rotate
  (rotate-examples! (cycle (shuffle examples))))

(defn format-query-string [s]
  (str/join " " (str/split (str/trim s) #"\s+")))

(defn shareable-link [state]
  (let [uri (.parse Uri (.. js/window -location -href))
        {:keys [:args :ret :exact-ret-match? :permutations?
                :no-args? :more?]} state
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
                                    (str "no-args=" no-args?))
                                  (when (true? more?)
                                    (str "more=" more?))])))
        _ (.setQuery uri qs)]
    (-> (str uri)
        (str/replace #"\(" "%28")
        (str/replace #"\)" "%29"))))

(defn sync-address-bar []
  (let [link (shareable-link @app-state)]
    (when (not= link (.. js/window -location -href))
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

(defn type-score [v1 v2]
  (cond (= (type v1) (type v2)) 1
        (and (coll? v1) (coll? v2)) .8
        :else 0))

(defn cd-encode [s]
  (-> s
      (str/replace #"/" "_fs")
      (str/replace #"\\" "_bs")
      (str/replace #"\?" "_q")))

(defn clojuredocs-url [sym]
  (let [base  "https://clojuredocs.org"
        ns (str/replace (namespace sym) #"^cljs.core$" "clojure.core")
        name (cd-encode (name sym))]
    (str base "/" ns "/" name)))

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
              ret-pred (when (fn? (first ret*))
                         (first ret*))
              ret-val (and ret*
                           (not= ret* ::invalid)
                           (not ret-pred)
                           (first ret*))
              more? (if from-example?
                      (:more? @example-state)
                      (:more? @app-state))
              match-args (cond-> {:printable-args printable-args}
                           more? (assoc :permutations? true)
                           args?
                           (assoc :args args*)
                           (and ret* ret-val)
                           (assoc :ret ret-val)
                           (and (not ret-pred)
                                args*
                                ret*
                                (not more?))
                           (assoc :exact-ret-match? true))
              results (try (mapply re-find/match match-args)
                           (catch :default e
                             (.error js/console e)
                             nil))
              results (cond ret-pred
                            (filter #(try (ret-pred (to-finite (:ret-val %)))
                                          (catch :default e
                                            (.error js/console e)
                                            nil)) results)
                            :else results)
              results (map (fn [m]
                             (cond-> m
                               (or (nil? ret*) ret-pred (= ret-val (:ret-val m)))
                               (assoc :exact? true)
                               (and ret* ret-val)
                               (assoc :type-score (type-score ret-val (:ret-val m)))))
                           results)]
          (let [no-perm-syms (set (keep #(when (not (:permutation? %))
                                           (:sym %)) results))
                results (map #(if (:permutation? %)
                                (assoc % :duplicate? (contains? no-perm-syms (:sym %)))
                                %) results)
                results (sort-by (juxt :exact?
                                       :type-score
                                       (comp not :permutation?)
                                       (comp not :duplicate?))
                                 > results)
                results (if more? results (take 5 results))]
            [:div {:class (when from-example? "example")}
             (if (seq results)
               [:table.table.results
                [:thead
                 [:tr
                  [:th "function"]
                  (when args? [:th "arguments"])
                  [:th (if args? "return value" ":ret spec")]]]
                [:tbody
                 (doall
                  (for [{:keys [:printable-args :sym :ret-val
                                :permutation? :duplicate? :ret-spec
                                :exact?] :as r}
                        results]
                    ^{:key (pr-str (show-sym sym) "-" (:printable-args r))}
                    [:tr {:class [(when duplicate? "duplicate")
                                  (when permutation? "permutation")
                                  (when-not exact? "non-exact")]}
                     [:td [:a {:href (clojuredocs-url sym)
                               :target "_blank"}
                           [highlight (show-sym sym)]]]
                     (when args? [:td [highlight (str/join " " (map pr-str printable-args))]])
                     [:td [highlight
                           (if args? (pr-str ret-val)
                               (pr-str (s/form ret-spec)))]]]))]]
               [:p "No results found."])
             [:a {:href (when-not from-example? "#")
                  :on-click (fn []
                              (when-not from-example? (swap! app-state update :more? not)))}
              (if more? "Show less\u2026" "Show more\u2026")]]))))))

(defn editor [id path]
  (r/create-class
   {:render (fn [] [:textarea
                    {:type "text"
                     :id id
                     :default-value (get-in @app-state path)
                     :auto-complete "off"
                     :placeholder (get-in @example-state path)}])
    :component-did-mount
    (fn [this]
      (let [opts #js {:mode "clojure"
                      :matchBrackets true
                      ;;parinfer does this better
                      ;;:autoCloseBrackets true
                      :lineNumbers false}
            cm (.fromTextArea js/CodeMirror
                              (r/dom-node this)
                              opts)]
        (.on cm "beforeChange"
             (fn [instance change]
               (when (.-update change)
                 (let [new-text (.join (.-text change) "")
                       new-text (str/join "" (str/split-lines new-text))
                       new-text (str/replace new-text #"\t" "")]
                   (.update change (.-from change) (.-to change) #js [new-text])
                   true))))
        (.on cm "change"
             (fn [x]
               (let [v (.getValue x)]
                 (swap! app-state assoc-in path v))))
        (js/parinferCodeMirror.init cm)
        (.removeKeyMap cm)
        (.setOption cm "extraKeys" #js {:Shift-Tab false
                                        :Tab false})
        (swap! editors assoc-in path cm)))
    :component-will-unmount
    (fn []
      (let [cm (get-in @editors path)]
        ;; toTextArea will destroy and clean up cm
        (.toTextArea cm)))}))

;; https://www.w3schools.com/bootstrap4/bootstrap_grid_system.asp
;; .col- (extra small devices - screen width less than 576px)
;; .col-sm- (small devices - screen width equal to or greater than 576px)
;; .col-md- (medium devices - screen width equal to or greater than 768px)
;; .col-lg- (large devices - screen width equal to or greater than 992px)
;; .col-xl- (xlarge devices - screen width equal to or greater than 1200px)

(defn app []
  (let [{:keys [:args :ret :exact-ret-match?
                :help? :permutations? :no-args?]} @app-state
        example-mode? (and (empty? args)
                           (empty? ret))]
    [:div#re-find.container
     [:div.row
      [:p.col-12.lead
       [:span
        "To find Clojure functions, start typing arguments and/or a return
         value/predicate."]]]
     [:div.row
      [:div.col-12 general-help]]
     [:form
      [:div.form-group.row
       {:style {:cursor "default"}
        :on-click #(swap! app-state update :help? not)}
       ;; col-$ is for small devices like iphone
       ;; col-md-$ is for screens like laptops with minimum 992 px
       [:label.label.col-md-2.col-4 "Show help"]
       [:div.field.col-md-10.col-8
        [:input {:type "checkbox"
                 :checked help?}]]]
      (when help? args-help)
      [:div.form-group.row
       [:label.label.col-md-2.col-sm-3.col-form-label {:for "args"} "Arguments"]
       [:div.field.col-md-10.col-sm-9
        [editor "args" [:args]]]]
      [:div.form-group.row
       [:label.label.col-md-2.col-sm-3.col-form-label {:for "ret"} "Returns"]
       [:div.field.col-md-10.col-sm-9
        [editor "ret" [:ret]]]]
      (when help?
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
