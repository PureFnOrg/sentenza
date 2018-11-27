(defproject org.purefn/sentenza "0.1.9-SNAPSHOT"
  :description "A library for easier parallel (single-node) processing"
  :dependencies [[org.clojure/clojure "1.9.0" :scope "provided"]
                 [clj-time "0.14.2"]
                 [me.raynes/fs "1.4.6"]
                 [org.clojure/core.async "0.3.443"]
                 [com.taoensso/timbre "4.10.0"]
                 [cheshire "5.8.0"]]
  :profiles {:dev {:dependencies [[org.clojure/tools.namespace "0.2.11"]
                                  [com.stuartsierra/component "0.3.2"]
                                  [criterium "0.4.4"]]
                   :jvm-opts ["-Xmx6g"]
                   :source-paths ["dev"]}})
