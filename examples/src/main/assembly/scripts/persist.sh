#!/bin/bash
#
# Copyright 2014-2015 by Cloudsoft Corporation Limited
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

# set persistence flags
export PERSISTENCE_FLAGS="--ignorePersistenceStartupErrors --persist auto --persistenceDir ${PERSISTENCE_DIR:-${ROOT}/data}"

# launch clocker
${ROOT}/bin/clocker.sh $@