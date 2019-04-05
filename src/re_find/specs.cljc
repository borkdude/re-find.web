(ns re-find.specs
  (:require
   [clojure.spec.alpha :as s]
   [medley.core :as medley]
   ;; load specs
   [speculative.core]
   [speculative.set]
   [speculative.string]))

;; Not yet in speculative:

(s/fdef clojure.core/<
  :args (s/+ number?)
  :ret boolean?)

(s/fdef clojure.core/>
  :args (s/+ number?)
  :ret boolean?)

;; Specs for medley:

(s/fdef medley.core/index-by :args (s/cat :f ifn? :coll coll?))
