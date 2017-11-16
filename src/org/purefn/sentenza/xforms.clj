(ns org.purefn.sentenza.xforms
  "Common transducers that may be useful in data pipelines."
  (:require [clojure.string :as str]
            [taoensso.timbre :as log]))

(defn csv-map
   "Converts rows from a CSV file with an initial header row into a
   lazy seq of maps with the header row keys (as keywords). The 0-arg
   version returns a transducer."
  ([]
   (fn [xf]
     (let [hdr (volatile! nil)]
       (fn
         ([] (xf))
         ([result] (xf result))
         ([result input]
           (let [in-split (str/split input #",")]
             (if-let [h @hdr]
               (xf result (zipmap h in-split))
               (do (vreset! hdr (map keyword in-split))
                   result))))))))

  ([coll]
   (let [[header & records] coll
         hdr (map keyword (str/split header #","))]
     (map #(zipmap hdr (str/split % #",")) records))))

(defn log-count
  "Logs a count of records it's seen with msg every n records."
  [msg n]
  (fn [xf]
    (let [cnt (volatile! 0)]
      (fn
        ([] (xf))
        ([result] (xf result))
        ([result input]
         (let [new-cnt (vswap! cnt inc)]
           (when (zero? (mod new-cnt n))
             (log/info msg :count new-cnt))
           (xf result input)))))))
