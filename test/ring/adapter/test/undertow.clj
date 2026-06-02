(ns ring.adapter.test.undertow
  (:require
    [clojure.test :refer :all]
    [ring.adapter.undertow :refer :all]
    [ring.websocket :as ws]
    [ring.websocket.protocols :as wsp]
    [clj-http.client :as http]
    [gniazdo.core :as gniazdo]
    [clojure.java.io :as io])
  (:import
    [java.nio ByteBuffer]
    [java.net URI]
    [org.eclipse.jetty.websocket.api Session]
    [org.xnio Xnio OptionMap]
    [io.undertow.server DefaultByteBufferPool]
    [io.undertow.websockets.client WebSocketClient]
    [io.undertow.websockets.core WebSockets WebSocketChannel
     AbstractReceiveListener BufferedBinaryMessage]))

(def test-port 4347)

(def test-url (str "http://localhost:" test-port))

(defn- hello-world [request]
  {:status  200
   :headers {"Content-Type" "text/plain"}
   :body    "Hello World"})

(defn- base-handler [body-fn]
  (constantly
    {:status  200
     :headers {"Content-Type" "text/plain"}
     :body    (body-fn)}))

(defn- content-type-handler [content-type]
  (constantly
    {:status  200
     :headers {"Content-Type" content-type}
     :body    ""}))

(defn- echo-handler [request]
  {:status  200
   :headers {"request-map" (str (dissoc request :body :server-exchange))}
   :body    (:body request)})

(defn- websocket-handler [ws-opts]
  (fn [request]
    {:undertow/websocket ws-opts}))

(defn- websocket-handler-with-headers [request]
  {:headers            {"X-Test-Header" "Hello!"}
   :undertow/websocket {}})

(defmacro with-server [app options & body]
  `(let [server# (run-undertow ~app ~options)]
     (try
       ~@body
       (finally (.stop server#)))))

(defn wait-until [f]
  (loop [val       (f)
         try-times 5]
    (if val
      val
      (if (zero? try-times)
        (throw (ex-info "Wait condition timed out" {:latest-val val}))
        (do
          (Thread/sleep 500)
          (recur (f) (dec try-times)))))))

(defn ^ByteBuffer str-to-bb
  [^String s]
  (ByteBuffer/wrap (.getBytes s "utf-8")))

(deftest response-formats
  "Aim is to match Ring StreamableResponseBody protocol in output"
  (testing "ByteBuffer response"
    (with-server (base-handler #(str-to-bb "A BB")) {:port test-port}
      (let [response (http/get test-url)]
        (is (= "A BB" (:body response))))))

  (testing "Byte array response"
    (with-server (base-handler #(.getBytes "Hello World")) {:port test-port}
      (let [response (http/get test-url)]
        (is (= "Hello World" (:body response))))))

  (testing "Seq response"
    (with-server (base-handler #(list "Hello" " " "World")) {:port test-port}
      (let [response (http/get test-url)]
        (is (= "Hello World" (:body response))))))

  (testing "InputStream response"
    (with-server (base-handler #(io/input-stream (.getBytes "InputStream here"))) {:port test-port}
      (let [response (http/get test-url)]
        (is (= "InputStream here" (:body response))))))

  (testing "nil response"
    (with-server (base-handler (constantly nil)) {:port test-port}
      (let [response (http/get test-url)]
        (is (= "" (:body response)))))))

(deftest test-run-undertow
  (testing "HTTP server"
    (with-server hello-world {:port test-port :max-entity-size 50}
      (let [response (http/get test-url)]
        (is (= (:status response) 200))
        (is (.startsWith (get-in response [:headers "content-type"])
                         "text/plain"))
        (is (= (:body response) "Hello World")))))

  (testing "default character encoding"
    (with-server (content-type-handler "text/plain") {:port test-port}
      (let [response (http/get test-url)]
        (is (.contains
              (get-in response [:headers "content-type"])
              "text/plain")))))

  (testing "custom content-type"
    (with-server (content-type-handler "text/plain;charset=UTF-16;version=1") {:port test-port}
      (let [response (http/get test-url)]
        (is (= (get-in response [:headers "content-type"])
               "text/plain;charset=UTF-16;version=1")))))

  (testing "request translation"
    (with-server echo-handler {:port test-port}
      (let [response (http/post "http://localhost:4347/foo/bar/baz?surname=jones&age=123" {:body "hello"})]
        (is (= (:status response) 200))
        (is (= (:body response) "hello"))
        (let [request-map (read-string (get-in response [:headers "request-map"]))]
          (is (= (:query-string request-map) "surname=jones&age=123"))
          (is (= (:uri request-map) "/foo/bar/baz"))
          (is (= (:content-length request-map) 5))
          (is (= (:character-encoding request-map) "UTF-8"))
          (is (= (:request-method request-map) :post))
          (is (= (:content-type request-map) "text/plain; charset=UTF-8"))
          (is (= (:remote-addr request-map) "127.0.0.1"))
          (is (= (:scheme request-map) :http))
          (is (= (:server-name request-map) "localhost"))
          (is (= (:server-port request-map) test-port))
          (is (= (:ssl-client-cert request-map) nil))
          (is (= (:websocket? request-map) false))))))

  (testing "websockets"
    (let [events  (atom [])
          ws-ch   (atom nil)
          result  (promise)
          ws-opts {:on-open          (fn [{:keys [channel]}]
                                       (reset! ws-ch channel)
                                       (swap! events conj :open))
                   :on-message       (fn [{:keys [data]}]
                                       (swap! events conj data))
                   :on-close-message (fn [_]
                                       (deliver result (swap! events conj :close)))}]
      (with-server (websocket-handler ws-opts) {:port test-port}
        (let [socket (gniazdo/connect "ws://localhost:4347/")]
          (gniazdo/send-msg socket "hello")
          (gniazdo/close socket))
        (is (= [:open "hello" :close] (deref result 2000 :fail)))
        (is (.isCloseFrameReceived @ws-ch) "Client close received")
        ;; Wait loop in order to avoid race conditions between close comms and assertions
        (is (wait-until #(.isCloseFrameSent @ws-ch)) "Client close acknowledged"))))

  (testing "websocket custom headers"
    (let [result (promise)]
      (with-server websocket-handler-with-headers {:port test-port}
        (let [tester (fn [^Session session]
                       (deliver result
                                (-> session
                                    (.getUpgradeResponse)
                                    (.getHeader "X-Test-Header"))))
              socket (gniazdo/connect "ws://localhost:4347/" :on-connect tester)]
          (gniazdo/close socket))
        (is (= "Hello!" (deref result 2000 :fail)))))))

(def thread-exceptions (atom []))

(defn- hello-world-cps [request respond raise]
  (respond {:status  200
            :headers {"Content-Type" "text/plain"}
            :body    "Hello World"}))

(defn- hello-world-cps-future [request respond raise]
  (future
    (try (respond {:status  200
                   :headers {"Content-Type" "text/plain"}
                   :body    "Hello World"})
         (catch Exception ex
           (swap! thread-exceptions conj ex)))))

(deftest undertow-ring-async
  (testing "ring async test"
    (with-server hello-world-cps {:port   test-port
                                  :async? true}
      (let [response (http/get test-url)]
        (is (= (:status response) 200))
        (is (.startsWith (get-in response [:headers "content-type"])
                         "text/plain"))
        (is (= (:body response) "Hello World")))))

  (testing "ring async future test"
    (reset! thread-exceptions [])
    (with-server hello-world-cps-future {:port   test-port
                                         :async? true}
      (let [response (http/get test-url)]
        (Thread/sleep 100)
        (is (empty? @thread-exceptions))
        (is (= (:status response) 200))
        (is (.startsWith (get-in response [:headers "content-type"])
                         "text/plain"))
        (is (= (:body response) "Hello World")))))

  (testing "ring async with dispatch test"
    (with-server hello-world-cps {:port      test-port
                                  :dispatch? true
                                  :async?    true}
      (let [response (http/get test-url)]
        (is (= (:status response) 200))
        (is (.startsWith (get-in response [:headers "content-type"])
                         "text/plain"))
        (is (= (:body response) "Hello World"))))))

(deftest number-header-test
  (testing "integer header values should work"
    (let [handler-with-int-headers (fn [_]
                                     {:status  200
                                      :headers {"X-Int"        (int 10)
                                                "X-Long"       (long 42)}
                                      :body    "hello"})]
      (with-server handler-with-int-headers {:port test-port}
        (let [response (http/get test-url)]
          (is (= (:status response) 200))
          (is (= (get-in response [:headers "x-int"]) "10"))
          (is (= (get-in response [:headers "x-long"]) "42")))))))


(deftest collection-headers-test
  (testing "set or list headers should work"
    (let [handler-with-int-headers (fn [_]
                                     {:status  200
                                      :headers {"banana"        ["a" "b"]
                                                "apple"         #{"x" "y"}}
                                      :body    "hello"})]
      (with-server handler-with-int-headers {:port test-port}
        (let [response (http/get test-url)]
          (is (= (:status response) 200))
          (is (= (get-in response [:headers "banana"]) ["a" "b"]))
          (is (= (get-in response [:headers "apple"]) ["x" "y"])))))))


(deftest undertow-graceful-shutdown-test
  (testing "graceful shutdown"
    (let [sleep-handler (fn [_]
                          ;; Needs to be in a separate thread
                          @(future
                             (println "Start req")
                             (Thread/sleep 500)
                             (println "Done work")
                             {:status  200
                              :headers {"Content-Type" "text/plain"}
                              :body    "Hello World"}))
          server      (run-undertow sleep-handler {:port                      test-port
                                                   :graceful-shutdown-timeout 1000})
          future-resp (future (http/get test-url))]
      (Thread/sleep 10)
      (println "Graceful stop started")
      (.stop server)
      (println "Graceful stop called")
      (let [response (deref future-resp)]
        (is (= (:status response) 200))
        (is (= (:body response) "Hello World"))))))

(deftest concurrent-request-limiting-test
  (testing "You have to set :concurrent-requests if you set :queue-size"
    (is (thrown? IllegalArgumentException
                 (run-undertow (fn [_] {:status 200 :body "OK"})
                               {:port       test-port
                                :queue-size 1}))))

  (let [processed (atom 0)
        latch     (promise)]
    (with-server (fn [_]
                   (swap! processed inc)
                   (deref latch 500 :timeout) ;; Block until we release all requests
                   {:status 200 :body "OK"})
                 {:port                test-port
                  :concurrent-requests 1
                  :queue-size          1}
      (let [r1 (future (http/get test-url))
            _  (Thread/sleep 10)
            r2 (future (http/get test-url))
            _  (Thread/sleep 10)
            r3 (future (http/get test-url {:throw-exceptions false}))]
        (Thread/sleep 10)
        (is (= @processed 1))
        (is (= (:status (deref r3 100 :timeout)) 503))
        (deliver latch true)
        (is (= (:status (deref r1 100 :timeout)) 200))
        (is (= (:status (deref r2 100 :timeout)) 200))))))

(deftest undertow-ring-websockets
  (let [events   (atom [])
        received (atom [])
        socket   (atom nil)
        result   (promise)
        listener (reify wsp/Listener
                   (on-open [_ sock]
                     (reset! socket sock)
                     (swap! events conj [:open]))
                   (on-message [_ sock mesg]
                     (swap! events conj [:message mesg])
                     (ws/send sock mesg))
                   (on-pong [_ _ _]
                     (swap! events conj :pong))
                   (on-close [_ _sock code reason]
                     (deliver result (swap! events conj [:close code reason]))))
        handler  (constantly {:ring.websocket/listener listener})]
    (with-server handler {:port test-port}
      (let [socket (gniazdo/connect "ws://localhost:4347/"
                                    :on-receive #(swap! received conj %))]
        (gniazdo/send-msg socket "hello")
        (wait-until #(seq @received))
        (gniazdo/close socket 1000 "normal closure"))
      (is (= ["hello"] @received))
      (is (= [[:open]
              [:message "hello"]
              [:close 1000 "normal closure"]]
             (deref result 2000 :fail)))
      (is (wait-until #(not (ws/open? @socket))) "Client close acknowledged"))))

(deftest undertow-ring-websockets-binary
  ;; The server must receive binary frames as a single ByteBuffer (Ring spec),
  ;; not as Undertow's internal ByteBuffer[]. It must also be able to send byte
  ;; arrays back out, both synchronously and asynchronously.
  (let [received  (atom nil)
        recv-bin  (atom [])
        async-ok  (promise)
        listener  (reify wsp/Listener
                    (on-open [_ sock]
                      (ws/send sock (.getBytes "async-bin" "utf-8")
                               #(deliver async-ok true)
                               #(deliver async-ok %)))
                    (on-message [_ sock msg]
                      (reset! received msg)
                      (ws/send sock (.getBytes "echo-bin" "utf-8")))
                    (on-pong [_ _ _])
                    (on-error [_ _ _])
                    (on-close [_ _ _ _]))
        handler   (constantly {:ring.websocket/listener listener})]
    (with-server handler {:port test-port}
      (let [socket (gniazdo/connect "ws://localhost:4347/"
                                    :on-binary (fn [bs off len]
                                                 (swap! recv-bin conj (String. bs off len))))]
        (gniazdo/send-msg socket (ByteBuffer/wrap (.getBytes "binframe" "utf-8")))
        (wait-until #(>= (count @recv-bin) 2))
        (gniazdo/close socket 1000 "normal closure"))
      (is (instance? ByteBuffer @received) "server received a ByteBuffer, not ByteBuffer[]")
      (is (= "binframe" (let [^ByteBuffer b @received
                              a (byte-array (.remaining b))]
                          (.get b a)
                          (String. a "utf-8"))))
      (is (true? (deref async-ok 2000 :fail)) "async byte-array send succeeded")
      (is (= #{"async-bin" "echo-bin"} (set @recv-bin))))))

(deftest undertow-ring-websockets-ping-byte-array
  ;; ring.websocket/ping and /pong accept byte arrays as well as ByteBuffers.
  (let [pinged (promise)
        listener (reify wsp/Listener
                   (on-open [_ sock]
                     (future
                       (try
                         (ws/ping sock (.getBytes "ping-data" "utf-8"))
                         (ws/pong sock (.getBytes "pong-data" "utf-8"))
                         (deliver pinged :ok)
                         (catch Throwable t (deliver pinged [:threw (class t)])))))
                   (on-message [_ _ _])
                   (on-pong [_ _ _])
                   (on-error [_ _ _])
                   (on-close [_ _ _ _]))
        handler (constantly {:ring.websocket/listener listener})]
    (with-server handler {:port test-port}
      (let [socket (gniazdo/connect "ws://localhost:4347/")]
        (is (= :ok (deref pinged 2000 :fail)) "ping/pong of byte arrays did not throw")
        (gniazdo/close socket)))))

(deftest undertow-ring-websockets-auto-pong
  ;; A listener that does not satisfy PingListener must still get an automatic
  ;; pong reply to incoming pings (RFC 6455). Uses Undertow's own client for
  ;; frame-level control.
  (let [listener (reify wsp/Listener
                   (on-open [_ _])
                   (on-message [_ _ _])
                   (on-pong [_ _ _])
                   (on-error [_ _ _])
                   (on-close [_ _ _ _]))
        handler  (constantly {:ring.websocket/listener listener})
        got-pong (promise)]
    (with-server handler {:port test-port}
      (let [worker (.createWorker (Xnio/getInstance) (OptionMap/EMPTY))
            pool   (DefaultByteBufferPool. false 1024)
            ch     ^WebSocketChannel
                   (.. (WebSocketClient/connectionBuilder
                         worker pool (URI. (str "ws://localhost:" test-port "/")))
                       (connect) (get))]
        (try
          (.set (.getReceiveSetter ch)
                (proxy [AbstractReceiveListener] []
                  (onFullPongMessage [^WebSocketChannel _c ^BufferedBinaryMessage _m]
                    (deliver got-pong :pong))))
          (.resumeReceives ch)
          (WebSockets/sendPingBlocking (ByteBuffer/wrap (.getBytes "probe" "utf-8")) ch)
          (is (= :pong (deref got-pong 2000 :no-pong))
              "server auto-replied to ping with pong")
          (finally
            (.sendClose ch)
            (.shutdown worker)))))))
