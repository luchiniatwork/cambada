(ns cambada.native-image
  (:require [cambada.cli :as cli]
            [cambada.compile :as compile]
            [cambada.jar-utils :as jar-utils]
            [cambada.utils :as utils]
            [clojure.java.io :as io]
            [clojure.java.shell :as shell]
            [clojure.tools.deps.alpha :as tools.deps]
            [clojure.string :as string])
  (:import
   java.io.File
   [java.nio.file Files Paths]
   java.nio.file.attribute.FileAttribute))

(def cli-options
  (concat [["-m" "--main NS_NAME" "The namespace with the -main function"]

           [nil "--image-name" "The name of the image to be created"
            :default (utils/directory-name)]

           [nil "--graalvm-home PATH" "Path of the GraalVM home (defaults to GRAALVM_HOME)"
            :default (System/getenv "GRAALVM_HOME")]

           ["-O" "--graalvm-opt OPT" "Opt to the GraalVM compiler. Can be specified multiple times"
            :default []
            :default-desc ""
            :assoc-fn (fn [m k v]
                        (let [opts (get m k)]
                          (assoc m k (conj opts v))))]]

          compile/cli-options))

(defn ^:private make-classpath
  [{:keys [deps-map out] :as task}]
  (tools.deps/make-classpath
   (tools.deps/resolve-deps deps-map nil)
   (conj (:paths deps-map) (utils/compiled-classes-path out))
   {:extra-paths (:extra-paths deps-map)}))

(defn ^:private graalvm-opts [coll-from-task]
  (map #(str "-" %) coll-from-task))

(defn ^:private shell-native-image
  [bin all-args]
  (let [{:keys [out err]} (apply shell/sh bin all-args)]
    (some-> err not-empty cli/abort)
    (some-> out not-empty cli/info)))

(defn ^:private build-native-image
  [{:keys [main graalvm-opt] :as task} bin image-file]
  (let [cp (make-classpath task)
        base-args ["--no-server"
                   "-cp" cp
                   "-H:+ReportUnsupportedElementsAtRuntime"
                   (format "-H:Name=%s" image-file)]
        all-args (cond-> base-args
                   graalvm-opt (concat (graalvm-opts graalvm-opt))
                   true vec
                   :always (conj main))]
    (shell-native-image bin all-args)))

(defn get-native-image-bin [graalvm-home]
  (let [out (io/file graalvm-home "bin/native-image")]
    (if-not (.exists out)
      (cli/abort (->> ["Can't find GraalVM's native-image."
                       "Make sure it's installed and --graalvm-home is used correctly."]
                      (string/join " ")))
      (.getAbsolutePath out))))

(defn apply! [{:keys [graalvm-home out image-name] :as task}]
  (compile/apply! task)
  (let [bin (get-native-image-bin graalvm-home)
        image-file (.getPath (io/file out image-name))]
    (cli/info "Creating" image-file)
    (build-native-image task bin image-file)))

(defn -main [& args]
  (let [{:keys [help] :as task} (cli/args->task args cli-options)]
    (cli/runner
     {:help? help
      :task task
      :entrypoint-main
      "cambada.native-image"
      :entrypoint-description
      "Uses GraalVM's native-image build to generate a self-hosted image."
      :apply-fn apply!})))
