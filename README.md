Clocker
=======

Clocker contains [Brooklyn](http://brooklyn.io/) entities, locations and examples that create a [Docker](http://docker.io) cloud infrastructure.

## Getting started

This project requires a Docker instance that Brooklyn will use as a target location when deploying application
blueprints.

You can use Brooklyn to install Docker onto a single existing machine, or create a Docker based cloud infrastructure on
your favourite cloud provider or on a private cloud using any of the [jclouds](http://jclouds.apache.org) supported APIs.

To install a Docker cloud infrastucture using the Clocker Brooklyn entities, there is an example blueprint at
[DockerCloud](https://raw.githubusercontent.com/brooklyncentral/clocker/master/examples/src/main/java/brooklyn/clocker/example/DockerCloud.java)
or [docker-cloud.yaml](https://raw.githubusercontent.com/brooklyncentral/clocker/master/examples/src/main/assembly/files/blueprints/docker-cloud.yaml).

### Using the latest Clocker release

You can build a *Docker Cloud Infrastructure* running these commands:
```Bash
    % wget --no-check-certificate --quiet \
      -O brooklyn-clocker-examples-0.6.2-dist.tar.gz http://git.io/O--JKw
    % tar zxf brooklyn-clocker-examples-0.6.2-dist.tar.gz
    % cd brooklyn-clocker-examples-0.6.2
    % ./clocker.sh launch --cloud --location <location>
```
Where `<location>` can be e.g. `jclouds:softlayer`, or a named location or a fixed IP e.g. `byon:(hosts="1.2.3.4")`.
Those simple steps will give you a running docker instance on your favourite cloud.

For more information on setting up locations, including supplying cloud provider credentials, see the [_Setting up Locations_ section of
Brooklyn Getting Started](https://brooklyn.incubator.apache.org/quickstart/#configuring-a-location), and the more detailed [locations guide](https://brooklyn.incubator.apache.org/v/0.7.0-M1/use/guide/locations/index.html).

**Important**: Please be sure that the location allows incoming connections on TCP ports *2375-2376* (the Docker daemon) and in
the range *49000-49900* used by Docker to map container ports onto ports on the host's public IP address. If you create a
security group on AWS called _docker_ this will be used automatically by the application.

The Brooklyn web-console, which will be deploying and managing your Docker Cloud, can be accessed at [http://localhost:8081](http://localhost:8081) - this URL will have been written to standard out during startup.

Once the `DockerCloud`  application has started, a new location named `my-docker-cloud` will be
available in the Locations drop-down list when adding new applications. Simply start a new application in this location
and it will use Docker containers instead of virtual machines.

For more information on deploying applications from the Brooklyn catalog, see [Getting Started - Policies and Catalogs](https://brooklyn.incubator.apache.org/quickstart/policies-and-catalogs.html). You can also paste a YAML blueprint (via "Add Application" -> "YAML"):

```Yaml
location: my-docker-cloud
services:
- type: brooklyn.entity.webapp.jboss.JBoss7Server
  brooklyn.config:
    wars.root: http://search.maven.org/remotecontent?filepath=io/brooklyn/example/brooklyn-example-hello-world-sql-webapp/0.6.0-M2/brooklyn-example-hello-world-sql-webapp-0.6.0-M2.war
```

### Building from source

Build and run the examples as follows:

```Bash
    % git clone https://github.com/brooklyncentral/clocker.git
    ...
    % cd clocker
    % mvn clean install
    ...
    % cd examples
    % mvn assembly:single
    ...
    % cd target
    % tar zxf brooklyn-clocker-examples-0.7.0-SNAPSHOT-dist.tar.gz
    % cd brooklyn-clocker-examples-0.7.0-SNAPSHOT
    % ./clocker.sh launch --cloud --location <location>
    ...
```

## Getting involved

Clocker is Apache 2.0 licensed, and builds on Apache Brooklyn. Please get involved and join the discussion on [Freenode](http://freenode.net/), IRC `#brooklyncentral` or the Apache Brooklyn community [mailing list](https://brooklyn.incubator.apache.org/community/).


### Documentation

Please visit the [wiki](https://github.com/brooklyncentral/clocker/wiki) for more details.

----
Copyright 2014 by Cloudsoft Corporation Limited

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
