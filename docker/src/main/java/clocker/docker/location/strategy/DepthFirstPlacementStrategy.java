/*
 * Copyright 2014-2016 by Cloudsoft Corporation Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package clocker.docker.location.strategy;

import clocker.docker.location.DockerHostLocation;

import com.google.common.collect.Ordering;

/**
 * Placement strategy that adds containers to Docker hosts in order.
 */
public class DepthFirstPlacementStrategy extends BasicDockerPlacementStrategy {

    /** Use an arbitrary, but fixed, ordering. */
    @Override
    public int compare(DockerHostLocation l1, DockerHostLocation l2) {
        return Ordering.arbitrary().compare(l1, l2);
    }

}
