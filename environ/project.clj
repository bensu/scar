(defproject environ "1.1.0"
  :description "Library for accessing environment variables"
  :url "https://github.com/weavejester/environ"
  :scm {:dir ".."}
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0-alpha12"]]
  :plugins [[lein-environ "1.1.0"]]
  :profiles {:test {:env {:environ.core-test/keyword :a}}})
