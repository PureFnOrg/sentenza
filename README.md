# sentenza

Sentenza is a library designed to create parallel data processing pipelines
running on a single machine.

It is built on top of [core.async](https://github.com/clojure/core.async).

## Rationale

Clojure provides transducers and core.async. On their own, these are powerful
and shiny tools, but together they form an even shinier and powerfuller duo.
However, it is easy to get lost in the jungle of juggling channels, buffers,
threads, xforms, reducing steps, and run-on sentences.

Sentenza is a juggler that will handle these low level plumbing details. This
frees you to focus on the data in the pipeline: where it comes from,
how it changes, and where it is goes. This brings the actual business logic
of the problem to the forefront while still allowing ways to cleanly tweak
the underlying plumbing when needed.

## Guide

Data pipelines are not complicated. At it's simplest, a Sentenza pipeline
is a story the journey of a piece of data. At each twist and turn of this story
stands a function, guiding and sheperding the data to the next stage in the journey.
Analogies only go so far though. In more concrete terms, a pipeline is
a description of changes we want to do *to* data and actions we want to take *with*
data.

Let's take a common business process that functional programmers often face:
taking a list of numbers, filtering out the even ones, doing some math, and then
writing them to a database. A real value-add to any organization.

``` clojure
(->> (range 100)
     (filter even?)
     (map inc)
     (map #(insert db %)))
```

It's easy to follow what's happening here. We thread a bunch of numbers through
a couple transformations before finally executing a side-effect<sup>1</sup>.
Each step happens one after the other in a nice and orderly fashion. Anyone
can at a glance understand the story this is trying to tell.

Our goal should be to parallelize this flow while maintaining its readable nature
as much as possible. Using core.async directly would mean manually creating
our own channels and connecting them with calls to `async/pipeline` or
`async/pipeline-blocking`. This will add extra verbosity and bookkeeping that
is irrelevant to the domain of the problem. What's worse is that this will also
ruin the story-like structure we are trying to keep. Using Sentenza we can
maintain the narrative of the code while also embracing concurrency. Let's look
at the first step:

``` clojure
;; We use `require` here so that the code can be dropped into a REPL
;; In actual practice these requires should be in an `ns` declaration
(require '[org.purefn.sentenza.api :as sz])
(require '[clojure.core.async :as async])

(sz/flow (async/to-chan (range 100))
         (filter even?)
         (map inc)
         (map #(insert db %)))
```

The pipeline is now wrapped in a call to the `flow` function in the `sentenza.api`
namespace. `flow` requires the first argument to be a channel containing the
records that will be processed. In this case we use core.async's `to-chan`
function to dump our numbers into a channel.

Under the hood, `flow` sets up the necessary plumbing but we still need to actually
tell Sentenza how to parallelize each step. There are two ways to add some
parallelism, one is for CPU-bound operations and another for IO-bound actions.
Let's start with the first step in the pipeline, the filter on even numbers.
This is clearly a CPU-bound operation since no side effects are performed, so
let's see how to handle that!

``` clojure
(require '[clojure.core.async :as async])
(require '[org.purefn.sentenza.api :as sz])
(require '[org.purefn.sentenza.annotate :as sza])

(sz/flow (async/to-chan (range 100))
         (-> (filter even?)
             (sza/cored 4))
         (map inc)
         (map #(insert db %)))
```

Boom, it's parallel!

...but how exactly?

First we bring in the annotations namespace. Annotations are the primary way
that Sentenza pipelines can be parallelized while still maintaining the
narrative structure emphasized in the beginning of this guide. Essentially,
annotations are modifications that can be done to various steps in a pipeline to
tweak parallelism, error handling, etc. What the step actually _does_ should
remain unaffected. It is only _how_ it does it that the annotation changes.

This means that the filter call is still just a filter call. Adding the `cored`
annotation does not change that, but it *does* tell Sentenza that this is a
CPU-bound operation and to dedicate four threads. The extensive workload
of filtering one hundred integers will then be spread out among those four
threads.

A major factor in Sentenza's design is the ability to quickly iterate to find
bottlenecks and adjust as needed. If later on we decide to allocate more or
fewer threads we just need to change the number being passed into `cored`.
The only limitation is the number of physical cores on the machine.

That's great for CPU-bound stuff, but what about IO, like that `insert` function
we have in there? Writing to a database can be relatively slow since it needs to
go over the network, wait for the database to do its job, and then circle back.
This is definitely something ripe for parallelizing.
Luckily, there's an annotation for this, too!

``` clojure
(require '[clojure.core.async :as async])
(require '[sentenza.api :as sz])
(require '[org.purefn.sentenza.annotate :as sza])

(sz/flow (async/to-chan (range 100))
         (-> (filter even?)
             (sza/cored 4))
         (map inc)
         (-> (map #(insert db %))
             (sza/threaded 50)))
```

Instead of cored, we use the `threaded` annotation. Here we tell Sentenza
that the `insert` step is IO-bound and to spin up 50 threads to start
hitting the database with.

We started with a data pipeline consisting of regular Clojure code. This pipeline
was easy to read and follow, but also very single-threaded. By adding a few
calls to Sentenza functions we turned this into a multi-threaded data processing
powerhouse while still maintaining the original structure.

1: Technically map's laziness means that the database insert won't be executed
just yet, but for the sake of clarity we can ignore this.

## Usage

FIXME

## License

Copyright © 2017 Ladders
