(ns scar.core-test
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io]
            [clojure.spec :as s]
            [scar.core :as env :refer [env defenv with-env]]))

(deftest keywordize
  (are [x y] (= y (env/keywordize x))
    "SCAR__CORE_TEST___INT" ::int
    "SCAR__CORE_TEST___STRING" ::string)
  (are [x y] (= y (env/->envar x))
    ::int "SCAR__CORE_TEST___INT"
    ::string "SCAR__CORE_TEST___STRING"))

(deftest defenv-checks-args
  (testing "defenv checks its arguments"
    (is (thrown? Exception (macroexpand `(defenv))))
    (is (thrown? Exception (macroexpand `(defenv ::a))))
    (is (thrown? Exception (macroexpand `(defenv 1 ::a))))
    (is (thrown? Exception (macroexpand `(defenv ::a ::a 1))))))

(defenv
  ::int integer?
  ::string string?
  ::number-string string?
  ::keyword keyword?
  ::map map?
  ::vector vector?
  ::set (s/and set? (s/* string?))
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
      (is (= [] (env ::vector)))))
  (testing "with-env also type checks"
    (is (thrown? Exception (with-env [::vector []
                                      ::string 1]
                             (is (= 1 (env ::string)))))))
  (testing "with-env can take bindings"
    (let [a 3]
      (with-env [::int a]
        (is (= 3 (env ::int)))))))

(deftest good-errors
  (testing "spec checking throws useful errors that include the source"
    (testing "when using init!"
      (defenv ::int set?)
      (is (thrown-with-msg? Exception #"int(.|\n)*SCAR__CORE_TEST___INT" (env/init!)))
      ;; restore defenv
      (defenv ::int integer?)
      (env/init!))
    (testing "when using with-env"
      (is (thrown-with-msg? Exception #"integer(.|\n)*string(.|\n)*with-env"
                            (with-env [::string 1
                                       ::int "a"]
                              (env ::string)))))))
