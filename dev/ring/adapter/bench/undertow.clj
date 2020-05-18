(ns ring.adapter.bench.undertow
  (:require
    [criterium.core :as c]

    [ring.adapter.undertow.headers :as headers])
  (:import [io.undertow.util HeaderMap]))

(defn headers-bench
  []
  (let [set-headers (fn [hm]
                      (headers/set-headers hm {"test"         "cow"
                                               "dog"          "123"
                                               "Content-Type" "text/plain"}))
        hm          (doto (HeaderMap.)
                      (headers/set-headers {"test"         "cow"
                                            "dog"          "123"
                                            "Content-Type" "text/plain"}))]
    (println "headers/get-headers bench")
    (c/quick-bench (headers/get-headers hm))

    (println "headers/set-headers bench")
    (c/quick-bench (set-headers hm))))

(defn run-all-benchmarks
  []
  (headers-bench))