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
package brooklyn.entity.container.docker.application;

import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.mgmt.classloading.BrooklynClassLoadingContext;
import org.apache.brooklyn.core.resolve.entity.AbstractEntitySpecResolver;
import org.apache.brooklyn.core.resolve.entity.EntitySpecResolver;

import brooklyn.entity.container.DockerAttributes;

/**
 * Implements a custom {@link EntitySpecResolver} which knows how to convert docker image IDs
 * to Brooklyn {@link EntitySpec} instances.
 */
public class DockerEntitySpecResolver extends AbstractEntitySpecResolver {

    private static final Logger log = LoggerFactory.getLogger(DockerEntitySpecResolver.class);

    public static final String PREFIX = "docker";

    public DockerEntitySpecResolver() {
        super(PREFIX);
    }

    @Override
    public EntitySpec<?> resolve(String type, BrooklynClassLoadingContext loader, Set<String> encounteredTypes) {
        String dockerServiceType = getLocalType(type);
        List<String> parts = Splitter.on(":").splitToList(dockerServiceType);
        if (parts.isEmpty() || parts.size() > 2) {
            throw new IllegalArgumentException("Docker serviceType cannot be parsed: " + dockerServiceType);
        }
        String imageName = Iterables.get(parts, 0);
        String imageTag = Iterables.get(parts, 1, "latest");
        log.debug("Creating Docker service entity with image {} and tag {}", imageName, imageTag);

        EntitySpec<VanillaDockerApplication> spec = EntitySpec.create(VanillaDockerApplication.class);
        spec.configure(DockerAttributes.DOCKER_IMAGE_NAME, imageName);
        if (parts.size() == 2) {
            spec.configure(DockerAttributes.DOCKER_IMAGE_TAG, imageTag);
        }
        return spec;
    }
}
