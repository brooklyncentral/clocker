Docker setup instructions
=========================

In order to be able to run the docker-examples, please consider the following instructions.

# Install docker on the host machine:
We suggest two different approaches here:

- using the brooklyn entity, or
- following the steps at [install docker](docs.docker.io/en/latest/installation/) and enable remote access to the docker API; on the host:
   `echo 'DOCKER_OPTS="-H tcp://0.0.0.0:4243"' | sudo tee -a /etc/default/docker`

## (Optional step, for convenience of manually connecting to docker)
### Install the docker client
   on OS X, see [official guide](http://docs.docker.io/en/latest/installation/mac/) or use homebrew:

       brew install docker
### Point at your docker host:
   export DOCKER_HOST=tcp://<host>:4243

# Create a docker image that is ssh'able
## Using a Dockerfile:

Create a `Dockerfile` similar to this:

	FROM ubuntu:12.04

	RUN apt-get update
	RUN apt-get install -y openssh-server

	RUN mkdir /var/run/sshd
	RUN /usr/sbin/sshd

	RUN echo 'root:password' | chpasswd

	EXPOSE 22
	CMD ["/usr/sbin/sshd", "-D"]

and from the folder containing the `Dockerfile` run:

    docker build -t <yourName>/ubuntu .

See the [Dockerfile](http://docs.docker.io/en/latest/reference/builder/) for more background.

## Or manually run a new container, install and start sshd, and commit that to create a new image.
   See [running_ssh_service](http://docs.docker.io/en/latest/examples/running_ssh_service/) for more background.

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
	docker commit $CONTAINER_ID $IMAGE_NAME

## Here are some simple docker commands to sanity check your setup:
- `docker images --no-trunc` to list all available images (showing full image ids)
- `docker run -i -t $IMAGE_NAME /bin/bash` to create a new docker container in interactive mode
- `docker ps -a` to list all containers; the `-a` says to include stopped images
- `docker ps -a -q | xargs docker stop | xargs docker rm` to stop and remove all containers
- `docker run -t -d -p 22 $IMAGE_NAME` to create a container, exposing port 22.
- `docker ps` to see port-mappings.
- `ssh -p <mapped-port> root@<hostAddress>` (with password "password", as supplied when creating the image) to confirm
 the new container is ssh'able.

# Docker entity instructions

You can use Brooklyn to install Docker onto an existing machine, or to install Docker onto a new cloud machine with your favourite cloud provider / cloud API.

To use the Brooklyn docker entity for installing docker on the host machine, there is an example blueprint at:
   [SingleDockerHostExample](https://github.com/cloudsoft/brooklyn-docker/blob/project-separation/docker-examples/src/main/java/io/cloudsoft/docker/example/SingleDockerHostExample.java)
   <ADD LINK TO YAML WHEN READY (BUT DO NOT SLOW DOWN BLOG FOR IT)>

* Install [brooklyn](http://brooklyncentral.github.io/use/guide/quickstart/index.html), or Cloudsoft's Application Management Platform (AMP) which is powered by Brooklyn.
* Add the docker entity, either to the catalog or to the brooklyn classpath
** If building the brooklyn-docker repo locally (with `mvn clean install`), then copy docker/target/brooklyn-docker-0.1.0-SNAPSHOT.jar to the $BROOKLYN_HOME/lib/dropin/
** Alternatively if using the catalog, then add to ~/.brooklyn/catalog.xml:

	# FIXME WHAT URL FOR SONATYPE?
	<catalog>
		<template type="io.cloudsoft.docker.example.SingleDockerHostExample" name="Docker host">
			<description>Sets up a Docker host. Docker is a tool for creating and managing lightweight containers, as an alternative to full-blown VMs.</description>
			<iconUrl>classpath://docker-top-logo.png</iconUrl>
		</template>

		<classpath>
			<entry>https://oss.sonatype.org/content/repositories/snapshots/io/cloudsoft/docker/brooklyn-docker/0.1.0-SNAPSHOT/brooklyn-docker-0.1.0-SNAPSHOT.jar</entry>
		</classpath>
	</catalog>

* Run `brooklyn launch --app io.cloudsoft.docker.example.SingleDockerHostExample --location <favouriteCloud>`
  Where the <favouriteCloud> could be a fixed IP (e.g. `byon:(hosts="1.2.3.4")`), or a cloud (e.g. `jclouds:softlayer`), or a named location.
  For more details of setting up locations, see <a href="">brooklyn docs</a>
