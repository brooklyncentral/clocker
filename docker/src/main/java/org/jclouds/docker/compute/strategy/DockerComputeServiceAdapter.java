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
package org.jclouds.docker.compute.strategy;

import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.jclouds.compute.ComputeServiceAdapter;
import org.jclouds.compute.domain.Hardware;
import org.jclouds.compute.domain.HardwareBuilder;
import org.jclouds.compute.domain.Template;
import org.jclouds.compute.reference.ComputeServiceConstants;
import org.jclouds.docker.DockerApi;
import org.jclouds.docker.compute.options.DockerTemplateOptions;
import org.jclouds.docker.domain.Config;
import org.jclouds.docker.domain.Container;
import org.jclouds.docker.domain.HostConfig;
import org.jclouds.docker.domain.Image;
import org.jclouds.domain.Location;
import org.jclouds.domain.LoginCredentials;
import org.jclouds.logging.Logger;

import javax.annotation.Resource;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Iterables.contains;
import static com.google.common.collect.Iterables.filter;
import static com.google.common.collect.Iterables.find;

/**
 * defines the connection between the {@link org.jclouds.docker.DockerApi} implementation and
 * the jclouds {@link org.jclouds.compute.ComputeService}
 */
@Singleton
public class DockerComputeServiceAdapter implements
        ComputeServiceAdapter<Container, Hardware, Image, Location> {

   @Resource
   @Named(ComputeServiceConstants.COMPUTE_LOGGER)
   protected Logger logger = Logger.NULL;

   private final DockerApi api;

   @Inject
   public DockerComputeServiceAdapter(DockerApi api) {
      this.api = checkNotNull(api, "api");
   }

   @Override
   public NodeAndInitialCredentials<Container> createNodeWithGroupEncodedIntoName(String group, String name,
                                                                                  Template template) {
      checkNotNull(template, "template was null");
      checkNotNull(template.getOptions(), "template options was null");
      DockerTemplateOptions templateOptions = DockerTemplateOptions.class.cast(template.getOptions());

      String imageId = checkNotNull(template.getImage().getId(), "template image id must not be null");
      String loginUser = template.getImage().getDefaultCredentials().getUser();
      String loginUserPassword = template.getImage().getDefaultCredentials().getPassword();

      Map<String, Object> exposedPorts = Maps.newHashMap();
      int[] inboundPorts = template.getOptions().getInboundPorts();
      for (int inboundPort : inboundPorts) {
         exposedPorts.put(inboundPort + "/tcp", Maps.newHashMap());
      }

      Config.Builder configBuilder = Config.builder()
              .imageId(imageId)
              .cmd(ImmutableList.of("/usr/sbin/sshd", "-D"))
              .exposedPorts(exposedPorts);

      Iterable<String> fqdn = Splitter.on('.').split(templateOptions.getUserMetadata().get("HostName"));
      String hostname = Iterables.getFirst(fqdn, name);
      String domainName = Joiner.on('.').join(Iterables.skip(fqdn, 1));
      configBuilder.hostname(hostname).domainName(domainName);

      if (templateOptions.getVolumes().isPresent()) {
         Map<String, Object> volumes = Maps.newLinkedHashMap();
         for (String containerDir : templateOptions.getVolumes().get().values()) {
            volumes.put(containerDir, Maps.newHashMap());
         }
         configBuilder.volumes(volumes);
      }
      Config config = configBuilder.build();

      logger.debug(">> creating new container with config(%s)", config);
      Container container = api.getRemoteApi().createContainer(name, config);
      logger.trace("<< container(%s)", container.getId());

      // set up for port bindings
      Map<String, List<Map<String, String>>> portBindings = Maps.newHashMap();
      HostConfig.Builder hostConfigBuilder = HostConfig.builder()
              .portBindings(portBindings)
              .publishAllPorts(true)
              .privileged(true);

      // set up for volume bindings
      if (templateOptions.getVolumes().isPresent()) {
         for (Map.Entry<String,String> entry : templateOptions.getVolumes().get().entrySet()) {
            hostConfigBuilder.binds(ImmutableList.of(entry.getKey() + ":" + entry.getValue()));
         }
      }
      HostConfig hostConfig = hostConfigBuilder.build();

      api.getRemoteApi().startContainer(container.getId(), hostConfig);
      container = api.getRemoteApi().inspectContainer(container.getId());
      if (!container.getState().isRunning()) {
         destroyNode(container.getId());
         throw new IllegalStateException(String.format("Container %s has not started correctly", container.getId()));
      }
      return new NodeAndInitialCredentials<Container>(container, container.getId(),
              LoginCredentials.builder().user(loginUser).password(loginUserPassword).build());
   }

   @Override
   public Iterable<Hardware> listHardwareProfiles() {
      Set<Hardware> hardware = Sets.newLinkedHashSet();
      // todo they are only placeholders at the moment
      hardware.add(new HardwareBuilder().ids("micro").hypervisor("lxc").name("micro").ram(512).build());
      hardware.add(new HardwareBuilder().ids("small").hypervisor("lxc").name("small").ram(1024).build());
      hardware.add(new HardwareBuilder().ids("medium").hypervisor("lxc").name("medium").ram(2048).build());
      hardware.add(new HardwareBuilder().ids("large").hypervisor("lxc").name("large").ram(3072).build());
      return hardware;
   }

   @Override
   public Set<Image> listImages() {
      //return api.getRemoteApi().listImages();
      Set<Image> images = Sets.newHashSet();
      for (Image image : api.getRemoteApi().listImages()) {
         // less efficient than just listNodes but returns richer json that needs repoTags coming from listImages
         Image inspected = api.getRemoteApi().inspectImage(image.getId());
         if(image.getRepoTags() != null) {
            inspected = Image.builder().fromImage(inspected).repoTags(image.getRepoTags()).build();
         }
         images.add(inspected);
      }
      return images;
   }

   @Override
   public Image getImage(final String imageId) {
      return find(listImages(), new Predicate<Image>() {

         @Override
         public boolean apply(Image input) {
            return input.getId().equals(imageId);
         }

      }, null);
   }

   @Override
   public Iterable<Container> listNodes() {
      Set<Container> containers = Sets.newHashSet();
      for (Container container : api.getRemoteApi().listContainers()) {
         // less efficient than just listNodes but returns richer json
         containers.add(api.getRemoteApi().inspectContainer(container.getId()));
      }
      return containers;
   }

   @Override
   public Iterable<Container> listNodesByIds(final Iterable<String> ids) {
      return filter(listNodes(), new Predicate<Container>() {

         @Override
         public boolean apply(Container server) {
            return contains(ids, server.getId());
         }
      });
   }

   @Override
   public Iterable<Location> listLocations() {
      return ImmutableSet.of();
   }

   @Override
   public Container getNode(String id) {
      return api.getRemoteApi().inspectContainer(id);
   }

   @Override
   public void destroyNode(String id) {
      api.getRemoteApi().stopContainer(id);
      api.getRemoteApi().removeContainer(id);
   }

   @Override
   public void rebootNode(String id) {
      api.getRemoteApi().startContainer(id);
   }

   @Override
   public void resumeNode(String id) {
      api.getRemoteApi().startContainer(id);
   }

   @Override
   public void suspendNode(String id) {
      api.getRemoteApi().stopContainer(id);
   }

}
