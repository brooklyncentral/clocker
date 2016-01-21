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
package brooklyn.location.docker.strategy;

import java.util.Map;

import com.google.common.base.Function;

import org.apache.brooklyn.entity.software.base.SoftwareProcess;

/**
 * Provisioning strategy for new Docker hosts.
 */
public interface DockerAwareProvisioningStrategy extends Function<Map<String,Object>,Map<String,Object>> {

    /**
     * Transform a set of {@link SoftwareProcess#PROVISIONING_PROPERTIES provisioning flags} to
     * implement a particular provisioning strategy.
     */
    @Override
    Map<String,Object> apply(Map<String,Object> context);

}
