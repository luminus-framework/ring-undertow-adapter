(ns ring.adapter.test.undertow
  (:require
    [clojure.test :refer :all]
    [ring.adapter.undertow :refer :all]
    [clj-http.client :as http]
    [gniazdo.core :as gniazdo]
    [clojure.java.io :as io])
  (:import
    [java.nio ByteBuffer]
    [org.eclipse.jetty.websocket.api Session]))

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
          ws-opts {:on-open    (fn [{:keys [channel]}]
                                 (reset! ws-ch channel)
                                 (swap! events conj :open))
                   :on-message (fn [{:keys [data]}]
                                 (swap! events conj data))
                   :on-close   (fn [_]
                                 (deliver result (swap! events conj :close)))}]
      (with-server (websocket-handler ws-opts) {:port test-port}
        (let [socket (gniazdo/connect "ws://localhost:4347/")]
          (gniazdo/send-msg socket "hello")
          (gniazdo/close socket))
        (is (= [:open "hello" :close] (deref result 2000 :fail)))
        (is (.isCloseFrameReceived @ws-ch) "Client close received")
        (is (.isCloseFrameSent @ws-ch) "Client close acknowledged"))))

  (testing "websocket custom headers"
    (let [result  (promise)]
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