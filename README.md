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

## Concepts

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
(require '[sentenza.api :as sz])

(sz/flow (async/to-chan (range 100))
         (filter even?)
         (map inc)
         (map #(insert db %)))
```


1: Technically map's laziness means that the database insert won't be executed
just yet, but for the sake of clarity we can ignore this.

## Usage

FIXME

## License

Copyright © 2017 Ladders
