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

*   [Docker](./common/catalog/docker/)
*   [Swarm](./swarm/catalog/swarm/)
*   [Kubernetes](./kubernetes/catalog/kubernetes/)

## Getting Started

### Add Clocker to Brooklyn (Karaf Edition)

Add catalog entries using the YAML below:

```YAML
brooklyn.catalog:
  brooklyn.libraries:
    - "https://oss.sonatype.org/service/local/artifact/maven/redirect?r=snapshots&g=io.brooklyn.etcd&a=brooklyn-etcd&v=2.7.0-SNAPSHOT"
    - "https://oss.sonatype.org/service/local/artifact/maven/redirect?r=snapshots&g=io.brooklyn.clocker&a=clocker-common&v=2.1.0-SNAPSHOT"
    - "https://oss.sonatype.org/service/local/artifact/maven/redirect?r=snapshots&g=io.brooklyn.clocker&a=clocker-swarm&v=2.1.0-SNAPSHOT"
    - "https://oss.sonatype.org/service/local/artifact/maven/redirect?r=snapshots&g=io.brooklyn.clocker&a=clocker-kubernetes&v=2.1.0-SNAPSHOT"
  items:
    - classpath://io.brooklyn.clocker.swarm:swarm/catalog.bom
    - classpath://io.brooklyn.clocker.kubernetes:kubernetes/catalog.bom
```

### Add Clocker to Brooklyn (Classic Edition)

You must add the following JARs to `./lib/dropins`:

*   [brooklyn-etcd](https://oss.sonatype.org/service/local/artifact/maven/redirect?r=snapshots&g=io.brooklyn.etcd&a=brooklyn-etcd&v=2.7.0-SNAPSHOT)
*   [common](https://oss.sonatype.org/service/local/artifact/maven/redirect?r=snapshots&g=io.brooklyn.clocker&a=clocker-common&v=2.1.0-SNAPSHOT)
*   [swarm](https://oss.sonatype.org/service/local/artifact/maven/redirect?r=snapshots&g=io.brooklyn.clocker&a=clocker-swarm&v=2.1.0-SNAPSHOT)
*   [kubernetes](https://oss.sonatype.org/service/local/artifact/maven/redirect?r=snapshots&g=io.brooklyn.clocker&a=clocker-kubernetes&v=2.1.0-SNAPSHOT)

Then add the catalog entries using the following YAML:

```YAML
brooklyn.catalog:
  items:
    - classpath://swarm/catalog.bom
    - classpath://kubernetes/catalog.bom
```

## Copyright

The following icon images have been used, and their use here is believed to be
acceptable under their licensing terms or fair use doctrine. The source URLs
and other links given contain more details:

- [`common/resources/icons/centos.png`](https://commons.wikimedia.org/wiki/File:Centos-logo-light.svg) Remixed version of freely available logo
- [`common/resources/icons/docker.png`](https://www.docker.com/brand-guidelines) Icon taken from media kit
- [`common/resources/icons/haproxy.png`](https://pbs.twimg.com/profile_images/737664607301566464/pmfqGAYU.jpg) Fair use of logo from Twitter account for open source project
- [`common/resources/icons/openssl.png`](https://commons.wikimedia.org/wiki/File:OpenSSL_logo.png) Public domain image
- [`swarm/resources/icons/swarm.png`](https://www.docker.com/sites/default/files/docker-swarm-hero2.png) Fair use of image from <https://www.docker.com/products/docker-swarm> and also found in Docker media kit
- [`kubernetes/resources/icons/calico.png`](https://github.com/projectcalico/calico/blob/master/images/favicon.png) APACHE 2.0 <https://github.com/projectcalico/calico/blob/master/LICENSE> image
- [`kubernetes/resources/icons/flannel.png`](https://github.com/coreos/flannel/blob/master/logos/flannel-horizontal-color.png) APACHE 2.0 <https://github.com/coreos/flannel/blob/master/LICENSE> image
- [`kubernetes/resources/icons/kubernetes.png`](https://raw.githubusercontent.com/kubernetes/kubernetes/master/logo/logo.png) APACHE 2.0 <https://github.com/kubernetes/kubernetes/blob/master/LICENSE> image
- [`kubernetes/resources/icons/prometheus.png`](https://github.com/prometheus/docs/blob/master/static/prometheus_logo.png) APACHE 2.0 <https://github.com/prometheus/docs/blob/master/LICENSE> image
- [`kubernetes/resources/icons/openshift.png`](https://github.com/openshift/origin-web-console/blob/e7a0c0a8f703d5429f70b78223abb31856a66670/app/images/openshift-logo.svg) APACHE 2.0 <https://github.com/openshift/origin-web-console/blob/master/LICENSE> image
