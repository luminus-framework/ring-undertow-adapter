(defproject luminus/ring-undertow-adapter "1.5.3"
  :description "Ring Undertow adapter"
  :url "http://github.com/luminus-framework/ring-adapter-undertow"
  :license {:name "MIT License"
            :url  "http://opensource.org/licenses/MIT"}
  :dependencies [[io.undertow/undertow-core "2.4.1.Final"]
                 [ring/ring-core "1.15.4"]]
  :profiles {:dev     {:dependencies [[org.clojure/clojure "1.11.1"]
                                      [clj-http "3.13.1"]
                                      [stylefruits/gniazdo "1.2.2"]
                                      [metosin/reitit-ring "0.9.1"]
                                      [criterium "0.4.6"]]
                       :source-paths ["dev"]}
             :precomp {:prep-tasks ["clean" "compile"]}}
  :javac-options ["--release" "17"]
  :java-source-paths ["src"]
  :deploy-repositories [["releases" :clojars]])
