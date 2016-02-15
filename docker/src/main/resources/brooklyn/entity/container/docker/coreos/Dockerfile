[#ftl]
#
#  Copyright 2014-2015 by Cloudsoft Corporation Limited
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#  http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.

FROM ubuntu:12.04
MAINTAINER Cloudsoft "brooklyn@cloudsoftcorp.com"

# CLOCKER_VERSION_BELOW
LABEL version="1.1.0-PREVIEW.20160215"

# make sure the package repository is up to date
RUN ( echo "deb http://archive.ubuntu.com/ubuntu precise main universe" ; echo "deb http://archive.ubuntu.com/ubuntu precise-updates main universe" ) > /etc/apt/sources.list
RUN apt-get update ; \
    apt-get clean

# install go from source
RUN DEBIAN_FRONTEND=noninteractive apt-get install -qy build-essential curl git ; \
    curl -s https://go.googlecode.com/files/go1.2.1.src.tar.gz | tar -v -C /usr/local -xz ; \
    cd /usr/local/go/src ; \
    ./make.bash --no-clean 2>&1
ENV PATH /usr/local/go/bin:[#noparse]$PATH[/#noparse]
ADD . /opt/etcd
RUN cd /opt/etcd ; \
    ./build

EXPOSE 4001 7001

ENTRYPOINT [ "/opt/etcd/bin/etcd" ]
