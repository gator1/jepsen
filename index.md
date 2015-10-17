---
title: Jepsen
layout: default
---

# Jepsen

<iframe width="520" height="293" src="http://www.ustream.tv/embed/recorded/61443262?html5ui" allowfullscreen webkitallowfullscreen scrolling="no" frameborder="0" style="border: 0 none transparent;"></iframe>

## Distributed Systems Safety Analysis

Jepsen is an effort to improve the safety of distributed databases, queues,
consensus systems, etc. It encompasses a [software
library](https://github.com/aphyr/jepsen) for systems testing, as well as [blog
posts](https://aphyr.com/tags/jepsen), and [conference
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

## Systems

|---|---|
| Aerospike     | [3.5.4](https://aphyr.com/posts/324-call-me-maybe-aerospike) |
| Cassandra     | [2.0.0](https://aphyr.com/posts/294-call-me-maybe-cassandra) |
| Chronos       | [2.4.0](https://aphyr.com/posts/326-call-me-maybe-chronos) |
| Elasticsearch | [1.1.0](https://aphyr.com/posts/317-call-me-maybe-elasticsearch), [1.5.0](https://aphyr.com/posts/323-call-me-maybe-elasticsearch-1-5-0) |
| etcd          | [0.4.1](https://aphyr.com/posts/316-call-me-maybe-etcd-and-consul) |
| Kafka         | [0.8 beta](https://aphyr.com/posts/293-call-me-maybe-kafka) |
| MariaDB Galera | [10.1](https://aphyr.com/posts/327-call-me-maybe-mariadb-galera-cluster) |
| MongoDB       | [2.4.3](https://aphyr.com/posts/284-call-me-maybe-mongodb), [2.6.7](https://aphyr.com/posts/322-call-me-maybe-mongodb-stale-reads) |
| NuoDB         | [1.2](https://aphyr.com/posts/292-call-me-maybe-nuodb) |
| Percona XtraDB Cluster | [5.6.25](https://aphyr.com/posts/328-call-me-maybe-percona-xtradb-cluster) |
| RabbitMQ      | [3.3.0](https://aphyr.com/posts/315-call-me-maybe-rabbitmq) |
| Redis         | [2.6.13](https://aphyr.com/posts/283-call-me-maybe-redis), [experimental WAIT](https://aphyr.com/posts/307-call-me-maybe-redis-redux) |
| Riak          | [1.2.1](https://aphyr.com/posts/285-call-me-maybe-riak) |
| Zookeeper     | [3.4.5](https://aphyr.com/posts/291-call-me-maybe-zookeeper) |

## Techniques

Jepsen occupies a particular niche of the correctness testing landscape. We
emphasize:

- Black-box systems testing: we evaluate real binaries running on real
  clusters. This allows us to test systems without access to their source, and
  without requiring deep packet inspection, formal annotations, etc. Bugs
  reproduced in Jepsen are *observable in production*, not theoretical.
  However, we sacrifice some of the strengths of formal methods: tests are
  nondeterministic, and we cannot prove correctness, only find errors.

- Testing *under distributed systems failure modes*: faulty networks,
  unsynchronized clocks, and partial failure. Many test suites only evaluate
  the behavior of healthy clusters, but production systems experience
  pathological failure modes. Jepsen shows behavior under strain.

- Generative testing: we construct random operations, apply them to the system,
  and construct a concurrent history of their results. That history is checked
  against a *model* to establish its correctness. Generative (or property-based)
  tests often reveal edge cases with subtle combinations of inputs.

## Request a test

Would you like to see a system analyzed? Send an email to [aphyr+jepsen@aphyr.com](mailto:aphyr+jepsen@aphyr.com).
