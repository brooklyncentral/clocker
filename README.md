Brooklyn Docker
===============

This project contains Brooklyn entities and examples for [Docker](http://www.docker.io).

### Getting started

This project requires a Docker instance that Brooklyn will use as a target location when deploying application blueprints. 

You can use Brooklyn to install Docker onto an existing machine, or to install Docker onto a new cloud machine with your
favourite cloud provider or cloud API.

To use the Brooklyn docker entity for installing docker on the host machine, there is an example blueprint at
[BasicInfrastructure](https://raw.githubusercontent.com/cloudsoft/brooklyn-docker/master/docker-examples/src/main/java/io/cloudsoft/docker/example/BasicInfrastructure.java)
or [docker-infrastructure.yaml](https://raw.githubusercontent.com/cloudsoft/brooklyn-docker/master/docker-examples/src/main/assembly/files/blueprints/docker-infrastructure.yaml).
    
    % mvn clean install
    % cd docker-examples
    % mvn clean install assembly:single

    % cd target
    % tar zxvf brooklyn-docker-examples-0.3.0-SNAPSHOT-dist.tar.gz
    % cd brooklyn-docker-examples-0.3.0-SNAPSHOT
    % ./start.sh launch --infrastructure --location cloud

Where `cloud` can be e.g. `jclouds:softlayer`, or a named location or a fixed IP e.g. `byon:(hosts="1.2.3.4")`. Those
simple steps will give you a running docker instance on your favourite cloud.

**Important**: Please be sure that docker host allows incoming connection on TCP port *4243* (docker daemon) and on
the range *49000-49900* used by docker to map containers' ports on the host's ports. If you create a security group
on AWS called _docker_ this will be used by the application.

For more information on setting up locations, see the "Setting up Locations" section of
[Brooklyn Getting Started](http://brooklyncentral.github.io/use/guide/quickstart/index.html), and the "Off-the-shelf
Locations" section of [Brooklyn Common Usage](http://brooklyncentral.github.io/use/guide/defining-applications/common-usage.html).

Once the Docker infrastructure application has started, a new location named `docker-infrastructure` will be
available in the Locations drop-doen lost when adding new applications. Simply start a new application in this location
and it will use Docker containers instead of virtual machines.

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
