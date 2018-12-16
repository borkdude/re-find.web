(ns ^:figwheel-hooks re-find.web
  (:require
   [cljs.js :as cljs]
   [clojure.string :as str]
   [re-find.core :as re-find]
   [reagent.core :as r]
   [speculative.core.extra] ;; load specs
   [speculative.instrument] ;; load specs
   )
  (:import [goog Uri]))

(def nbsp "\u00a0")

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
          #_(println err)
          ::invalid)
        (:value @result)))
    (catch :default _ ::invalid)))

(def general-help
  [:div.help
   [:div.row
    [:p.col-12 "This app helps you find Clojure functions. It leverages specs provided by "
     [:a {:href "https://github.com/slipset/speculative"} "speculative"] ". If
      you are not finding the function you should be finding, it's either a bug
      in this app, or there is currently no spec for this function. Please
      report an issue about this at "
     [:a
      {:href "https://github.com/borkdude/re-find"} "re-find"]
     " or " [:a
             {:href "https://github.com/borkdude/re-find"} "speculative"] "."]]])

(def args-help
  [:div.help
   [:div.row
    [:p.col-12 "The arguments are matched against the " [:span.mono ":args"] "
    spec."]]
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

(def init-state {:args ""
                 :ret ""
                 :exact-ret-match? nil
                 :help false
                 :copy-text "Click to copy"})

(defonce app-state (r/atom init-state))

(defn state-from-query-params []
  (let [uri (.parse Uri (-> js/window .-location .-href))
        qd (.getQueryData uri)
        args (first (.getValues qd "args"))
        returns (first (.getValues qd "ret"))
        exact? (first (.getValues qd "exact"))]
    (cond-> {}
      args (assoc :args args)
      returns (assoc :ret returns)
      exact? (assoc :exact-ret-match? (= "true" exact?)))))

(defn shareable-link [state]
  (let [uri (.parse Uri (.. js/window -location -href))
        {:keys [:args :ret :exact-ret-match?]} state
        qs (str/join "&" (filter identity
                                 [(when (not-empty args) (str "args=" args))
                                  (when (not-empty ret) (str "ret=" ret))
                                  (when (boolean? exact-ret-match?)
                                    (str "exact=" exact-ret-match?))]))
        _ (.setQuery uri qs)]
    (str uri)))

(defn sync-address-bar []
    (let [link (shareable-link @app-state)]
      (when (and (not= init-state @app-state)
                 (not= link (.. js/window -location -href)))
        (.replaceState js/window.history nil "" link))))

(r/track! sync-address-bar)

(defn show-sym [sym]
  (if (= "cljs.core" (namespace sym))
    (name sym)
    (pr-str sym)))

(defn print-10 [v]
  (binding [*print-length* 10]
    (pr-str v)))

(defn search-results []
  (let [{:keys [:args :ret :exact-ret-match?]} @app-state
        results
        (let [args* (eval-str args)
              ret* (eval-str ret)]
          (when (and (not= args* ::invalid)
                     (not= ret* ::invalid)
                     #_(do (prn "ARGS" (first ret*))
                           true)
                     #_(do (prn "RET" (first ret*))
                         true))
            (cond
              (and (not (str/blank? args))
                   (not (str/blank? ret)))
              (re-find/match
               :args args*
               :ret (first ret*)
               :exact-ret-match? exact-ret-match?)
              (not (str/blank? args))
              (re-find/match :args args*)
              (not (str/blank? ret))
              (try (re-find/match :ret (first ret*))
                   (catch :default e
                     (.error js/console e)
                     nil)))))]
    (when (seq results)
      [:table.table
       [:thead
        [:tr
         [:th "function"]
         [:th "arguments"]
         [:th "return value"]]]
       [:tbody.mono
        (doall
         (for [{:keys [:sym :ret-val]} results]
           ^{:key (str (show-sym sym) "-" (print-10 args))}
           [:tr
            [:td (show-sym sym)]
            [:td args]
            [:td (print-10 ret-val)]]))]])))

(defn app []
  (let [{:keys [:args :ret :exact-ret-match? :help]} @app-state]
    [:div.container
     #_[:pre (pr-str @app-state)]
     [:div.jumbotron
      [:h3 "Welcome to re-find"]]
     [:p.lead
      [:span
       "To find Clojure functions, start typing arguments and/or a return
         value/predicate."]]
     general-help
     [:form
      [:div.form-group.row
       {:style {:cursor "default"}
        :on-click #(swap! app-state update :help not)}
       [:div.col-md-2.col-sm-3 "Show help"]
       [:div.col-md-10.col-sm-9
        [:input#exact {:type "checkbox"
                       :checked help}]]]
      [:div.form-group.row
       [:label.col-md-2.col-sm-3.col-form-label {:for "args"} "Arguments"]
       [:div.col-md-10.col-sm-9
        [:input#args.form-control.mono
         {:placeholder "inc [1 2 3]"
          :value args
          :on-change #(swap! app-state assoc :args (.. % -target -value))}]]]
      (when help
        args-help)
      [:div.form-group.row
       [:label.col-md-2.col-sm-3.col-form-label {:for "ret"} "Returns"]
       [:div.col-md-8.col-sm-7
        [:input#ret.form-control.mono
         {:placeholder "[2 3 4]"
          :value ret
          :on-change #(do
                        (.log js/console "ret val input" (.. % -target -value))
                        (swap! app-state assoc :ret (.. % -target -value)))}]]
       (let [exact-disabled? (or
                              (str/blank? (str/trim args))
                              (str/blank? (str/trim ret)))]
         [:div.col-md-2.col-sm-3
          {:style {:cursor "default"}
           :on-click #(when-not exact-disabled?
                        (swap! app-state update :exact-ret-match? not))}
          [:input#exact {:type "checkbox"
                         :disabled exact-disabled?
                         :checked exact-ret-match?
                         :on-change (fn [])}]
          nbsp
          [:label.col-form-label
           {:style {:opacity (if exact-disabled? "0.4" "1")}}
           "exact match?"]])]
      (when help
        returns-help)]
     [:div.row
      [:div.col-12
       [search-results]]]]))

(defn page []
  [:div.page
   [app]])

(defn mount [el]
  (r/render-component [page] el))

(defn mount-app-element []
  (when-let [el (js/document.getElementById "app")]
    (mount el)))

;; conditionally start your application based on the presence of an "app" element
;; this is particularly helpful for testing this ns without launching the app
(swap! app-state merge (state-from-query-params))
(mount-app-element)

;; specify reload hook with ^;after-load metadata
(defn ^:after-load on-reload []
  (mount-app-element)
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
  )
