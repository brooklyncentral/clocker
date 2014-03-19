/*
 * Copyright 2014 by Cloudsoft Corporation Limited
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
package brooklyn.entity.container.docker;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;

import brooklyn.entity.basic.SoftwareProcessImpl;
import brooklyn.entity.trait.StartableMethods;
import brooklyn.location.Location;
import brooklyn.location.LocationDefinition;
import brooklyn.location.LocationSpec;
import brooklyn.location.basic.BasicLocationDefinition;
import brooklyn.location.basic.Machines;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.location.dynamic.DynamicLocation;
import brooklyn.location.jclouds.JcloudsLocation;
import brooklyn.location.jclouds.JcloudsSshMachineLocation;
import brooklyn.management.LocationManager;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.task.DynamicTasks;
import brooklyn.util.text.Identifiers;
import brooklyn.util.text.Strings;
import com.google.common.base.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Collection;
import java.util.Map;

/**
 * @author Andrea Turli
 */
public class DockerNodeImpl extends SoftwareProcessImpl implements DockerNode {

    private static final Logger log = LoggerFactory.getLogger(DockerNodeImpl.class);

    private JcloudsLocation machine;

    public DockerNodeImpl() {
    }

    @Override
    public Class<?> getDriverInterface() {
        return DockerDriver.class;
    }

    public int getPort() {
        return checkNotNull(getAttribute(DOCKER_PORT), "%s must not be null", DOCKER_PORT);
    }

    public String getSocketUid() {
        String result = getAttribute(DockerNode.SOCKET_UID);
        if (Strings.isBlank(result)) {
            result = Identifiers.makeRandomId(6);
            setAttribute(DockerNode.SOCKET_UID, result);
        }
        return result;
    }

    @Override
    protected void connectSensors() {
        super.connectSensors();
        //TODO implementation
        setAttribute(SERVICE_UP, true);
    }

    @Override
    protected void disconnectSensors() {
        //TODO implementation
        super.disconnectSensors();
    }

    @Override
    public void doStart(Collection<? extends Location> locations) {
        super.doStart(locations);

        Optional<SshMachineLocation> found = Machines.findUniqueSshMachineLocation(getLocations());
        JcloudsSshMachineLocation sshMachineLocation = (JcloudsSshMachineLocation) found.get();
        Map<String, ?> flags = MutableMap.<String, Object>builder()
                .putAll(getConfig(LOCATION_FLAGS))
                .put("machine", sshMachineLocation)
                .build();
        machine = createLocation(flags);
        log.info("New Docker location {} created", machine);
        setAttribute(DYNAMIC_LOCATION, machine);

        DynamicTasks.queue(StartableMethods.startingChildren(this));
    }

    @Override
    public void doStop() {
        DynamicTasks.queue(StartableMethods.stoppingChildren(this));

        LocationManager mgr = getManagementContext().getLocationManager();
        JcloudsLocation location = getDynamicLocation();
        if (location != null && mgr.isManaged(location)) {
            mgr.unmanage(location);
            setAttribute(DYNAMIC_LOCATION,  null);
        }
        super.doStop();
    }

    @Override
    public JcloudsLocation getDynamicLocation() {
        return (JcloudsLocation) getAttribute(DYNAMIC_LOCATION);
    }

    /**
     * Create a new {@link brooklyn.location.jclouds.JcloudsLocation} wrapping the machine we are starting in.
     */
    @Override
    public JcloudsLocation createLocation(Map flags) {
        String locationName = "my-docker";
        LocationSpec<JcloudsLocation> spec = LocationSpec.create(JcloudsLocation.class)
                .configure(flags)
                .configure(DynamicLocation.OWNER, this)
                .displayName(((JcloudsSshMachineLocation) flags.get("machine")).getNode().getName())
                .id(((JcloudsSshMachineLocation) flags.get("machine")).getNode().getId());
        JcloudsLocation location = getManagementContext().getLocationManager().createLocation(spec);
        setAttribute(DYNAMIC_LOCATION, location);
        setAttribute(LOCATION_NAME, location.getId());

        String locationSpec = format("jclouds:%s:http://%s:%s",locationName, this.getAttribute(HOSTNAME), this.getPort());
        setAttribute(LOCATION_SPEC, locationSpec);
        LocationDefinition definition = new BasicLocationDefinition(locationName, locationSpec, flags);
        getManagementContext().getLocationRegistry().updateDefinedLocation(definition);

        return location;
    }

    @Override
    public boolean isLocationAvailable() {
        // TODO implementation
        return machine != null;
    }

    @Override
    public void deleteLocation() {
        // TODO implementation
        getManagementContext().getLocationRegistry().removeDefinedLocation(machine.getId());
    }

}
