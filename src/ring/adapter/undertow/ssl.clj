(ns ring.adapter.undertow.ssl
    "A few SSL-related utilities, typically invoked via [[immutant.web.undertow/options]]"
  (:require [clojure.java.io :as io])
  (:import [java.security KeyStore]
           [javax.net.ssl SSLContext KeyManagerFactory TrustManagerFactory]))

(defn- ^KeyStore load-keystore
  [keystore ^String password]
  (if (instance? KeyStore keystore)
    keystore
    (with-open [in (io/input-stream keystore)]
      (doto (KeyStore/getInstance (KeyStore/getDefaultType))
        (.load in (.toCharArray password))))))

(defn keystore->key-managers
  "Return a KeyManager[] given a KeyStore and password"
  [^KeyStore keystore ^String password]
  (.getKeyManagers
    (doto (KeyManagerFactory/getInstance (KeyManagerFactory/getDefaultAlgorithm))
      (.init keystore (.toCharArray password)))))

(defn truststore->trust-managers
  "Return a TrustManager[] for a KeyStore"
  [^KeyStore truststore]
  (.getTrustManagers
    (doto (TrustManagerFactory/getInstance (TrustManagerFactory/getDefaultAlgorithm))
      (.init truststore))))

(defn ^SSLContext keystore->ssl-context
  "Turn a keystore and optional truststore, which may be either
  strings denoting file paths or actual KeyStore instances, into an
  SSLContext instance"
  [{:keys [keystore key-password truststore trust-password]}]
  (when keystore
    (let [ks (load-keystore keystore key-password)
          ts (if truststore
               (load-keystore truststore trust-password)
               ks)]
      (doto (SSLContext/getInstance "TLS")
        (.init
          (keystore->key-managers ks key-password)
          (truststore->trust-managers ts)
          nil)))))