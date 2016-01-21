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
package brooklyn.storage.softlayer;

import java.util.Arrays;

import org.jclouds.compute.ComputeService;
import org.jclouds.compute.domain.TemplateBuilder;
import org.jclouds.compute.options.TemplateOptions;
import org.jclouds.ec2.compute.options.EC2TemplateOptions;

import org.apache.brooklyn.location.jclouds.BasicJcloudsLocationCustomizer;
import org.apache.brooklyn.location.jclouds.JcloudsLocationCustomizer;
import org.apache.brooklyn.location.jclouds.JcloudsSshMachineLocation;

/**
 * Customization hooks to ensure that any EC2 instances provisioned via a corresponding jclouds location become associated
 * with an EBS volume (either an existing volume, specified by ID, or newly created).
 */
public class SoftlayerVolumeCustomizer {

    /**
     * Returns a location customizer that:
     * <ul>
     *   <li>configures the AWS availability zone</li>
     *   <li>creates a new EBS volume of the requested size in the given availability zone</li>
     *   <li>attaches the new volume to the newly-provisioned EC2 instance</li>
     *   <li>formats the new volume with the requested filesystem</li>
     *   <li>mounts the filesystem under the requested path</li>
     * </ul>
     */
    public static JcloudsLocationCustomizer withNewVolume(final String ec2DeviceName, final String osDeviceName, final String mountPoint, final String filesystemType,
        final String availabilityZone, final int sizeInGib, final boolean deleteOnTermination) {

        return new BasicJcloudsLocationCustomizer() {
            public void customize(ComputeService computeService, TemplateBuilder templateBuilder) {
                templateBuilder.locationId(availabilityZone);
            }
            public void customize(ComputeService computeService, TemplateOptions templateOptions) {
                ((EC2TemplateOptions) templateOptions).mapNewVolumeToDeviceName(ec2DeviceName, sizeInGib, deleteOnTermination);
            }
            public void customize(ComputeService computeService, JcloudsSshMachineLocation machine) {
                createFilesystem(machine, osDeviceName, filesystemType);
                mountFilesystem(machine, osDeviceName, mountPoint);
            }
        };
    }

    /**
     * Returns a location customizer that:
     * <ul>
     *   <li>configures the AWS availability zone</li>
     *   <li>obtains a new EBS volume from the specified snapshot in the given availability zone</li>
     *   <li>attaches the new volume to the newly-provisioned EC2 instance</li>
     *   <li>mounts the filesystem under the requested path</li>
     * </ul>
     */
    public static JcloudsLocationCustomizer withExistingSnapshot(final String ec2DeviceName, final String osDeviceName, final String mountPoint,
        final String availabilityZone, final String snapshotId, final int sizeInGib, final boolean deleteOnTermination) {

        return new BasicJcloudsLocationCustomizer() {
            public void customize(ComputeService computeService, TemplateBuilder templateBuilder) {
                templateBuilder.locationId(availabilityZone);
            }
            public void customize(ComputeService computeService, TemplateOptions templateOptions) {
                ((EC2TemplateOptions) templateOptions).mapEBSSnapshotToDeviceName(ec2DeviceName, snapshotId, sizeInGib, deleteOnTermination);
            }
            public void customize(ComputeService computeService, JcloudsSshMachineLocation machine) {
                mountFilesystem(machine, osDeviceName, mountPoint);
            }
        };
    }

    private static void createFilesystem(JcloudsSshMachineLocation machine, String osDeviceName, String filesystemType) {
        machine.execCommands("Creating filesystem on EBS volume", Arrays.asList(
            "mkfs." + filesystemType + " " + osDeviceName
        ));
    }

    private static void mountFilesystem(JcloudsSshMachineLocation machine, String osDeviceName, String mountPoint) {
        // NOTE: also adds an entry to fstab so the mount remains available after a reboot.
        machine.execCommands("Mounting EBS volume", Arrays.asList(
            "mkdir -m 000 " + mountPoint,
            "echo \"" + osDeviceName + " " + mountPoint + " auto noatime 0 0\" | sudo tee -a /etc/fstab",
            "mount " + mountPoint
        ));
    }

}
