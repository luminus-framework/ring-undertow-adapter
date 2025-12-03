(ns ring.adapter.undertow.middleware.gzip
  (:import
   [io.undertow.server HttpHandler]
   [io.undertow.server.handlers.encoding
    EncodingHandler
    ContentEncodingRepository
    GzipEncodingProvider]))

(defn wrap-with-gzip-handler
  "Wraps an HttpHandler with Undertow's EncodingHandler configured for gzip compression.
  
  Options:
    :deflate-level - compression level for gzip (optional, defaults to GzipEncodingProvider default)
  
  Returns an HttpHandler that automatically compresses responses with gzip when the client
  accepts gzip encoding via the Accept-Encoding header."
  ([^HttpHandler handler]
   (wrap-with-gzip-handler {} handler))
  ([{:keys [deflate-level]} ^HttpHandler handler]
   (let [gzip-provider (if deflate-level
                         (GzipEncodingProvider. deflate-level)
                         (GzipEncodingProvider.))
         encoding-repo (doto (ContentEncodingRepository.)
                        (.addEncodingHandler "gzip" gzip-provider 100))]
     (EncodingHandler. handler encoding-repo))))
