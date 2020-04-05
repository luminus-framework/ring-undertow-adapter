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

(defn ^:no-doc undertow-handler
  "Returns an Undertow HttpHandler implementation for the given Ring handler."
  [handler non-blocking]
  (reify HttpHandler
    (handleRequest [_ exchange]
      (when-not non-blocking
        (.startBlocking exchange))
      (let [request-map  (build-exchange-map exchange)
            response-map (handler request-map)]
        (if (:websocket? response-map)
          (->> response-map :ws-config (ws/ws-callback) (ws/ws-request exchange))
          (set-exchange-response exchange response-map))))))

(defn ^:no-doc on-io-proxy
  [handler]
  (undertow-handler handler false))

(defn ^:no-doc dispatching-proxy
  [handler]
  (BlockingHandler. (undertow-handler handler true)))

(defn ^:no-doc tune
  [^Undertow$Builder builder {:keys [io-threads worker-threads buffer-size direct-buffers?]}]
  (cond-> builder
          io-threads (.setIoThreads io-threads)
          worker-threads (.setWorkerThreads worker-threads)
          buffer-size (.setBufferSize buffer-size)
          (not (nil? direct-buffers?)) (.setDirectBuffers direct-buffers?)))

(defn ^:no-doc listen
  [^Undertow$Builder builder {:keys [host port ssl-port ssl-context key-managers trust-managers]
                              :as   options
                              :or   {host "localhost" port 80 ssl-context (keystore->ssl-context options)}}]
  (cond-> builder
          (and ssl-port ssl-context) (.addHttpsListener ssl-port host ssl-context)
          (and ssl-port (not ssl-context)) (.addHttpsListener ^int ssl-port ^String host ^"[Ljavax.net.ssl.KeyManager;" key-managers ^"[Ljavax.net.ssl.TrustManager;" trust-managers)
          (and port) (.addHttpListener port host)))

(defn ^:no-doc client-auth [^Undertow$Builder builder {:keys [client-auth]}]
  (when client-auth
    (case client-auth
      (:want :requested)
      (.setSocketOption builder Options/SSL_CLIENT_AUTH_MODE SslClientAuthMode/REQUESTED)
      (:need :required)
      (.setSocketOption builder Options/SSL_CLIENT_AUTH_MODE SslClientAuthMode/REQUIRED))))

(defn ^:no-doc http2 [^Undertow$Builder builder {:keys [http2?]}]
  (when http2?
    (.setServerOption builder UndertowOptions/ENABLE_HTTP2 true)
    (.setServerOption builder UndertowOptions/ENABLE_SPDY true)))

(defn ^Undertow run-undertow
  "Start an Undertow webserver using given handler and the supplied options:

  :configurator   - a function called with the Undertow Builder instance
  :host           - the hostname to listen on
  :port           - the port to listen on (defaults to 80)
  :ssl-port       - a number, requires either :ssl-context, :keystore, or :key-managers
  :keystore - the filepath (a String) to the keystore
  :key-password - the password for the keystore
  :truststore - if separate from the keystore
  :trust-password - if :truststore passed
  :ssl-context - a valid javax.net.ssl.SSLContext
  :key-managers - a valid javax.net.ssl.KeyManager []
  :trust-managers - a valid javax.net.ssl.TrustManager []
  :http2?
  :io-threads - # threads handling IO, defaults to available processors
  :worker-threads - # threads invoking handlers, defaults to (* io-threads 8)
  :buffer-size - a number, defaults to 16k for modern servers
  :direct-buffers? - boolean, defaults to true
  :dispatch?      - dispatch handlers off the I/O threads (default: true)

  Returns an Undertow server instance. To stop call (.stop server)."
  [handler {:keys [dispatch?]
            :or   {dispatch? true}
            :as   options}]
  (let [^Undertow$Builder builder (Undertow/builder)
        handler-proxy             (if dispatch? dispatching-proxy on-io-proxy)]

    (.setHandler builder (handler-proxy handler))
    (tune builder options)
    (http2 builder options)
    (client-auth builder options)
    (listen builder options)

    (when-let [configurator (:configurator options)]
      (configurator builder))

    (let [^Undertow server (.build builder)]
      (try
        (.start server)
        server
        (catch Exception ex
          (.stop server)
          (throw ex))))))
