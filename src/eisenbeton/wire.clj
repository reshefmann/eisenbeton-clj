(ns eisenbeton.wire
  (:import [com.google.flatbuffers FlatBufferBuilder]
           [eisenbeton.wire.request EisenRequest]
           [eisenbeton.wire.response EisenResponse Header]))

(set! *warn-on-reflection* true)


(defn open-eisen-request
  [flatbuff-data]
  (let [buf (java.nio.ByteBuffer/wrap flatbuff-data)
        ^EisenRequest req (EisenRequest/getRootAsEisenRequest buf)]
    {:request req
     :uri (.uri req)
     :path (.path req)
     :method (.method req)
     :content-type (.contentType req)
     :content (.contentAsByteBuffer req)}))

(defn open-eisen-response
  [flatbuff-data]
  (let [buf (java.nio.ByteBuffer/wrap flatbuff-data)
        ^EisenResponse resp (EisenResponse/getRootAsEisenResponse buf)]
    {:response resp
     :status (.status resp)
     :headers (into {} 
                    (for [i (range (.headersLength resp)) :let [h (.headers resp i)]]
                      [(.key h) (.value h)]))
     :content (.contentAsByteBuffer resp)}))

(defn build-eisen-response
  [{:response/keys [status headers content]}]
  (let [builder (FlatBufferBuilder. 1024)
        headers (EisenResponse/createHeadersVector 
                  builder
                  (into-array Integer/TYPE
                              (for [[k v] headers] 
                                (do
                                  (Header/createHeader builder (.createString builder ^String k) (.createString builder ^String v))))))
        content (EisenResponse/createContentVector builder ^bytes content)
        builder (doto builder
                  (EisenResponse/startEisenResponse)
                  (EisenResponse/addStatus status)
                  (EisenResponse/addHeaders headers)
                  (EisenResponse/addContent content)
                  (EisenResponse/finishEisenResponseBuffer (EisenResponse/endEisenResponse builder)))]
    (.sizedByteArray builder)))



(defn build-eisen-request
  [{:request/keys [uri path method content-type content]}]
  (let [builder (FlatBufferBuilder. 1024)
        uri (.createString builder ^String uri)
        path (.createString builder ^String path)
        method (.createString builder ^String method)
        content-type (.createString builder ^String content-type)
        content (EisenRequest/createContentVector builder ^bytes content)
        builder (doto builder
                  (EisenRequest/startEisenRequest)
                  (EisenRequest/addUri uri)
                  (EisenRequest/addPath path)
                  (EisenRequest/addMethod method)
                  (EisenRequest/addContentType content-type)
                  (EisenRequest/addContent content)
                  (EisenRequest/finishEisenRequestBuffer (EisenRequest/endEisenRequest builder)))]
    (.sizedByteArray builder)))





(comment
  (defn reflect [o] (->> o clojure.reflect/reflect :members (map :name))) 

  (require '[byte-streams :as bs])


  (def test-resp (build-eisen-response #:response{:status 200 :content (byte-array [67 66 65]) :headers [["Content-Type" "text/plain"]]}))
  (def resp (open-eisen-response test-resp)) 
  (reflect (:response resp ))
  
  
  (-> resp :response (.headers 0) (.value))
  (def mm (build-eisen-request #:request{:uri "abb" :path "hahha" :method "GET" :content-type "text/plain" :content (byte-array [65 66 67])})) 
  ( -> (extract-flatbuff-msg mm) :content bs/to-string)


  )

