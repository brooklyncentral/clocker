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

import javax.ws.rs.core.Response;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;

import org.apache.brooklyn.rest.client.BrooklynApi;
import org.apache.brooklyn.rest.domain.Status;
import org.apache.brooklyn.rest.domain.TaskSummary;
import org.apache.brooklyn.util.collections.MutableSet;
import org.apache.brooklyn.util.core.text.TemplateProcessor;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.os.Os;
import org.apache.brooklyn.util.repeat.Repeater;
import org.apache.brooklyn.util.time.Duration;

public class BlueprintIntegrationTest {

    private static final Logger LOG = LoggerFactory.getLogger(BlueprintIntegrationTest.class);
    private static final String BLUEPRINT_DIRECTORY = "src/test/resources/blueprints/";

    private final static String LOCATION_NAME_SOFTLAYER = "my-docker-cloud-softlayer";
    private final static String LOCATION_NAME_SOFTLAYER_CALICO = "my-docker-cloud-softlayer-calico";
    private final static String LOCATION_NAME_SOFTLAYER_WEAVE = "my-docker-cloud-softlayer-weave";

    private final static String LOCATION_NAME_AWS = "my-docker-cloud-aws";
    private final static String LOCATION_NAME_AWS_CALICO = "my-docker-cloud-aws-calico";
    private final static String LOCATION_NAME_AWS_WEAVE = "my-docker-cloud-aws-weave";

    private final static int MINS_TO_WAIT_FOR_TEST = 40;
    private final static int MINS_TO_WAIT_FOR_CLOCKER_INFRASTRUCTURE = 20;
    private final static int MINS_TO_WAIT_FOR_APPLICATION_UNDEPLOY = 5;
    private final static int SECONDS_TO_WAIT_FOR_BROOKLYN_STARTUP = 15;


    private Set<String> deployedInfrastructures = MutableSet.of();

    private String brooklynUrl;

    @DataProvider(name = "testDataProvider", parallel = true)
    public static Object[][] data() {
        return new Object[][]{
                {"tomcat-application.yaml", LOCATION_NAME_SOFTLAYER},
                {"tomcat-application.yaml", LOCATION_NAME_SOFTLAYER_CALICO},
                {"tomcat-application.yaml", LOCATION_NAME_SOFTLAYER_WEAVE},
                {"tomcat-application.yaml", LOCATION_NAME_AWS},
                {"tomcat-application.yaml", LOCATION_NAME_AWS_CALICO},
                {"tomcat-application.yaml", LOCATION_NAME_AWS_WEAVE},

                {"riak_demo.yaml", LOCATION_NAME_SOFTLAYER},
                {"riak_demo.yaml", LOCATION_NAME_SOFTLAYER_CALICO},
                {"riak_demo.yaml", LOCATION_NAME_SOFTLAYER_WEAVE},
                {"riak_demo.yaml", LOCATION_NAME_AWS},
                {"riak_demo.yaml", LOCATION_NAME_AWS_CALICO},
                {"riak_demo.yaml", LOCATION_NAME_AWS_WEAVE},

                {"nodejs-demo.yaml", LOCATION_NAME_SOFTLAYER},
                {"nodejs-demo.yaml", LOCATION_NAME_SOFTLAYER_CALICO},
                {"nodejs-demo.yaml", LOCATION_NAME_SOFTLAYER_WEAVE},
                {"nodejs-demo.yaml", LOCATION_NAME_AWS},
                {"nodejs-demo.yaml", LOCATION_NAME_AWS_CALICO},
                {"nodejs-demo.yaml", LOCATION_NAME_AWS_WEAVE}
        };
    }


    @BeforeClass(alwaysRun = true)
    @Parameters({"brooklyn"})
    public void setSpec(String brooklyn) {
        brooklynUrl = brooklyn;
        LOG.info("Running {} in {}", getClass().getName(), brooklyn);

        //Wait for brookyln to be fully initalised
        //Could be replaced with a call to REST API /v1/server/healthy
        try {
            Thread.sleep(SECONDS_TO_WAIT_FOR_BROOKLYN_STARTUP * 1000l);
        } catch (InterruptedException e) {
            return;
        }

        //Deploy different Clocker versions
        Set<DockerInfrastructureCreator> dockerInfrastructureCreators = MutableSet.of();

        dockerInfrastructureCreators.add(new DockerInfrastructureCreator(Os.mergePaths(BLUEPRINT_DIRECTORY, "docker-cloud-softlayer.yaml")));
        dockerInfrastructureCreators.add(new DockerInfrastructureCreator(Os.mergePaths(BLUEPRINT_DIRECTORY, "docker-cloud-softlayer-weave.yaml")));
        dockerInfrastructureCreators.add(new DockerInfrastructureCreator(Os.mergePaths(BLUEPRINT_DIRECTORY, "docker-cloud-softlayer-calico.yaml")));

        dockerInfrastructureCreators.add(new DockerInfrastructureCreator(Os.mergePaths(BLUEPRINT_DIRECTORY, "docker-cloud-aws.yaml")));
        dockerInfrastructureCreators.add(new DockerInfrastructureCreator(Os.mergePaths(BLUEPRINT_DIRECTORY, "docker-cloud-aws-weave.yaml")));
        dockerInfrastructureCreators.add(new DockerInfrastructureCreator(Os.mergePaths(BLUEPRINT_DIRECTORY, "docker-cloud-aws-calico.yaml")));

        ExecutorService executor = Executors.newCachedThreadPool();

        try {
            List<Future<String>>  futures = executor.invokeAll(dockerInfrastructureCreators, MINS_TO_WAIT_FOR_CLOCKER_INFRASTRUCTURE, TimeUnit.MINUTES);

            for (Future<String> future : futures) {
                String applicationId = future.get();
                deployedInfrastructures.add(applicationId);
            }
        } catch (InterruptedException e) {
            LOG.info(e.getLocalizedMessage());
            return;
        } catch (ExecutionException e) {
            LOG.info(e.getLocalizedMessage());
            return;
        } finally {
            executor.shutdown();
        }
    }

    @AfterSuite
    public void tearDown() {
        BrooklynApi api = new BrooklynApi(brooklynUrl);
        for (String deployedInfrastructure : deployedInfrastructures) {
            try {
                api.getEffectorApi().invoke(deployedInfrastructure, deployedInfrastructure, "stop", "never", ImmutableMap.<String, Object>of());
            } catch(Exception e){
                LOG.info("Exception when shutting down docker infrastructure {} continuing. Exception {}", deployedInfrastructure, e.getLocalizedMessage());
            }
        }
    }

    @Test(dataProvider = "testDataProvider")
    public void loadAndTestTemplate(String blueprintName, String location) {
        String blueprint = TemplateProcessor.processTemplateFile(Os.mergePaths(BLUEPRINT_DIRECTORY, blueprintName),
                ImmutableMap.of("location", location,
                        "id", blueprintName));

        testBlueprintDeploys(blueprint, true, MINS_TO_WAIT_FOR_TEST);
    }

    class DockerInfrastructureCreator implements Callable<String> {
        private final String blueprintName;

        DockerInfrastructureCreator(String blueprintName) {
            this.blueprintName = blueprintName;
        }

        public String call() {
            return testBlueprintDeploys(loadBlueprint(blueprintName), false, MINS_TO_WAIT_FOR_CLOCKER_INFRASTRUCTURE);
        }
    }


    public String testBlueprintDeploys(String blueprint, boolean stopAfter, int minsToWait) {
        BrooklynApi api = new BrooklynApi(brooklynUrl);
        Response r = api.getApplicationApi().createFromYaml(blueprint);
        final TaskSummary task = BrooklynApi.getEntity(r, TaskSummary.class);
        final String application = task.getEntityId();
        try {
            assertAppStatusEventually(api, application, Status.RUNNING, minsToWait);
        } catch (Throwable e){
            LOG.info("Caught exception in testBlueprintDeploys, rethrowing "+e.getLocalizedMessage());
            throw e;
        } finally {
            if (stopAfter) {
                LOG.info("Stopping {}", application);
                api.getEffectorApi().invoke(application, application, "stop", MINS_TO_WAIT_FOR_APPLICATION_UNDEPLOY+"m",
                        ImmutableMap.<String, Object>of());
            }
        }

        return application;
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
     *
     * @return the final polled status
     */
    protected Status assertAppStatusEventually(final BrooklynApi api, final String application, final Status desiredStatus, int minsToWait) {
        final AtomicReference<Status> appStatus = new AtomicReference<Status>(Status.UNKNOWN);
        final Duration timeout = Duration.of(minsToWait, TimeUnit.MINUTES);
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

        LOG.info("Application status found out " + application + " is " + appStatus.get().name() + " desired status " + desiredStatus.name()+ " are they equal? "+appStatus.get().equals(desiredStatus));

        assert appStatus.get().equals(desiredStatus);
        return appStatus.get();
    }

}
