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
#
# sshd
#
# VERSION 0.0.2

FROM centos
MAINTAINER Cloudsoft "brooklyn@cloudsoftcorp.com"

# install packages
RUN yum -y groupinstall 'Development Tools'
RUN yum -y install sudo openssh-server openssh-clients openssl-devel

# setup root account
RUN ssh-keygen -q -N "" -t dsa -f /etc/ssh/ssh_host_dsa_key && ssh-keygen -q -N "" -t rsa -f /etc/ssh/ssh_host_rsa_key && sed -i "s/#UsePrivilegeSeparation.*/UsePrivilegeSeparation no/g" /etc/ssh/sshd_config && sed -i "s/UsePAM.*/UsePAM no/g" /etc/ssh/sshd_config
RUN echo 'root:${entity.password}' | chpasswd

# install sshd
RUN chkconfig sshd on
RUN service sshd start

EXPOSE 22

CMD [ "/usr/sbin/sshd", "-D" ]