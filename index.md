---
title: Jepsen
layout: default
---

# Jepsen

## Distributed systems safety analysis

Jepsen is an effort to improve the safety of distributed databases, queues,
consensus systems, etc. It encompasses a [software
library](https://github.com/aphyr/jepsen) for systems testing, as well as [blog
posts](https://aphyr.com/media/tags/jepsen), and [conference
talks](http://www.ustream.tv/recorded/61443262) exploring particular systems'
failure modes. In each post we explore whether the system lives up to its
documentation's claims, file new bugs, and suggest recommendations for
operators.

Jepsen pushes vendors to make accurate claims and test their software
rigorously, helps users choose databases and queues that fit their needs, and
teaches everyone how to evaluate distributed systems correctness.

Jepsen started as a project in my nights and weekends. In February
[Stripe](http://stripe.com/jobs) hired me to continue Jepsen analyses full
time; they currently sponsor the project.

## Request a test.

Would you like to see a system analyzed? Send an email to <a
href="aphyr+jepsen@aphyr.com">aphyr+jepsen@aphyr.com</a>.

## Techniques

Jepsen occupies a particular niche of the correctness testing landscape. We
emphasize:

- Black-box systems testing: we evaluate real binaries running on real
  clusters. This allows us to test systems without access to their source, and
  without requiring deep packet inspection, formal annotations, etc. Bugs
  reproduced in Jepsen are *observable in production*, not theoretical. On the
  other hand, this makes it harder to detect bugs and extrapolate their root
  causes. Tests are also nondeterministic; we're at the whim of the network and
  thread scheduler. Finally, we cannot *prove* correctness, only errors.

- Testing *under distributed systems failure modes*: faulty networks,
  unsynchronized clocks, and partial failure. Many test suites only evaluate
  the behavior of healthy clusters, but production systems experience
  pathological failure modes. Jepsen shows behavior under strain.

- Generative testing: we construct random operations, apply them to the system,
  and construct a concurrent history of their results. That history is checked
  against a *model* to establish its correctness. Generative (or property-based)
  tests often reveal edge cases with subtle combinations of inputs.
