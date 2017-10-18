(ns sentenza.api
  "A library for building up pipelines of transducers
   using core.async for parallelism."
  (:require [clojure.string :as str]
            [clojure.spec.alpha :as s]
            [clojure.core.async
             :as async
             :refer [>! <! >!! <!! close! chan go-loop]]
            [clojure.core.async.impl.protocols :as async-impl]
            [taoensso.timbre :as log]
            [sentenza.proto :as proto]
            [sentenza.annotate :as sann]))

(defn threaded-pipe
  "Takes elements from the from channel and calls the transformer on it in
   a separate thread using blocking semantics. Useful if,
   say, the to channel has an io-bound transducer attached to it. Merges the
   output of all these channels and puts it into a `to' channel that it creates.
   Returns this to channel."
  [n from xf]
  (log/info "Creating " n " threads.")
  (let [chs (map
             (fn [_] (chan 50 xf (sann/warner xf)))
             (range n))]
    (doseq [ch chs]
      (async/thread
        (let [v (<!! from)]
          (if (nil? v)
            (close! ch)
            (do
              (>!! ch v)
              (recur))))))
    (async/pipe (async/merge chs) (chan 500))))

(defn channel?
  "Test if something is a channel by checking for certain interfaces."
  [c]
  (and (satisfies? async-impl/ReadPort c)
       (satisfies? async-impl/WritePort c)
       (satisfies? async-impl/Channel c)))

(defn n-or-all
  "If n is falsy return the entire collection, otherwise take
   n elements and returnns those."
  [n coll]
  (cond->> coll
    n (take n)))

(defn channeled
  "Converts a pipeline source into a channel. Currently accepts
   BufferedReaders, channels, and collections."
  [source n]
  (let [c (chan 500)]
    (cond
      (instance? java.io.BufferedReader source)
      (do (async/thread (doseq [l (n-or-all n (line-seq source))]
                          (>!! c l))
                        (async/close! c))
          c)

      (coll? source)
      (do (async/onto-chan c (n-or-all n source))
          c)

      (channel? source)
      (if n
        (let [c (chan n)]
          (dotimes [_ n]
            (>!! c (<!! source)))
          (close! source)
          (close! c)
          c)
        source)

      :else
      (throw (ex-info (str "Pipeline's source must be either a BufferedReader, channel, or a coll.\n"
                           "Got " (type source) ".")
                      {:source source})))))

(defn chan-seq
  "Simple utility function that will convert a channel to a lazy seq."
  [c]
  (lazy-seq
   (when-let [v (<!! c)]
     (cons v (chan-seq c)))))

(defn seqed
  "Converts a pipeline source into a sequence. Used by `tryout' for
   doing sequential execution."
  [source]
  (cond
    (instance? java.io.BufferedReader source)
    (line-seq source)

    (coll? source)
    source

    (channel? source)
    (chan-seq source)

    :else
    (throw (ex-info (str "Pipeline's source must be either a BufferedReader, channel, or a coll."
                         "Got " (type source) ".")
                    {:source source}))))

(defn paral?
  "Returns whether a given trasnformer should be treated as parallelizable."
  [xf]
  (or (sann/threaded? xf)
      (sann/cored? xf)))

;; =============================
;; Main API
;; =============================

;; ===========================================================
;; Starting with source, we want to ayncly apply each xform.
;; We will create an output channel at each step, the output
;; channel will have the xform for that step.
;; ===========================================================

(defn flow
  "Takes a source collection and an arbitrary number of transducers,
   and funnels the source through each transducer.

   If the source is a plain sequential collection we defer to
   transduce and sequential execution.

   If the source is a channel this will be parallelized using
   core.async channels. Returns the last transducer's channel.

   For each transducer, it will create a channel and pipe in the
   contents of the previous channel. If the transducer has metadata
   indicating that it involves blocking, IO-bound operations it will
   pipe using core.async's thread and blocking operations. Otherwise
   it simply defers to async/pipe to connect the two channels.

   We chose not to use async/pipeline since it performs extra work for
   features (ordering,multiple values) that we don't need."
  [source & xfs]
  (if (channel? source)
    (loop [from source
           xfs xfs
           chans [from]
           i 0]
      (let [[xf & rxfs] xfs
            ;; If xf is single-threaded it is likely stateful with a volatile
            ;; and needs to be attached to the channel, therefore staying in
            ;; the same thread.
            to (if (paral? xf)
                      (chan 500)
                      (chan 500 xf (sann/warner xf)))]
        (cond
          (sann/threaded? xf)
          (async/pipeline-blocking (sann/paral xf)
                                   to
                                   xf
                                   from
                                   true
                                   (sann/warner xf))

          (sann/cored? xf)
          (async/pipeline (sann/paral xf)
                          to
                          xf
                          from
                          true
                          (sann/warner xf))

          :default
          (async/pipe from to))
        (if (seq rxfs)
          (recur to rxfs (conj chans to) (inc i))
          (conj chans to))))
    (transduce (apply comp xfs) conj [] source)))

(defn kickoff
  "Given an instance of Pipeline, will fetch its source, turn it into
   a channel, and funnel it into the pipeline's run function, where it
   will process the pipeline with multiple threads. Can take an optional
   n number of records to process, if n is nill all records will be processed."
  ([pipeline args]
   (kickoff pipeline nil args))
  ([pipeline n args]
   (System/setProperty "clojure.core.async.pool-size" "16")
   (let [state  (proto/init pipeline args)
         source (channeled (proto/source pipeline state) n)
         chs    (proto/run pipeline state source)]
     (go-loop []
       (if (nil? (<! (last chs)))
         (do
           (close! (last chs))
           (proto/cleanup pipeline state))
         (recur)))
     chs)))

(defn tryout
  "Given an instance of Pipeline, will fetch its source and funnel
   it into the pipeline's run function as a simple collection,
   this will cause the pipeline to be executed sequentially. Takes
   an optional number `n' of number of source records to execute, defaults
   to 1000."
  ([pipeline args]
   (tryout pipeline nil args))
  ([pipeline n args]
   (let [state (proto/init pipeline args)
         source (cond->> (seqed (proto/source pipeline state))
                  n (take n))
         res   (proto/run pipeline state source)]
     (proto/cleanup pipeline state)
     res)))

;; =============================
;; Specs
;; =============================
(def pipeline? (partial satisfies? proto/Pipeline))

(s/def ::transducer fn?)
(s/def ::channel channel?)

(s/fdef flow
        :args (s/cat :source (s/or :seq seqable?
                                   :channel ::channel)
                     :xfs (s/* ::transducer))
        :ret ::channel)

(s/fdef kickoff
        :args (s/cat :pipeline pipeline?
                     :n (s/? nat-int?)))

(s/fdef tryout
        :args (s/cat :pipeline pipeline?
                     :n (s/? nat-int?)))
