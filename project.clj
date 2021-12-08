(defproject luminus/ring-undertow-adapter "1.2.5"
  :description "Ring Undertow adapter"
  :url "http://github.com/luminus-framework/ring-adapter-undertow"
  :license {:name "MIT License"
            :url  "http://opensource.org/licenses/MIT"}
  :dependencies [[io.undertow/undertow-core "2.2.14.Final"]
                 [ring/ring-core "1.9.4"]]
  :profiles {:dev     {:dependencies [[org.clojure/clojure "1.10.3"]
                                      [clj-http "3.12.3"]
                                      [stylefruits/gniazdo "1.2.0"]
                                      [metosin/reitit-ring "0.5.15"]
                                      [criterium "0.4.6"]]
                       :source-paths ["dev"]}
             :precomp {:prep-tasks ["clean" "compile"]}}
  :javac-options ["-target" "1.8" "-source" "1.8"]
  :java-source-paths ["src"]
  :deploy-repositories [["releases" :clojars]])
