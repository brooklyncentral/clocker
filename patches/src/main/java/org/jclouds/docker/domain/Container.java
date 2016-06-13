/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jclouds.docker.domain;

import static org.jclouds.docker.internal.NullSafeCopies.copyOf;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.jclouds.javax.annotation.Nullable;
import org.jclouds.json.SerializedNames;

import com.google.auto.value.AutoValue;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

@AutoValue
public abstract class Container {
   public abstract String id();

   @Nullable public abstract Date created();

   @Nullable public abstract String path();

   @Nullable public abstract String name();

   public abstract List<String> args();

   @Nullable public abstract Config config();

   @Nullable public abstract State state();

   @Nullable public abstract String image();

   @Nullable public abstract NetworkSettings networkSettings();

   @Nullable public abstract String sysInitPath();

   @Nullable public abstract String resolvConfPath();

   public abstract Map<String, String> volumes();

   @Nullable public abstract HostConfig hostConfig();

   @Nullable public abstract String driver();

   @Nullable public abstract String execDriver();

   public abstract Map<String, Boolean> volumesRW();

   @Nullable public abstract String command();

   @Nullable public abstract String status();

   public abstract List<Port> ports();

   @Nullable public abstract String hostnamePath();

   @Nullable public abstract String hostsPath();

   @Nullable public abstract String mountLabel();

   @Nullable public abstract String processLabel();

   public abstract Optional<Node> node();

   Container() {
   }

   @SerializedNames(
         {
                 "Id", "Created", "Path", "Name", "Args", "Config", "State", "Image", "NetworkSettings", "SysInitPath",
                 "ResolvConfPath", "Volumes", "HostConfig", "Driver", "ExecDriver", "VolumesRW", "Command", "Status",
                 "Ports", "HostnamePath", "HostsPath", "MountLabel", "ProcessLabel", "Node"
         })
   public static Container create(String id, Date created, String path, String name, List<String> args, Config config,
                                  State state, String image, NetworkSettings networkSettings, String sysInitPath,
                                  String resolvConfPath, Map<String, String> volumes, HostConfig hostConfig,
                                  String driver, String execDriver, Map<String, Boolean> volumesRW, String command,
                                  String status, List<Port> ports, String hostnamePath, String hostsPath,
                                  String mountLabel, String processLabel, Optional<Node> node) {
      return new AutoValue_Container(id, created, path, name, copyOf(args), config, state, image, networkSettings,
              sysInitPath, resolvConfPath, copyOf(volumes), hostConfig, driver, execDriver, copyOf(volumesRW), command,
              status, copyOf(ports), hostnamePath, hostsPath, mountLabel, processLabel, node);
   }

   public static Builder builder() {
      return new Builder();
   }

   public Builder toBuilder() {
      return builder().fromContainer(this);
   }

   public static final class Builder {

      private String id;
      private Date created;
      private String path;
      private String name;
      private List<String> args;
      private Config config;
      private State state;
      private String image;
      private NetworkSettings networkSettings;
      private String sysInitPath;
      private String resolvConfPath;
      private Map<String, String> volumes = ImmutableMap.of();
      private HostConfig hostConfig;
      private String driver;
      private String execDriver;
      private Map<String, Boolean> volumesRW = ImmutableMap.of();
      private String command;
      private String status;
      private List<Port> ports = ImmutableList.of();
      private String hostnamePath;
      private String hostsPath;
      private String mountLabel;
      private String processLabel;
      private Optional<Node> node = Optional.absent();

      public Builder id(String id) {
         this.id = id;
         return this;
      }

      public Builder created(Date created) {
         this.created = created;
         return this;
      }

      public Builder path(String path) {
         this.path = path;
         return this;
      }

      public Builder name(String name) {
         this.name = name;
         return this;
      }

      public Builder args(List<String> args) {
         this.args = args;
         return this;
      }

      public Builder config(Config config) {
         this.config = config;
         return this;
      }

      public Builder state(State state) {
         this.state = state;
         return this;
      }

      public Builder image(String imageName) {
         this.image = imageName;
         return this;
      }

      public Builder networkSettings(NetworkSettings networkSettings) {
         this.networkSettings = networkSettings;
         return this;
      }

      public Builder sysInitPath(String sysInitPath) {
         this.sysInitPath = sysInitPath;
         return this;
      }

      public Builder resolvConfPath(String resolvConfPath) {
         this.resolvConfPath = resolvConfPath;
         return this;
      }

      public Builder volumes(Map<String, String> volumes) {
         this.volumes = volumes;
         return this;
      }

      public Builder hostConfig(HostConfig hostConfig) {
         this.hostConfig = hostConfig;
         return this;
      }

      public Builder driver(String driver) {
         this.driver = driver;
         return this;
      }

      public Builder execDriver(String execDriver) {
         this.execDriver = execDriver;
         return this;
      }

      public Builder volumesRW(Map<String, Boolean> volumesRW) {
         this.volumesRW = volumesRW;
         return this;
      }

      public Builder command(String command) {
         this.command = command;
         return this;
      }

      public Builder status(String status) {
         this.status = status;
         return this;
      }

      public Builder ports(List<Port> ports) {
         this.ports = ports;
         return this;
      }

      public Builder hostnamePath(String hostnamePath) {
         this.hostnamePath = hostnamePath;
         return this;
      }

      public Builder hostsPath(String hostsPath) {
         this.hostsPath = hostsPath;
         return this;
      }

      public Builder mountLabel(String mountLabel) {
         this.mountLabel = mountLabel;
         return this;
      }

      public Builder processLabel(String processLabel) {
         this.processLabel = processLabel;
         return this;
      }

      public Builder node(Node node) {
         this.node = Optional.fromNullable(node);
         return this;
      }

      public Container build() {
         return Container.create(id, created, path, name, args, config, state, image, networkSettings,
                 sysInitPath, resolvConfPath, volumes, hostConfig, driver, execDriver, volumesRW, command, status,
                 ports, hostnamePath, hostsPath, mountLabel, processLabel, node);
      }

      public Builder fromContainer(Container in) {
         return this.id(in.id()).name(in.name()).created(in.created()).path(in.path()).args(in.args())
                 .config(in.config()).state(in.state()).image(in.image()).networkSettings(in.networkSettings())
                 .sysInitPath(in.sysInitPath()).resolvConfPath(in.resolvConfPath()).driver(in.driver())
                 .execDriver(in.execDriver()).volumes(in.volumes()).hostConfig(in.hostConfig()).volumesRW(in.volumesRW())
                 .command(in.command()).status(in.status()).ports(in.ports()).hostnamePath(in.hostnamePath())
                 .hostsPath(in.hostsPath()).mountLabel(in.mountLabel()).processLabel(in.processLabel()).node(in.node().orNull());
      }
   }
}
