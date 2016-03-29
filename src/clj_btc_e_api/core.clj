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

(defrecord Stock [account settings nonce-fn])

(defn default-nonce
  "Returns function that generates nonce.
  This nonce generator use time in millis to generate nonce.
  shft is a parameter that selects the interval of new nonce.
  shft = 0 - each millisecond, 3 - each second e.t.c."
  [shft]
  #(mod (bit-shift-right (System/currentTimeMillis) shft) 4294967295))

(defn atom-nonce
  "Returns function that generates nonce.
  Each call will generate incremental nonce.
  val - initial value of nonce."
  [val]
  (let [n (atom val)]
    (fn []
      (swap! n inc))))

(defn new-stock
  "key - your key from btc-e.com
  secret - your secret from btc-e.com
  nonce-fn - 0-arity function that returns new incremental values each call.
  You can use default-nonce and atom-nonce to create nonce-fn."
  ([]
   (new-stock nil nil nil))
  ([key secret]
   (new-stock key secret (default-nonce 2)))
  ([key secret nonce-fn]
   (->Stock (new-account key secret)
            (new-settings)
            nonce-fn)))

(def ^:dynamic *default-stock* (new-stock))

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

(defn get-public
  "Get public info.
  stock - create it with new-stock.
  trade-pair - a currency trade pair (string symbol keyword).
  method - one of :fee :depth :ticker :trades.
  Returns a future with json response already converted to map with keywords as keys."
  [stock trade-pair method]
  (request-public (:settings stock) trade-pair method))

(defmacro defnpub [rtype]
  `(defn ~(symbol (clojure.string/join `("get-" ~(name rtype))))
     ~(str "Request " (name rtype) " with public API." \newline
           "Argument trade-pair can be string, symbol or keyword (ex. :btc-usd)." \newline
           "Dashes in keywords and symbold will be converted to underscores." \newline
           "Returns a future with json response already converted to map with "
           "keywords as keys.")
     ~'[trade-pair]
     (get-public *default-stock* ~'trade-pair ~rtype)))

(defnpub :fee)
(defnpub :trades)
(defnpub :depth)
(defnpub :ticker)

(defn trade
  "Calls trade API.
  stock - create it with new-api,
  type - trade api method name (Can be string, keyword and symbol).
  params - trade api method parameters. Can be any type.
  Symbols and keys will be converted to strings.
  Dashes in symbols and keys will be converted to underscores.
  Returns a future with json response already converted to map with keywords as keys."
  [stock type & params]
  (request-trade (:account stock)
                 (:settings stock)
                 (assoc (when hash-map (apply hash-map (map clojure->btce params)))
                        :nonce ((:nonce-fn stock))
                        :method (name type))))


