(ns cambada.compile
  (:gen-class)
  (:require [cambada.clean :as clean]
            [cambada.cli :as cli]
            [cambada.utils :as utils]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.tools.namespace.find :as ns.find]))

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

(defn apply! [{:keys [deps-map out aot] :as task}]
  (clean/apply! task)
  (let [target (utils/compiled-classes-path out)
        aot-ns (aot-namespaces task)]
    (utils/mkdirs target)
    (cli/info "Creating" target)
    (binding [*compile-path* target]
      (doseq [ns aot-ns]
        (cli/info "  Compiling" ns)
        (compile ns)))))

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
