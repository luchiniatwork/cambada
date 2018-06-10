(ns cambada.jar-utils
  (:require [clojure.java.io :as io]))

(defn get-jar-filename
  [{:keys [out app-artifact-id app-version] :as task}
   & [{:keys [kind] :or {kind :jar}}]]
  (let [suffix (if (= kind :uberjar) "-standalone" "")
        jar-name (str app-artifact-id "-" app-version suffix ".jar")]
    (str (io/file out jar-name))))
