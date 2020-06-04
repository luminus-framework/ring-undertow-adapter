(ns ring.adapter.undertow
  "Adapter for the Undertow webserver."
  (:require
    [ring.adapter.undertow.request :refer [build-exchange-map]]
    [ring.adapter.undertow.response :refer [set-exchange-response]]
    [ring.adapter.undertow.ssl :refer [keystore->ssl-context]]
    [ring.adapter.undertow.websocket :as ws])
  (:import
    [io.undertow Undertow Undertow$Builder UndertowOptions]
    [org.xnio Options SslClientAuthMode]
    [io.undertow.server HttpHandler]
    [io.undertow.server.handlers BlockingHandler]))

#_(set! *warn-on-reflection* true)

;; TODO: cleanup
(defn ^:no-doc undertow-handler
  "Returns an function that returns Undertow HttpHandler implementation for the given Ring handler."
  [{:keys [dispatch? websocket?]
    :or   {dispatch?  true
           websocket? true}}]
  (fn [handler]
    (reify HttpHandler
      (handleRequest [_ exchange]
        (when-not dispatch? (.startBlocking exchange))
        (let [request-map  (build-exchange-map exchange)
              response-map (handler request-map)]
          (if websocket?
            (if-let [ws-config (:undertow/websocket response-map)]
              (->> ws-config (ws/ws-callback) (ws/ws-request exchange))
              (set-exchange-response exchange response-map))
            (set-exchange-response exchange response-map)))))))

(defn ^:no-doc async-undertow-handler
  [{:keys [websocket?]
    :or   {websocket? true}}]
  (fn [handler]
    (reify HttpHandler
      (handleRequest [_ exchange]
        (.dispatch exchange
                   (fn []
                     (handler
                       (build-exchange-map exchange)
                       (fn [response-map]
                         (if websocket?
                           (if-let [ws-config (:undertow/websocket response-map)]
                             (->> ws-config (ws/ws-callback) (ws/ws-request exchange))
                             (set-exchange-response exchange response-map))
                           (set-exchange-response exchange response-map)))
                       (fn [^Throwable exception]
                         (set-exchange-response exchange {:status 500
                                                          :body   (.getMessage exception)})))))))))

(defn ^:no-doc handler!
  [handler builder {:keys [dispatch? handler-proxy websocket? async?]
                    :or   {dispatch?  true
                           websocket? true
                           async?     false}
                    :as   options}]
  (let [target-handler-proxy (cond
                               (some? handler-proxy) handler-proxy
                               async? (async-undertow-handler options)
                               :else (undertow-handler options))]
    (cond->> (target-handler-proxy handler)

             (and (nil? handler-proxy)
                  (not async?)
                  dispatch?)
             (BlockingHandler.)

             true
             (.setHandler builder))))

(defn ^:no-doc tune!
  [^Undertow$Builder builder {:keys [io-threads worker-threads buffer-size direct-buffers?]}]
  (cond-> builder
          io-threads (.setIoThreads io-threads)
          worker-threads (.setWorkerThreads worker-threads)
          buffer-size (.setBufferSize buffer-size)
          (not (nil? direct-buffers?)) (.setDirectBuffers direct-buffers?)))

(defn ^:no-doc listen!
  [^Undertow$Builder builder {:keys [host port ssl-port ssl-context key-managers trust-managers]
                              :as   options
                              :or   {host "localhost" port 80 ssl-context (keystore->ssl-context options)}}]
  (cond-> builder
          (and ssl-port ssl-context) (.addHttpsListener ssl-port host ssl-context)
          (and ssl-port (not ssl-context)) (.addHttpsListener ^int ssl-port ^String host ^"[Ljavax.net.ssl.KeyManager;" key-managers ^"[Ljavax.net.ssl.TrustManager;" trust-managers)
          (and port) (.addHttpListener port host)))

(defn ^:no-doc client-auth! [^Undertow$Builder builder {:keys [client-auth]}]
  (when client-auth
    (case client-auth
      (:want :requested)
      (.setSocketOption builder Options/SSL_CLIENT_AUTH_MODE SslClientAuthMode/REQUESTED)
      (:need :required)
      (.setSocketOption builder Options/SSL_CLIENT_AUTH_MODE SslClientAuthMode/REQUIRED))))

(defn ^:no-doc http2! [^Undertow$Builder builder {:keys [http2?]}]
  (when http2?
    (.setServerOption builder UndertowOptions/ENABLE_HTTP2 true)
    (.setServerOption builder UndertowOptions/ENABLE_SPDY true)))

(defn ^Undertow run-undertow
  "Start an Undertow webserver using given handler and the supplied options:

  :configurator     - a function called with the Undertow Builder instance
  :host             - the hostname to listen on
  :port             - the port to listen on (defaults to 80)
  :ssl-port         - a number, requires either :ssl-context, :keystore, or :key-managers
  :keystore         - the filepath (a String) to the keystore
  :key-password     - the password for the keystore
  :truststore       - if separate from the keystore
  :trust-password   - if :truststore passed
  :ssl-context      - a valid javax.net.ssl.SSLContext
  :key-managers     - a valid javax.net.ssl.KeyManager []
  :trust-managers   - a valid javax.net.ssl.TrustManager []
  :http2?           - flag to enable http2
  :io-threads       - # threads handling IO, defaults to available processors
  :worker-threads   - # threads invoking handlers, defaults to (* io-threads 8)
  :buffer-size      - a number, defaults to 16k for modern servers
  :direct-buffers?  - boolean, defaults to true
  :dispatch?        - dispatch handlers off the I/O threads (default: true)
  :websocket?       - built-in handler support for websocket callbacks
  :async?           - ring async flag. When true, expect a ring async three arity handler function
  :handler-proxy    - an optional custom handler proxy function taking handler as single argument

  Returns an Undertow server instance. To stop call (.stop server)."
  [handler options]
  (let [^Undertow$Builder builder (Undertow/builder)]
    (handler! handler builder options)
    (tune! builder options)
    (http2! builder options)
    (client-auth! builder options)
    (listen! builder options)

    (when-some [configurator (:configurator options)]
      (configurator builder))

    (let [^Undertow server (.build builder)]
      (try
        (.start server)
        server
        (catch Exception ex
          (.stop server)
          (throw ex))))))
