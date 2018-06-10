(ns cambada.clean
  (:gen-class)
  (:require [cambada.cli :as cli]
            [cambada.utils :as utils]))

(def ^:private cli-options cli/base-cli-options)

(defn apply! [{:keys [out] :as task}]
  (cli/info "Cleaning" out)
  (utils/delete-file-recursively out true))

(defn -main [& args]
  (let [{:keys [help] :as task} (cli/args->task args cli-options)]
    (cli/runner
     {:help? help
      :task task
      :entrypoint-main
      "cambada.clean"
      :entrypoint-description
      "Cleans the target directory."
      :apply-fn apply!})))
