(ns cambada.fs
  (:import [java.io File RandomAccessFile]
           [java.nio.file.attribute BasicFileAttributes FileAttribute]
           [java.nio ByteBuffer]
           [java.nio.charset StandardCharsets]
           [java.nio.channels FileChannel]
           [java.nio.file FileSystem FileSystems FileVisitOption Files Path Paths
            CopyOption LinkOption StandardCopyOption OpenOption StandardOpenOption]
           [java.util.function BiPredicate]))




(def kw->open-option
  {:append StandardOpenOption/APPEND
   :create StandardOpenOption/CREATE
   :create-new StandardOpenOption/CREATE_NEW
   :delete-on-close StandardOpenOption/DELETE_ON_CLOSE
   :dsync StandardOpenOption/DSYNC
   :read StandardOpenOption/READ
   :sparse StandardOpenOption/SPARSE
   :sync StandardOpenOption/SYNC
   :truncate-existing StandardOpenOption/TRUNCATE_EXISTING
   :write StandardOpenOption/WRITE})



(defn normalize-open-options
  "returns a set of StandardOpenOption from an input of
   opts which contains keywords"
  [opts]
  (set (map kw->open-option opts)))

(defn ^FileSystem default-fs
  []
  (FileSystems/getDefault))

(defprotocol IPath
  (path [this]))

(extend-protocol IPath
  String
  (path [this]
    (path (-> (default-fs) (.getPath this (make-array String 0)))))

  File
  (path [this]
    (path (.getPath this)))

  Path
  (path [this] this))


(defn directory?
  "path is an instance of java.nio.file.Path"
  [path & link-opts]
  (Files/isDirectory path (into-array LinkOption link-opts)))

(defn exists?
  [path-like & link-opts]
  (Files/exists (path path-like) (into-array LinkOption link-opts)))


(def overwrite   "Overwrite file option"   ::overwrite)
(def atomic-move "Atomic Move file option" ::atomic-move)
(def copy-attrs  "Copy file attrs"         ::copy-attrs)

(defn ^:private bi-predicate
  [f]
  (reify BiPredicate
    (test [this t u] (f t u))))

(defn relative-path
  "Given parent path of `/usr/local/lib` and child of
  `/usr/local/lib/clojure/deps.edn`returns a path that
  is `clojure/deps.edn`"
  ^Path [^Path parent ^Path child]
  (.relativize parent child))

(defn find-files
  "root-path is something that can be converted to a path. Can be a String.
   Returns nil or a seq of paths. nil is returned if the root-path doesn't
   represent a valid path"
  [root-path bi-pred]
  (let [root-path (path root-path)]
    (when (exists? root-path)
      (->> (Files/find
            root-path
            Integer/MAX_VALUE
            (bi-predicate bi-pred)
            (into-array FileVisitOption []))
           .iterator
           iterator-seq))))

(defn find-non-source-files
  [root-path]
  (let [pattern (re-pattern #".+(clj|cljs|cljc)$")]
    (find-files
     root-path
     (fn [^Path path ^BasicFileAttributes file-attrs]
       (and (.isRegularFile file-attrs)
            (->> path .getFileName str (re-find pattern) nil?))))))


(defn input-stream
  "open-opts are keywords"
  [file-path & open-opts]
  (Files/newInputStream
   (path file-path)
   (into-array (normalize-open-options open-opts))))
