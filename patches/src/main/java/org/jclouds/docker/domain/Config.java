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

import java.beans.ConstructorProperties;
import java.util.List;
import java.util.Map;

import org.jclouds.javax.annotation.Nullable;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gson.annotations.SerializedName;

public class Config {

   @SerializedName("Hostname")
   private final String hostname;
   @SerializedName("Domainname")
   private final String domainName;
   @SerializedName("User")
   private final String user;
   @SerializedName("Memory")
   private final int memory;
   @SerializedName("MemorySwap")
   private final int memorySwap;
   @SerializedName("CpuShares")
   private final int cpuShares;
   @SerializedName("AttachStdin")
   private final boolean attachStdin;
   @SerializedName("AttachStdout")
   private final boolean attachStdout;
   @SerializedName("AttachStderr")
   private final boolean attachStderr;
   @SerializedName("ExposedPorts")
   private final Map<String, ?> exposedPorts;
   @SerializedName("Tty")
   private final boolean tty;
   @SerializedName("OpenStdin")
   private final boolean openStdin;
   @SerializedName("StdinOnce")
   private final boolean stdinOnce;
   @SerializedName("Env")
   private final List<String> env;
   @SerializedName("Cmd")
   private final List<String> cmd;
   @SerializedName("Dns")
   private final List<String> dns;
   @SerializedName("Image")
   private final String imageId;
   @SerializedName("Volumes")
   private final Map<String, ?> volumes;
   @SerializedName("VolumesFrom")
   private final String volumesFrom;
   @SerializedName("WorkingDir")
   private final String workingDir;
   @SerializedName("Entrypoint")
   private final List<String> entrypoint;
   @SerializedName("NetworkDisabled")
   private final boolean networkDisabled;
   @SerializedName("OnBuild")
   private final List<String> onBuild;


   @ConstructorProperties({ "Hostname", "Domainname", "User", "Memory", "MemorySwap", "CpuShares", "AttachStdin",
           "AttachStdout", "AttachStderr", "ExposedPorts", "Tty", "OpenStdin", "StdinOnce", "Env", "Cmd",
           "Dns", "Image", "Volumes", "VolumesFrom", "WorkingDir", "Entrypoint", "NetworkDisabled", "OnBuild" })
   protected Config(@Nullable String hostname, @Nullable String domainName, @Nullable String user,
                             int memory, int memorySwap, int cpuShares, boolean attachStdin, boolean attachStdout,
                             boolean attachStderr, Map<String, ?> exposedPorts, boolean tty, boolean openStdin,
                             boolean stdinOnce, @Nullable List<String> env, @Nullable List<String> cmd,
                             @Nullable List<String> dns, String imageId, @Nullable Map<String, ?> volumes,
                             @Nullable String volumesFrom, @Nullable String workingDir, @Nullable List<String> entrypoint,
                             @Nullable boolean networkDisabled, @Nullable List<String> onBuild) {
      this.hostname = hostname;
      this.domainName = domainName;
      this.user = user;
      this.memory = checkNotNull(memory, "memory");
      this.memorySwap = checkNotNull(memorySwap, "memorySwap");
      this.cpuShares = checkNotNull(cpuShares, "cpuShares");
      this.attachStdin = checkNotNull(attachStdin, "attachStdin");
      this.attachStdout = checkNotNull(attachStdout, "attachStdout");
      this.attachStderr = checkNotNull(attachStderr, "attachStderr");
      this.exposedPorts = exposedPorts != null ? ImmutableMap.copyOf(exposedPorts) : ImmutableMap.<String, Object> of();
      this.tty = checkNotNull(tty, "tty");
      this.openStdin = checkNotNull(openStdin, "openStdin");
      this.stdinOnce = checkNotNull(stdinOnce, "stdinOnce");
      this.env = env != null ? ImmutableList.copyOf(env) : ImmutableList.<String> of();
      this.cmd = cmd != null ? ImmutableList.copyOf(cmd) : ImmutableList.<String> of();
      this.dns = dns != null ? ImmutableList.copyOf(dns) : ImmutableList.<String> of();
      this.imageId = checkNotNull(imageId, "imageId");
      this.volumes = volumes != null ? ImmutableMap.copyOf(volumes) : ImmutableMap.<String, Object> of();
      this.volumesFrom = volumesFrom;
      this.workingDir = workingDir;
      this.entrypoint = entrypoint;
      this.networkDisabled = networkDisabled;
      this.onBuild = onBuild != null ? ImmutableList.copyOf(onBuild) : ImmutableList.<String> of();
   }

   public String getHostname() {
      return hostname;
   }

   public String getDomainName() {
      return domainName;
   }

   public String getUser() {
      return user;
   }

   public int getMemory() {
      return memory;
   }

   public int getMemorySwap() {
      return memorySwap;
   }

   public int getCpuShares() {
      return cpuShares;
   }

   public boolean isAttachStdin() {
      return attachStdin;
   }

   public boolean isAttachStdout() {
      return attachStdout;
   }

   public boolean isAttachStderr() {
      return attachStderr;
   }

   public Map<String, ?> getExposedPorts() {
      return exposedPorts;
   }

   public boolean isTty() {
      return tty;
   }

   public boolean isOpenStdin() {
      return openStdin;
   }

   public boolean isStdinOnce() {
      return stdinOnce;
   }

   public List<String> getEnv() {
      return env;
   }

   public List<String> getCmd() {
      return cmd;
   }

   public List<String> getDns() {
      return dns;
   }

   public String getImageId() {
      return imageId;
   }

   public Map<String, ?> getVolumes() {
      return volumes;
   }

   public String getVolumesFrom() {
      return volumesFrom;
   }

   public String getWorkingDir() {
      return workingDir;
   }

   public List<String> getEntrypoint() {
      return entrypoint;
   }

   public boolean isNetworkDisabled() {
      return networkDisabled;
   }

   public List<String> getOnBuild() {
      return onBuild;
   }

   @Override
   public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Config that = (Config) o;

      return Objects.equal(this.hostname, that.hostname) &&
              Objects.equal(this.domainName, that.domainName) &&
              Objects.equal(this.user, that.user) &&
              Objects.equal(this.memory, that.memory) &&
              Objects.equal(this.memorySwap, that.memorySwap) &&
              Objects.equal(this.cpuShares, that.cpuShares) &&
              Objects.equal(this.attachStdin, that.attachStdin) &&
              Objects.equal(this.attachStdout, that.attachStdout) &&
              Objects.equal(this.attachStderr, that.attachStderr) &&
              Objects.equal(this.exposedPorts, that.exposedPorts) &&
              Objects.equal(this.tty, that.tty) &&
              Objects.equal(this.openStdin, that.openStdin) &&
              Objects.equal(this.stdinOnce, that.stdinOnce) &&
              Objects.equal(this.env, that.env) &&
              Objects.equal(this.cmd, that.cmd) &&
              Objects.equal(this.dns, that.dns) &&
              Objects.equal(this.imageId, that.imageId) &&
              Objects.equal(this.volumes, that.volumes) &&
              Objects.equal(this.volumesFrom, that.volumesFrom) &&
              Objects.equal(this.workingDir, that.workingDir) &&
              Objects.equal(this.entrypoint, that.entrypoint) &&
              Objects.equal(this.onBuild, that.onBuild);
   }

   @Override
   public int hashCode() {
      return Objects.hashCode(hostname, domainName, user, memory, memorySwap, cpuShares, attachStdin, attachStdout,
              attachStderr, exposedPorts, tty, openStdin, stdinOnce, env, cmd, dns, imageId, volumes,
              volumesFrom, workingDir, entrypoint, networkDisabled, onBuild);
   }

   @Override
   public String toString() {
      return Objects.toStringHelper(this)
              .add("hostname", hostname)
              .add("domainName", domainName)
              .add("user", user)
              .add("memory", memory)
              .add("memorySwap", memorySwap)
              .add("cpuShares", cpuShares)
              .add("attachStdin", attachStdin)
              .add("attachStdout", attachStdout)
              .add("attachStderr", attachStderr)
              .add("exposedPorts", exposedPorts)
              .add("tty", tty)
              .add("openStdin", openStdin)
              .add("stdinOnce", stdinOnce)
              .add("env", env)
              .add("cmd", cmd)
              .add("dns", dns)
              .add("imageId", imageId)
              .add("volumes", volumes)
              .add("volumesFrom", volumesFrom)
              .add("workingDir", workingDir)
              .add("entrypoint", entrypoint)
              .add("networkDisabled", networkDisabled)
              .add("onBuild", onBuild)
              .toString();
   }

   public static Builder builder() {
      return new Builder();
   }

   public Builder toBuilder() {
      return builder().fromConfig(this);
   }

   public static final class Builder {
      private String hostname;
      private String domainName;
      private String user;
      private int memory;
      private int memorySwap;
      private int cpuShares;
      private boolean attachStdin;
      private boolean attachStdout;
      private boolean attachStderr;
      private Map<String, ?> exposedPorts = ImmutableMap.of();
      private List<String> env = ImmutableList.of();
      private boolean tty;
      private boolean openStdin;
      private boolean stdinOnce;
      private List<String> cmd = ImmutableList.of();
      private List<String> dns = ImmutableList.of();
      private String imageId;
      private Map<String, ?> volumes = ImmutableMap.of();
      private String volumesFrom;
      private String workingDir;
      private List<String> entrypoint = ImmutableList.of();
      private boolean networkDisabled;
      private List<String> onBuild = ImmutableList.of();

      public Builder hostname(String hostname) {
         this.hostname = hostname;
         return this;
      }

      public Builder domainName(String domainName) {
         this.domainName = domainName;
         return this;
      }

      public Builder user(String user) {
         this.user = user;
         return this;
      }

      public Builder memory(int memory) {
         this.memory = memory;
         return this;
      }

      public Builder memorySwap(int memorySwap) {
         this.memorySwap = memorySwap;
         return this;
      }

      public Builder cpuShares(int cpuShares) {
         this.cpuShares = cpuShares;
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

      public Builder exposedPorts(Map<String, ?> exposedPorts) {
         this.exposedPorts = ImmutableMap.copyOf(checkNotNull(exposedPorts, "exposedPorts"));
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
         this.cmd = ImmutableList.copyOf(checkNotNull(cmd, "cmd"));
         return this;
      }

      public Builder dns(List<String> dns) {
         this.dns = ImmutableList.copyOf(checkNotNull(dns, "dns"));
         return this;
      }

      public Builder imageId(String imageId) {
         this.imageId = imageId;
         return this;
      }

      public Builder volumes(Map<String, ?> volumes) {
         this.volumes = ImmutableMap.copyOf(checkNotNull(volumes, "volumes"));
         return this;
      }

      public Builder volumesFrom(String volumesFrom) {
         this.volumesFrom = volumesFrom;
         return this;
      }

      public Builder workingDir(String workingDir) {
         this.workingDir = workingDir;
         return this;
      }

      public Builder entrypoint(List<String> entrypoint) {
         this.entrypoint = entrypoint;
         return this;
      }

      public Builder networkDisabled(boolean networkDisabled) {
         this.networkDisabled = networkDisabled;
         return this;
      }

      public Builder onBuild(List<String> onBuild) {
         this.onBuild = ImmutableList.copyOf(checkNotNull(onBuild, "onBuild"));
         return this;
      }

      public Config build() {
         return new Config(hostname, domainName, user, memory, memorySwap, cpuShares, attachStdin, attachStdout,
                 attachStderr, exposedPorts, tty, openStdin, stdinOnce, env, cmd, dns, imageId, volumes,
                 volumesFrom, workingDir, entrypoint, networkDisabled, onBuild);
      }

      public Builder fromConfig(Config in) {
         return this
                 .hostname(in.getHostname())
                 .domainName(in.getDomainName())
                 .user(in.getUser())
                 .memory(in.getMemory())
                 .memorySwap(in.getMemorySwap())
                 .cpuShares(in.getCpuShares())
                 .attachStdin(in.isAttachStdin())
                 .attachStdout(in.isAttachStdout())
                 .attachStderr(in.isAttachStderr())
                 .exposedPorts(in.getExposedPorts())
                 .tty(in.isTty())
                 .openStdin(in.isOpenStdin())
                 .stdinOnce(in.isStdinOnce())
                 .env(in.getEnv())
                 .cmd(in.getCmd())
                 .dns(in.getDns())
                 .imageId(in.getImageId())
                 .volumes(in.getVolumes())
                 .volumesFrom(in.getVolumesFrom())
                 .workingDir(in.getWorkingDir())
                 .entrypoint(in.getEntrypoint())
                 .networkDisabled(in.isNetworkDisabled())
                 .onBuild(in.getOnBuild());
      }

   }
}
