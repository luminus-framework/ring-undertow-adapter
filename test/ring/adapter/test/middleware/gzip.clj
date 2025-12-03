(ns ring.adapter.test.middleware.gzip
  (:require
    [clojure.test :refer :all]
    [ring.adapter.undertow :refer [run-undertow]]
    [clj-http.client :as http]))

(def test-port 4347)

(def test-url (str "http://localhost:" test-port))

(defn- large-content-handler
  "Returns a response with content large enough to benefit from compression"
  [request]
  {:status  200
   :headers {"Content-Type" "text/plain"}
   :body    (apply str (repeat 100 "This is a test string that will be compressed. "))})

(defmacro with-server [app options & body]
  `(let [server# (run-undertow ~app ~options)]
     (try
       ~@body
       (finally (.stop server#)))))

(deftest gzip-compression-test
  (testing "gzip compression when enabled and client accepts gzip"
    (let [expected-body (apply str (repeat 100 "This is a test string that will be compressed. "))]
      (with-server large-content-handler {:port test-port :gzip? true}
        (let [response (http/get test-url {:headers {"Accept-Encoding" "gzip"}
                                           :decompress-body false})]
          (is (= (:status response) 200))
          (is (= (get-in response [:headers "content-encoding"]) "gzip"))
          ;; Verify body is actually compressed (smaller than original)
          (is (< (count (:body response)) (count expected-body)))))))

  (testing "gzip compression not applied when disabled"
    (with-server large-content-handler {:port test-port :gzip? false}
      (let [response (http/get test-url {:headers {"Accept-Encoding" "gzip"}})]
        (is (= (:status response) 200))
        (is (nil? (get-in response [:headers "content-encoding"])))
        (is (= (:body response) (apply str (repeat 100 "This is a test string that will be compressed. ")))))))

  (testing "gzip compression not applied when client doesn't send Accept-Encoding"
    (with-server large-content-handler {:port test-port :gzip? true}
      (let [response (http/get test-url)]
        (is (= (:status response) 200))
        (is (nil? (get-in response [:headers "content-encoding"])))
        (is (= (:body response) (apply str (repeat 100 "This is a test string that will be compressed. ")))))))

  (testing "gzip compression not applied to small responses (< 1KB)"
    (let [small-handler (fn [_]
                         {:status  200
                          :headers {"Content-Type" "text/plain"}
                          :body    "Small response"})]
      (with-server small-handler {:port test-port :gzip? true}
        (let [response (http/get test-url {:headers {"Accept-Encoding" "gzip"}})]
          (is (= (:status response) 200))
          (is (nil? (get-in response [:headers "content-encoding"])))
          (is (= (:body response) "Small response"))))))

  (testing "gzip compression not applied to already-compressed content types"
    (let [image-handler (fn [_]
                         {:status  200
                          :headers {"Content-Type" "image/jpeg"}
                          :body    (apply str (repeat 200 "fake jpeg data "))})]
      (with-server image-handler {:port test-port :gzip? true}
        (let [response (http/get test-url {:headers {"Accept-Encoding" "gzip"}})]
          (is (= (:status response) 200))
          (is (nil? (get-in response [:headers "content-encoding"])))))))

  (testing "gzip compression applied to compressible content types"
    (let [json-handler (fn [_]
                        {:status  200
                         :headers {"Content-Type" "application/json"}
                         :body    (apply str (repeat 200 "{\"key\":\"value\"}"))})]
      (with-server json-handler {:port test-port :gzip? true}
        (let [response (http/get test-url {:headers {"Accept-Encoding" "gzip"}
                                           :decompress-body false})]
          (is (= (:status response) 200))
          (is (= (get-in response [:headers "content-encoding"]) "gzip"))
          ;; Verify body is actually compressed (smaller than original)
          (is (< (count (:body response)) (* 200 18)))))))

  (testing "gzip compression applied to text/html content type"
    (let [html-handler (fn [_]
                        {:status  200
                         :headers {"Content-Type" "text/html"}
                         :body    (apply str (repeat 200 "<html><body>content</body></html>"))})]
      (with-server html-handler {:port test-port :gzip? true}
        (let [response (http/get test-url {:headers {"Accept-Encoding" "gzip"}
                                           :decompress-body false})]
          (is (= (:status response) 200))
          (is (= (get-in response [:headers "content-encoding"]) "gzip"))
          ;; Verify body is actually compressed (smaller than original)
          (is (< (count (:body response)) (* 200 40))))))))

