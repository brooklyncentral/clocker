---
layout: base
title: Documentation
---

## Overview
Clocker adds container support for Apache Brooklyn. It currently comes with various entities to be able to deploy to [Docker Swarms](https://docs.docker.com/swarm/){:target="blank"} and [Kubernetes clusters](http://kubernetes.io/){:target="blank"}

The way it works is as follow:

1. Use Brooklyn to deploy one of the container platforms into clouds. This deployment will effectively become a Brooklyn location.
2. Use Brooklyn to deploy apps onto those newly deployed container plateforms

In results, both container platforms and applications are deployed **and** managed into a central and common interface: *Brooklyn*.

## Entities available
Check out our documentation for each entity that Clocker supports:

* [Docker Swarm](swarm-cluster.html)
* [Kubernetes cluster](kubernetes-cluster.html)
