Clocker
=======

Clocker creates and manages a **[Docker](http://docker.io/)** cloud infrastructure. Clocker supports
single-click deployment and runtime management of multi-node applications that can run on
containers distributed across multiple hosts. Plugins are included for both
**[Project Calico](https://github.com/Metaswitch/calico-docker)** and **[Weave](http://github.com/zettio/weave/)**
to provide seamless Software-Defined Networking integration. Application blueprints written for
**[Apache Brooklyn](https://brooklyn.incubator.apache.org/)** can thus be deployed to a distributed
Docker Cloud infrastructure.

This repository contains all of the required Brooklyn entities, locations and examples.

[![Build Status](https://api.travis-ci.org/brooklyncentral/clocker.svg?branch=master)](https://travis-ci.org/brooklyncentral/clocker)
[![Latest Builds](http://img.shields.io/badge/version-0.9.0--SNAPSHOT-blue.svg?style=flat)](http://clocker-latest.s3-website-eu-west-1.amazonaws.com/)
[![Gitter](https://badges.gitter.im/Join Chat.svg)](https://gitter.im/brooklyncentral/clocker)

## Getting started

To get started, you just have to download the Clocker distribution archive, deploy one of the
**Docker Cloud** blueprints to the cloud or machines of your choice, and then use Clocker to
deploy your applications. This will automatically create the required Docker containers.

You can create a Docker based cloud infrastructure on your favourite cloud provider or on a 
private cloud using any of the jclouds supported APIs. Alternatively you can target one or 
more existing machines for running Docker.

If you are keen to peek under the covers, you will find the Docker Cloud infrastructure
blueprints at either
[docker-cloud-weave.yaml](https://raw.githubusercontent.com/brooklyncentral/clocker/master/examples/src/main/assembly/files/blueprints/docker-cloud-weave.yaml) or
[docker-cloud-calico.yaml](https://raw.githubusercontent.com/brooklyncentral/clocker/master/examples/src/main/assembly/files/blueprints/docker-cloud-calico.yaml) depending on your choice of SDN provider. 

### Using the latest Clocker release

The latest version of Clocker is [0.8.1](https://github.com/brooklyncentral/clocker/releases/tag/v0.8.1).
You can deploy your own **Docker Cloud** with a Weave SDN by running these commands, to use
Project Calico as your SDN provider, change the last command to `./bin/calico.sh` instead:
```Bash
% wget --no-check-certificate --quiet \
    -O brooklyn-clocker-dist.tar.gz http://git.io/vfCE8
% tar zxf brooklyn-clocker-dist.tar.gz
% cd brooklyn-clocker
% ./bin/clocker.sh location
```
The _location_ argument specifies the destination to deploy to.

For example, you can specify the jclouds provider for SoftLayer in San Jose by using
_jclouds:softlayer:sjc01_, a group of machines as _byon:(hosts="10.1.2.3,10.1.2.4")_ or a specific
location from your `brooklyn.properties` file as _named:alias_.

For all cloud locations you must first configure the `~/.brooklyn/brooklyn.properties` file with any
necessary credentials and security details, and select an SSH key (defaulting to `~/.ssh/id_rsa`).
A basic `brooklyn.properties` file should look like the following:

```
brooklyn.ssh.config.privateKeyFile = ~/.ssh/id_rsa_clocker
brooklyn.ssh.config.publicKeyFile = ~/.ssh/id_rsa_clocker.pub

brooklyn.location.jclouds.softlayer.identity = user.name
brooklyn.location.jclouds.softlayer.credential = softlayersecretapikey
brooklyn.location.named.Softlayer\ California = jclouds:softlayer:sjc01

brooklyn.location.jclouds.aws-ec2.identity = ACCESS_KEY
brooklyn.location.jclouds.aws-ec2.credential = awssecretkey
brooklyn.location.named.Amazon\ Ireland = jclouds:aws-ec2:eu-west-1
```

For more information on setting up locations, including supplying cloud provider credentials, see the
[_Setting up Locations_ section of Brooklyn Getting Started](https://brooklyn.incubator.apache.org/quickstart/#configuring-a-location),
and the more detailed [locations guide](https://brooklyn.incubator.apache.org/v/0.7.0-M1/use/guide/locations/index.html).
The Brooklyn documentation also covers setting up security for the web-console, and configuring users
and passwords.

The Brooklyn web-console, which will be deploying and managing your Docker Cloud, can be accessed at 
[http://localhost:8081](http://localhost:8081) - this URL will have been written to standard out during startup.
A preview of the new Clocker web-console, which shows a summary of the deployed Docker Clouds, is also available on the
same server, at [http://localhost:8081/clocker/](http://localhost:8081/clocker/).

Once the Docker Cloud application has started, a new location named `my-docker-cloud` will be
available in the Locations drop-down list when adding new applications. Simply start a new application in this location
and it will use Docker containers instead of virtual machines.

For more information on deploying applications from the Brooklyn catalog, see
[Getting Started - Policies and Catalogs](https://brooklyn.incubator.apache.org/quickstart/policies-and-catalogs.html).

You can also paste a YAML blueprint into the _YAML_ tab of the _Add Application_ dialog, as follows:
```JS
location: my-docker-cloud
services:
- type: brooklyn.entity.webapp.jboss.JBoss7Server
  brooklyn.config:
    wars.root:
    - "https://s3-eu-west-1.amazonaws.com/brooklyn-clocker/hello-world.war"
```

A blueprint for an application using a Docker image would look like this:
```JS
location: my-docker-cloud
services:
- type: docker:redis:2.8.19
  openPorts:
  - 6379
  directPorts:
  - 6379
```

### Building from source

<!-- CLOCKER_VERSION_BELOW -->
The master branch of Clocker is at version [0.9.0-SNAPSHOT](http://github.com/brooklyncentral/clocker/).
Build and run this version of Clocker from source as follows:

```Bash
    % git clone https://github.com/brooklyncentral/clocker.git
    ...
    % cd clocker
    % mvn clean install
    ...
    % tar zxf examples/target/brooklyn-clocker-dist.tar.gz
    % cd brooklyn-clocker
    % ./bin/clocker.sh location
    ...
```

If you just want to test the latest code, then our [Travis CI](https://travis-ci.org/brooklyncentral/clocker)
build runs for every commit and the resulting distribution files are archived and made available for
download on [Amazon S3](http://clocker-latest.s3-website-eu-west-1.amazonaws.com/).

## Getting involved

Clocker is [Apache 2.0](http://www.apache.org/licenses/LICENSE-2.0) licensed, and builds on  the
[Apache Brooklyn](http://brooklyn.incubator.apache.org/) project. Please get involved and join the 
discussion on [Freenode](http://freenode.net/), IRC `#brooklyncentral` or the Apache Brooklyn 
community [mailing list](https://brooklyn.incubator.apache.org/community/). We also maintain a
[Trello](https://trello.com/b/lhS7ltyi/clocker) board with the current roadmap and active tasks.

### Documentation

Please visit the [Wiki](https://github.com/brooklyncentral/clocker/wiki) for more details.

----
Copyright 2014-2015 by Cloudsoft Corporation Limited.
