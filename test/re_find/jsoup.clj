(ns re-find.jsoup
  (:require  [clojure.test :as t])
  (:import [org.jsoup Jsoup]
           [org.jsoup.nodes Document Element Attribute]
           [org.jsoup.nodes Document$OutputSettings$Syntax
            Entities$EscapeMode]
           [org.jsoup.parser Parser]
           [org.jsoup.safety Whitelist Cleaner]))

;;;; Jsoup

(defn parse
  "Parses String s to Jsoup document. Returns s unchanged if it's an
  Element (Document is a subclass of Element)"
  ^Element
  [s]
  (if (instance? Element s)
    s
    (Jsoup/parseBodyFragment ^String s)))

(defn select
  "Selects elements from doc using CSS selector. doc can be either string
  or Jsoup Document."
  [doc selector]
  (.select (parse doc) selector))

(defn select-1
  ^Element [doc selector]
  (first (select doc selector)))

(defn text
  "Gets text from Jsoup Element or html string."
  [elt]
  (when elt
    (.text (parse elt))))

(defn outer-html
  "Gets outerHtml from Jsoup Element"
  [^Element elt]
  (.outerHtml elt))
