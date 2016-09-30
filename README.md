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
#    TODO: Replace below URLs with link to release JARS    
    - "http://localhost:8000/io/brooklyn/clocker/common/2.0.0-SNAPSHOT/common-2.0.0-SNAPSHOT.jar"
    - "http://localhost:8000/io/brooklyn/clocker/swarm/2.0.0-SNAPSHOT/swarm-2.0.0-SNAPSHOT.jar"
    - "http://localhost:8000/io/brooklyn/clocker/kubernetes/2.0.0-SNAPSHOT/kubernetes-2.0.0-SNAPSHOT.jar"
  items:
    - classpath://io.brooklyn.etcd.brooklyn-etcd:brooklyn-etcd/catalog.bom
    - classpath://io.brooklyn.clocker.common:main/catalog.bom
    - classpath://io.brooklyn.clocker.swarm:main/catalog.bom
    - classpath://io.brooklyn.clocker.kubernetes:main/catalog.bom
```


# Add Clocker to Brooklyn (Standard Edition)

You must add the following JARs to lib/dropins
* [brooklyn-etcd](https://oss.sonatype.org/service/local/artifact/maven/redirect?r=snapshots&g=io.brooklyn.etcd&a=brooklyn-etcd&v=2.3.0-SNAPSHOT)
* [common](./common) 
* [swarm](./swarm) 
* [kubernetes](./kubernetes) 

```yaml
brooklyn.catalog:
  items:
    - classpath://io.brooklyn.etcd.brooklyn-etcd:brooklyn-etcd/catalog.bom
    - classpath://io.brooklyn.clocker.common:main/catalog.bom
    - classpath://io.brooklyn.clocker.swarm:swarm/swarm.bom
    - classpath://io.brooklyn.clocker.kubernetes:kubernetes/plugins.bom
    - classpath://io.brooklyn.clocker.kubernetes:kubernetes/kubernetes.bom
```
