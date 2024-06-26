// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.util.xml;

import com.intellij.openapi.util.Comparing;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Objects;

/**
 * @author Gregory.Shrago
 */
public final class GenericValueUtil {
  private GenericValueUtil() {
  }

  public static NullableFunction<GenericValue, String> STRING_VALUE = genericValue -> genericValue.getStringValue();
  public static NullableFunction<GenericValue, Object> OBJECT_VALUE = genericValue -> genericValue.getValue();


  public static boolean containsString(final Collection<? extends GenericValue<?>> collection, String value) {
    for (GenericValue<?> o : collection) {
      if (Objects.equals(value, o.getStringValue())) return true;
    }
    return false;
  }

  public static <T> boolean containsValue(final Collection<? extends GenericValue<? extends T>> collection, T value) {
    for (GenericValue<? extends T> o : collection) {
      if (Comparing.equal(value, o.getValue())) return true;
    }
    return false;
  }

  public static @NotNull <T> Collection<T> getValueCollection(final Collection<? extends GenericValue<? extends T>> collection, Collection<T> result) {
    for (GenericValue<? extends T> o : collection) {
      ContainerUtil.addIfNotNull(result, o.getValue());
    }
    return result;
  }

  public static @NotNull Collection<String> getStringCollection(final Collection<? extends GenericValue> collection, Collection<String> result) {
    for (GenericValue o : collection) {
      ContainerUtil.addIfNotNull(result, o.getStringValue());
    }
    return result;
  }

  public static @NotNull Collection<String> getClassStringCollection(final Collection<? extends GenericValue> collection, Collection<String> result) {
    for (GenericValue o : collection) {
      final String value = o.getStringValue();
      if (value != null) {
        result.add(value.replace('$', '.'));
      }
    }
    return result;
  }

}
