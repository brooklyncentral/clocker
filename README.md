[![Build Status](https://travis-ci.org/brooklyncentral/clocker.svg?branch=master)](https://travis-ci.org/brooklyncentral/clocker)

# Clocker

The current release is **2.0.0**, available in Maven Central. For more
information, see the [official Clocker site](http://www.clocker.io/), including
[documentation](http://www.clocker.io/docs/) and [tutorials](http://www.clocker.io/tutorials/).

The development version is **2.1.0-SNAPSHOT**, available in the Sonatype Open-Source
repository. To install this, follow the instructions below.

## Overview

Clocker for [Apache Brooklyn](https://brooklyn.apache.org/) is a set of open
source, Apache Licensed tools designed to make working with [Docker](https://www.docker.com/)
containers as simple as a few clicks. Clocker contains [Brooklyn blueprints](http://brooklyn.apache.org/v/latest/start/blueprints.html)
to enable deployment and management of [Docker Swarm](https://www.docker.com/products/docker-swarm)
and [Kubernetes](http://kubernetes.io/) clusters.

You will find the source code for the blueprints in this repository.

*   [Docker](./common/src/main/resources/docker/)
*   [Swarm](./swarm/src/main/resources/swarm/)
*   [Kubernetes](./kubernetes/src/main/resources/kubernetes/)

## Getting Started

### Add Clocker to Brooklyn (Karaf Edition)

Add catalog entries using the YAML below:

```YAML
brooklyn.catalog:
  brooklyn.libraries:
    - "https://oss.sonatype.org/service/local/artifact/maven/redirect?r=snapshots&g=io.brooklyn.etcd&a=brooklyn-etcd&v=2.3.0-SNAPSHOT"
    - "https://oss.sonatype.org/service/local/artifact/maven/redirect?r=snapshots&g=io.brooklyn.clocker&a=clocker-common&v=2.1.0-SNAPSHOT"
    - "https://oss.sonatype.org/service/local/artifact/maven/redirect?r=snapshots&g=io.brooklyn.clocker&a=clocker-swarm&v=2.1.0-SNAPSHOT"
    - "https://oss.sonatype.org/service/local/artifact/maven/redirect?r=snapshots&g=io.brooklyn.clocker&a=clocker-kubernetes&v=2.1.0-SNAPSHOT"
  items:
    - classpath://io.brooklyn.etcd.brooklyn-etcd:brooklyn-etcd/catalog.bom
    - classpath://io.brooklyn.clocker.common:docker/catalog.bom
    - classpath://io.brooklyn.clocker.swarm:swarm/catalog.bom
    - classpath://io.brooklyn.clocker.kubernetes:kubernetes/catalog.bom
```

### Add Clocker to Brooklyn (Classic Edition)

You must add the following JARs to `./lib/dropins`:

*   [brooklyn-etcd](https://oss.sonatype.org/service/local/artifact/maven/redirect?r=snapshots&g=io.brooklyn.etcd&a=brooklyn-etcd&v=2.3.0-SNAPSHOT)
*   [common](https://oss.sonatype.org/service/local/artifact/maven/redirect?r=snapshots&g=io.brooklyn.clocker&a=clocker-common&v=2.1.0-SNAPSHOT)
*   [swarm](https://oss.sonatype.org/service/local/artifact/maven/redirect?r=snapshots&g=io.brooklyn.clocker&a=clocker-swarm&v=2.1.0-SNAPSHOT)
*   [kubernetes](https://oss.sonatype.org/service/local/artifact/maven/redirect?r=snapshots&g=io.brooklyn.clocker&a=clocker-kubernetes&v=2.1.0-SNAPSHOT)

Then add the catalog entries using the following YAML:

```YAML
brooklyn.catalog:
  items:
    - classpath://io.brooklyn.etcd.brooklyn-etcd:brooklyn-etcd/catalog.bom
    - classpath://io.brooklyn.clocker.common:common/common.bom
    - classpath://io.brooklyn.clocker.common:common/ca.bom
    - classpath://io.brooklyn.clocker.common:docker/docker.bom
    - classpath://io.brooklyn.clocker.swarm:swarm/swarm.bom
    - classpath://io.brooklyn.clocker.kubernetes:kubernetes/plugins.bom
    - classpath://io.brooklyn.clocker.kubernetes:kubernetes/kubernetes.bom
```
