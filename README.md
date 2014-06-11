Clocker
=======

Clocker contains Brooklyn entities, locations and examples that create a [Docker](http://www.docker.io) cloud infrastructure.

### Getting started

This project requires a Docker instance that Brooklyn will use as a target location when deploying application blueprints. 

You can use Brooklyn to install Docker onto a single existing machine, or create a Docker based cloud infrastructure on
your favourite cloud provider or on a private cloud using any of the jclouds supported APIs.

To install a Docker cloud infrastucture using the Clocker Brooklyn entities, there is an example blueprint at
[DockerCloud](https://raw.githubusercontent.com/brooklyncentral/clocker/master/examples/src/main/java/brooklyn/clocker/example/DockerCloud.java)
or [docker-cloud.yaml](https://raw.githubusercontent.com/brooklyncentral/clocker/master/examples/src/main/assembly/files/blueprints/docker-cloud.yaml).

Build and run the examples as follows:

```Bash
    % mvn clean install
    % cd examples
    % mvn clean install assembly:single

    % cd target
    % tar zxf brooklyn-clocker-examples-0.3.0-dist.tar.gz
    % cd brooklyn-clocker-examples-0.3.0
    % ./clocker.sh launch --cloud --location location
```

Where `location` can be e.g. `jclouds:softlayer`, or a named location or a fixed IP e.g. `byon:(hosts="1.2.3.4")`. Those
simple steps will give you a running docker instance on your favourite cloud.

**Important**: Please be sure that the location allows incoming connections on TCP port *4243* (the Docker daemon) and in
the range *49000-49900* used by Docker to map container ports onto ports on the hosts public IP address. If you create a
security group on AWS called _docker_ this will be used automatically by the application.

For more information on setting up locations, see the _Setting up Locations_ section of
[Brooklyn Getting Started](http://brooklyncentral.github.io/use/guide/quickstart/index.html), and the "Off-the-shelf
Locations" section of [Brooklyn Common Usage](http://brooklyncentral.github.io/use/guide/defining-applications/common-usage.html).

Once the `DockerCloud`  application has started, a new location named `my-docker-cloud` will be
available in the Locations drop-down list when adding new applications. Simply start a new application in this location
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
