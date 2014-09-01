[#ftl]
#
#  Copyright 2014 by Cloudsoft Corporation Limited
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
#
# sshd
#
# VERSION 0.0.4

FROM ubuntu:12.04
MAINTAINER Cloudsoft "brooklyn@cloudsoftcorp.com"

# make sure the package repository is up to date
RUN ( echo "deb http://archive.ubuntu.com/ubuntu precise main universe" ; echo "deb http://archive.ubuntu.com/ubuntu precise-updates main universe" ) > /etc/apt/sources.list
RUN apt-get update
RUN locale-gen en_US.UTF-8

# setup root account
RUN echo 'root:${entity.password}' | chpasswd

# install sshd
RUN apt-get install -y openssh-server
RUN mkdir /var/run/sshd
RUN chmod 600 /var/run/sshd
RUN /usr/sbin/sshd

EXPOSE 22

CMD [ "/usr/sbin/sshd", "-D" ]
