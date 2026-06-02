---
description: Bridge Ring's protocol-based WebSocket API to Undertow's native WebSocket implementation
tags: [clojure, ring, undertow, websocket]
---

# Ring WebSocket Bridge for Undertow

Bridge Ring's protocol-based WebSocket API (`ring.websocket.protocols`) to Undertow's native WebSocket implementation.

## When to use

Adding standard Ring WebSocket support alongside or replacing a custom WS API in an Undertow-based adapter.

## Architecture

Ring 1.11+ standardizes WS via two namespaces:
- `ring.websocket.protocols` (separate artifact `org.ring-clojure/ring-websocket-protocols`, transitive dep of `ring-core`): defines `Listener`, `PingListener`, `Socket`, `AsyncSocket`
- `ring.websocket` (in `ring-core`): utility functions (`send`, `close`, `ping`, `pong`, `open?`, `websocket-response?`, `upgrade-request?`) + `IPersistentMap` extension for `Listener`/`PingListener`

Handler returns `{:ring.websocket/listener listener}` to trigger upgrade.

## Implementation pattern

Three-part bridge:

### 1. Socket wrapper — wrap `WebSocketChannel` in Ring's `Socket` + `AsyncSocket` protocols

```clojure
(defn ws-socket [^WebSocketChannel channel]
  (reify wsp/Socket
    (-open? [_] (.isOpen channel))
    (-send [_ message]
      (if (instance? CharSequence message)
        (WebSockets/sendTextBlocking (.toString ^CharSequence message) channel)
        (WebSockets/sendBinaryBlocking ^ByteBuffer message channel)))
    (-ping [_ data] (WebSockets/sendPingBlocking ^ByteBuffer data channel))
    (-pong [_ data] (WebSockets/sendPongBlocking ^ByteBuffer data channel))
    (-close [_ code reason]
      (WebSockets/sendCloseBlocking ^long code ^String reason channel))
    wsp/AsyncSocket
    (-send-async [_ message succeed fail]
      (let [callback (reify WebSocketCallback
                       (complete [_ _ _] (succeed))
                       (onError [_ _ _ ex] (fail ex)))]
        (if (instance? CharSequence message)
          (WebSockets/sendText (.toString ^CharSequence message) channel callback)
          (WebSockets/sendBinary ^ByteBuffer message channel callback))))))
```

### 2. Listener bridge — wrap Ring `Listener` in Undertow `AbstractReceiveListener`

```clojure
(defn ws-listener [listener socket]
  (proxy [AbstractReceiveListener] []
    (onFullTextMessage [^WebSocketChannel _channel ^BufferedTextMessage message]
      (wsp/on-message listener socket (.getData message)))
    (onFullBinaryMessage [^WebSocketChannel _channel ^BufferedBinaryMessage message]
      (let [pooled (.getData message)]
        (try
          (wsp/on-message listener socket (.getResource pooled))
          (finally (.close pooled)))))
    (onCloseMessage [^CloseMessage message ^WebSocketChannel _channel]
      (wsp/on-close listener socket (.getCode message) (.getReason message)))
    (onError [^WebSocketChannel channel ^Throwable error]
      (wsp/on-error listener socket error))
    (onFullPingMessage [^WebSocketChannel channel ^BufferedBinaryMessage message]
      (when (satisfies? wsp/PingListener listener)
        (let [pooled (.getData message)]
          (try
            (wsp/on-ping listener socket (.getResource pooled))
            (finally (.close pooled))))))
    (onFullPongMessage [^WebSocketChannel channel ^BufferedBinaryMessage message]
      (let [pooled (.getData message)]
        (try
          (wsp/on-pong listener socket (.getResource pooled))
          (finally (.close pooled)))))))
```

### 3. Connection callback — Undertow `WebSocketConnectionCallback`

```clojure
(defn ws-callback [{:keys [ring.websocket/listener]}]
  (reify WebSocketConnectionCallback
    (^void onConnect [_ ^WebSocketHttpExchange _exchange ^WebSocketChannel channel]
      (let [socket (ws-socket channel)]
        (wsp/on-open listener socket)
        (.set (.getReceiveSetter channel) (ws-listener listener socket))
        (.resumeReceives channel)))))
```

### 4. Routing in `handle-request`

Give priority to existing custom API, fall through to Ring standard:

```clojure
(defn handle-request [websocket? exchange response-map]
  (if websocket?
    (if-let [ws-config (:undertow/websocket response-map)]
      (->> ws-config (ws/ws-callback) (ws/ws-request exchange (:headers response-map)))
      (if (ring-ws/websocket-response? response-map)
        (->> (rws/ws-callback response-map) (ws/ws-request exchange (:headers response-map)))
        (set-exchange-response exchange response-map)))
    (set-exchange-response exchange response-map)))
```

## Pitfalls

- **Pooled buffer cleanup**: Undertow 2.4.x uses `.close()` not `.free()` on pooled ByteBuffers from `BufferedBinaryMessage.getData()`. Always use try/finally.
- **Byte array wrapping**: Ring's `-send` spec allows byte arrays in addition to `CharSequence`/`ByteBuffer`. If your `-send` passes non-CharSequence directly as ByteBuffer, add a `ByteBuffer/wrap` fallback for byte arrays.
- **PingListener**: Not all Undertow versions fire ping events. Check that `onFullPingMessage` actually fires in your version. Default Ring behavior (pong back) is handled by `IPersistentMap` extension.
- **Namespace-qualified key**: `:ring.websocket/listener` must match exactly.

## Testing

Uses `gniazdo` as WS client. Test pattern:

```clojure
(deftest undertow-ring-websockets
  (let [events   (atom [])
        received (atom [])
        socket   (atom nil)
        result   (promise)
        listener (reify wsp/Listener ...)
        handler  (constantly {:ring.websocket/listener listener})]
    (with-server handler {:port test-port}
      (let [sock (gniazdo/connect "ws://localhost:4347/"
                                  :on-receive #(swap! received conj %))]
        (gniazdo/send-msg sock "hello")
        (wait-until #(seq @received))
        (gniazdo/close sock 1000 "normal closure"))
      (is (= ["hello"] @received))
      (is (= [[:open] [:message "hello"] [:close 1000 "normal closure"]]
             (deref result 2000 :fail)))
      (is (wait-until #(not (ws/open? @socket))) "Client close acknowledged"))))
```
