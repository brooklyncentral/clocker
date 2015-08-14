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
package org.jclouds.softlayer.features;

import java.util.Set;

import javax.inject.Named;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.jclouds.Fallbacks.FalseOnNotFoundOr404;
import org.jclouds.Fallbacks.NullOnNotFoundOr404;
import org.jclouds.Fallbacks.VoidOnNotFoundOr404;
import org.jclouds.http.filters.BasicAuthentication;
import org.jclouds.rest.annotations.BinderParam;
import org.jclouds.rest.annotations.Fallback;
import org.jclouds.rest.annotations.QueryParams;
import org.jclouds.rest.annotations.RequestFilters;
import org.jclouds.softlayer.binders.NotesToJson;
import org.jclouds.softlayer.binders.TagToJson;
import org.jclouds.softlayer.binders.VirtualGuestToJson;
import org.jclouds.softlayer.domain.ContainerVirtualGuestConfiguration;
import org.jclouds.softlayer.domain.VirtualGuest;

/**
 * Provides access to VirtualGuest via their REST API.
 * <p/>
 *
 * @see <a http://sldn.softlayer.com/reference/services/SoftLayer_Virtual_Guest" />
 */
@RequestFilters(BasicAuthentication.class)
@Path("/v{jclouds.api-version}")
@Consumes(MediaType.APPLICATION_JSON)
public interface VirtualGuestApi {

   String GUEST_MASK = "id;hostname;domain;fullyQualifiedDomainName;powerState;maxCpu;maxMemory;" +
           "statusId;operatingSystem.passwords;primaryBackendIpAddress;primaryIpAddress;activeTransactionCount;" +
           "primaryBackendNetworkComponent;primaryBackendNetworkComponent.networkVlan;" + // FIXME temporary addition
           "blockDevices.diskImage;datacenter;tagReferences;privateNetworkOnlyFlag;sshKeys";

   String NOTES_MASK = "id;notes";

   /**
    * Enables the creation of computing instances on an account.
    * @param virtualGuest this data type presents the structure in which all virtual guests will be presented.
    * @return the new Virtual Guest
    * @see <a href="http://sldn.softlayer.com/reference/services/SoftLayer_Virtual_Guest/createObject" />
    */
   @Named("VirtualGuests:create")
   @POST
   @Path("SoftLayer_Virtual_Guest")
   @Produces(MediaType.APPLICATION_JSON)
   VirtualGuest createVirtualGuest(@BinderParam(VirtualGuestToJson.class) VirtualGuest virtualGuest);

   /**
    * @param id id of the virtual guest
    * @return virtual guest or null if not found
    * @see <a href="http://sldn.softlayer.com/reference/services/SoftLayer_Virtual_Guest/getObject" />
    */
   @Named("VirtualGuests:get")
   @GET
   @Path("/SoftLayer_Virtual_Guest/{id}/getObject")
   @QueryParams(keys = "objectMask", values = GUEST_MASK)
   @Fallback(NullOnNotFoundOr404.class)
   VirtualGuest getVirtualGuest(@PathParam("id") long id);

   /**
    * Returns a {@link VirtualGuest} with the fields listed in the filter string.
    * @param id id of the virtual guest
    * @param filter semicolon separated list of fields to return in the resulting object
    * @return virtual guest or null if not found
    * @see <a href="http://sldn.softlayer.com/reference/services/SoftLayer_Virtual_Guest/getObject" />
    * @see <a href="http://sldn.softlayer.com/article/object-masks" />
    */
   @Named("VirtualGuests:get")
   @GET
   @Path("/SoftLayer_Virtual_Guest/{id}/getObject")
   @Fallback(NullOnNotFoundOr404.class)
   VirtualGuest getVirtualGuestFiltered(@PathParam("id") long id, @QueryParam("objectMask") String filter);

   /**
    * Delete a computing instance
    * @param id the id of the virtual guest.
    * @return the result of the deletion
    * @see <a href="http://sldn.softlayer.com/reference/services/SoftLayer_Virtual_Guest/deleteObject" />
    */
   @Named("VirtualGuests:delete")
   @GET
   @Path("/SoftLayer_Virtual_Guest/{id}/deleteObject")
   @Fallback(FalseOnNotFoundOr404.class)
   boolean deleteVirtualGuest(@PathParam("id") long id);

   /**
    * Determine options available when creating a computing instance
    * @see <a href="http://sldn.softlayer.com/reference/services/SoftLayer_Virtual_Guest/getCreateObjectOptions" />
    */
   @Named("VirtualGuests:getCreateObjectOptions")
   @GET
   @Path("/SoftLayer_Virtual_Guest/getCreateObjectOptions")
   @Fallback(NullOnNotFoundOr404.class)
   ContainerVirtualGuestConfiguration getCreateObjectOptions();

   /**
    * Hard reboot the guest.
    *
    * @param id
    *           id of the virtual guest
    */
   @Named("VirtualGuest:rebootHard")
   @GET
   @Path("/SoftLayer_Virtual_Guest/{id}/rebootHard.json")
   @Fallback(VoidOnNotFoundOr404.class)
   void rebootHardVirtualGuest(@PathParam("id") long id);

   /**
    * Pause the guest.
    *
    * @param id
    *           id of the virtual guest
    */
   @Named("VirtualGuest:pause")
   @GET
   @Path("/SoftLayer_Virtual_Guest/{id}/pause.json")
   @Fallback(VoidOnNotFoundOr404.class)
   void pauseVirtualGuest(@PathParam("id") long id);

   /**
    * Resume the guest.
    *
    * @param id
    *           id of the virtual guest
    */
   @Named("VirtualGuest:resume")
   @GET
   @Path("/SoftLayer_Virtual_Guest/{id}/resume.json")
   @Fallback(VoidOnNotFoundOr404.class)
   void resumeVirtualGuest(@PathParam("id") long id);

   /**
    * Set the tags on the instance
    *
    * @param id
    *           id of the virtual guest
    */
   @Named("VirtualGuest:setTags")
   @POST
   @Path("/SoftLayer_Virtual_Guest/{id}/setTags")
   @Produces(MediaType.APPLICATION_JSON)
   @Fallback(FalseOnNotFoundOr404.class)
   boolean setTags(@PathParam("id") long id, @BinderParam(TagToJson.class) Set<String> tags);

   /**
    * Set notes (visible in UI)
    *
    * @param id id of the virtual guest
    * @param notes The notes property to set on the machine - visible in UI
    */
   @Named("VirtualGuest:setNotes")
   @POST
   @Path("/SoftLayer_Virtual_Guest/{id}/editObject")
   @Produces(MediaType.APPLICATION_JSON)
   boolean setNotes(@PathParam("id") long id, @BinderParam(NotesToJson.class) String notes);

   /**
    * Get notes (visible in UI)
    *
    * Don't include it in default getObject mask as it can get quite big (up to 1000 chars).
    * Also no place to put it in NodeMetadata.
    *
    * @param id
    *           id of the virtual guest
    */
   @Named("VirtualGuest:getNotes")
   @GET
   @Path("/SoftLayer_Virtual_Guest/{id}/getObject")
   @Produces(MediaType.APPLICATION_JSON)
   @QueryParams(keys = "objectMask", values = NOTES_MASK)
   @Fallback(NullOnNotFoundOr404.class)
   VirtualGuest getNotes(@PathParam("id") long id);
}
