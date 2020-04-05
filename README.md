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

```clojure
(require '[ring.adapter.undertow.websocket :as ws])

(fn [request]
  {:websocket? true
    :ws-config {:on-open (fn [_] (println "WS open!"))
    :on-message (fn [{:keys [channel data]}]
                  (println "WS message" data)
                  (ws/send "message received" channel))
    :on-close   (fn [_] (println "WS closeed!"))}})
```

## License

Distributed under ISC License.
