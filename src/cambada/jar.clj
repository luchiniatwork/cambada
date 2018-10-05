(ns cambada.jar
  (:gen-class)
  (:require [cambada.cli :as cli]
            [cambada.compile :as compile]
            [cambada.jar-utils :as jar-utils]
            [cambada.utils :as utils]
            [clojure.data.xml :as xml]
            [clojure.data.xml.event :as event]
            [clojure.data.xml.tree :as tree]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.tools.deps.alpha.gen.pom :as gen.pom]
            [clojure.zip :as zip])
  (:import [clojure.data.xml.node Element]
           [java.io Reader BufferedOutputStream FileOutputStream ByteArrayInputStream]
           [java.util.jar Manifest JarEntry JarOutputStream]))

(xml/alias-uri 'pom "http://maven.apache.org/POM/4.0.0")

(def cli-options
  (concat [["-m" "--main NS_NAME" "The namespace with the -main function"]

           [nil "--app-group-id STRING" "Application Maven group ID"
            :default (utils/directory-name)]

           [nil "--app-artifact-id STRING" "Application Maven artifact ID"
            :default (utils/directory-name)]

           [nil "--app-version STRING" "Application version"
            :default "1.0.0-SNAPSHOT"]

           [nil "--[no-]copy-source" "Copy source files by default"
            :default true]]

          compile/cli-options))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Information functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn ^:private skip-file?
  "Skips the file if it doesn't exist. If the file is not the
  root-file (specified by :path), will also skip it if it is a dotfile, emacs
  backup file or matches an exclusion pattern."
  [file relative-path root-file exclusion-patterns inclusion-patterns]
  (or (not (.exists file))
      (and
       (not= file root-file)
       (not (some #(re-find % relative-path) inclusion-patterns))
       (or
        (re-find #"^\.?#" (.getName file))
        (re-find #"~$" (.getName file))
        (some #(re-find % relative-path) exclusion-patterns)))))

(defn- added-file?
  "Returns true if the file is already added to the jar, false otherwise. Prints
  a warning if the file is not a directory."
  [file relative-path added-paths]
  ;; Path may be blank if it is the root path
  (if (or (string/blank? relative-path) (added-paths relative-path))
    (do
      (when-not (.isDirectory file)
        (cli/info "Warning: skipped duplicate file:" relative-path))
      true)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Manifest functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- default-manifest [{:keys [app-group-id
                                 app-artifact-id
                                 app-version
                                 main] :as task}]
  (cond-> {"Created-By" (str "Cambada version TBD")
           "Built-By" (System/getProperty "user.name")
           "Build-Jdk" (System/getProperty "java.version")
           "Cambada-Project-ArtifactId" app-artifact-id
           "Cambada-Project-GroupId" app-group-id
           "Cambada-Project-Version" app-version}
    (not (nil? main)) (assoc "Main-Class" main)))

(defn ^:private make-manifest-bytes [{:keys [main] :as task}]
  (->> (default-manifest task)
       (cons ["Manifest-Version" "1.0"]) ;; Manifest-Version line must be first
       (map (fn [[k v]] (str k ": " v)))
       (string/join "\n")
       ((fn [x] (str x "\n"))) ;; Must have an extra \n at the end
       .getBytes))

(defn ^:private make-manifest [{:keys [main] :as task}]
  (->> (make-manifest-bytes task)
       ByteArrayInputStream.
       Manifest.))


(defn make-project-properties
  [{:keys [app-group-id app-artifact-id app-version] :as task}]
  (with-open [baos (java.io.ByteArrayOutputStream.)]
    (let [properties (doto (java.util.Properties.)
                       (.setProperty "version" app-version)
                       (.setProperty "groupId" app-group-id)
                       (.setProperty "artifactId" app-artifact-id))]
      (when-let [revision (utils/read-git-head)]
        (.setProperty properties "revision" revision))
      (.store properties baos "Cambada"))
    (str baos)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Jar proper functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn ^:private put-jar-entry!
  "Adds a jar entry to the Jar output stream."
  [jar-os file path]
  (.putNextEntry jar-os (doto (JarEntry. path)
                          (.setTime (.lastModified file))))
  (when-not (.isDirectory file)
    (io/copy file jar-os)))

(defmulti ^:private copy-to-jar (fn [project jar-os acc spec] (:type spec)))

(defmethod copy-to-jar :path [project jar-os acc spec]
  (let [root-file (io/file (:path spec))
        root-dir-path (utils/unix-path (utils/dir-string root-file))
        paths (for [child (file-seq root-file)
                    :let [path (utils/relativize-path
                                (utils/full-path child (utils/unix-path (str child)))
                                root-dir-path)]]
                (when-not (or (skip-file? child path root-file
                                          (:jar-exclusions project)
                                          (:jar-inclusions project))
                              (added-file? child path acc))
                  (put-jar-entry! jar-os child path)
                  path))]
    (into acc paths)))

(defmethod copy-to-jar :paths [project jar-os acc spec]
  (reduce (partial copy-to-jar project jar-os) acc
          (for [path (:paths spec)]
            {:type :path :path path})))

(defmethod copy-to-jar :bytes [project jar-os acc spec]
  (let [path (utils/unix-path (:path spec))]
    (when-not (some #(re-find % path) (:jar-exclusions project))
      (.putNextEntry jar-os (JarEntry. path))
      (let [bytes (if (string? (:bytes spec))
                    (.getBytes (:bytes spec))
                    (:bytes spec))]
        (io/copy (ByteArrayInputStream. bytes) jar-os)))
    (conj acc path)))

(defmethod copy-to-jar :fn [project jar-os acc spec]
  (let [f (eval (:fn spec))
        dynamic-spec (f project)]
    (copy-to-jar project jar-os acc dynamic-spec)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Filespec
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- filespecs [{{:keys [paths extra-paths]} :deps-map
                   :keys [out copy-source] :as task}]
  (concat [{:type :path :path (utils/compiled-classes-path out)}
           {:type :paths :paths extra-paths}]
          (if copy-source
            [{:type :paths
              :paths paths}])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; POM update functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn ^:private make-xml-element
  [{:keys [tag attrs] :as node} children]
  (with-meta
    (apply xml/element tag attrs children)
    (meta node)))

(defn- xml-update
  [root tag-path replace-node]
  (let [z (zip/zipper xml/element? :content make-xml-element root)]
    (zip/root
     (loop [[tag & more-tags :as tags] tag-path, parent z, child (zip/down z)]
       (if child
         (if (= tag (:tag (zip/node child)))
           (if (seq more-tags)
             (recur more-tags child (zip/down child))
             (zip/edit child (constantly replace-node)))
           (recur tags parent (zip/right child)))
         (zip/append-child parent replace-node))))))

(defn- replace-header
  [pom {:keys [app-group-id app-artifact-id app-version] :as task}]
  (cond-> pom
    app-group-id    (xml-update [::pom/groupId]
                                (xml/sexp-as-element [::pom/groupId app-group-id]))
    app-artifact-id (xml-update [::pom/artifactId]
                                (xml/sexp-as-element [::pom/artifactId app-artifact-id]))
    app-version     (xml-update [::pom/version]
                                (xml/sexp-as-element [::pom/version app-version]))))

(defn- parse-xml
  [^Reader rdr]
  (let [roots (tree/seq-tree event/event-element event/event-exit? event/event-node
                             (xml/event-seq rdr {:include-node? #{:element :characters :comment}}))]
    (first (filter #(instance? Element %) (first roots)))))


(defn- write-pom-properties [{:keys [app-group-id app-artifact-id] :as task} jar-os jar-paths]
  (let [path (format "META-INF/maven/%s/%s/pom.properties" app-group-id app-artifact-id)
        props (make-project-properties task)]
    (copy-to-jar task jar-os jar-paths {:type :bytes :bytes props :path path})))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Main functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn ^:private write-jar [{:keys [main] :as task}
                           out-file filespecs]
  (with-open [jar-os (-> out-file
                         (FileOutputStream.)
                         (BufferedOutputStream.)
                         (JarOutputStream. (make-manifest task)))]
    (let [jar-paths (reduce (partial copy-to-jar task jar-os)
                            #{}
                            filespecs)]
      (write-pom-properties task jar-os jar-paths)
      (if main
        (let [main-path (str (-> (string/replace main "." "/")
                                 (string/replace "-" "_"))
                             ".class")]
          (when-not (some #{main-path} jar-paths)
            (cli/info "Warning: The Main-Class specified does not exist"
                      "within the jar. It may not be executable as expected."
                      "A gen-class directive may be missing in the namespace"
                      "which contains the main method, or the namespace has not"
                      "been AOT-compiled."))))
      jar-paths)))

(defn ^:private sync-pom
  [{:keys [deps-map] :as task}]
  (cli/info "Updating pom.xml")
  (gen.pom/sync-pom deps-map (io/file "."))
  (let [pom-file (io/file "." "pom.xml")
        pom (with-open [rdr (io/reader pom-file)]
              (-> rdr
                  parse-xml
                  (replace-header task)))]
    (spit pom-file (xml/indent-str pom))))

(defn apply! [{:keys [deps-map] :as task}]
  (compile/apply! task)
  (let [jar-file (jar-utils/get-jar-filename task)]
    (cli/info "Creating" jar-file)
    (sync-pom task)
    (write-jar task jar-file (filespecs task))))

(defn -main [& args]
  (let [{:keys [help] :as task} (cli/args->task args cli-options)]
    (cli/runner
     {:help? help
      :task task
      :entrypoint-main
      "cambada.jar"
      :entrypoint-description
      "Package up all the project's files into a jar file."
      :apply-fn apply!})))
