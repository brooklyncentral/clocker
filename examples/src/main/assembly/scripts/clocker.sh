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

ROOT=$(cd "$(dirname "$0")/.." && pwd -P)

# discover BROOKLYN_HOME if not set, by attempting to resolve absolute path of this command
if [ -z "$BROOKLYN_HOME" ] ; then
    BROOKLYN_HOME=$(cd "$(dirname "$(readlink -f "$0" 2> /dev/null || readlink "$0" 2> /dev/null || echo "$0")")/.." && pwd)
fi

# set blueprint and catalog options
if [ $# -eq 1 ] ; then
    CLOCKER="--app ${ROOT}/blueprints/docker-cloud.yaml --location $1"
fi
export JAVA_OPTS="-Xms1g -Xmx1g -Dbrooklyn.catalog.url=classpath://catalog.xml -Dbrooklyn.catalog.mode=LOAD_BROOKLYN_CATALOG_URL"

# launch clocker
${ROOT}/bin/brooklyn.sh launch ${CLOCKER}