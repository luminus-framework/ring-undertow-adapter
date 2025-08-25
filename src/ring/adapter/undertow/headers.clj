(ns ring.adapter.undertow.headers
  (:require
    [clojure.string :as string])
  (:import
    [io.undertow.util HeaderMap HeaderValues HttpString]))

(defn get-headers
  [^HeaderMap header-map]
  (persistent!
    (reduce
      (fn [headers ^HeaderValues entry]
        (let [k   (.. entry getHeaderName toString toLowerCase)
              val (if (> (.size entry) 1)
                    (string/join "," (iterator-seq (.iterator entry)))
                    (.get entry 0))]
          (assoc! headers k val)))
      (transient {})
      header-map)))

(defn set-headers
  [^HeaderMap header-map headers]
  (reduce-kv
    (fn [^HeaderMap header-map ^String key val-or-vals]
      (let [key (HttpString. key)]
        (if (or (sequential? val-or-vals) (set? val-or-vals))
          (.putAll header-map key val-or-vals)
          (.put header-map key ^String (str val-or-vals))))
      header-map)
    header-map
    headers))
