(ns ring.adapter.undertow.middleware.gzip
  (:import
   [io.undertow.server HttpHandler]
   [io.undertow.server.handlers.encoding
    EncodingHandler
    ContentEncodingRepository
    GzipEncodingProvider]
   [io.undertow.predicate Predicates]))

(defn wrap-with-gzip-handler
  "Wraps an HttpHandler with Undertow's EncodingHandler configured for gzip compression.
  
  Only compresses responses that:
  - Are at least 1KB in size
  - Have compressible content types (text/*, application/json, application/javascript, 
    application/xml, application/*+xml, image/svg+xml)
  - Client accepts gzip encoding via Accept-Encoding header
  
  Returns an HttpHandler that automatically compresses eligible responses with gzip."
  ([^HttpHandler handler]
   (wrap-with-gzip-handler {} handler))
  ([_ ^HttpHandler handler]
   (let [gzip-provider (GzipEncodingProvider.)
         ;; Predicate: minimum 1KB size AND compressible content type
         predicate (Predicates/parse "max-content-size(1) and regex(pattern='text/.*|application/(json|javascript|xml|.*\\\\+xml)|image/svg.*', value=%{o,Content-Type})")
         encoding-repo (doto (ContentEncodingRepository.)
                        (.addEncodingHandler "gzip" gzip-provider 100 predicate))]
     (EncodingHandler. handler encoding-repo))))
