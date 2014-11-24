#!/bin/bash
#
# Copyright 2014 by Cloudsoft Corporation Limited
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

#set -x # debug

# get base directory
ROOT=$(cd "$(dirname "$0")/.." && pwd -P)

# check command line arguments for location
if [ $# -eq 2 ] ; then
    LAUNCH_FLAGS="--app $2 --location $1"
elif [ $# -eq 1 ] ; then
    LAUNCH_FLAGS="--app ${ROOT}/blueprints/docker-cloud.yaml --location $1"
elif [ $# -ne 0 ] ; then
    echo "Too many arguments; Usage: clocker.sh [location] [blueprint]"
    exit 1
fi

# set catalog and java options
CATALOG_OPTS="-Dbrooklyn.catalog.url=classpath://catalog.xml -Dbrooklyn.catalog.mode=LOAD_BROOKLYN_CATALOG_URL"
JAVA_OPTS="${JAVA_OPTS:--Xms1g -Xmx1g} ${CLOCKER_OPTS} ${CATALOG_OPTS}"
export JAVA_OPTS

# launch clocker
${ROOT}/bin/brooklyn.sh clocker ${LAUNCH_FLAGS} \
    --ignoreManagedAppsStartupErrors \
    --ignorePersistenceStartupErrors \
    --persist auto \
    --persistenceDir ${HOME}/.clocker \
    --stopOnShutdown none 2>&1 | tee -a ${ROOT}/console.log
