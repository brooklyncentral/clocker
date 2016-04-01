# Copyright 2015 by Cloudsoft Corporation Limited
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

FROM gliderlabs/alpine:3.1
MAINTAINER andrew.kennedy@cloudsoft.io

# CLOCKER_VERSION_BELOW
LABEL version="1.1.0-PREVIEW.20160402"

RUN apk-install openjdk7-jre-base ; \
    apk-install openjdk7-jre-lib ; \
    apk-install bash ; \
    apk-install wget

COPY certgen.sh /tmp/certgen.sh

RUN bash /tmp/certgen.sh /usr/lib/jvm/java-1.7-openjdk/jre/lib/security/cacerts ; \
    rm -f /tmp/certgen.sh

# CLOCKER_VERSION_BELOW
RUN wget --no-check-certificate http://ccweb.cloudsoftcorp.com/maven/libs-release-local/io/brooklyn/clocker/brooklyn-clocker-dist/1.1.0-PREVIEW.20160402/brooklyn-clocker-dist-1.1.0-PREVIEW.20160402-dist.tar.gz -O /brooklyn-clocker-dist.tar.gz ; \
    tar zxf brooklyn-clocker-dist.tar.gz ; \
    rm -f brooklyn-clocker-dist.tar.gz

WORKDIR /brooklyn-clocker

VOLUME [ "/root/.brooklyn", "/root/.ssh" ]

EXPOSE 80 443 8081 8443

ENTRYPOINT [ "./bin/clocker.sh" ]
