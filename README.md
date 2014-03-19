Brooklyn Docker
================

You can use Brooklyn to install Docker onto an existing machine, or to install Docker onto a new cloud machine with your favourite cloud provider / cloud API.

To use the Brooklyn docker entity for installing docker on the host machine, there is an example blueprint at:
   [SingleDockerHostExample](<FIXME LINK TO JAVA FILE>)
   <ADD LINK TO YAML WHEN READY (BUT DO NOT SLOW DOWN BLOG FOR IT)>

    % cd docker-examples
    % mvn clean install assembly:single

    % cd target
    % tar zxvf brooklyn-docker-examples-0.1.0-SNAPSHOT-dist.tar.gz
    % cd brooklyn-docker-examples-0.1.0-SNAPSHOT
    % ./start.sh docker --location <favouriteCloud>

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
