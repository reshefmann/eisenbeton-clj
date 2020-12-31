(ns eisenbeton.core
  (:require [com.appsflyer.donkey.core :refer [create-donkey create-server]]
            [com.appsflyer.donkey.server :refer [start]]
            [com.appsflyer.donkey.result :refer [on-success]]
            [aero.core :refer [read-config]]
            [iapetos.core :as prometheus]
            [iapetos.collector.ring :as pring]
            [byte-streams :as bs]
            [eisenbeton.wire :as wire])
  (:import [io.nats.client Connection Message MessageHandler]
           [java.util.concurrent CompletableFuture]
           [org.slf4j Logger LoggerFactory]))

(set! *warn-on-reflection* true)


(defonce ^Logger logger (LoggerFactory/getLogger (str *ns*)))



(Thread/setDefaultUncaughtExceptionHandler
 (reify Thread$UncaughtExceptionHandler
   (uncaughtException [_ thread ex]
     (.error logger (str "Uncaught exception on" (.getName thread)) ex))))



(defn request-url
  "Return the full URL of the request."
  [request]
  (str (-> request :scheme name)
       "://"
       (get-in request [:headers "host"])
       (:uri request)
       (if-let [query (:query-string request)]
         (str "?" query))))

(defn handle-completion
  "Handles the completion of a completable future.
  cf - The CompletableFuture
  on-success-cb - The callback to call upon success
  on-error-cb - The callback to call upon failure"
  [^CompletableFuture cf
   timeout
   on-success-cb
   on-error-cb]
  (-> cf
      (.orTimeout (.toMillis ^java.time.Duration timeout) java.util.concurrent.TimeUnit/MILLISECONDS) 
      (.whenComplete 
        (reify java.util.function.BiConsumer
          (^void accept [this res ex]
            (if (nil? ex)
              (on-success-cb res)
              (do 
                (.error logger "nats.io communication error" ex)
                (on-error-cb ex)))))))) 


(defn send-request-to-nats
  [nats-conn 
   subject
   ^bytes request
   timeout
   on-success-cb
   on-error-cb]
  (let [resp-future (.request ^Connection nats-conn subject request)]
    (handle-completion 
      resp-future
      timeout
      on-success-cb
      on-error-cb)))


(defn eisen-response-to-ring
  [^Message eisen-resp]
  (let [{:keys [content] :as resp-map} (-> eisen-resp (.getData) wire/open-eisen-response)
        resp-map                       (assoc resp-map :body (bs/to-input-stream content))
        resp-map                       (dissoc resp-map :response :content)]
    resp-map))

(defn make-donkey-handler
  [nats-conn nats-timeout]
  (fn [request respond raise]
    (send-request-to-nats
      nats-conn
      (:uri request)
      (wire/build-eisen-request #:request{:uri (request-url request)
                                          :path (:uri request)
                                          :method (-> request :request-method name clojure.string/upper-case)
                                          :content-type (-> request :headers (get "content-type"))
                                          :content (:body request)})
      nats-timeout
      (fn [res]
        (respond (eisen-response-to-ring res)))
      (fn [ex]
        (respond {:status 200})))))


(defn server 
  [donkey nats-conn metrics config]
  (let [donkey-server (create-server 
                        donkey
                        {:bind "0.0.0.0"
                         :idle-timeout-seconds (-> config :nats-reply-timeout-ms (+ 1000) java.time.Duration/ofMillis .toSeconds)
                         :port (read-string (:http-port config))
                         :routes [{:handler (fn [req] (pring/metrics-response metrics))
                                   :handler-mode :blocking
                                   :path "/metrics"}
                                  {:handler (make-donkey-handler nats-conn (-> config :nats-reply-timeout-ms java.time.Duration/ofMillis))}]})]
    (-> donkey-server 
        start 
        (on-success (fn [_] (.info logger "started"))))
    donkey-server))


(defn start-eisenbeton
  "Starts the eisenbeton server"
  [args]
  (let [
        config (read-config "config.edn")
        metric-reg (com.codahale.metrics.MetricRegistry.)
        promethues-reg (-> (prometheus/collector-registry)
                           (prometheus/register 
                             (io.prometheus.client.dropwizard.DropwizardExports. metric-reg)))
        nats-conn (io.nats.client.Nats/connect ^String (:nats-uri config))
        donkey-core (create-donkey {:metric-registry metric-reg})]
    (server 
      donkey-core
      nats-conn
      promethues-reg
      config))
  )








