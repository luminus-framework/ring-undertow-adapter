(defproject luminus/ring-undertow-adapter "1.1.3"
  :description "Ring Underow adapter"
  :url "http://github.com/luminus-framework/ring-adapter-undertow"
  :license {:name "ISC License"
            :url  "http://opensource.org/licenses/ISC"}
  :dependencies [[io.undertow/undertow-core "2.1.3.Final"]
                 [ring/ring-core "1.8.1"]]
  :profiles {:dev     {:dependencies [[org.clojure/clojure "1.10.1"]
                                      [clj-http "3.10.1"]
                                      [stylefruits/gniazdo "1.1.3"]
                                      [metosin/reitit-ring "0.4.2"]
                                      [criterium "0.4.5"]]
                       :source-paths ["dev"]}
             :precomp {:prep-tasks ["compile"]}}
  :javac-options ["-target" "1.8" "-source" "1.8"]
  :java-source-paths ["src"]
  :compile-path "target")
