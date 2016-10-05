[![Build Status](https://travis-ci.org/brooklyncentral/clocker.svg?branch=master)](https://travis-ci.org/brooklyncentral/clocker)

Clocker
=======

For more information, see the [official Clocker site](http://www.clocker.io/), including the 
[docs](http://www.clocker.io/docs/) and [tutorials](http://www.clocker.io/tutorials/).

Clocker for [Apache Brooklyn](https://brooklyn.apache.org/) is a set of open source, Apache 
Licensed tools designed to make working with [Docker](https://www.docker.com/) containers 
as simple as a few clicks. Clocker contains 
[Brooklyn blueprints](http://brooklyn.apache.org/v/latest/start/blueprints.html) to enable 
deployment and management of [Docker Swarm](https://www.docker.com/products/docker-swarm) 
and [Kubernetes clusters](http://kubernetes.io/).

You can find the source code for the blueprints in this repo at:

* [Docker](./common/src/main/resources/docker/)

* [Swarm](./swarm/src/main/resources/swarm/)

* [Kubernetes](./kubernetes/src/main/resources/kubernetes/)


# Add Clocker to Brooklyn (Karaf Edition)

```yaml
brooklyn.catalog:
  brooklyn.libraries:
    - "https://oss.sonatype.org/service/local/artifact/maven/redirect?r=snapshots&g=io.brooklyn.etcd&a=brooklyn-etcd&v=2.3.0-SNAPSHOT"
    - "https://oss.sonatype.org/service/local/repositories/releases/content/io/brooklyn/clocker/common/2.0.0/common-2.0.0.jar"
    - "https://oss.sonatype.org/service/local/repositories/releases/content/io/brooklyn/clocker/swarm/2.0.0/swarm-2.0.0.jar" 
    - "https://oss.sonatype.org/service/local/repositories/releases/content/io/brooklyn/clocker/kubernetes/2.0.0/kubernetes-2.0.0.jar"
  items:
    - classpath://io.brooklyn.etcd.brooklyn-etcd:brooklyn-etcd/catalog.bom
    - classpath://io.brooklyn.clocker.common:main/catalog.bom
    - classpath://io.brooklyn.clocker.swarm:swarm/swarm.bom
    - classpath://io.brooklyn.clocker.kubernetes:kubernetes/plugins.bom
    - classpath://io.brooklyn.clocker.kubernetes:kubernetes/kubernetes.bom
```

# Add Clocker to Brooklyn (Classic Edition)

You must add the following JARs to `./lib/dropins`:
* [brooklyn-etcd](https://oss.sonatype.org/service/local/artifact/maven/redirect?r=snapshots&g=io.brooklyn.etcd&a=brooklyn-etcd&v=2.3.0-SNAPSHOT)
* [common](https://oss.sonatype.org/service/local/artifact/maven/redirect?r=releases&g=io.brooklyn.clocker&a=common&v=2.0.0) 
* [swarm](https://oss.sonatype.org/service/local/artifact/maven/redirect?r=releases&g=io.brooklyn.clocker&a=swarm&v=2.0.0) 
* [kubernetes](https://oss.sonatype.org/service/local/artifact/maven/redirect?r=releases&g=io.brooklyn.clocker&a=kubernetes&v=2.0.0) 

```yaml
brooklyn.catalog:
  items:
    - classpath://io.brooklyn.etcd.brooklyn-etcd:brooklyn-etcd/catalog.bom
    - classpath://io.brooklyn.clocker.common:common/common.bom
    - classpath://io.brooklyn.clocker.common:docker/docker.bom
    - classpath://io.brooklyn.clocker.common:main/catalog.bom
    - classpath://io.brooklyn.clocker.swarm:swarm/swarm.bom
    - classpath://io.brooklyn.clocker.kubernetes:kubernetes/plugins.bom
    - classpath://io.brooklyn.clocker.kubernetes:kubernetes/kubernetes.bom
```
