# ring-undertow-adapter

ring-undertow-adapter is a [Ring](https://github.com/ring-clojure/ring) server built with
[Undertow](http://undertow.io).

## Installation

TODO

## Usage

### HTTP Handler

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

- `:on-message` - fn taking map of keys `:channel`, `:data` (optional)
- `:on-close-message` - fn taking map of keys `:channel`, `:message` (optional)
- `:on-close` - fn taking map of keys `:channel`, `:ws-channel` (optional)
- `:on-error` - fn taking map of keys `:channel`, `:error` (optional)

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
