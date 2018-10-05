(ns cambada.utils
  (:require [clojure.java.io :as io]
            [clojure.java.shell :as sh])
  (:import (java.io File)))

;; # OS detection

(defn- get-by-pattern
  "Gets a value from map m, but uses the keys as regex patterns, trying
  to match against k instead of doing an exact match."
  [m k]
  (m (first (drop-while #(nil? (re-find (re-pattern %) k))
                        (keys m)))))

(defn- get-with-pattern-fallback
  "Gets a value from map m, but if it doesn't exist, fallback
   to use get-by-pattern."
  [m k]
  (let [exact-match (m k)]
    (if (nil? exact-match)
      (get-by-pattern m k)
      exact-match)))

(def ^:private native-names
  {"Mac OS X" :macosx "Windows" :windows "Linux" :linux
   "FreeBSD" :freebsd "OpenBSD" :openbsd
   "amd64" :x86_64 "x86_64" :x86_64 "x86" :x86 "i386" :x86
   "arm" :arm "SunOS" :solaris "sparc" :sparc "Darwin" :macosx})

(defn get-os
  "Returns a keyword naming the host OS."
  []
  (get-with-pattern-fallback native-names (System/getProperty "os.name")))

(defn get-arch
  "Returns a keyword naming the host architecture"
  []
  (get-with-pattern-fallback native-names (System/getProperty "os.arch")))


(defn symlink?
  "Checks if a File is a symbolic link or points to another file."
  [file]
  (let [canon (if-not (.getParent file)
                file
                (-> (.. file getParentFile getCanonicalFile)
                    (File. (.getName file))))]
    (not= (.getCanonicalFile canon)
          (.getAbsoluteFile canon))))

(defn real-directory?
  "Returns true if this file is a real directory, false if it is a symlink or a
  normal file."
  [f]
  (if (= :windows (get-os))
    (.isDirectory f)
    (and (.isDirectory f)
         (not (symlink? f)))))

(defn delete-file-recursively
  "Delete file f. If it's a directory, recursively delete all its contents.
  Raise an exception if any deletion fails unless silently is true."
  [f & [silently]]
  (let [f (io/file f)]
    (when (real-directory? f)
      (doseq [child (.listFiles f)]
        (delete-file-recursively child silently)))
    (.setWritable f true)
    (io/delete-file f silently)))

(defn mkdirs
  "Make a given directory and its parents, but throw an Exception on failure."
  [f] ; whyyyyy does .mkdirs fail silently ugh
  (let [already-exists? (.exists (io/file f))]
    (when-not (or (.mkdirs (io/file f)) already-exists?)
      (throw (Exception. (str "Couldn't create directories: " (io/file f)))))))

(defn directory-name []
  (-> "."
      io/file
      .getAbsoluteFile
      .getParentFile
      .getName))

(defn unix-path [path]
  (.replace path "\\" "/"))

(defn dir-string
  "Returns the file's directory as a string, or the string representation of the
  file itself if it is a directory."
  [file]
  (if-not (.isDirectory file)
    (str (.getParent file) "/")
    (str file "/")))

(defn relativize-path
  "Relativizes a path: Removes the root-path of a path if not already removed."
  [path root-path]
  (if (.startsWith path root-path)
    (.substring path (.length root-path))
    path))

(defn full-path
  "Appends the path string with a '/' if the file is a directory."
  [file path]
  (if (.isDirectory file)
    (str path "/")
    path))

(defn compiled-classes-path
  [out-path]
  (str out-path "/classes"))

(defn group-by+
  "Similar to group by, but allows applying val-fn to each item in the grouped by list of each key.
   Can also apply val-agg-fn to the result of mapping val-fn. All input fns are 1 arity.
   If val-fn and val-agg-fn were the identity fn then this behaves the same as group-by."
  ([key-fn val-fn xs]
   (group-by+ key-fn val-fn identity xs))
  ([key-fn val-fn val-agg-fn xs]
   (reduce (fn [m [k v]]
             (assoc m k (val-agg-fn (map val-fn v))))
           {}
           (group-by key-fn xs))))


(defn read-git-head
  "Reads the value of HEAD and returns a commit SHA1, or nil if no commit
  exist."
  []
  (try
    (let [git-ref (sh/sh "git" "rev-parse" "HEAD")]
      (when (= (:exit git-ref) 0)
        (.trim (:out git-ref))))
    (catch java.io.IOException e)))
