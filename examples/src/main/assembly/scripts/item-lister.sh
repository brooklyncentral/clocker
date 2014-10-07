#!/bin/bash
#
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#  http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.

#set -x # debug

ROOT=$(cd "$(dirname "$0")/.." && pwd -P)

# discover BROOKLYN_HOME if not set, by attempting to resolve absolute path of this command
if [ -z "$BROOKLYN_HOME" ] ; then
    BROOKLYN_HOME=$(cd "$(dirname "$(readlink -f "$0" 2> /dev/null || readlink "$0" 2> /dev/null || echo "$0")")/.." && pwd)
fi

# use default memory settings, if not specified
if [ -z "${JAVA_OPTS}" ] ; then
    JAVA_OPTS="-Xms256m -Xmx1g -XX:MaxPermSize=256m"
fi

# set up the classpath
INITIAL_CLASSPATH=${BROOKLYN_HOME}/conf:${BROOKLYN_HOME}/lib/patch/*:${BROOKLYN_HOME}/lib/brooklyn/*:${BROOKLYN_HOME}/lib/dropins/*
# specify additional CP args in BROOKLYN_CLASSPATH
if [ ! -z "${BROOKLYN_CLASSPATH}" ]; then
    INITIAL_CLASSPATH=${BROOKLYN_CLASSPATH}:${INITIAL_CLASSPATH}
fi

# run item-lister
exec java ${JAVA_OPTS} -cp "${INITIAL_CLASSPATH}" brooklyn.cli.itemlister.ItemLister "$@"
