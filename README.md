[![Build Status](https://travis-ci.org/brooklyncentral/clocker.svg?branch=master)](https://travis-ci.org/brooklyncentral/clocker)

Clocker2
=======
## [Docker](./common/src/main/resources/docker/)

## [Swarm](./swarm/src/main/resources/swarm/)

## [Kubernetes](./kubernetes/src/main/resources/kubernetes/)


# Add Clocker to Brooklyn (Karaf Edition)

Add the yaml below (note this assumes clocker-swarm & clocker-kubernetes JARs hosted)

```yaml
brooklyn.catalog:
  brooklyn.libraries:
    - "https://oss.sonatype.org/service/local/artifact/maven/redirect?r=snapshots&g=io.brooklyn.etcd&a=brooklyn-etcd&v=2.3.0-SNAPSHOT"
    - "https://oss.sonatype.org/service/local/artifact/maven/redirect?r=releases&g=io.brooklyn.clocker&a=common&v=2.0.0"
    - "https://oss.sonatype.org/service/local/artifact/maven/redirect?r=releases&g=io.brooklyn.clocker&a=swarm&v=2.0.0"
    - "https://oss.sonatype.org/service/local/artifact/maven/redirect?r=releases&g=io.brooklyn.clocker&a=kubernetes&v=2.0.0"
  items:
    - classpath://io.brooklyn.etcd.brooklyn-etcd:brooklyn-etcd/catalog.bom
    - classpath://io.brooklyn.clocker.common:main/catalog.bom
    - classpath://io.brooklyn.clocker.swarm:swarm/swarm.bom
    - classpath://io.brooklyn.clocker.kubernetes:kubernetes/plugins.bom
    - classpath://io.brooklyn.clocker.kubernetes:kubernetes/kubernetes.bom
```

# Add Clocker to Brooklyn (Standard Edition)

You must add the following JARs to lib/dropins
* [brooklyn-etcd](https://oss.sonatype.org/service/local/artifact/maven/redirect?r=snapshots&g=io.brooklyn.etcd&a=brooklyn-etcd&v=2.3.0-SNAPSHOT)
* [common](https://oss.sonatype.org/service/local/artifact/maven/redirect?r=releases&g=io.brooklyn.clocker&a=common&v=2.0.0) 
* [swarm](https://oss.sonatype.org/service/local/artifact/maven/redirect?r=releases&g=io.brooklyn.clocker&a=swarm&v=2.0.0) 
* [kubernetes](https://oss.sonatype.org/service/local/artifact/maven/redirect?r=releases&g=io.brooklyn.clocker&a=kubernetes&v=2.0.0) 

```yaml
brooklyn.catalog:
  items:
    - classpath://io.brooklyn.etcd.brooklyn-etcd:brooklyn-etcd/catalog.bom
    - classpath://io.brooklyn.clocker.common:main/catalog.bom
    - classpath://io.brooklyn.clocker.swarm:swarm/swarm.bom
    - classpath://io.brooklyn.clocker.kubernetes:kubernetes/plugins.bom
    - classpath://io.brooklyn.clocker.kubernetes:kubernetes/kubernetes.bom
```
