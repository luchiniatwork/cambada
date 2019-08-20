(ns cambada.uberjar
  (:gen-class)
  (:require [cambada.cli :as cli]
            [cambada.jar :as jar]
            [cambada.jar-utils :as jar-utils]
            [cambada.utils :as utils]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.tools.deps.alpha :as tools.deps])
  (:import [java.io BufferedOutputStream FileOutputStream ByteArrayInputStream]
           [java.nio.file Files Paths]
           [java.util.jar Manifest JarEntry JarOutputStream]
           [java.util.regex Pattern]
           [java.util.zip ZipFile ZipOutputStream ZipEntry]
           [org.apache.commons.io.output CloseShieldOutputStream]))

(def cli-options jar/cli-options)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Merger functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn ^:private  merger-match? [[pattern] filename]
  (boolean
   (condp instance? pattern
     String (= pattern filename)
     Pattern (re-find pattern filename))))

(def ^:private default-merger
  [(fn [in out file prev]
     (when-not prev
       (.setCompressedSize file -1)
       (.putNextEntry out file)
       (io/copy (.getInputStream in file) out)
       (.closeEntry out))
     ::skip)
   (constantly nil)])

(defn ^:private make-merger [fns]
  {:pre [(sequential? fns) (= 3 (count fns)) (every? ifn? fns)]}
  (let [[read-fn merge-fn write-fn] fns]
    [(fn [in out file prev]
       (with-open [ins (.getInputStream in file)]
         (let [new (read-fn ins)]
           (if-not prev
             new
             (merge-fn new prev)))))
     (fn [out filename result]
       (.putNextEntry out (ZipEntry. filename))
       (write-fn (CloseShieldOutputStream. out) result)
       (.closeEntry out))]))

(defn ^:private map-vals
  "Like 'update', but for all values in a map."
  [m f & args]
  (zipmap (keys m) (map #(apply f % args) (vals m))))

(def ^:private skip-merger
  [(constantly ::skip)
   (constantly nil)])

(defn ^:private make-mergers [project]
  ;; TODO: this is too lein-specific and I've simply defaulted to a basic merger
  #_(into (map-vals (:uberjar-merge-with project)
                    (comp make-merger eval))
          (map #(vector % skip-merger)
               (:uberjar-exclusions project)))
  (into (map-vals {}
                  (comp make-merger eval))
        (map #(vector % skip-merger)
             [])))

(defn ^:private select-merger [mergers filename]
  (or (->> mergers (filter #(merger-match? % filename)) first second)
      default-merger))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Jar functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn ^:private warn-on-drop [filename]
  (let [non-code #".*/|project\.clj|META-INF/(MANIFEST\.MF|(NOTICE|LICENSE)(.*\.txt)?|DEPENDENCIES)"]
    (if-not (re-matches non-code filename)
      (cli/debug "  Dropping" filename))))

(defn ^:private copy-entries
  "Read entries of ZipFile `in` and apply the filename-determined entry-merging
  logic captured in `mergers`. The default merger copies entry contents directly
  to the ZipOutputStream `out` and skips subsequent same-named files. Returns
  new `merged-map` merged entry map."
  [in out mergers merged-map]
  (reduce (fn [merged-map file]
            (let [filename (.getName file), prev (get merged-map filename)]
              (if (identical? ::skip prev)
                (do (warn-on-drop filename)
                    merged-map)
                (let [[read-merge] (select-merger mergers filename)]
                  (assoc merged-map
                         filename (read-merge in out file prev))))))
          merged-map (enumeration-seq (.entries in))))

(defn ^:private include-dep [out mergers merged-map dep]
  (cli/info "  Including" (.getName dep))
  (with-open [zipfile (ZipFile. dep)]
    (copy-entries zipfile out mergers merged-map)))

(defn ^:private get-dep-jars
  [{:keys [deps-map] :as task}]
  (let [deps-aliases (map keyword (some-> task :resolve-deps (string/split #":")))
        extra-deps (when (not-empty deps-aliases) (tools.deps/combine-aliases deps-map deps-aliases))]
    (->> (tools.deps/resolve-deps deps-map extra-deps)
         (map (fn [[_ {:keys [paths]}]] paths))
         (mapcat identity))))

(defn ^:private write-components
  "Given a list of jarfiles, writes contents to a stream"
  [task jars out]
  (let [mergers (make-mergers task)
        include-dep (partial include-dep out mergers)
        merged-map (reduce include-dep {} (map io/file jars))]
    (doseq [[filename result] merged-map
            :when (not (identical? ::skip result))
            :let [[_ write] (select-merger mergers filename)]]
      (write out filename result))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Main functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn apply! [{:keys [deps deps-map out] :as task}]
  (jar/apply! task)
  (let [filename (jar-utils/get-jar-filename task {:kind :uberjar})
        jars (conj (get-dep-jars task)
                   (jar-utils/get-jar-filename task {:kind :jar}))]
    (cli/info "Creating" filename)
    (with-open [out (-> filename
                        (FileOutputStream.)
                        (ZipOutputStream.))]
      (write-components task jars out))))

(defn -main [& args]
  (let [{:keys [help] :as task} (cli/args->task args cli-options)]
    (cli/runner
     {:help? help
      :task task
      :entrypoint-main
      "cambada.uberjar"
      :entrypoint-description
      "Package up the project files and all dependencies into a jar file."
      :apply-fn apply!})))
