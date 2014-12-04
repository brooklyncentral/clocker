Clocker
=======

Clocker creates and manages a [Docker](http://docker.io/) cloud infrastructure. Clocker support 
single-click deployment and runtime management of multi-node applications that can run on
containers distributed across multiple hosts, using the [Weave](http://github.com/zettio/weave/) SDN.
Application blueprints written for [Brooklyn](https://brooklyn.incubator.apache.org/) can thus
be deployed to a distributed Docker Cloud Infrastructure.

This repository contains the required Brooklyn entities, locations and examples.

[![Build Status](https://api.travis-ci.org/brooklyncentral/clocker.svg?branch=master)](https://travis-ci.org/brooklyncentral/clocker)
[![Issue Stats](http://issuestats.com/github/brooklyncentral/clocker/badge/pr)](http://issuestats.com/github/brooklyncentral/clocker)
[![Latest Builds](http://img.shields.io/badge/version-0.8.0--SNAPSHOT-blue.svg)](http://clocker-latest.s3-website-eu-west-1.amazonaws.com/)

## Getting started

To get started, you just have to download Clocker, deploy the _Docker Cloud_ blueprint to the 
cloud or machines of your choice, and then use Clocker to deploy your applications. This will 
automatically create the required Docker containers.

You can create a Docker based cloud infrastructure on your favourite cloud provider or on a 
private cloud using any of the jclouds supported APIs. Alternatively you can target one or 
more existing machines for running Docker.

If you are keen to peek under the covers, you can find the Docker cloud infrastructure blueprint at 
[docker-cloud.yaml](https://raw.githubusercontent.com/brooklyncentral/clocker/master/examples/src/main/assembly/files/blueprints/docker-cloud.yaml). 

### Using the latest Clocker release

The latest version of Clocker is [0.7.0](https://github.com/brooklyncentral/clocker/releases/tag/v0.7.0).
You can deploy this *Docker Cloud Infrastructure* by running these commands:
```Bash
% wget --no-check-certificate --quiet \
    -O brooklyn-clocker-dist.tar.gz http://git.io/w8jsYQ
% tar zxf brooklyn-clocker-dist.tar.gz
% cd brooklyn-clocker
% ./bin/clocker.sh location
```
Where _location_ specifies the destination to deploy to. For example this can be a jclouds provider
like _jclouds:softlayer:sjc01_, a group of machines _byon:(hosts="10.1.2.3,10.1.2.4")_ or a named
location from your `brooklyn.properties` file.

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

Once the `DockerCloud`  application has started, a new location named `my-docker-cloud` will be
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

### Building from source

The master branch of Clocker is at version [0.8.0-SNAPSHOT](http://github.com/brooklyncentral/clocker/).
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
Copyright 2014 by Cloudsoft Corporation Limited.
