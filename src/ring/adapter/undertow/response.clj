(ns ring.adapter.undertow.response
  (:require
    [ring.adapter.undertow.headers :refer [set-headers]]
    [clojure.java.io :as io])
  (:import
    [clojure.lang ISeq]
    [io.undertow.server HttpServerExchange]
    [java.nio ByteBuffer]
    [java.io File InputStream]))

(defprotocol RespondBody
  (respond [_ ^HttpServerExchange exchange]))

(extend-protocol RespondBody
  (Class/forName "[B")
  (respond [^bytes body ^HttpServerExchange exchange]
    (respond (ByteBuffer/wrap body) exchange))

  String
  (respond [body ^HttpServerExchange exchange]
    (.send (.getResponseSender exchange) ^String body))

  ByteBuffer
  (respond [body ^HttpServerExchange exchange]
    (.send (.getResponseSender exchange) ^ByteBuffer body))

  InputStream
  (respond [body ^HttpServerExchange exchange]
    (if (.isInIoThread exchange)
      (.dispatch exchange ^Runnable (^:once fn* [] (respond body exchange)))
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

  ISeq
  (respond [body ^HttpServerExchange exchange]
    (respond (reduce str body) exchange))

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
