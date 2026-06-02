(ns ring.adapter.undertow.ring-websocket
  (:require [ring.websocket.protocols :as wsp])
  (:import
    [io.undertow.websockets
     WebSocketConnectionCallback]
    [io.undertow.websockets.core
     AbstractReceiveListener
     BufferedBinaryMessage
     BufferedTextMessage
     CloseMessage
     WebSocketChannel
     WebSockets
     WebSocketCallback]
    [io.undertow.websockets.spi WebSocketHttpExchange]
    [java.nio ByteBuffer]))

(defn ^:private ^ByteBuffer ->byte-buffer
  "Coerces a Ring websocket payload (a ByteBuffer or a byte array) into a
  ByteBuffer."
  [data]
  (if (instance? ByteBuffer data)
    data
    (ByteBuffer/wrap ^bytes data)))

(defn ^:private ^ByteBuffer buffered-data
  "Reads a buffered binary message into a single ByteBuffer. Undertow hands back
  a pooled ByteBuffer[]; mergeBuffers copies it into a fresh heap buffer that is
  independent of the pool, so it remains valid after the pool is closed."
  [^BufferedBinaryMessage message]
  (let [pooled (.getData message)]
    (try
      (WebSockets/mergeBuffers ^"[Ljava.nio.ByteBuffer;" (.getResource pooled))
      (finally (.close pooled)))))

(defn ws-socket [^WebSocketChannel channel]
  (reify wsp/Socket
    (-open? [_]
      (.isOpen channel))
    (-send [_ message]
      (if (instance? CharSequence message)
        (WebSockets/sendTextBlocking (.toString ^CharSequence message) channel)
        (WebSockets/sendBinaryBlocking (->byte-buffer message) channel)))
    (-ping [_ data]
      (WebSockets/sendPingBlocking (->byte-buffer data) channel))
    (-pong [_ data]
      (WebSockets/sendPongBlocking (->byte-buffer data) channel))
    (-close [_ code reason]
      (WebSockets/sendCloseBlocking ^long code ^String reason channel))
    wsp/AsyncSocket
    (-send-async [_ message succeed fail]
      (let [callback (reify WebSocketCallback
                       (complete [_ _ _] (succeed))
                       (onError [_ _ _ ex] (fail ex)))]
        (if (instance? CharSequence message)
          (WebSockets/sendText (.toString ^CharSequence message) channel callback)
          (WebSockets/sendBinary (->byte-buffer message) channel callback))))))

(defn ws-listener [listener socket]
  (proxy [AbstractReceiveListener] []
    (onFullTextMessage [^WebSocketChannel _channel ^BufferedTextMessage message]
      (wsp/on-message listener socket (.getData message)))
    (onFullBinaryMessage [^WebSocketChannel _channel ^BufferedBinaryMessage message]
      (wsp/on-message listener socket (buffered-data message)))
    (onCloseMessage [^CloseMessage message ^WebSocketChannel _channel]
      (wsp/on-close listener socket (.getCode message) (.getReason message)))
    (onError [^WebSocketChannel _channel ^Throwable error]
      (wsp/on-error listener socket error))
    (onFullPingMessage [^WebSocketChannel channel ^BufferedBinaryMessage message]
      (let [data (buffered-data message)]
        ;; When the listener handles pings it is responsible for the pong;
        ;; otherwise reply automatically as required by RFC 6455.
        (if (satisfies? wsp/PingListener listener)
          (wsp/on-ping listener socket data)
          (WebSockets/sendPong data channel
                               (reify WebSocketCallback
                                 (complete [_ _ _])
                                 (onError [_ _ _ _]))))))
    (onFullPongMessage [^WebSocketChannel _channel ^BufferedBinaryMessage message]
      (wsp/on-pong listener socket (buffered-data message)))))

(defn ws-callback [{:keys [ring.websocket/listener]}]
  (reify WebSocketConnectionCallback
    (^void onConnect [_ ^WebSocketHttpExchange _exchange ^WebSocketChannel channel]
      (let [socket (ws-socket channel)]
        (wsp/on-open listener socket)
        (.set (.getReceiveSetter channel) (ws-listener listener socket))
        (.resumeReceives channel)))))
