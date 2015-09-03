(ns clj-btc-e-api.core
  (:require [org.httpkit.client :as http]
            [clojure.data.json :as js]
            [pandect.algo.sha512 :as sign]))

(def ^:dynamic *default-connection-options* {:keepalive 30000})

(defrecord Settings [url public-api trade-api])

(defn- new-settings []
  (->Settings "https://btc-e.com"
                   "api/2"
                   "tapi"))

(defrecord Account [key secret])

(defn- new-account [key secret]
  (->Account key secret))

(defrecord Api [account settings nonce-fn])

(defn default-nonce
  "shft: 0 - millis, 3 - seconds"
  [shft]
  #(mod (bit-shift-right (System/currentTimeMillis) shft) 4294967295))

(defn atom-nonce
  "val - initial value of nonce"
  [val]
  (let [n (atom val)]
    (fn []
      (swap! n inc))))

(defn new-api
  ([]
   (new-api nil nil nil))
  ([key secret]
   (new-api key secret (default-nonce 2)))
  ([key secret nonce-fn]
   (->Api (new-account key secret)
          (new-settings)
          nonce-fn)))

(def ^:dynamic *default-api* (new-api))

(defn- flatjoin [coll]
  (-> coll
      flatten
      clojure.string/join))

(defn- post-string [post-data]
  (#'org.httpkit.client/query-string post-data))

(defn- base-url [settings api-type]
  (flatjoin
   (interpose "/"
              ((juxt :url api-type) settings))))

(defn- clojure->btce [something]
  (if (or (keyword? something) (symbol? something))
    (flatjoin (replace {\- \_} (name something)))
    something))

(defn- public-url-path [trade-pair rtype]
  (flatjoin (map (comp (partial replace {\- \_}) name)
                 [trade-pair "/" rtype])))

(defn- public-url [settings trade-pair rtype]
  (str (base-url settings :public-api)
       "/"
       (public-url-path trade-pair rtype)))

(defn- trade-url [settings]
  (base-url settings :trade-api))

(defn- parse-body [response]
  (future
    (try
      (-> @response
          :body
          (js/read-str :key-fn keyword))
      (catch Exception e {:error e}))))

(defn- request [opts]
  (parse-body (http/request (merge opts *default-connection-options*))))

(defn- request-get [url]
  (request {:url url
            :method :get}))

(defn- request-post [url headers post-data]
  (request {:url url
            :method :post
            :headers (assoc headers "Content-Type" "application/x-www-form-urlencoded")
            :body post-data}))

(defn- request-public [settings trade-pair rtype]
  (request-get (public-url settings trade-pair rtype)))

(defn- request-trade [account settings params]
  (let [{:keys [key secret]} account
        url (trade-url settings)
        post-data (post-string params)
        headers {"Key" key
                 "Sign" (sign/sha512-hmac post-data secret)}]
    (request-post url
                  headers
                  post-data)))

(defn get-public [api trade-pair method]
  (request-public (:settings api) trade-pair method))

(defmacro defnpub [rtype]
  `(defn ~(symbol (clojure.string/join `("get-" ~(name rtype))))
     ~'[trade-pair]
     (get-public *default-api* ~'trade-pair ~rtype)))

(defnpub :fee)
(defnpub :trades)
(defnpub :depth)
(defnpub :ticker)

(defn trade
  "api - api data,
  type - trade api method name (Can be string, keyword and symbol).
  params - trade api method parameters.
  Dashes will be converted to underscores."
  [api type & params]
  (request-trade (:account api)
                 (:settings api)
                 (assoc (when hash-map (apply hash-map (map clojure->btce params)))
                        :nonce ((:nonce-fn api))
                        :method (name type))))


