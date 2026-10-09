(ns app
  "The whole app. Datastar answers a request in one of three ways:
     text/html         -> patch the elements (matched by id)
     application/json  -> patch the signals
     text/event-stream -> do either one, repeatedly, on a live connection"
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.core.protocols :refer [StreamableResponseBody]]
            [ring.middleware.params :refer [wrap-params]])
  (:import [java.time LocalTime]
           [java.time.format DateTimeFormatter]))

;; A plugin's key comes after a colon: data-on:click, NOT data-on-click.
;; Hyphenated misspellings are silently ignored. Keyless plugins (data-text,
;; data-init) have no colon.
(def page
  "<!doctype html><title>Datastar + Ring</title>
<script type=module src=https://cdn.jsdelivr.net/gh/starfederation/datastar@v1.0.4/bundles/datastar.js></script>
<body data-signals:count=0 data-init=\"@get('/clock')\">
<h1>Datastar + Ring</h1>
<p>Server clock: <b id=clock>connecting&hellip;</b></p>
<button data-on:click=\"@post('/inc')\">clicked <span data-text=$count></span>&times;</button>")

(defn signals
  "Datastar sends the page's signals with every request:
   ?datastar=<json> on a GET, a JSON body on anything else."
  [{:keys [params body]}]
  (json/read-str (or (params "datastar") (slurp body)) :key-fn keyword))

(defn sse
  "Hands `f` a `send!` that writes one Datastar event: (send! :elements \"<b id=x>hi</b>\")"
  [f]
  {:status 200
   :headers {"Content-Type" "text/event-stream" "Cache-Control" "no-cache"}
   :body (reify StreamableResponseBody
           (write-body-to-stream [_ _ out]
             (with-open [w (io/writer out)]
               (try (f (fn [kind data]
                         (.write w (format "event: datastar-patch-%s\ndata: %s %s\n\n"
                                           (name kind) (name kind) data))
                         (.flush w)))
                    (catch Exception _ :client-went-away)))))})

(defn handler [{:keys [uri] :as req}]
  (case uri
    "/"      {:status 200 :headers {"Content-Type" "text/html"} :body page}
    "/inc"   {:status 200 :headers {"Content-Type" "application/json"}
              :body (json/write-str {:count (inc (:count (signals req)))})}
    "/clock" (sse (fn [send!]
                    (while true
                      (send! :elements (str "<b id=clock>"
                                            (.format (LocalTime/now)
                                                     (DateTimeFormatter/ofPattern "HH:mm:ss"))
                                            "</b>"))
                      (Thread/sleep 1000))))
    {:status 404 :body "not found"}))

(defn -main [& _]
  (println "http://localhost:8080")
  (run-jetty (wrap-params handler) {:port 8080 :join? true}))
