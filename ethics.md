---
title: Research Ethics
layout: default
---

People rely on Jepsen to help them test their own systems, to make decisions
about algorithms, to compare different databases against one another, and to
suggest how to work with a particular system's constraints. To put this work in
context, I'd like to talk about Jepsen's limitations and biases--technical,
social, and fiscal.

## Technical limitations

Jepsen analyses generally consist of running operations against a distributed
system in a dedicated cluster, introducing faults into that cluster, and
observing whether the results of those operations are consistent with some
model. This introduces various sources for error: bugs, bounds on the search
space, and the problem of induction. Jepsen's design also limits its use as a
performance benchmark.

As software, Jepsen and the libraries it uses have bugs. I do my best to avoid
them, but there *have* been bugs in Jepsen before and there will be again.
These could lead to false negatives and false positives. I write automated
tests and read histories by hand to double-check, but I can always make
mistakes.

Jepsen operates on real software, not abstract models. This prevents us from
seeing pathological thread schedulings, or bugs that arise only on particular
platforms. Because Jepsen performs real network requests, we can't explore the
system's search space as quickly as a model checker--and our temporal
resolution is limited. Memory and time complexity further limit the scope of
a test. However, testing executables often reveals implementation errors or
subsystem interactions which a model checker would miss! We can also test
programs without having to modify or even read their source.

Because Jepsen tests are experiments, they can only prove the existence of
errors, not their absence. We cannot prove correctness, only *suggest* that a
system is *less likely* to fail because we have not, so far, observed a
problem.

Finally, I should note that Jepsen is a safety checker, not a benchmarking
tool. It can emit performance analyses, but benchmark design is a complex,
separate domain. Hardware, OS tuning, workload, concurrency, network
behavior... all play significant roles in performance. Jepsen's latency
measurements are generally only comparable to *the same software* tested on
*the same platform*. Its throughput measurements are not a performance
benchmark; Jepsen controls its own request rate to reveal concurrency errors.

## Conflicts of interest

In addition to Jepsen's technical limitations, there are social and financial
motivations which influence the work. Nobody can be totally objective--all
research is colored by the researchers' worldview--but I can still strive to
publish high-quality results that people can trust.

First, my personal motivations. I work on Jepsen because I believe it's
*important*, and because I think the work *helps us build better systems*. I
believe that distributed systems should clearly communicate their requirements
and guarantees, making tradeoffs explicit. I believe documentation should be
complete and accurate, using formally analyzed concurrency models to describe a
system's invariants. I want every operator and engineer to have the tools and
know-how to analyze the systems they rely on, and the systems they build.

My personal connections in the distributed systems world influence my work as
well. I strive to be critical of the systems I'm affiliated with: for example,
at Basho's RICON 2013 conference, I demonstrated write loss in one of their
database products. I recommend Kafka frequently, but also wrote about a
data-loss issue in Kafka. Conversely, harsh critiques often lead to productive
relationships: I spoke at Aerospike after publishing a negative report of their
isolation guarantees, and a heated dispute with Salvatore Sanfillipo has led to
friendly dialogue on the design of Disque.

There are also financial conflicts of interest when a vendor pays me to
analyze their database. Few companies like seeing their product discussed in a
negative light, which puts pressure on me to deliver positive results. At
the same time, if I consistently fail to find problems, there's no reason to
hire me: I can't tell you anything new, and I can't help improve your product.
Then there are second-order effects: companies often believe that a positive
Jepsen post may suggest tampering, and *encourage me to find problems*.
Database users have shown surprising goodwill towards vendors which are open to
public criticism, and who commit to improvement.

Finally, when someone pays me to analyze any system--not just their own--it
changes the depth and character of the investigation. Since I'm being paid, I
can afford to devote *more time* to the analysis--a closer reading of the
documentation, more aggressive test suites, and more time for back-and-forth
collaboration with the vendor to identify the causes of a test failure and to
come up with workarounds.

## Promises

In an ideal world, I'd crank out a comprehensive analysis of every release of
every database on the planet and make it available to the public. I *want*
users to understand the software they use. I want to keep vendors honest, and
help them find and fix bugs. I want to teach everyone how to analyze their own
systems, and for us as an industry to produce software which is resilient to
common failure modes.

That said, each analysis takes months of work, and I have to eat. I have to
balance the public good, what clients ask for, and my own constraints of time
and money. Here's what I promise:

- I will do my best to provide educational, clear, accurate, and timely
  analyses of distributed systems, in the interests of users, academics, and
  vendors alike.

- Every analysis I release will include a clear statement of who funded the
  work.

- Clients and I may collaborate on test design and the written analysis.
  I'll ask for client feedback as I work and for review of drafts before
  publication.

- That said, I will never allow a client to tamper with results.  Clients may
  suggest possible causes, related bugs, workarounds, etc, and I'll happily
  include those in the report, but if a database allows stale reads, or a queue
  drops data, I won't claim otherwise. If I feel a client's requested edits
  compromise the accuracy of the analysis, I can veto publication.

- To protect clients, if a client feels my analysis is inaccurate or wouldn't
  be wise to release, they can veto or delay publication. No matter what, the
  analysis and code will still be available to the client, so they can work on
  fixing any issues privately.

- To prevent readers from assuming the worst in the event that I don't publish,
  I will not disclose the existence of a contract without client consent.

- Every analysis will include complete code for reproducing my findings.
  Arguments around formal models will be backed up by appropriate citations,
  and interpretation of intended behavior will be supported by documentation
  excerpts.

- When I make a mistake in an analysis, I will update the post with a clear
  retraction or correction.

Most clients have asked that I operate with maximal freedom--they aren't
interested in influencing results. I think that's great! Conversely, I decline
requests for sponsored content and other fluff pieces; I don't feel it's
consistent with Jepsen's goals.

Finally, I want to emphasize that Jepsen analyses aren't just database
reviews. Each one is written as a case study in test design, and you should
build on their techniques to evaluate your own systems. Everyone is free to use
the Jepsen software to design their own tests for anything they please. It's my
sincere hope that we can collectively raise the bar for distributed systems
safety.
