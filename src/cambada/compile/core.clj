(ns cambada.compile.core
  (:refer-clojure :exclude [compile])
  (:require [cambada.cli :as cli])
  (:import [java.nio.file Path Paths Files]
           [java.nio.file.attribute FileAttribute]
           [java.io File]
           [java.net URL URI URLClassLoader]))

(defn- do-compile
  ([namespaces]
   (do-compile namespaces nil))
  ([namespaces {:keys [compile-path compiler-options]}]
   (let [namespaces (if (coll? namespaces)
                      namespaces
                      [namespaces])
         compile-path (or compile-path "target/classes")
         compile-path (if (string? compile-path)
                        (Paths/get compile-path (make-array String 0))
                        compile-path)]
     (Files/createDirectories compile-path (make-array FileAttribute 0))
     (binding [*compile-path* (str compile-path)
               *compiler-options* (or compiler-options *compiler-options*)]
       (doseq [namespace namespaces]
         (cli/info "  Compiling" namespace)
         (clojure.core/compile namespace))))))

(defn classpath->paths [classpath]
  (when classpath
    (for [path (-> classpath
                   clojure.string/trim
                   (.split File/pathSeparator))]
      (Paths/get path (make-array String 0)))))

(defn paths->urls [paths]
  (->> paths
       (map #(.toUri ^Path %))
       (map #(.toURL ^URI %))))

(defn -main [namespaces options]
  (let [namespaces (read-string namespaces)
        options (read-string options)]
    (do-compile namespaces options)
    (clojure.core/shutdown-agents)))
