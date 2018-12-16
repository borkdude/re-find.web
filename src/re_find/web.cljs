(ns ^:figwheel-hooks re-find.web
  (:require
   [cljs.js :as cljs]
   [clojure.string :as str]
   [goog.dom :as gdom]
   [goog.string :as gstring]
   [goog.string.format]
   [re-find.core :as re-find]
   [reagent.core :as r]
   [speculative.core.extra] ;; load specs
   [speculative.instrument] ;; load specs
   ))

(def nbsp "\u00a0")

(defn get-app-element []
  (gdom/getElement "app"))

(defn eval-str [s]
  (try
    (let [args (gstring/format "[%s]" s)
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

(defn show-sym [sym]
  (if (= "cljs.core" (namespace sym))
    (name sym)
    (pr-str sym)))

(defn results [args search-results]
  [:table.table
   [:thead
    [:tr
     [:th "function"]
     [:th "arguments"]
     [:th "return value"]]]
   [:tbody.mono
    (for [{:keys [:sym :ret-val]} search-results]
      ^{:key (str sym "-" args)}
      [:tr
       [:td (show-sym sym)]
       [:td args]
       [:td (binding [*print-length* 10]
              (pr-str ret-val))]])]])

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
    return a string are listed."]]])

(def exact-help
  [:div.help
   [:div.row
    [:p.col-12 "When enabled, the return value of a function applied to
            arguments must be equal to the given return value."]]
   [:div.row
    [:p.col-12 "Example: when enabled and given return value
    is " [:span.mono "\"message\""] ", only functions that return the string
    \"message\" are listed."]]])

(defn app []
  (let [args (r/atom "")
        ret (r/atom "")
        exact-ret-match? (r/atom false)
        help (r/atom false)]
    (fn []
      (let [search-results
            (let [args* (eval-str @args)
                  ret* (eval-str @ret)]
              (when (and (not= args* ::invalid)
                         (not= ret* ::invalid)
                         #_(do (prn "ARGS" (first ret*))
                               true)
                         #_(do (prn "RET" (first ret*))
                             true))
                (cond
                  (and (not (str/blank? @args))
                       (not (str/blank? @ret)))
                  (re-find/match
                   :args args*
                   :ret (first ret*)
                   :exact-ret-match? @exact-ret-match?)
                  (not (str/blank? @args))
                  (re-find/match :args args*)
                  (not (str/blank? @ret))
                  (try (re-find/match :ret (first ret*))
                       (catch :default e
                         (.error js/console e)
                         nil)))))]
        [:div.container
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
            :on-click #(swap! help not)}
           [:div.col-3 "Show help"]
           [:div.col-9
            [:input#exact {:type "checkbox"
                           :checked @help}]]]
          [:div.form-group.row
           [:label.col-3.col-form-label {:for "args"} "Arguments"]
           [:div.col-9
            [:input#args.form-control.mono
             {:placeholder "inc [1 2 3]"
              :value @args
              :on-change #(reset! args (.. % -target -value))}]]]
          (when @help
            args-help)
          [:div.form-group.row
           [:label.col-3.col-form-label {:for "ret"} "Returns"]
           [:div.col-9
            [:input#ret.form-control.mono
             {:placeholder "[2 3 4]"
              :value @ret
              :on-change #(do
                            ;; (.log js/console %)
                            (reset! ret (.. % -target -value)))}]]]
          (when @help
            returns-help)
          (when-not (str/blank? (str/trim @ret))
            [:div
             [:div
              [:div.form-group.row
               {:style {:cursor "default"}
                :on-click #(swap! exact-ret-match? not)}
               [:div.col-3
                [:span "Exact match?"]]
               [:div.col-9
                [:input#exact {:type "checkbox"
                               :checked @exact-ret-match?}]]]]
             (when @help
               exact-help)])]
         [:div.row
          [:div.col-12
           (when (seq search-results)
             [results @args search-results])]]]))))

(defn page []
  [:div.page
   [app]])

(defn mount [el]
  (r/render-component [page] el))

(defn mount-app-element []
  (when-let [el (get-app-element)]
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
