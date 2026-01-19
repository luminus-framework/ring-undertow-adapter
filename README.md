# ring-undertow-adapter

ring-undertow-adapter is a [Ring](https://github.com/ring-clojure/ring) server built with
[Undertow](http://undertow.io).

## Installation

[![Clojars Project](https://img.shields.io/clojars/v/luminus/ring-undertow-adapter.svg)](https://clojars.org/luminus/ring-undertow-adapter)

## Usage

### HTTP Handler

HTTP handler returns an Undertow server instance. To stop call `(.stop <handler instance>)`.
The handler is initialized using a map with the following keys:

#### Network

| Key | Default | Info |
|-----|---------|------|
| `:host` | `localhost` | The hostname to listen on. (Set to `0.0.0.0` to be reachable from the wider internet)  |
| `:http?` | `true` | Flag to enable HTTP |
| `:port` | `80` | The port to listen on |
| `:http2?` | `false` | Flag to enable HTTP/2 |
| `:gzip?` | `false` | Flag to enable gzip compression |

#### SSL

| Key | Default | Info |
|-----|---------|------|
| `:ssl-port` | - | SSL port number (requires `:ssl-context`, `:keystore`, or `:key-managers`) |
| `:keystore` | - | Filepath (String) to the keystore |
| `:key-password` | - | Password for the keystore |
| `:truststore` | - | Truststore if separate from the keystore |
| `:trust-password` | - | Password for the truststore |
| `:ssl-context` | - | Valid `javax.net.ssl.SSLContext` |
| `:key-managers` | - | Valid `javax.net.ssl.KeyManager[]` |
| `:trust-managers` | - | Valid `javax.net.ssl.TrustManager[]` |
| `:client-auth` | - | SSL client authentication mode: `:want`/`:requested` or `:need`/`:required` |

#### Concurrency

| Key | Default | Info |
|-----|---------|------|
| `:concurrent-requests` | `nil` (unlimited) | Maximum number of requests that can be processed concurrently |
| `:queue-size` | `nil` (unlimited) | Maximum number of requests that can be queued when concurrent limit is reached. Requires `:concurrent-requests` to be set. When the queue is full, requests receive a 503 status |
| `:worker-threads` | `:concurrent-requests` if set, else `(* io-threads 8)` | Number of threads invoking handlers |
| `:graceful-shutdown-timeout` | `nil` (no graceful shutdown) | Timeout for graceful shutdown in milliseconds |

#### Performance

| Key | Default | Info |
|-----|---------|------|
| `:io-threads` | Available processors | Number of threads handling I/O |
| `:buffer-size` | `16k` | Buffer size for modern servers |
| `:direct-buffers?` | `true` | Use direct buffers |
| `:dispatch?` | `true` | Dispatch handlers off the I/O threads |
| `:max-entity-size` | `nil` (unlimited) | Maximum size of the request body in bytes |

#### Misc

| Key | Default | Info |
|-----|---------|------|
| `:configurator` | - | Function called with the Undertow Builder instance |
| `:websocket?` | `true` | Built-in handler support for websocket callbacks |
| `:async?` | `false` | Ring async flag. When true, expect a ring async three arity handler function |
| `:handler-proxy` | - | Optional custom handler proxy function taking handler as single argument |
| `:session-manager?` | `true` | Initialize Undertow session manager |
| `:custom-manager` | - | Custom implementation that extends `io.undertow.server.session.SessionManager` interface. Only used if `:session-manager?` is true. If not provided, defaults to Undertow InMemorySessionManager |
| `:max-sessions` | `-1` | Maximum number of Undertow sessions, for use with InMemorySessionManager |
| `:server-name` | `"ring-undertow"` | Server name for use with InMemorySessionManager |

### Code example

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

* `:on-open` - fn taking a map with the key `:channel` (optional)
* `:on-message` - fn taking map of keys `:channel`, `:data` (optional)
* `:on-close-message` - fn taking map of keys `:channel`, `:message` (optional)
* `:on-error` - fn taking map of keys `:channel`, `:error` (optional)

```clojure
(require '[ring.adapter.undertow.websocket :as ws])

(fn [request]
  {:undertow/websocket
   {:on-open (fn [{:keys [channel]}] (println "WS open!"))
    :on-message (fn [{:keys [channel data]}] (ws/send "message received" channel))
    :on-close-message (fn [{:keys [channel message]}] (println "WS closed!"))}})
```

If headers are provided in the map returned from the handler function they are included in the
response to the WebSocket upgrade request. Handlers relevant to the WebSocket handshake (eg
Connection) will be overwritten so that the WebSocket handshake completes correctly:

```clojure
(defn- websocket-handler-with-headers [request]
  {:headers            {"X-Test-Header" "Hello!"}
   :undertow/websocket {}})
```

### Middleware

Undertow adapter provides session middleware using Undertow session.
By default, sessions will timeout after 30 minutes of inactivity.

Supported options:

* `:timeout` The number of seconds of inactivity before session expires [1800], value less than or equal to zero indicates the session
  should never expire.
* `:cookie-name` The name of the cookie that holds the session key [\"JSESSIONID\"]
* `:cookie-attrs` A map of attributes to associate with the session cookie with the following options:
  * `:path`      - the subpath the cookie is valid for
  * `:domain`    - the domain the cookie is valid for
  * `:max-age`   - the maximum age in seconds of the cookie
  * `:secure`    - set to true if the cookie requires HTTPS, prevent HTTP access
  * `:http-only` - set to true if the cookie is valid for HTTP and HTTPS only (ie. prevent JavaScript access)

```clojure
(require '[ring.adapter.undertow.middleware.session :refer [wrap-session]])

(wrap-session handler {:http-only true})
```

### Gzip Compression

Gzip compression can be enabled by setting `:gzip? true` in the options map when calling `run-undertow`.
The middleware uses Undertow's native `GzipEncodingProvider` and automatically compresses responses
when clients send the `Accept-Encoding: gzip` header.

Compression is automatically applied only to:
- Responses that are at least 1KB in size
- Responses with compressible content types (text/*, application/json, application/javascript,
  application/xml, application/*+xml, image/svg+xml)
- Already-compressed content types (image/jpeg, image/png, video/*, application/pdf, etc.) are skipped

```clojure
(require '[ring.adapter.undertow :refer [run-undertow]])

(run-undertow handler {:port 8080 :gzip? true})
```

### Request Limiting

Request limiting controls concurrent request processing and queueing to protect your server from overload, and allow quick recovery afterwards.

To enable, set `:concurrent-requests` and `:queue-size` options. When the queue is full, backpressure will be applied in the form of status 503 responses.

`:worker-threads` should not need to be set if you have `:concurrent-requests`, unless you're doing asynchronous requests.

## License

Distributed under MIT License.
