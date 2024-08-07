/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.options.binding;

import org.jetbrains.annotations.ApiStatus;

/**
 * @author Dmitry Avdeev
 *
 * @deprecated Use Kotlin UI DSL with bindings
 */
@ApiStatus.Internal
@Deprecated(forRemoval = true)
public abstract class ValueTypeConverter<A, B> {

  public abstract A to(B b);
  public abstract B from(A a);
  public abstract Class<A> getSourceType();
  public abstract Class<B> getTargetType();

  public static final ValueTypeConverter<String, Integer>STRING_2_INTEGER = new ValueTypeConverter<>() {
    @Override
    public String to(Integer integer) {
      return integer.toString();
    }

    @Override
    public Integer from(String s) {
      return Integer.decode(s);
    }

    @Override
    public Class<String> getSourceType() {
      return String.class;
    }

    @Override
    public Class<Integer> getTargetType() {
      return Integer.class;
    }
  };

  public static final ValueTypeConverter[] STANDARD = new ValueTypeConverter[] {
    STRING_2_INTEGER
  };

}
