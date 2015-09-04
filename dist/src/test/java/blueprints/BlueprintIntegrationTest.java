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
package blueprints;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;

import org.apache.brooklyn.rest.client.BrooklynApi;
import org.apache.brooklyn.rest.domain.Status;
import org.apache.brooklyn.rest.domain.TaskSummary;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.repeat.Repeater;
import org.apache.brooklyn.util.time.Duration;

public class BlueprintIntegrationTest {

    private static final Logger LOG = LoggerFactory.getLogger(BlueprintIntegrationTest.class);
    private static final String BLUEPRINT_DIRECTORY = "src/main/assembly/files/blueprints/";

    String brooklynUrl;

    @BeforeClass(alwaysRun = true)
    @Parameters({"brooklyn"})
    public void setSpec(String brooklyn) {
        brooklynUrl = brooklyn;
        LOG.info("Running {} in {}", getClass().getName(), brooklyn);
    }

    public void testBlueprintDeploys(String name) {
        BrooklynApi api = new BrooklynApi(brooklynUrl);
        LOG.info("Testing " + name);
        String blueprint = loadBlueprint(name);
        Response r = api.getApplicationApi().createFromYaml(blueprint);
        final TaskSummary task = BrooklynApi.getEntity(r, TaskSummary.class);
        final String application = task.getEntityId();
        try {
            assertAppStatusEventually(api, application, Status.RUNNING);
        } finally {
            LOG.info("Stopping {} (deployed from {})", application, name);
            api.getEffectorApi().invoke(application, application, "stop", "never",
                    ImmutableMap.<String, Object>of());
        }
    }

    @Test(groups = "Integration")
    public void testNodeJsTodo() {
        testBlueprintDeploys(BLUEPRINT_DIRECTORY + "nodejs-todo.yaml");
    }

    @Test(groups = "Integration")
    public void testCouchbase() {
        testBlueprintDeploys(BLUEPRINT_DIRECTORY + "couchbase.yaml");
    }

    @Test(groups = "Integration")
    public void testCouchbaseWithPillofight() {
        testBlueprintDeploys(BLUEPRINT_DIRECTORY + "couchbase-with-pillowfight.yaml");
    }

    @Test(groups = "Integration")
    public void testDockerfilMysql() {
        testBlueprintDeploys(BLUEPRINT_DIRECTORY + "dockerfile-mysql.yaml");
    }

    @Test(groups = "Integration")
    public void testRiakWebappCluster() {
        testBlueprintDeploys(BLUEPRINT_DIRECTORY + "riak-webapp-cluster.yaml");
    }

    @Test(groups = "Integration")
    public void testTomcatApplication() {
        testBlueprintDeploys(BLUEPRINT_DIRECTORY + "tomcat-application.yaml");
    }

    @Test(groups = "Integration")
    public void testTomcatApplicationWithVolumes() {
        testBlueprintDeploys(BLUEPRINT_DIRECTORY + "tomcat-application-with-volumes.yaml");
    }

    @Test(groups = "Integration")
    public void testTomcatClusterWithMysql() {
        testBlueprintDeploys(BLUEPRINT_DIRECTORY + "tomcat-cluster-with-mysql.yaml");
    }

    @Test(groups = "Integration")
    public void testTomcatSolrApplication() {
        testBlueprintDeploys(BLUEPRINT_DIRECTORY + "tomcat-solr-application.yaml");
    }

    private String loadBlueprint(String name) {
        try {
            return Joiner.on('\n').join(Files.readLines(new File(name), Charsets.UTF_8));
        } catch (IOException e) {
            throw Exceptions.propagate(e);
        }
    }

    /**
     * Polls Brooklyn until the given application has the given status. Quits early if the application's
     * status is {@link brooklyn.rest.domain.Status#ERROR} or {@link brooklyn.rest.domain.Status#UNKNOWN}
     * and desiredStatus is something else.
     * @return the final polled status
     */
    protected Status assertAppStatusEventually(final BrooklynApi api, final String application, final Status desiredStatus) {
        final AtomicReference<Status> appStatus = new AtomicReference<Status>(Status.UNKNOWN);
        final Duration timeout = Duration.of(10, TimeUnit.MINUTES);
        final boolean shortcutOnError = !Status.ERROR.equals(desiredStatus) && !Status.UNKNOWN.equals(desiredStatus);
        LOG.info("Waiting " + timeout + " for application " + application + " to be " + desiredStatus);
        boolean finalAppStatusKnown = Repeater.create("Waiting for application " + application + " status to be " + desiredStatus)
                .every(Duration.FIVE_SECONDS)
                .limitTimeTo(timeout)
                .rethrowExceptionImmediately()
                .until(new Callable<Boolean>() {
                    @Override
                    public Boolean call() throws Exception {
                        Status status = api.getApplicationApi().get(application).getStatus();
                        LOG.debug("Application " + application + " status is: " + status);
                        appStatus.set(status);
                        return desiredStatus.equals(status) || (shortcutOnError &&
                                (Status.ERROR.equals(status) || Status.UNKNOWN.equals(status)));
                    }
                })
                .run();
        if (appStatus.get().equals(desiredStatus)) {
            LOG.info("Application " + application + " is " + desiredStatus.name());
        } else {
            String message = "Application is not " + desiredStatus.name() + " within " + timeout +
                    ". Status is: " + appStatus.get();
            LOG.error(message);
            throw new RuntimeException(message);
        }
        return appStatus.get();
    }

}
