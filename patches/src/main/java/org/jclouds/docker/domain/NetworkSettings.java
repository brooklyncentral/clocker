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

import java.util.List;
import java.util.Map;

import org.jclouds.docker.internal.NullSafeCopies;
import org.jclouds.javax.annotation.Nullable;
import org.jclouds.json.SerializedNames;

import com.google.auto.value.AutoValue;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

@AutoValue
public abstract class NetworkSettings {

   @AutoValue
   public abstract static class Details {

      Details() {} // For AutoValue only!

      public abstract String endpoint();

      public abstract String gateway();

      public abstract String ipAddress();

      public abstract int ipPrefixLen();

      public abstract String ipv6Gateway();

      public abstract String globalIPv6Address();

      public abstract int globalIPv6PrefixLen();

      public abstract String macAddress();

      @SerializedNames({ "EndpointID", "Gateway", "IPAddress", "IPPrefixLen", "IPv6Gateway", "GlobalIPv6Address", "GlobalIPv6PrefixLen", "MacAddress" })
      public static Details create(String endpointId, String gateway, String ipAddress, int ipPrefixLen, String ipv6Gateway, String globalIPv6Address,
                                   int globalIPv6PrefixLen, String macAddress) {
         return builder().endpoint(endpointId).gateway(gateway).ipAddress(ipAddress).ipPrefixLen(ipPrefixLen)
               .ipv6Gateway(ipv6Gateway).globalIPv6Address(globalIPv6Address)
               .globalIPv6PrefixLen(globalIPv6PrefixLen).macAddress(macAddress)
               .build();
      }
      
      public Builder toBuilder() {
         return new AutoValue_NetworkSettings_Details.Builder(this);
      }

      public static Builder builder() {
         return new AutoValue_NetworkSettings_Details.Builder();
      }

      @AutoValue.Builder
      public abstract static class Builder {
         public abstract Builder endpoint(String value);
         public abstract Builder gateway(String value);
         public abstract Builder ipAddress(String value);
         public abstract Builder ipPrefixLen(int value);
         public abstract Builder ipv6Gateway(String value);
         public abstract Builder globalIPv6Address(String value);
         public abstract Builder globalIPv6PrefixLen(int value);
         public abstract Builder macAddress(String value);

         public abstract Details build();
      }
   }

   public abstract String bridge();

   @Nullable public abstract String sandboxId();

   public abstract boolean hairpinMode();

   @Nullable public abstract String linkLocalIPv6Address();

   public abstract int linkLocalIPv6PrefixLen();

   @Nullable public abstract Map<String, List<Map<String, String>>> ports();

   @Nullable public abstract String sandboxKey();

   public abstract List<String> secondaryIPAddresses();

   public abstract List<String> secondaryIPv6Addresses();

   @Nullable public abstract String endpointId();

   public abstract String gateway();

   @Nullable public abstract String globalIPv6Address();

   public abstract int globalIPv6PrefixLen();

   public abstract String ipAddress();

   public abstract int ipPrefixLen();

   @Nullable public abstract String ipv6Gateway();

   @Nullable public abstract String macAddress();

   public abstract Map<String, Details> networks();

   @Nullable public abstract String portMapping();

   NetworkSettings() {
   }

   @SerializedNames({ "Bridge", "SandboxID", "HairpinMode", "LinkLocalIPv6Address",
           "LinkLocalIPv6PrefixLen", "Ports", "SandboxKey", "SecondaryIPAddresses",
           "SecondaryIPv6Addresses", "EndpointID", "Gateway", "GlobalIPv6Address",
           "GlobalIPv6PrefixLen", "IPAddress", "IPPrefixLen", "IPv6Gateway",
           "MacAddress", "Networks", "PortMapping" })
   public static NetworkSettings create(String bridge, String sandboxId, boolean hairpinMode, String linkLocalIPv6Address,
                                        int linkLocalIPv6PrefixLen, Map<String, List<Map<String, String>>> ports, String sandboxKey, List<String> secondaryIPAddresses,
                                        List<String> secondaryIPv6Addresses, String endpointId, String gateway, String globalIPv6Address,
                                        int globalIPv6PrefixLen, String ipAddress, int ipPrefixLen, String ipv6Gateway,
                                        String macAddress, Map<String, Details> networks, String portMapping) {
      return new AutoValue_NetworkSettings(
              bridge, sandboxId, hairpinMode, linkLocalIPv6Address,
              linkLocalIPv6PrefixLen, ports, sandboxKey, copyOf(secondaryIPAddresses), copyOf(secondaryIPv6Addresses),
              endpointId, gateway, globalIPv6Address, globalIPv6PrefixLen,
              ipAddress, ipPrefixLen, ipv6Gateway,
              macAddress, copyOf(networks), portMapping);
   }

   public static Builder builder() {
      return new Builder();
   }

   public Builder toBuilder() {
      return builder().fromNetworkSettings(this);
   }

   public static final class Builder {

      private String ipAddress;
      private int ipPrefixLen;
      private String gateway;
      private String bridge;
      private String portMapping;
      private Map<String, List<Map<String, String>>> ports;
      private String sandboxId;
      private boolean hairpinMode;
      private String linkLocalIPv6Address;
      private int linkLocalIPv6PrefixLen;
      private String sandboxKey;
      private List<String> secondaryIPAddresses = Lists.newArrayList();
      private List<String> secondaryIPv6Addresses = Lists.newArrayList();
      private String endpointId;
      private String globalIPv6Address;
      private int globalIPv6PrefixLen;
      private String ipv6Gateway;
      private String macAddress;
      private Map<String, Details> networks = Maps.newHashMap();

      public Builder ipAddress(String ipAddress) {
         this.ipAddress = ipAddress;
         return this;
      }

      public Builder ipPrefixLen(int ipPrefixLen) {
         this.ipPrefixLen = ipPrefixLen;
         return this;
      }

      public Builder gateway(String gateway) {
         this.gateway = gateway;
         return this;
      }

      public Builder bridge(String bridge) {
         this.bridge = bridge;
         return this;
      }

      public Builder portMapping(String portMapping) {
         this.portMapping = portMapping;
         return this;
      }

      public Builder ports(Map<String, List<Map<String, String>>> ports) {
         this.ports = NullSafeCopies.copyWithNullOf(ports);
         return this;
      }

      public Builder sandboxId(String sandboxId) {
         this.sandboxId = sandboxId;
         return this;
      }

      public Builder hairpinMode(boolean hairpinMode) {
         this.hairpinMode = hairpinMode;
         return this;
      }

      public Builder linkLocalIPv6Address(String linkLocalIPv6Address) {
         this.linkLocalIPv6Address = linkLocalIPv6Address;
         return this;
      }

      public Builder linkLocalIPv6PrefixLen(int linkLocalIPv6PrefixLen) {
         this.linkLocalIPv6PrefixLen = linkLocalIPv6PrefixLen;
         return this;
      }

      public Builder sandboxKey(String sandboxKey) {
         this.sandboxKey = sandboxKey;
         return this;
      }

      public Builder secondaryIPAddresses(List<String> secondaryIPAddresses) {
         this.secondaryIPAddresses = secondaryIPAddresses;
         return this;
      }

      public Builder secondaryIPv6Addresses(List<String> secondaryIPv6Addresses) {
         this.secondaryIPv6Addresses = secondaryIPv6Addresses;
         return this;
      }

      public Builder endpointId(String endpointId) {
         this.endpointId = endpointId;
         return this;
      }

      public Builder globalIPv6Address(String globalIPv6Address) {
         this.globalIPv6Address = globalIPv6Address;
         return this;
      }

      public Builder globalIPv6PrefixLen(int globalIPv6PrefixLen) {
         this.globalIPv6PrefixLen = globalIPv6PrefixLen;
         return this;
      }

      public Builder ipv6Gateway(String ipv6Gateway) {
         this.ipv6Gateway = ipv6Gateway;
         return this;
      }

      public Builder macAddress(String macAddress) {
         this.macAddress = macAddress;
         return this;
      }

      public Builder networks(Map<String, Details> networks) {
         this.networks.putAll(networks);
         return this;
      }

      public NetworkSettings build() {
         return NetworkSettings.create(bridge, sandboxId, hairpinMode, linkLocalIPv6Address, linkLocalIPv6PrefixLen, ports,
                 sandboxKey, secondaryIPAddresses, secondaryIPv6Addresses, endpointId, gateway,
                 globalIPv6Address, globalIPv6PrefixLen, ipAddress, ipPrefixLen, ipv6Gateway, macAddress, networks, portMapping);
      }

      public Builder fromNetworkSettings(NetworkSettings in) {
         return this.ipAddress(in.ipAddress()).ipPrefixLen(in.ipPrefixLen()).gateway(in.gateway()).bridge(in.bridge())
               .portMapping(in.portMapping()).ports(in.ports()).sandboxId(in.sandboxId()).hairpinMode(in.hairpinMode()).linkLocalIPv6Address(in
                         .linkLocalIPv6Address()).linkLocalIPv6PrefixLen(in.linkLocalIPv6PrefixLen()).sandboxKey(in.sandboxKey()).secondaryIPAddresses(in
                         .secondaryIPAddresses()).secondaryIPv6Addresses(in.secondaryIPv6Addresses()).endpointId(in.endpointId()).globalIPv6Address(in
                         .globalIPv6Address()).globalIPv6PrefixLen(in.globalIPv6PrefixLen()).ipv6Gateway(in.ipv6Gateway()).macAddress(in.macAddress())
                 .networks(in.networks());
      }
   }

}
