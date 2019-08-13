(ns re-find.specs
  (:require
   [clojure.spec.alpha :as s]
   ;; load libs to spec
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

(s/fdef clojure.core/take-nth
  :args (s/cat :n int? :coll (s/? seqable?))
  :ret (s/or :transducer ifn? :seqable seqable?))

(s/fdef clojure.core/reverse
  :args (s/cat :coll seqable?)
  :ret seqable?)

;; Specs for medley:

(s/fdef medley.core/index-by
  :args (s/cat :f ifn? :coll coll?)
  :ret map?)
