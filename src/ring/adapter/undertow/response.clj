(ns ring.adapter.undertow.response
  (:require
    [ring.adapter.undertow.headers :refer [set-headers]]
    [clojure.java.io :as io])
  (:import
    [java.nio ByteBuffer]
    [java.io File InputStream]
    [io.undertow.server HttpServerExchange]))

(defprotocol RespondBody
  (respond [_ ^HttpServerExchange exchange]))

(extend-protocol RespondBody
  String
  (respond [body ^HttpServerExchange exchange]
    (.send (.getResponseSender exchange) ^String body))

  ByteBuffer
  (respond [body ^HttpServerExchange exchange]
    (.send (.getResponseSender exchange) ^ByteBuffer body))

  InputStream
  (respond [body ^HttpServerExchange exchange]
    (if (.isInIoThread exchange)
      (.dispatch exchange ^Runnable (fn [] (respond body exchange)))
      (with-open [stream ^InputStream body]
        (.startBlocking exchange)
        (io/copy stream (.getOutputStream exchange))
        (.endExchange exchange))))

  File
  (respond [f exchange]
    (respond (io/input-stream f) exchange))

  Object
  (respond [body _]
    (throw (UnsupportedOperationException. (str "Body class not supported: " (class body)))))

  nil
  (respond [_ ^HttpServerExchange exchange]
    (.endExchange exchange)))

(defn set-exchange-response
  [^HttpServerExchange exchange {:keys [status headers body]}]
  (when-not exchange
    (throw (Exception. "Null exchange given.")))
  (when status
    (.setStatusCode exchange status))
  (set-headers (.getResponseHeaders exchange) headers)
  (respond body exchange))
