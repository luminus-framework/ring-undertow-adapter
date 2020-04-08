(ns ring.adapter.undertow.middleware.session
  (:require [ring.middleware.session :as ring-session])
  (:import [io.undertow.util Sessions]
           [io.undertow.server HttpServerExchange]
           [io.undertow.server.session Session
                                       SessionConfig
                                       SessionCookieConfig]))

;; Mostly copied from https://github.com/immutant/immutant/blob/master/web/src/immutant/web/internal/undertow.clj

(defprotocol RingSession
  (attribute [session key])
  (set-attribute! [session key value])
  (get-expiry [session])
  (set-expiry [session timeout]))

(extend-type Session
  RingSession
  (attribute [session key]
    (.getAttribute session key))
  (set-attribute! [session key value]
    (.setAttribute session key value))
  (get-expiry [session]
    (.getMaxInactiveInterval session))
  (set-expiry [session timeout]
    (.setMaxInactiveInterval session timeout)))

(def ring-session-key "ring-session-data")

(defn ring-session [session]
  (if session (attribute session ring-session-key)))
(defn set-ring-session! [session data]
  (set-attribute! session ring-session-key data))

(defn set-session-expiry
  [session timeout]
  (when (not= timeout (get-expiry session))
    (set-expiry session timeout))
  session)

(def ^{:tag SessionCookieConfig :private true} set-cookie-config!
  (memoize
    (fn [^SessionCookieConfig config
         {:keys [cookie-name]
          {:keys [path domain max-age secure http-only]} :cookie-attrs}]
      (cond-> config
              cookie-name (.setCookieName cookie-name)
              path        (.setPath path)
              domain      (.setDomain domain)
              max-age     (.setMaxAge max-age)
              secure      (.setSecure secure)
              http-only   (.setHttpOnly http-only)))))

(defn- get-or-create-session
  ([exchange]
   (get-or-create-session exchange nil))
  ([^HttpServerExchange exchange {:keys [timeout] :as options}]
   (when options
     (set-cookie-config!
       (.getAttachment exchange SessionConfig/ATTACHMENT_KEY)
       options))
   (-> exchange
       Sessions/getOrCreateSession
       (as-> session
             (if options
               (set-session-expiry session timeout)
               session)))))

(defn wrap-undertow-session
  "Ring middleware to insert a :session entry into the request, its
  value stored in the `io.undertow.server.session.Session` from the
  associated handler"
  [handler options]
  (let [fallback (delay (ring-session/wrap-session handler options))]
    (fn [request]
      (if-let [^HttpServerExchange exchange (:server-exchange request)]
        (let [response (handler
                         (assoc request
                           ;; we assume the request map automatically derefs delays
                           :session (delay (ring-session (get-or-create-session exchange options)))))]
          (when (contains? response :session)
            (if-let [data (:session response)]
              (when-let [session (get-or-create-session exchange)]
                (set-ring-session! session data))
              (when-let [session (Sessions/getSession exchange)]
                (.invalidate session exchange))))
          response)
        (@fallback request)))))

;; From https://github.com/immutant/immutant/blob/master/web/src/immutant/web/middleware.clj
(defn wrap-session
  "Uses the session from either Undertow. By default, sessions will timeout
  after 30 minutes of inactivity.
  Supported options:
     * :timeout The number of seconds of inactivity before session expires [1800]
     * :cookie-name The name of the cookie that holds the session key [\"JSESSIONID\"]
  A :timeout value less than or equal to zero indicates the session
  should never expire.
  When running embedded, i.e. not deployed to a WildFly/EAP container,
  another option is available:
     * :cookie-attrs A map of attributes to associate with the session cookie [nil]
  And the following :cookie-attrs keys are supported:
     * :path      - the subpath the cookie is valid for
     * :domain    - the domain the cookie is valid for
     * :max-age   - the maximum age in seconds of the cookie
     * :secure    - set to true if the cookie requires HTTPS, prevent HTTP access
     * :http-only - set to true if the cookie is valid for HTTP and HTTPS only
                    (ie. prevent JavaScript access)"
  ([handler]
   (wrap-session handler {}))
  ([handler options]
   (let [options (merge {:timeout (* 30 60)} options)]
     (wrap-undertow-session handler options))))