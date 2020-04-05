(defproject luminus/ring-undertow-adapter "1.0.2"
  :description "Ring Underow adapter"
  :url "http://github.com/luminus-framework/ring-adapter-undertow"
  :license {:name "ISC License"
            :url  "http://opensource.org/licenses/ISC"}
  :dependencies [[io.undertow/undertow-core "2.0.30.Final"]]
  :profiles {:dev     {:dependencies [[org.clojure/clojure "1.10.1"]
                                      [clj-http "3.10.0"]
                                      [stylefruits/gniazdo "1.1.3"]]}
             :precomp {:prep-tasks ["compile"]}}
  :java-source-paths ["src"]
  :compile-path "target")
