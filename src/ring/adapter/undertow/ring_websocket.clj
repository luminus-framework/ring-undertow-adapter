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

(defn ws-socket [^WebSocketChannel channel]
  (reify wsp/Socket
    (-open? [_]
      (.isOpen channel))
    (-send [_ message]
      (if (instance? CharSequence message)
        (WebSockets/sendTextBlocking (.toString ^CharSequence message) channel)
        (WebSockets/sendBinaryBlocking ^ByteBuffer message channel)))
    (-ping [_ data]
      (WebSockets/sendPingBlocking ^ByteBuffer data channel))
    (-pong [_ data]
      (WebSockets/sendPongBlocking ^ByteBuffer data channel))
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

(defn ws-listener [listener socket]
  (proxy [AbstractReceiveListener] []
    (onFullTextMessage [^WebSocketChannel _channel ^BufferedTextMessage message]
      (wsp/on-message listener socket (.getData message)))
    (onFullBinaryMessage [^WebSocketChannel _channel ^BufferedBinaryMessage message]
      (let [pooled (.getData message)]
        (try
          (wsp/on-message listener socket (.getResource pooled))
          (finally (.free pooled)))))
    (onCloseMessage [^CloseMessage message ^WebSocketChannel _channel]
      (wsp/on-close listener socket (.getCode message) (.getReason message)))
    (onError [^WebSocketChannel channel ^Throwable error]
      (wsp/on-error listener socket error))
    (onFullPingMessage [^WebSocketChannel channel ^BufferedBinaryMessage message]
      (when (satisfies? wsp/PingListener listener)
        (let [pooled (.getData message)]
          (try
            (wsp/on-ping listener socket (.getResource pooled))
            (finally (.free pooled))))))
    (onFullPongMessage [^WebSocketChannel channel ^BufferedBinaryMessage message]
      (let [pooled (.getData message)]
        (try
          (wsp/on-pong listener socket (.getResource pooled))
          (finally (.free pooled)))))))

(defn ws-callback [{:keys [ring.websocket/listener]}]
  (reify WebSocketConnectionCallback
    (^void onConnect [_ ^WebSocketHttpExchange _exchange ^WebSocketChannel channel]
      (let [socket (ws-socket channel)]
        (wsp/on-open listener socket)
        (.set (.getReceiveSetter channel) (ws-listener listener socket))
        (.resumeReceives channel)))))
