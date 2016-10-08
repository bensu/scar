(ns environ.core-test
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io]
            [environ.core :as env :refer [env defenv with-env]]))

(deftest keywordize
  (are [x y] (= y (env/keywordize x))
    "ENVIRON__CORE_TEST___INT" ::int
    "ENVIRON__CORE_TEST___STRING" ::string))

(defenv
  ::int integer?
  ::string string?
  ::number-string string?
  ::keyword keyword?
  ::map map?
  ::vector vector?
  ::set set?
  ::uuid uuid?)

(deftest load-env
  (env/init!)
  (testing "from the .edn file passed as an ENV"
    (is (= "a" (env ::string))))
  (testing "from the env vars"
    (is (= 1 (env ::int)) "Basic edn values work")
    (is (= {} (env ::map)) "Data literals work")
    (is (= [1 2 "abc"] (env ::vector)) "Compound edn values work")
    (is (= #{"a set with strings"} (env ::set)) "Non escaped strings work")
    (is (= (env ::uuid) #uuid "10e4193c-d374-4d20-9914-b03af25a1adc") "Reader tags work")
    (is (= "12345" (env ::number-string)) "Strings that could be edn/read work"))
  (testing "from project.clj"
    (is (= :a (env ::keyword)))))

(deftest test-with-env
  (env/init!)
  (testing "I can change some values for testing purposes"
    (with-env [::string "a new string"
               ::vector []]
      (is (= "a new string" (env ::string)))
      (is (= [] (env ::vector))))))
