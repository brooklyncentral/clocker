/*
 * Copyright 2014-2015 by Cloudsoft Corporation Limited
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
package brooklyn.clocker.example;

import static brooklyn.event.basic.DependentConfiguration.attributeWhenReady;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import org.apache.brooklyn.api.catalog.Catalog;
import org.apache.brooklyn.api.entity.proxying.EntitySpec;
import org.apache.brooklyn.entity.nosql.redis.RedisStore;
import org.apache.brooklyn.entity.webapp.nodejs.NodeJsWebAppService;

import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.basic.StartableApplication;
import brooklyn.entity.container.DockerUtils;
import brooklyn.entity.trait.Startable;
import brooklyn.event.basic.DependentConfiguration;

/**
 * Node.JS Todo Application
 */
@Catalog(name="NodeJS Todo",
        description="Node.JS Todo Application, with a Redis store",
        iconUrl="classpath://nodejs-logo.png")
public class NodeJsTodoApplication extends AbstractApplication implements StartableApplication {

    @Override
    public void initApp() {
        RedisStore redis = addChild(EntitySpec.create(RedisStore.class)
                .configure(SoftwareProcess.SUGGESTED_VERSION, "3.0.0"));

        addChild(EntitySpec.create(NodeJsWebAppService.class)
                .configure(NodeJsWebAppService.APP_GIT_REPOSITORY_URL, "https://github.com/grkvlt/nodejs-todo/")
                .configure(NodeJsWebAppService.APP_FILE, "server.js")
                .configure(NodeJsWebAppService.APP_NAME, "nodejs-todo")
                .configure(NodeJsWebAppService.NODE_PACKAGE_LIST,
                        ImmutableList.of("express", "ejs", "jasmine-node", "underscore", "method-override", "cookie-parser", "express-session", "body-parser", "cookie-session", "redis", "redis-url", "connect"))
                .configure(SoftwareProcess.SHELL_ENVIRONMENT,
                        ImmutableMap.<String, Object>of("REDISTOGO_URL", DependentConfiguration.formatString("redis://%s/", attributeWhenReady(redis, DockerUtils.mappedPortSensor(RedisStore.REDIS_PORT)))))
                .configure(SoftwareProcess.LAUNCH_LATCH, attributeWhenReady(redis, Startable.SERVICE_UP)));
    }

}
