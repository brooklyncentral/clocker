---
layout: base
title: Docker Container Documentation
---

### Overview
Clocker provides a base Docker container entity that will deploy a Docker Engine and then start a selected Docker image automatically in it's Docker container. This entity can be deployed in any cloud / VM location.

### Configuration 
The Docker container entity comes with built-in configuration that allows you to control how your Docker container will be deployed and managed.

| Config Name                 | Description                                                                                             |
|-----------------------------|---------------------------------------------------------------------------------------------------------|
| docker.image                | The docker image to use when running the container                                                      |
| docker.run.arguments        | Arguments to pass to the docker run command                                                             |
| docker.run.volumes          | List of volumes to mount. Items follow the documented docker format for the '-v' option                 |
| docker.run.env              | Map of environment variables to pass to the container                                                   |
| docker.restart              | Restart policy on the container. One of no, on-failure[:max-retries], always or unless-stopped          |
| image.run.additionaloptions | [Additional options](https://docs.docker.com/engine/reference/run/) to pass to the 'docker run' command |
