# ring-undertow-adapter

ring-undertow-adapter is a [Ring](https://github.com/ring-clojure/ring) server built with
[Undertow](http://undertow.io).

## Installation

[![Clojars Project](https://img.shields.io/clojars/v/luminus/ring-undertow-adapter.svg)](https://clojars.org/luminus/ring-undertow-adapter)

## Usage

### HTTP Handler

HTTP handler returns an Undertow server instance. To stop call `(.stop <handler instance>)`.
The handler is initialized using a map with the following keys:

* `:configurator` - a function called with the Undertow Builder instance
* `:host` - the hostname to listen on
* `:port` - the port to listen on (defaults to 80)
* `:ssl-port` - a number, requires either :ssl-context, :keystore, or :key-managers
* `:keystore` - the filepath (a String) to the keystore
* `:key-password` - the password for the keystore
* `:truststore` - if separate from the keystore
* `:trust-password` - if :truststore passed
* `:ssl-context` - a valid javax.net.ssl.SSLContext
* `:key-managers` - a valid javax.net.ssl.KeyManager []
* `:trust-managers` - a valid javax.net.ssl.TrustManager []
* `:http2?` - boolean
* `:io-threads` - # threads handling IO, defaults to available processors
* `:worker-threads` - # threads invoking handlers, defaults to (* io-threads 8)
* `:buffer-size` - a number, defaults to 16k for modern servers
* `:direct-buffers?` - boolean, defaults to true
* `:dispatch?`      - dispatch handlers off the I/O threads (default: true)

```clojure
(require '[ring.adapter.undertow :refer [run-undertow]])

(defn handler [req]
  {:status 200
   :body "Hello world"})

(run-undertow handler {:port 8080})
```

### WebSocket Handler

A WebSocket handler is created using a Ring handler function that returns a map
containing a `:undertow/websocket` containing the configuration map:

* `:on-message` - fn taking map of keys `:channel`, `:data` (optional)
* `:on-close-message` - fn taking map of keys `:channel`, `:message` (optional)
* `:on-close` - fn taking map of keys `:channel`, `:ws-channel` (optional)
* `:on-error` - fn taking map of keys `:channel`, `:error` (optional)

```clojure
(require '[ring.adapter.undertow.websocket :as ws])

(fn [request]
  {:undertow/websocket 
   {:on-open (fn [_] (println "WS open!"))
    :on-message (fn [{:keys [channel data]}] (ws/send "message received" channel))
    :on-close   (fn [_] (println "WS closeed!"))}})
```

## License

Distributed under ISC License.
