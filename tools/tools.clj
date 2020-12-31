(ns tools
  (:require [clojure.java.shell :as sh]))



(defn flatc
  [args]
  (let [commands [["flatc" "-o" "java-src" "--java" "../eisenbeton-go/flatbuff/request.fbs"]
                  ["flatc" "-o" "java-src" "--java" "../eisenbeton-go/flatbuff/response.fbs"]
                  ["javac" "-cp" (str (System/getenv "HOME") "/.m2/repository/com/google/flatbuffers/flatbuffers-java/1.12.0/flatbuffers-java-1.12.0.jar") "-d" "classes" "java-src/eisenbeton/wire/response/Header.java" "java-src/eisenbeton/wire/response/EisenResponse.java"]
                  ["javac" "-cp" (str (System/getenv "HOME") "/.m2/repository/com/google/flatbuffers/flatbuffers-java/1.12.0/flatbuffers-java-1.12.0.jar") "-d" "classes" "java-src/eisenbeton/wire/request/EisenRequest.java"]]]

    (doseq [cmd commands]
      (println "Executing " cmd)
      (->> cmd (apply sh/sh) :err println)))
  (System/exit 0))


