Brooklyn Docker
===============

This project contains Brooklyn entities and examples for [Docker](http://www.docker.io).

### Getting started
This project requires a Docker instance that Brooklyn will use as a target location when deploying application blueprints. 

We suggest two ways to deploy Docker: choose one of them!

#### Option 1: Installing Docker using Brooklyn docker entity
You can use Brooklyn to install Docker onto an existing machine, or to install Docker onto a new cloud machine with your favourite cloud provider / cloud API.

To use the Brooklyn docker entity for installing docker on the host machine, there is an example blueprint at: [SingleDockerHostExample](https://github.com/cloudsoft/brooklyn-docker/blob/master/docker-examples/src/main/java/io/cloudsoft/docker/example/SingleDockerHostExample.java)

    % cd docker-examples
    % mvn clean install assembly:single

    % cd target
    % tar zxvf brooklyn-docker-examples-0.1.0-SNAPSHOT-dist.tar.gz
    % cd brooklyn-docker-examples-0.1.0-SNAPSHOT
    % ./start.sh docker --location <favouriteCloud>

Where `<favouriteCloud>` can be e.g. `jclouds:softlayer`, or a named location or a fixed IP e.g. `byon:(hosts="1.2.3.4")`.
Those simple steps will give you a running docker instance on your favourite cloud.

**N.B.**: Please be sure that docker host allows incoming connection on TCP port `4243` (docker daemon) and on the range `49000..49900` used by docker to map containers' ports on the host's ports.

For more information on setting up locations, see the "Setting up Locations" section of [Brooklyn Getting Started](http://brooklyncentral.github.io/use/guide/quickstart/index.html), 
and the "Off-the-shelf Locations" section of [Brooklyn Common Usage](http://brooklyncentral.github.io/use/guide/defining-applications/common-usage.html).

#### Option 2: Installing Docker manually

Follow the [Getting Started](http://docs.docker.io/en/latest/installation/) guide and enable remote access to the docker API; on the host:
   `echo 'DOCKER_OPTS="-H tcp://0.0.0.0:4243"' | sudo tee -a /etc/default/docker`

###### Install the docker client (Optional step, for convenience of manually connecting to docker)
On OS X, see [official guide](http://docs.docker.io/en/latest/installation/mac/) or use homebrew:
    `brew install docker`
       
and point at your docker host:
    `export DOCKER_HOST=tcp://<host>:4243`

### Create a docker image that is ssh'able
Now that you have a Docker instance running, we need to create images that contain an ssh server so that brooklyn can reach them.

The following instructions will help you in running a new container, installing and starting sshd on it, and committing that to create a new image. See [SSH Daemon Service](http://docs.docker.io/en/latest/examples/running_ssh_service/) for more background.

    
	# First import a standard ubuntu image
	docker pull ubuntu

	# Start a new container
	docker run -i -t ubuntu:12.04 /bin/bash

	# From inside the container, set up sshd and the initial login credentials
	apt-get update
	apt-get install -y openssh-server
	mkdir /var/run/sshd
	/usr/sbin/sshd
	echo 'root:password' | chpasswd
	exit

	# Then from the host (or remote docker client)
	# grab the containerId of the customized container
	docker ps -a
	CONTAINER_ID=<containerId>

	IMAGE_NAME=<yourName>/ubuntu
	IMAGE_ID=$(docker commit $CONTAINER_ID $IMAGE_NAME)
	
#### Docker commands to confirm the IMAGE_ID is ssh'able:
        CONTAINER_ID=$(docker run -t -d -p 22 $IMAGE_NAME /usr/sbin/sshd -D)
        PORT=$(docker port $CONTAINER_ID 22|cut -d: -f2)
        ssh -p $PORT root@<host>

#### Useful docker commands to sanity check your setup:
- `docker images --no-trunc` to list all available images (showing full image ids)
- `docker run -i -t $IMAGE_NAME /bin/bash` to create a new docker container in interactive mode
- `docker ps -a` to list all containers; the `-a` says to include stopped images
- `docker ps -a -q | xargs docker stop | xargs docker rm` to stop and remove all containers
- `docker run -t -d -p 22 $IMAGE_NAME` to create a container, exposing port 22.
- `docker ps` to see port-mappings.
- `ssh -p <mapped-port> root@<hostAddress>` (with password "password", as supplied when creating the image) to confirm
  the new container is ssh'able (where `<mapped-port>` is the port mapped to 22 for that container, as shown by `docker ps`.

### Give it a go!

Now you have all the prerequisites satisfied so you can start playing with Brooklyn and Docker.

* Create a section on your `~/.brooklyn/brooklyn.properties` as follows:

    ```bash
    # Defines a location named “docker” that can be used as a target when
    # deploying applications
    brooklyn.location.named.docker=jclouds:docker:http://<docker-host>:4243
    # Credentials currently ignored by docker API
    brooklyn.location.named.docker.identity=notused
    brooklyn.location.named.docker.credential=notused
    # Choosing a particular image
    # from the list returned by `docker images --no-trunc`
    brooklyn.location.named.docker.imageId=<IMAGE ID>
    
    # by default, the ssh credentials used are `root/password`
    # if your image has a different account uncomment the following:
    #brooklyn.location.named.docker.loginUser=<user>
    #brooklyn.location.named.docker.loginUser.password=<password>

To run a simple web app app on a single Docker container, try:
* Checkout `brooklyn-docker` repo locally (with `git checkout https://github.com/cloudsoft/brooklyn-docker.git`)

    ```bash
    cd docker-examples
    mvn clean install assembly:single
    cd target
    tar zxvf brooklyn-docker-examples-0.1.0-SNAPSHOT-dist.tar.gz
    cd brooklyn-docker-examples-0.1.0-SNAPSHOT
    ./start.sh launch --app io.cloudsoft.docker.example.SingleWebServerExample --location named:docker
    ```
    where, "named:docker" matches the name you used in `brooklyn.properties` for `brooklyn.location.named.docker`.

* View the Brooklyn web-console to see the state of the app - the URL will be written to stdout by the `brooklyn launch`; by default it will be `http://localhost:8081`

To run a more interesting app that spans multiple Docker containers, try:
* `./start.sh launch --app io.cloudsoft.docker.example.WebClusterDatabaseExample --location named:docker`


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
