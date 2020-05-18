(ns ring.adapter.undertow.request
  (:require
    [ring.adapter.undertow.headers :refer [get-headers]])
  (:import
    [io.undertow.server HttpServerExchange]
    [io.undertow.util Headers]))

(defn build-exchange-map
  [^HttpServerExchange exchange]
  (let [headers (.getRequestHeaders exchange)
        ctype   (.getFirst headers Headers/CONTENT_TYPE)]
    {:server-port        (-> exchange .getDestinationAddress .getPort)
     :server-name        (-> exchange .getHostName)
     :remote-addr        (-> exchange .getSourceAddress .getAddress .getHostAddress)
     :uri                (-> exchange .getRequestURI)
     :query-string       (let [qs (.getQueryString exchange)] (if-not (.equals "" qs) qs))
     :scheme             (-> exchange .getRequestScheme .toString .toLowerCase keyword)
     :request-method     (-> exchange .getRequestMethod .toString .toLowerCase keyword)
     :protocol           (-> exchange .getProtocol .toString)
     :headers            (get-headers headers)
     :content-type       ctype
     :content-length     (-> exchange .getRequestContentLength)
     :character-encoding (or (when ctype (Headers/extractQuotedValueFromHeader ctype "charset")) "ISO-8859-1")
     :body               (when (.isBlocking exchange) (.getInputStream exchange))
     :context            (.getResolvedPath exchange)
     :websocket?         (= "websocket" (.getFirst headers Headers/UPGRADE))}))
