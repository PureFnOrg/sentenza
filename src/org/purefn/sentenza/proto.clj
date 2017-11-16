(ns org.purefn.sentenza.proto)

(defprotocol Pipeline
  (init [this args]
    "Initialize any transient state specific to a particular send.")
  (cleanup [this state]
    "Destroy any transient state created by init for a particular send.")
  (source [this state]
    "Return a sequential collection of source records that will be fed into the pipeline.")
  (run [this state source]
    "Provides the source seq/chan and lets the provider begin processing."))

(extend-protocol Pipeline
  Object
  (init [this args] nil)
  (cleanup [this state] nil))
