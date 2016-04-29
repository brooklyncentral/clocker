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

import static com.google.common.base.Preconditions.checkNotNull;
import static org.jclouds.docker.internal.NullSafeCopies.copyOf;
import static org.jclouds.docker.internal.NullSafeCopies.copyWithNullOf;

import java.util.List;
import java.util.Map;

import org.jclouds.javax.annotation.Nullable;
import org.jclouds.json.SerializedNames;

import com.google.auto.value.AutoValue;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

@AutoValue
public abstract class Config {
   @Nullable public abstract String hostname();

   @Nullable public abstract String domainname();

   @Nullable public abstract String user();

   public abstract int memory();

   public abstract int memorySwap();

   public abstract int cpuShares();

   public abstract boolean attachStdin();

   public abstract boolean attachStdout();

   public abstract boolean attachStderr();

   public abstract boolean tty();

   public abstract boolean openStdin();

   public abstract boolean stdinOnce();

   @Nullable public abstract List<String> env();

   @Nullable public abstract List<String> cmd();

   @Nullable public abstract List<String> entrypoint();

   public abstract String image();

   @Nullable public abstract Map<String, ?> volumes();

   @Nullable public abstract String workingDir();

   public abstract boolean networkDisabled();

   public abstract Map<String, ?> exposedPorts();

   public abstract List<String> securityOpts();

   @Nullable public abstract HostConfig hostConfig();

   @Nullable public abstract List<String> binds();

   @Nullable public abstract List<String> links();

   public abstract List<Map<String, String>> lxcConf();

   public abstract Map<String, List<Map<String, String>>> portBindings();

   public abstract boolean publishAllPorts();

   public abstract boolean privileged();

   @Nullable public abstract List<String> dns();

   @Nullable public abstract List<String> dnsSearch();

   @Nullable public abstract List<String> volumesFrom();

   @Nullable public abstract List<String> capAdd();

   @Nullable public abstract List<String> capDrop();

   public abstract Map<String, String> restartPolicy();

   Config() {
   }

   @SerializedNames(
         {
                 "Hostname", "Domainname", "User", "Memory", "MemorySwap", "CpuShares", "AttachStdin", "AttachStdout",
                 "AttachStderr", "Tty", "OpenStdin", "StdinOnce", "Env", "Cmd", "Entrypoint", "Image", "Volumes",
                 "WorkingDir", "NetworkDisabled", "ExposedPorts", "SecurityOpts", "HostConfig", "Binds", "Links",
                 "LxcConf", "PortBindings", "PublishAllPorts", "Privileged", "Dns", "DnsSearch", "VolumesFrom",
                 "CapAdd", "CapDrop", "RestartPolicy"
         })
   public static Config create(String hostname, String domainname, String user, int memory, int memorySwap,
         int cpuShares, boolean attachStdin, boolean attachStdout, boolean attachStderr, boolean tty,
         boolean openStdin, boolean stdinOnce, List<String> env, List<String> cmd, List<String> entrypoint,
         String image, Map<String, ?> volumes, String workingDir, boolean networkDisabled,
         Map<String, ?> exposedPorts, List<String> securityOpts, HostConfig hostConfig, List<String> binds,
         List<String> links, List<Map<String, String>> lxcConf, Map<String, List<Map<String, String>>> portBindings,
         boolean publishAllPorts, boolean privileged, List<String> dns, List<String> dnsSearch, List<String> volumesFrom,
         List<String> capAdd, List<String> capDrop, Map<String, String> restartPolicy) {
      return new AutoValue_Config(hostname, domainname, user, memory, memorySwap, cpuShares, attachStdin,
              attachStdout, attachStderr, tty, openStdin, stdinOnce, copyWithNullOf(env), copyWithNullOf(cmd),
              copyWithNullOf(entrypoint), image, copyWithNullOf(volumes), workingDir, networkDisabled,
              copyOf(exposedPorts), copyOf(securityOpts), hostConfig,
              copyWithNullOf(binds), copyWithNullOf(links), copyOf(lxcConf), copyOf(portBindings), publishAllPorts, privileged,
              copyWithNullOf(dns), copyWithNullOf(dnsSearch), copyWithNullOf(volumesFrom), copyWithNullOf(capAdd), copyWithNullOf(capDrop), copyOf(restartPolicy));
   }

   public static Builder builder() {
      return new Builder();
   }

   public Builder toBuilder() {
      return builder().fromConfig(this);
   }

   public static final class Builder {
      private String hostname;
      private String domainname;
      private String user;
      private int memory;
      private int memorySwap;
      private int cpuShares;
      private boolean attachStdin;
      private boolean attachStdout;
      private boolean attachStderr;
      private boolean tty;
      private boolean openStdin;
      private boolean stdinOnce;
      private List<String> env;
      private List<String> cmd;
      private List<String> entrypoint;
      private String image;
      private Map<String, ?> volumes;
      private String workingDir;
      private boolean networkDisabled;
      private Map<String, ?> exposedPorts = Maps.newHashMap();
      private List<String> securityOpts = Lists.newArrayList();
      private HostConfig hostConfig;
      private List<String> binds;
      private List<String> links;
      private List<Map<String, String>> lxcConf = Lists.newArrayList();
      private Map<String, List<Map<String, String>>> portBindings = Maps.newHashMap();
      private boolean publishAllPorts;
      private boolean privileged;
      private List<String> dns;
      private List<String> dnsSearch;
      private List<String> volumesFrom;
      private List<String> capAdd;
      private List<String> capDrop;
      private Map<String, String> restartPolicy = Maps.newHashMap();

      public Builder hostname(String hostname) {
         this.hostname = hostname;
         return this;
      }

      public Builder domainname(String domainname) {
         this.domainname = domainname;
         return this;
      }

      public Builder user(String user) {
         this.user = user;
         return this;
      }

      public Builder memory(Integer memory) {
         if (memory != null) {
            this.memory = memory;
         }
         return this;
      }

      public Builder memorySwap(Integer memorySwap) {
         if (memorySwap != null) {
            this.memorySwap = memorySwap;
         }
         return this;
      }

      public Builder cpuShares(Integer cpuShares) {
         if (cpuShares != null) {
            this.cpuShares = cpuShares;
         }
         return this;
      }

      public Builder attachStdin(boolean attachStdin) {
         this.attachStdin = attachStdin;
         return this;
      }

      public Builder attachStdout(boolean attachStdout) {
         this.attachStdout = attachStdout;
         return this;
      }

      public Builder attachStderr(boolean attachStderr) {
         this.attachStderr = attachStderr;
         return this;
      }

      public Builder tty(boolean tty) {
         this.tty = tty;
         return this;
      }

      public Builder openStdin(boolean openStdin) {
         this.openStdin = openStdin;
         return this;
      }

      public Builder stdinOnce(boolean stdinOnce) {
         this.stdinOnce = stdinOnce;
         return this;
      }

      public Builder env(List<String> env) {
         this.env = env;
         return this;
      }

      public Builder cmd(List<String> cmd) {
         this.cmd = cmd;
         return this;
      }

      public Builder entrypoint(List<String> entrypoint) {
         this.entrypoint = entrypoint;
         return this;
      }

      public Builder image(String image) {
         this.image = checkNotNull(image, "image");
         return this;
      }

      public Builder volumes(Map<String, ?> volumes) {
         this.volumes = volumes;
         return this;
      }

      public Builder workingDir(String workingDir) {
         this.workingDir = workingDir;
         return this;
      }

      public Builder networkDisabled(boolean networkDisabled) {
         this.networkDisabled = networkDisabled;
         return this;
      }

      public Builder exposedPorts(Map<String, ?> exposedPorts) {
         this.exposedPorts = exposedPorts;
         return this;
      }

      public Builder securityOpts(List<String> securityOpts) {
         this.securityOpts = securityOpts;
         return this;
      }

      public Builder hostConfig(HostConfig hostConfig) {
         this.hostConfig = hostConfig;
         return this;
      }

      public Builder binds(List<String> binds) {
         this.binds = binds;
         return this;
      }

      public Builder links(List<String> links) {
         this.links = links;
         return this;
      }

      public Builder lxcConf(List<Map<String, String>> lxcConf) {
         this.lxcConf = lxcConf;
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

      public Builder privileged(boolean privileged) {
         this.privileged = privileged;
         return this;
      }

      public Builder dns(List<String>  dns) {
         this.dns = dns;
         return this;
      }

      public Builder dnsSearch(List<String> dnsSearch) {
         this.dnsSearch = dnsSearch;
         return this;
      }

      public Builder volumesFrom(List<String> volumesFrom) {
         this.volumesFrom = volumesFrom;
         return this;
      }

      public Builder capAdd(List<String> capAdd) {
         this.capAdd = capAdd;
         return this;
      }

      public Builder capDrop(List<String> capDrop) {
         this.capDrop = capDrop;
         return this;
      }

      public Builder restartPolicy(Map<String, String> restartPolicy) {
         this.restartPolicy = restartPolicy;
         return this;
      }

      public Config build() {
         return Config.create(hostname, domainname, user, memory, memorySwap, cpuShares, attachStdin, attachStdout,
                 attachStderr, tty, openStdin, stdinOnce, env, cmd, entrypoint, image, volumes, workingDir,
                 networkDisabled, exposedPorts, securityOpts, hostConfig, binds, links, lxcConf, portBindings,
                 publishAllPorts, privileged, dns, dnsSearch, volumesFrom, capAdd, capDrop, restartPolicy);
      }

      public Builder fromConfig(Config in) {
         return hostname(in.hostname()).domainname(in.domainname()).user(in.user()).memory(in.memory())
                 .memorySwap(in.memorySwap()).cpuShares(in.cpuShares()).attachStdin(in.attachStdin())
                 .attachStdout(in.attachStdout()).attachStderr(in.attachStderr()).tty(in.tty())
                 .openStdin(in.openStdin()).stdinOnce(in.stdinOnce()).env(in.env()).cmd(in.cmd())
                 .entrypoint(in.entrypoint()).image(in.image()).volumes(in.volumes()).workingDir(in.workingDir())
                 .networkDisabled(in.networkDisabled()).exposedPorts(in.exposedPorts()).securityOpts(in.securityOpts())
                 .hostConfig(in.hostConfig()).binds(in.binds()).links(in.links()).lxcConf(in.lxcConf())
                 .portBindings(in.portBindings()).publishAllPorts(in.publishAllPorts()).privileged(in.privileged())
                 .dns(in.dns()).dnsSearch(in.dnsSearch()).volumesFrom(in.volumesFrom()).capAdd(in.capAdd())
                 .capDrop(in.capDrop()).restartPolicy(in.restartPolicy());
      }

   }
}
