(defproject scar "0.1.0"
  :description "Library for configuring your app in different environments"
  :url "https://github.com/bensu/scar"
  :scm {:dir ".."}
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0-alpha12"]]
  :plugins [[lein-environ "1.1.0"]]
  :profiles {:test {:env {:scar.core-test/keyword :a}}})
