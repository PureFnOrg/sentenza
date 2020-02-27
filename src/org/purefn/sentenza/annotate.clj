(ns org.purefn.sentenza.annotate
  "Various annotations of metadata that can be added to transducers
   to modify how they are used in the pipeline."
  (:require [taoensso.timbre :as log]
            [clojure.spec.alpha :as s]))

(defn- warner-fn
  "Helper function that returns a pipeline ex-handler that will simply
   warn with the given string and return nil"
  [msg]
  (fn [e]
    (log/warn e msg)
    nil))

(defn warned
  "Adds a warning function as metadata to the transducer"
  [xf msg]
  (vary-meta xf assoc ::warner (warner-fn msg)))

(defn warner
  "Returned the warner function from the transducer's metadata."
  [xf]
  (::warner (meta xf)))

(defn threaded
  "Marks a transducer as blocking by setting its metadata. Also
   takes the number of threads to spin up, defaults to 16."
  ([xf]
   (threaded xf 16))
  ([xf n]
   (vary-meta xf assoc
              ::threaded true
              ::paral n)))

(defn threaded?
  "Checks whether a given transducer is marked as blocking."
  [xf]
  (::threaded (meta xf)))

(defn cored
  "Marks the transducer as a stateless and therefore parallelizable
   over multiple cores."
  [xf n]
  (vary-meta xf assoc
             ::cored true
             ::paral n))

(defn cored?
  "Checks whether a given transducer is marked as stateless."
  [xf]
  (::cored (meta xf)))

(defn paral
  "Returns the number of threads to spin up for a threading transducer."
  [xf]
  (::paral (meta xf)))

(defn named
  "Marks the transducer with a human readable name, useful for monitoring."
  [xf name]
  (vary-meta xf assoc ::name name))

(defn name-of
  "Fetches the human readable name for a transducer."
  [xf]
  (::name (meta xf)))

(defn has-meta
  "Checks whether a transducer has all of the given metadata keys."
  [xf & ks]
  (some-> (meta xf)
          (every? ks)))

(defn sized
  "Describes the size of the channel buffer this transducer's results will
  accumulate into. Defaults to 500 if unset."
  [xf size]
  (vary-meta xf assoc ::size size))

(defn size
  [xf]
  (-> xf meta ::size))

(s/def ::transducer fn?)

(s/fdef warned
        :args (s/cat :xf ::transducer :msg string?)
        :ret (s/and ::transducer #(fn? (::warner %))))

(s/fdef threaded
        :args (s/cat :xf ::transducer :n (s/? nat-int?))
        :ret (s/and ::transducer #(has-meta % ::threaded ::paral)))

(s/fdef named
        :args (s/cat :xf ::transducer :name (s/or :str string? :key keyword?))
        :ret (s/and ::transducer #(has-meta % ::name)))
