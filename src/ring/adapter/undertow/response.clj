(ns ring.adapter.undertow.response
  (:require
    [ring.adapter.undertow.headers :refer [set-headers]]
    [clojure.java.io :as io])
  (:import
    [java.nio ByteBuffer]
    [java.io File InputStream]
    [io.undertow.io Sender]
    [io.undertow.server HttpServerExchange]
    [clojure.lang ISeq]))

(defn ^ByteBuffer str-to-bb
  [^String s]
  (ByteBuffer/wrap (.getBytes s "utf-8")))

(defprotocol RespondBody
  (respond [_ ^HttpServerExchange exchange]))

(extend-protocol RespondBody
  String
  (respond [body ^HttpServerExchange exchange]
    (.send ^Sender (.getResponseSender exchange) body))

  InputStream
  (respond [body ^HttpServerExchange exchange]
    (with-open [^InputStream b body]
      (io/copy b (.getOutputStream exchange))))

  File
  (respond [f exchange]
    (respond (io/input-stream f) exchange))

  ISeq
  (respond [coll ^HttpServerExchange exchange]
    (reduce
      (fn [^Sender sender i]
        (.send sender (str-to-bb i))
        sender)
      (.getResponseSender exchange)
      coll))

  nil
  (respond [_ _]))

(defn set-exchange-response
  [^HttpServerExchange exchange {:keys [status headers body]}]
  (when-not exchange
    (throw (Exception. "Null exchange given.")))
  (when status
    (.setStatusCode exchange status))
  (set-headers (.getResponseHeaders exchange) headers)
  (respond body exchange))
