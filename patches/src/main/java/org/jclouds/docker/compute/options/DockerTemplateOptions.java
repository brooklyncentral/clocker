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
package org.jclouds.docker.compute.options;

import static com.google.common.base.Objects.equal;

import java.util.List;
import java.util.Map;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import org.jclouds.compute.options.TemplateOptions;
import org.jclouds.docker.domain.Config;
import org.jclouds.docker.internal.NullSafeCopies;
import org.jclouds.domain.LoginCredentials;
import org.jclouds.javax.annotation.Nullable;
import org.jclouds.scriptbuilder.domain.Statement;

/**
 * Contains options supported by the
 * {@link org.jclouds.compute.ComputeService#createNodesInGroup(String, int, TemplateOptions)
 * createNodes} operation on the <em>docker</em> provider.
 *
 * <h2>Usage</h2>
 *
 * The recommended way to instantiate a DockerTemplateOptions object is to
 * statically import {@code DockerTemplateOptions.Builder.*} and invoke one of
 * the static creation methods, followed by an instance mutator if needed.
 *
 * <pre>
 * {@code import static org.jclouds.docker.compute.options.DockerTemplateOptions.Builder.*;
 *
 * ComputeService api = // get connection
 * templateBuilder.options(inboundPorts(22, 80, 8080, 443));
 * Set<? extends NodeMetadata> set = api.createNodesInGroup(tag, 2, templateBuilder.build());}
 * </pre>
 *
 * <h2>Advanced Usage</h2>
 * <p>
 * In addition to basic configuration through its methods, this class also
 * provides possibility to work directly with Docker API configuration object (
 * {@link Config.Builder}). When the
 * {@link #configBuilder(org.jclouds.docker.domain.Config.Builder)} is used to
 * configure not-<code>null</code> configBuilder, then this configuration object
 * takes precedence over the other configuration in this class (i.e. the other
 * config entries are not used)
 * </p>
 * <p>
 * Note: The {@code image} property in the provided {@link Config.Builder} is rewritten by a placeholder value.
 * The real value is configured by ComputeServiceAdapter.
 * </p>
 *
 * <pre>
 * {@code import static org.jclouds.docker.compute.options.DockerTemplateOptions.Builder.*;
 *
 * ComputeService api = // get connection
 * DockerTemplateOptions options = DockerTemplateOptions.Builder
 *       .configBuilder(
 *                Config.builder().env(ImmutableList.<String> of("SSH_PORT=8822"))
 *                      .hostConfig(HostConfig.builder().networkMode("host").build()));
 * templateBuilder.options(options);
 * Set<? extends NodeMetadata> set = api.createNodesInGroup("sample-group", 1, templateBuilder.build());}
 * </pre>
 */
public class DockerTemplateOptions extends TemplateOptions implements Cloneable {

   private static final String NO_IMAGE = "jclouds-placeholder-for-image";

   protected List<String> dns = ImmutableList.of();
   @Nullable protected String hostname;
   @Nullable protected Integer memory;
   @Nullable protected Integer cpuShares;
   @Nullable List<String> entrypoint;
   @Nullable List<String> commands;
   protected Map<String, String> volumes = ImmutableMap.of();
   @Nullable protected List<String> env;
   protected Map<Integer, Integer> portBindings = ImmutableMap.of();
   @Nullable protected String networkMode;
   protected Map<String, String> extraHosts = ImmutableMap.of();
   protected List<String> volumesFrom = ImmutableList.of();
   protected boolean privileged;
   protected Config.Builder configBuilder;

   @Override
   public DockerTemplateOptions clone() {
      DockerTemplateOptions options = new DockerTemplateOptions();
      copyTo(options);
      return options;
   }

   @Override
   public void copyTo(TemplateOptions to) {
      super.copyTo(to);
      if (to instanceof DockerTemplateOptions) {
         DockerTemplateOptions eTo = DockerTemplateOptions.class.cast(to);
         eTo.volumes(volumes);
         eTo.hostname(hostname);
         eTo.dns(dns);
         eTo.memory(memory);
         eTo.cpuShares(cpuShares);
         eTo.entrypoint(entrypoint);
         eTo.commands(commands);
         eTo.env(env);
         eTo.portBindings(portBindings);
         eTo.networkMode(networkMode);
         eTo.extraHosts(extraHosts);
         eTo.volumesFrom(volumesFrom);
         eTo.privileged(privileged);
         eTo.configBuilder(configBuilder);
      }
   }

   @Override
   public boolean equals(Object o) {
      if (this == o)
         return true;
      if (o == null || getClass() != o.getClass())
         return false;
      DockerTemplateOptions that = DockerTemplateOptions.class.cast(o);
      return super.equals(that) &&
              equal(this.volumes, that.volumes) &&
              equal(this.hostname, that.hostname) &&
              equal(this.dns, that.dns) &&
              equal(this.memory, that.memory) &&
              equal(this.cpuShares, that.cpuShares) &&
              equal(this.entrypoint, that.entrypoint) &&
              equal(this.commands, that.commands) &&
              equal(this.env, that.env) &&
              equal(this.portBindings, that.portBindings) &&
              equal(this.networkMode, that.networkMode) &&
              equal(this.extraHosts, that.extraHosts) &&
              equal(this.volumesFrom, that.volumesFrom) &&
              equal(this.privileged, that.privileged) &&
              buildersEqual(this.configBuilder, that.configBuilder);
   }


   /**
    * Compares two Config.Builder instances.
    */
   private boolean buildersEqual(Config.Builder b1, Config.Builder b2) {
      return b1 == b2 || (b1 != null && b2 != null && b1.build().equals(b2.build()));
   }

   @Override
   public int hashCode() {
      return Objects.hashCode(super.hashCode(), volumes, hostname, dns, memory, entrypoint, commands, cpuShares, env,
            portBindings, extraHosts, configBuilder);
   }

   @Override
   public String toString() {
      return Objects.toStringHelper(this)
              .add("volumes", volumes)
              .add("hostname", hostname)
              .add("dns", dns)
              .add("memory", memory)
              .add("cpuShares", cpuShares)
              .add("entrypoint", entrypoint)
              .add("commands", commands)
              .add("env", env)
              .add("portBindings", portBindings)
              .add("networkMode", networkMode)
              .add("extraHosts", extraHosts)
              .add("volumesFrom", volumesFrom)
              .add("configBuilder", configBuilder)
              .toString();
   }

   public DockerTemplateOptions volumes(Map<String, String> volumes) {
      this.volumes = NullSafeCopies.copyOf(volumes);
      return this;
   }

   public DockerTemplateOptions dns(Iterable<String> dns) {
      this.dns = NullSafeCopies.copyOf(dns);
      return this;
   }

   public DockerTemplateOptions dns(String...dns) {
      this.dns = NullSafeCopies.copyOf(dns);
      return this;
   }

   public DockerTemplateOptions hostname(@Nullable String hostname) {
      this.hostname = hostname;
      return this;
   }

   public DockerTemplateOptions memory(@Nullable Integer memory) {
      this.memory = memory;
      return this;
   }

   public DockerTemplateOptions entrypoint(Iterable<String> entrypoint) {
      this.entrypoint = NullSafeCopies.copyWithNullOf(entrypoint);
      return this;
   }

   public DockerTemplateOptions entrypoint(String... entrypoint) {
      this.entrypoint = NullSafeCopies.copyWithNullOf(entrypoint);
      return this;
   }

   public DockerTemplateOptions commands(Iterable<String> commands) {
      this.commands = NullSafeCopies.copyWithNullOf(commands);
      return this;
   }

   public DockerTemplateOptions commands(String...commands) {
      this.commands = NullSafeCopies.copyWithNullOf(commands);
      return this;
   }

   public DockerTemplateOptions cpuShares(@Nullable Integer cpuShares) {
      this.cpuShares = cpuShares;
      return this;
   }

   public DockerTemplateOptions env(Iterable<String> env) {
      this.env = NullSafeCopies.copyWithNullOf(env);
      return this;
   }

   public DockerTemplateOptions env(String...env) {
      this.env = NullSafeCopies.copyWithNullOf(env);
      return this;
   }

   /**
    * Set port bindings between the Docker host and a container.
    * <p>
    * The {@link Map} keys are host ports number, and the value for an entry is the
    * container port number. This is the same order as the arguments for the
    * {@code --publish} command-line option to {@code docker run} which is
    * {@code hostPort:containerPort}.
    *
    * @param portBindings the map of host to container port bindings
    */
   public DockerTemplateOptions portBindings(Map<Integer, Integer> portBindings) {
      this.portBindings = NullSafeCopies.copyOf(portBindings);
      return this;
   }

   /**
    * Sets the networking mode for the container. Supported values are: bridge, host, and container:[name|id]
    * @param networkMode
    * @return this instance
    */
   public DockerTemplateOptions networkMode(@Nullable String networkMode) {
      this.networkMode = networkMode;
      return this;
   }

   /**
    * Set extra hosts file entries for a container.
    * <p>
    * The {@link Map} keys are host names, and the value is an IP address that
    * can be accessed by the container. This is the same order as the arguments for the
    * {@code --add-host} command-line option to {@code docker run}.
    *
    * @param extraHosts the map of host names to IP addresses
    */
   public DockerTemplateOptions extraHosts(Map<String, String> extraHosts) {
      this.extraHosts = NullSafeCopies.copyOf(extraHosts);
      return this;
   }

   /**
    * Set list of containers to mount volumes from onto this container.
    *
    * @param volumesFrom the list of container names
    */
   public DockerTemplateOptions volumesFrom(Iterable<String> volumesFrom) {
      this.volumesFrom = NullSafeCopies.copyOf(volumesFrom);
      return this;
   }

   /**
    * By default, Docker containers are unprivileged and cannot execute privileged operations or access certain
    * host devices.
    *
    * @param privileged Whether the container should run in privileged mode or not
    * @return this instance
    */
   public DockerTemplateOptions privileged(boolean privileged) {
      this.privileged = privileged;
      return this;
   }

   /**
    * This method sets Config.Builder configuration object, which can be used as
    * a replacement for all the other settings from this class. Some values in
    * the provided Config.Builder instance (the image name for instance) can be
    * ignored or their value can be changed.
    *
    * @param configBuilder
    *           Config.Builder instance. This instance can be changed in this
    *           method!
    */
   public DockerTemplateOptions configBuilder(Config.Builder configBuilder) {
      this.configBuilder = configBuilder != null
            ? Config.builder().fromConfig(configBuilder.image(NO_IMAGE).build())
            : null;
      return this;
   }

   public Map<String, String> getVolumes() { return volumes; }

   public List<String> getDns() { return dns; }

   public List<String> getVolumesFrom() { return volumesFrom; }

   public String getHostname() { return hostname; }

   public Integer getMemory() { return memory; }

   public List<String> getEntrypoint() { return entrypoint; }

   public List<String> getCommands() { return commands; }

   public Integer getCpuShares() { return cpuShares; }

   public List<String> getEnv() { return env; }

   public Map<Integer, Integer> getPortBindings() { return portBindings; }

   public String getNetworkMode() { return networkMode; }

   public Map<String, String> getExtraHosts() { return extraHosts; }

   public boolean getPrivileged() { return privileged; }

   public Config.Builder getConfigBuilder() { return configBuilder; }

   public static class Builder {

      /**
       * @see DockerTemplateOptions#volumes(Map)
       */
      public static DockerTemplateOptions volumes(Map<String, String> volumes) {
         DockerTemplateOptions options = new DockerTemplateOptions();
         return options.volumes(volumes);
      }

      /**
       * @see DockerTemplateOptions#dns(String...)
       */
      public static DockerTemplateOptions dns(String...dns) {
         DockerTemplateOptions options = new DockerTemplateOptions();
         return options.dns(dns);
      }

      /**
       * @see DockerTemplateOptions#dns(List)
       */
      public static DockerTemplateOptions dns(Iterable<String> dns) {
         DockerTemplateOptions options = new DockerTemplateOptions();
         return options.dns(dns);
      }

      /**
       * @see DockerTemplateOptions#hostname(String)
       */
      public static DockerTemplateOptions hostname(@Nullable String hostname) {
         DockerTemplateOptions options = new DockerTemplateOptions();
         return options.hostname(hostname);
      }

      /**
       * @see DockerTemplateOptions#memory(Integer)
       */
      public static DockerTemplateOptions memory(@Nullable Integer memory) {
         DockerTemplateOptions options = new DockerTemplateOptions();
         return options.memory(memory);
      }

      /**
       * @see DockerTemplateOptions#entrypoint(String...)
       */
      public static DockerTemplateOptions entrypoint(String...entrypoint) {
         DockerTemplateOptions options = new DockerTemplateOptions();
         return options.entrypoint(entrypoint);
      }

      /**
       * @see DockerTemplateOptions#entrypoint(Iterable)
       */
      public static DockerTemplateOptions entrypoint(Iterable<String> entrypoint) {
         DockerTemplateOptions options = new DockerTemplateOptions();
         return options.entrypoint(entrypoint);
      }

      /**
       * @see DockerTemplateOptions#commands(String...)
       */
      public static DockerTemplateOptions commands(String...commands) {
         DockerTemplateOptions options = new DockerTemplateOptions();
         return options.commands(commands);
      }

      /**
       * @see DockerTemplateOptions#commands(Iterable)
       */
      public static DockerTemplateOptions commands(Iterable<String> commands) {
         DockerTemplateOptions options = new DockerTemplateOptions();
         return options.commands(commands);
      }

      /**
       * @see DockerTemplateOptions#cpuShares(Integer)
       */
      public static DockerTemplateOptions cpuShares(@Nullable Integer cpuShares) {
         DockerTemplateOptions options = new DockerTemplateOptions();
         return options.cpuShares(cpuShares);
      }

      /**
       * @see DockerTemplateOptions#env(String...)
       */
      public static DockerTemplateOptions env(String...env) {
         DockerTemplateOptions options = new DockerTemplateOptions();
         return options.env(env);
      }

      /**
       * @see DockerTemplateOptions#env(Iterable)
       */
      public static DockerTemplateOptions env(Iterable<String> env) {
         DockerTemplateOptions options = new DockerTemplateOptions();
         return options.env(env);
      }

      /**
       * @see DockerTemplateOptions#portBindings(Map)
       */
      public static DockerTemplateOptions portBindings(Map<Integer, Integer> portBindings) {
         DockerTemplateOptions options = new DockerTemplateOptions();
         return options.portBindings(portBindings);
      }

      /**
       * @see DockerTemplateOptions#networkMode(String)
       */
      public static DockerTemplateOptions networkMode(@Nullable String networkMode) {
         DockerTemplateOptions options = new DockerTemplateOptions();
         return options.networkMode(networkMode);
      }

      /**
       * @see DockerTemplateOptions#extraHosts(Map)
       */
      public static DockerTemplateOptions extraHosts(Map<String, String> extraHosts) {
         DockerTemplateOptions options = new DockerTemplateOptions();
         return options.extraHosts(extraHosts);
      }

      /**
       * @see DockerTemplateOptions#privileged(boolean)
       */
      public static DockerTemplateOptions privileged(boolean privileged) {
         DockerTemplateOptions options = new DockerTemplateOptions();
         return options.privileged(privileged);
      }

      /**
       * @see DockerTemplateOptions#configBuilder(Config.Builder)
       */
      public static DockerTemplateOptions configBuilder(Config.Builder configBuilder) {
         DockerTemplateOptions options = new DockerTemplateOptions();
         return options.configBuilder(configBuilder);
      }

      /**
       * @see DockerTemplateOptions#volumesFrom(Iterable)
       */
      public static DockerTemplateOptions volumesFrom(Iterable<String> volumesFrom) {
         DockerTemplateOptions options = new DockerTemplateOptions();
         return options.volumesFrom(volumesFrom);
      }

      /**
       * @see TemplateOptions#inboundPorts(int...)
       */
      public static DockerTemplateOptions inboundPorts(int... ports) {
         DockerTemplateOptions options = new DockerTemplateOptions();
         return options.inboundPorts(ports);
      }

      /**
       * @see TemplateOptions#blockOnPort(int, int)
       */
      public static DockerTemplateOptions blockOnPort(int port, int seconds) {
         DockerTemplateOptions options = new DockerTemplateOptions();
         return options.blockOnPort(port, seconds);
      }

      /**
       * @see TemplateOptions#installPrivateKey(String)
       */
      public static DockerTemplateOptions installPrivateKey(String rsaKey) {
         DockerTemplateOptions options = new DockerTemplateOptions();
         return options.installPrivateKey(rsaKey);
      }

      /**
       * @see TemplateOptions#authorizePublicKey(String)
       */
      public static DockerTemplateOptions authorizePublicKey(String rsaKey) {
         DockerTemplateOptions options = new DockerTemplateOptions();
         return options.authorizePublicKey(rsaKey);
      }

      /**
       * @see TemplateOptions#userMetadata(Map)
       */
      public static DockerTemplateOptions userMetadata(Map<String, String> userMetadata) {
         DockerTemplateOptions options = new DockerTemplateOptions();
         return options.userMetadata(userMetadata);
      }

      /**
       * @see TemplateOptions#nodeNames(Iterable)
       */
      public static DockerTemplateOptions nodeNames(Iterable<String> nodeNames) {
         DockerTemplateOptions options = new DockerTemplateOptions();
         return options.nodeNames(nodeNames);
      }

      /**
       * @see TemplateOptions#networks(Iterable)
       */
      public static DockerTemplateOptions networks(Iterable<String> networks) {
         DockerTemplateOptions options = new DockerTemplateOptions();
         return options.networks(networks);
      }

      /**
       * @see TemplateOptions#overrideLoginUser(String)
       */
      public static DockerTemplateOptions overrideLoginUser(String user) {
         DockerTemplateOptions options = new DockerTemplateOptions();
         return options.overrideLoginUser(user);
      }

      /**
       * @see TemplateOptions#overrideLoginPassword(String)
       */
      public static DockerTemplateOptions overrideLoginPassword(String password) {
         DockerTemplateOptions options = new DockerTemplateOptions();
         return options.overrideLoginPassword(password);
      }

      /**
       * @see TemplateOptions#overrideLoginPrivateKey(String)
       */
      public static DockerTemplateOptions overrideLoginPrivateKey(String privateKey) {
         DockerTemplateOptions options = new DockerTemplateOptions();
         return options.overrideLoginPrivateKey(privateKey);
      }

      /**
       * @see TemplateOptions#overrideAuthenticateSudo(boolean)
       */
      public static DockerTemplateOptions overrideAuthenticateSudo(boolean authenticateSudo) {
         DockerTemplateOptions options = new DockerTemplateOptions();
         return options.overrideAuthenticateSudo(authenticateSudo);
      }

      /**
       * @see TemplateOptions#overrideLoginCredentials(LoginCredentials)
       */
      public static DockerTemplateOptions overrideLoginCredentials(LoginCredentials credentials) {
         DockerTemplateOptions options = new DockerTemplateOptions();
         return options.overrideLoginCredentials(credentials);
      }

      /**
       * @see TemplateOptions#blockUntilRunning(boolean)
       */
      public static DockerTemplateOptions blockUntilRunning(boolean blockUntilRunning) {
         DockerTemplateOptions options = new DockerTemplateOptions();
         return options.blockUntilRunning(blockUntilRunning);
      }

   }

   // methods that only facilitate returning the correct object type

   /**
    * {@inheritDoc}
    */
   @Override
   public DockerTemplateOptions blockOnPort(int port, int seconds) {
      return DockerTemplateOptions.class.cast(super.blockOnPort(port, seconds));
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public DockerTemplateOptions inboundPorts(int... ports) {
      return DockerTemplateOptions.class.cast(super.inboundPorts(ports));
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public DockerTemplateOptions authorizePublicKey(String publicKey) {
      return DockerTemplateOptions.class.cast(super.authorizePublicKey(publicKey));
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public DockerTemplateOptions installPrivateKey(String privateKey) {
      return DockerTemplateOptions.class.cast(super.installPrivateKey(privateKey));
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public DockerTemplateOptions blockUntilRunning(boolean blockUntilRunning) {
      return DockerTemplateOptions.class.cast(super.blockUntilRunning(blockUntilRunning));
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public DockerTemplateOptions dontAuthorizePublicKey() {
      return DockerTemplateOptions.class.cast(super.dontAuthorizePublicKey());
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public DockerTemplateOptions nameTask(String name) {
      return DockerTemplateOptions.class.cast(super.nameTask(name));
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public DockerTemplateOptions runAsRoot(boolean runAsRoot) {
      return DockerTemplateOptions.class.cast(super.runAsRoot(runAsRoot));
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public DockerTemplateOptions runScript(Statement script) {
      return DockerTemplateOptions.class.cast(super.runScript(script));
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public DockerTemplateOptions overrideLoginCredentials(LoginCredentials overridingCredentials) {
      return DockerTemplateOptions.class.cast(super.overrideLoginCredentials(overridingCredentials));
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public DockerTemplateOptions overrideLoginPassword(String password) {
      return DockerTemplateOptions.class.cast(super.overrideLoginPassword(password));
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public DockerTemplateOptions overrideLoginPrivateKey(String privateKey) {
      return DockerTemplateOptions.class.cast(super.overrideLoginPrivateKey(privateKey));
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public DockerTemplateOptions overrideLoginUser(String loginUser) {
      return DockerTemplateOptions.class.cast(super.overrideLoginUser(loginUser));
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public DockerTemplateOptions overrideAuthenticateSudo(boolean authenticateSudo) {
      return DockerTemplateOptions.class.cast(super.overrideAuthenticateSudo(authenticateSudo));
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public DockerTemplateOptions userMetadata(Map<String, String> userMetadata) {
      return DockerTemplateOptions.class.cast(super.userMetadata(userMetadata));
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public DockerTemplateOptions userMetadata(String key, String value) {
      return DockerTemplateOptions.class.cast(super.userMetadata(key, value));
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public DockerTemplateOptions nodeNames(Iterable<String> nodeNames) {
      return DockerTemplateOptions.class.cast(super.nodeNames(nodeNames));
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public DockerTemplateOptions networks(Iterable<String> networks) {
      return DockerTemplateOptions.class.cast(super.networks(networks));
   }

}
