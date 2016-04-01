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
import static org.jclouds.docker.internal.NullSafeCopies.copyWithNullOf;

import java.util.List;
import java.util.Map;

import com.google.auto.value.AutoValue;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import org.jclouds.javax.annotation.Nullable;
import org.jclouds.json.SerializedNames;

@AutoValue
public abstract class HostConfig {
   @Nullable public abstract String containerIDFile();

   @Nullable public abstract List<String> binds();

   public abstract List<Map<String, String>> lxcConf();

   public abstract boolean privileged();

   @Nullable public abstract List<String> dns();

   @Nullable public abstract List<String> dnsSearch();

   public abstract Map<String, List<Map<String, String>>> portBindings();

   @Nullable public abstract List<String> links();

   @Nullable public abstract List<String> extraHosts();

   public abstract boolean publishAllPorts();

   @Nullable public abstract List<String> volumesFrom();

   @Nullable public abstract String networkMode();

   HostConfig() {
   }

   @SerializedNames({ "ContainerIDFile", "Binds", "LxcConf", "Privileged", "Dns", "DnsSearch", "PortBindings",
         "Links", "ExtraHosts", "PublishAllPorts", "VolumesFrom", "NetworkMode" })
   public static HostConfig create(String containerIDFile, List<String> binds, List<Map<String, String>> lxcConf,
         boolean privileged, List<String> dns, List<String> dnsSearch, Map<String, List<Map<String, String>>> portBindings,
         List<String> links, List<String> extraHosts, boolean publishAllPorts, List<String> volumesFrom, String networkMode) {
      return new AutoValue_HostConfig(containerIDFile, copyWithNullOf(binds), copyOf(lxcConf), privileged, copyWithNullOf(dns), copyWithNullOf(dnsSearch),
            copyOf(portBindings), copyWithNullOf(links), copyWithNullOf(extraHosts), publishAllPorts, copyWithNullOf(volumesFrom), networkMode);
   }

   public static Builder builder() {
      return new Builder();
   }

   public Builder toBuilder() {
      return builder().fromHostConfig(this);
   }

   public static final class Builder {

      private String containerIDFile;
      private List<String> binds;
      private List<Map<String, String>> lxcConf = Lists.newArrayList();
      private boolean privileged;
      private List<String> dns;
      private List<String> dnsSearch;
      private Map<String, List<Map<String, String>>> portBindings = Maps.newLinkedHashMap();
      private List<String> links;
      private List<String> extraHosts;
      private boolean publishAllPorts;
      private List<String> volumesFrom;
      private String networkMode;

      public Builder containerIDFile(String containerIDFile) {
         this.containerIDFile = containerIDFile;
         return this;
      }

      public Builder binds(List<String> binds) {
         this.binds = binds;
         return this;
      }

      public Builder lxcConf(List<Map<String, String>> lxcConf) {
         this.lxcConf = lxcConf;
         return this;
      }

      public Builder privileged(boolean privileged) {
         this.privileged = privileged;
         return this;
      }

      public Builder dns(List<String> dns) {
         this.dns = dns;
         return this;
      }

      public Builder dnsSearch(List<String> dnsSearch) {
         this.dnsSearch = dnsSearch;
         return this;
      }

      public Builder links(List<String> links) {
         this.links = links;
         return this;
      }

      public Builder extraHosts(List<String> extraHosts) {
         this.extraHosts = extraHosts;
         return this;
      }

      public Builder portBindings(Map<String, List<Map<String, String>>> portBindings) {
         this.portBindings = portBindings;
         return this;
      }

      public Builder publishAllPorts(boolean publishAllPorts) {
         this.publishAllPorts = publishAllPorts;
         return this;
      }

      public Builder volumesFrom(List<String> volumesFrom) {
         this.volumesFrom = volumesFrom;
         return this;
      }

      public Builder networkMode(String networkMode) {
         this.networkMode = networkMode;
         return this;
      }

      public HostConfig build() {
         return HostConfig.create(containerIDFile, binds, lxcConf, privileged, dns, dnsSearch, portBindings, links,
               extraHosts, publishAllPorts, volumesFrom, networkMode);
      }

      public Builder fromHostConfig(HostConfig in) {
         return this.containerIDFile(in.containerIDFile()).binds(in.binds()).lxcConf(in.lxcConf())
               .privileged(in.privileged()).dns(in.dns()).dnsSearch(in.dnsSearch()).links(in.links())
               .extraHosts(in.extraHosts()).portBindings(in.portBindings()).publishAllPorts(in.publishAllPorts())
               .volumesFrom(in.volumesFrom()).networkMode(in.networkMode());
      }
   }
}
