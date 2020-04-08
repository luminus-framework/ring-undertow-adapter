(ns ring.adapter.test.middleware.session
  (:require [clojure.test :refer :all]
            [reitit.ring :as ring]
            [ring.util.response :as response]
            [ring.adapter.undertow.middleware.session :as session]))

(def router
  (ring/router
    [["/" {:get (fn [{:keys [session]}]
                  (-> (response/response "0")
                      (assoc :session (assoc session :count 0))))}]]))

(defn app [& middleware]
  (ring/ring-handler router nil {:middleware middleware}))

(deftest wrap-session-test
  (let [request {:uri "/" :request-method :get}]
    (is (= "0" (:body ((app) request))))
    (is (= "0" (:body ((app session/wrap-session) request))))
    (is (get-in ((app session/wrap-session) request) [:headers "Set-Cookie"]))))