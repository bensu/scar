(ns scar.core
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.spec :as s]))

(defn- keywordize [s]
  (-> (str/lower-case s)
      (str/replace "_" "-")
      (str/replace "." "-")
      (keyword)))

(defn- sanitize-key [k]
  (let [s (keywordize (name k))]
    (if-not (= k s) (println "Warning: environ key" k "has been corrected to" s))
    s))

(defn- sanitize-val [k v]
  (if (string? v)
    v
    (do (println "Warning: environ value" (pr-str v) "for key" k "has been cast to string")
        (str v))))

(defonce env-specs (atom #{}))

(defn- maybe-keyword [s]
  (try
    (keyword s)
    (catch Throwable _ nil)))

(defn- maybe-read-edn [s]
  (try
    (edn/read-string s)
    (catch Throwable _ s)))

(defn- read-value [spec str]
  (if-not (s/invalid? (s/conform spec str))
    str
    (maybe-read-edn str)))

;; maybe (str/replace #"." "-") for Java System properties
(defn keywordize [s]
  (-> s
      str/lower-case
      (str/replace #"___" "/")
      (str/replace #"__" ".")
      (str/replace #"_" "-")
      maybe-keyword))

(defn- validate-spec [k v]
  (when (s/invalid? (s/conform k v))
    (s/explain-str k v)))

(defn- validate-spec! [k v]
  (some-> (validate-spec k v)
          (ex-info {:key k :val v})
          throw ))

(defn- read-configs [source]
  (->> source
       (map (fn [[k v]]
              (let [k' (keywordize k)]
                (when-let [spec (get @env-specs k')]
                  [k' (read-value spec v)]))))
       (into {})))

(defn- read-env-file [f]
  (when-let [env-file (io/file f)]
    (when (.exists env-file)
      (edn/read-string (slurp env-file)))))

(defn- read-main-file []
  (some-> (System/getenv "SCAR__CORE___FILE")
          io/resource
          read-env-file))

;; ======================================================================
;; API

(s/fdef defenv
  :args (s/and #((every-pred pos? even?) (count %))
               #(every? keyword? (take-nth 2 %))))

(defmacro defenv [& specs]
  `(do
     ~@(map (fn [[k# s#]]
              `(do
                 (swap! env-specs #(conj % ~k#))
                 (s/def ~k# ~s#)))
            (partition 2 specs))))

(defonce env-map (atom {}))

(def ^:dynamic *temp-env* {})

(defn env
  ([k] (env k nil))
  ([k default] (or (get *temp-env* k)
                   (get @env-map k)
                   default)))

(defn validate! []
  (when-let [errors (->> @env-specs
                         (map #(when-let [error (validate-spec % (env %))]
                                 {:key %
                                  :value (env %)
                                  :error error}))
                         (remove nil?)
                         seq)]
    (let [msg (format "The following envs didn't conform to their expected values:\n\n\t%s\n"
                      (str/join "\n\t" (map :error errors)))]
      (throw (ex-info msg {:errors errors})))))

;; TODO: add meta for source name for nice exception messages
(defn load-env! [source]
  (swap! env-map #(merge % source))
  (validate!))

(defn init! []
  (load-env! (merge (read-env-file ".lein-env")
                    (read-env-file (io/resource ".boot-env"))
                    (read-main-file)
                    (read-configs (System/getenv))
                    (read-configs (System/getProperties)))))


(s/fdef with-env
  :args (s/cat :bindings (s/and vector? #(even? (count %))
                                (s/* (s/or :keyword keyword? :value any?)))
               :body (s/* any?))
  :ret any?)

(defmacro with-env [bindings & body]
  `(binding [*temp-env* (merge *temp-env* ~(into {} (map vec (partition 2 bindings))))]
     (validate!)
     ~@body))
