(ns scar.core
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.spec :as s]))

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
  {:pre [(string? s)]}
  (-> s
      str/lower-case
      (str/replace #"___" "/")
      (str/replace #"__" ".")
      (str/replace #"_" "-")
      maybe-keyword))

(defn ->envar [k]
  {:pre [(keyword? k)]}
  (-> k
      str
      (str/replace #":" "")
      (str/replace #"-" "_")
      (str/replace #"\." "__")
      (str/replace #"\/" "___")
      str/upper-case))

(defn- validate-spec [k v]
  (when (s/invalid? (s/conform k v))
    (s/explain-str k v)))

(defonce env-specs (atom #{}))

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

(defn- main-file-name []
  (System/getenv "SCAR__CORE___FILE"))

(defn- read-main-file []
  (some-> (main-file-name)
          io/resource
          read-env-file))

(defonce env-map (atom {}))

(defonce env-sources (atom {}))

(def ^:dynamic *temp-env* {})

(def ^:dynamic *temp-sources* {})

;; ======================================================================
;; API

(defn require!
  "States that kw needs to be present at runtime conforming to a spec.
  Assumes that the spec was registered elsewhere. Useful when you don't want to complect a
  spec with the name of a config.

  Ex: we have one spec for db urls that we want to reuse for several db instances

  (s/def ::db.core/db-spec string?) ;; we have one spec for db urls that we want to reuse

  (s/def :db.logs/url :db.core/db-spec) ;; we associate it to one instance

  (s/def :db.main/url :db.core/db-spec) ;; we associate it to another instance

  ;; we require those instances for the environment
  (env/require! :db.logs/url)
  (env/require! :db.main/url)"
  [kw]
  {:pre [(keyword? kw)]}
  (swap! env-specs #(conj % kw)))

(s/fdef defenv
  :args (s/and #((every-pred pos? even?) (count %))
               #(every? keyword? (take-nth 2 %))))

(defmacro defenv [& specs]
  `(do
     ~@(map (fn [[k# s#]]
              `(do
                 (s/def ~k# ~s#)
                 (require! ~k#)))
            (partition 2 specs))))

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
                                  :error error
                                  :source (let [source (or (get *temp-sources* %)
                                                           (get @env-sources %))]
                                            (if (= ::envar source)
                                              (->envar %)
                                              source))}))
                         (remove nil?)
                         seq)]
    (let [msg (format "The following envs didn't conform to their expected values:\n\n\t%s\n"
                      (->> errors
                           (map #(format "%s\tfrom: %s\n" (:error %) (:source %)))
                           (str/join "\n\t")))]
      (throw (ex-info msg {:errors errors})))))

(defn add-source! [source-name configs]
  (swap! env-sources #(merge % (into {} (map (fn [[k _]] [k source-name]) configs))))
  (swap! env-map #(merge % configs)))

(defn init! []
  (add-source! ".lein-env" (read-env-file ".lein-env"))
  (add-source! ".boot-env" (read-env-file (io/resource ".boot-env")))
  (add-source! (main-file-name) (read-main-file))
  (add-source! ::envar (read-configs (System/getenv)))
  (add-source! "java.properties" (read-configs (System/getProperties)))
  (validate!))

(s/fdef with-env
  :args (s/cat :bindings (s/and vector? #(even? (count %))
                                (s/* (s/or :keyword keyword? :value any?)))
               :body (s/* any?))
  :ret any?)

(defmacro with-env [bindings & body]
  `(let [new-env# ~(into {} (map vec (partition 2 bindings)))
         new-keys# (keys new-env#)]
     (binding [*temp-env* (merge *temp-env* new-env#)
               *temp-sources* (->> (map vector new-keys# (repeat "with-env"))
                                   (into {})
                                   (merge *temp-sources*))]
       (validate!)
       ~@body)))
