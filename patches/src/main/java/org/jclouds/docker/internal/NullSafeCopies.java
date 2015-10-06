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
package org.jclouds.docker.internal;

import java.util.List;
import java.util.Map;

import org.jclouds.javax.annotation.Nullable;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class NullSafeCopies {

   public static <K, V> Map<K, V> copyOf(@Nullable Map<K, V> map) {
      return map != null ? ImmutableMap.copyOf(map) : ImmutableMap.<K, V> of();
   }

   public static <E> List<E> copyOf(@Nullable List<E> list) {
      return list != null ? ImmutableList.copyOf(list) : ImmutableList.<E> of();
   }

   /**
    * Copies given List with keeping null value if provided.
    *
    * @param list
    *           instance to copy (maybe <code>null</code>)
    * @return if the parameter is not-<code>null</code> then immutable copy;
    *         <code>null</code> otherwise
    */
   public static <E> List<E> copyWithNullOf(@Nullable List<E> list) {
      return list != null ? ImmutableList.copyOf(list) : null;
   }

   /**
    * Copies given {@link Iterable} into immutable {@link List} with keeping null value if provided.
    *
    * @param iterable
    *           instance to copy (maybe <code>null</code>)
    * @return if the parameter is not-<code>null</code> then immutable copy;
    *         <code>null</code> otherwise
    */
   public static <E> List<E> copyWithNullOf(@Nullable Iterable<E> iterable) {
      return iterable != null ? ImmutableList.copyOf(iterable) : null;
   }

   /**
    * Copies given array into immutable {@link List} with keeping null value if provided.
    *
    * @param array
    *           instance to copy (maybe <code>null</code>)
    * @return if the parameter is not-<code>null</code> then immutable copy;
    *         <code>null</code> otherwise
    */
   public static <E> List<E> copyWithNullOf(@Nullable E[] array) {
      return array != null ? ImmutableList.copyOf(array) : null;
   }

   private NullSafeCopies() {
   }
}
