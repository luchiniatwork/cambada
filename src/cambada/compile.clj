(ns cambada.compile
  (:gen-class)
  (:refer-clojure :exclude [compile])
  (:require [cambada.clean :as clean]
            [cambada.cli :as cli]
            [cambada.utils :as utils]
            [cambada.compile.core :as compile]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.tools.namespace.find :as ns.find])
  (:import [java.nio.file Path Paths Files]
           [java.nio.file.attribute FileAttribute]
           [java.io File]
           [java.net URL URI URLClassLoader]))

(def cli-options
  (concat [["-a" "--aot NS_NAMES" "Namespaces to be AOT-compiled or `all` (default)"
            :default ['all]
            :default-desc "all"
            :parse-fn #(as-> % $
                         (string/split $ #":")
                         (map symbol $))]]
          cli/base-cli-options))

(defn ^:private aot-namespaces
  [{:keys [aot deps-map] :as task}]
  (if (= (first aot) 'all)
    (->> (:paths deps-map)
         (map io/file)
         (map ns.find/find-namespaces-in-dir)
         flatten)
    aot))

(defn apply! [{:keys [deps-map out] :as task}]
  (clean/apply! task)
  (let [target (utils/compiled-classes-path out)
        aot-ns (aot-namespaces task)]
    (utils/mkdirs target)
    (cli/info "Creating" target)
    (let [options *compiler-options*
          classpath (System/getProperty "java.class.path")
          classpath-urls (->> classpath compile/classpath->paths compile/paths->urls (into-array URL))
          classloader (URLClassLoader. classpath-urls
                                       (.getParent (ClassLoader/getSystemClassLoader)))
          main-class (.loadClass classloader "clojure.main")
          main-method (.getMethod
                       main-class "main"
                       (into-array Class [(Class/forName "[Ljava.lang.String;")]))
          t (Thread. (fn []
                       (.setContextClassLoader (Thread/currentThread) classloader)
                       (.invoke
                        main-method
                        nil
                        (into-array
                         Object [(into-array String ["--main"
                                                     "cambada.compile.core"
                                                     (pr-str aot-ns)
                                                     (pr-str options)])]))))]
      (.start t)
      (.join t)
      (.close classloader))))

(defn -main [& args]
  (let [{:keys [help] :as task} (cli/args->task args cli-options)]
    (cli/runner
     {:help? help
      :task task
      :entrypoint-main
      "cambada.compile"
      :entrypoint-description
      "Compiles the specified namespaces into a set of classfiles."
      :apply-fn apply!})))
