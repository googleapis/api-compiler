/*
 * Copyright (C) 2016 Google, Inc.
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

package com.google.api.tools.framework.tools;

import com.google.common.base.Throwables;
import java.lang.reflect.InvocationTargetException;
import org.apache.commons.cli.ParseException;

/**
 * A class which integrates a tool driver based on {@link ToolOptions}.
 */
public class ToolBase<Driver extends GenericToolDriverBase> {

  protected final Class<Driver> driverClass;

  /** Creates a new instance of this class based on the given driver type. */
  public ToolBase(Class<Driver> driverClass) {
    this.driverClass = driverClass;
  }

  /**
   * Creates an instance of the driver. This first creates {@link ToolOptions} from  command args,
   * then initializes an instance of {@link ToolOptions} from it, by calling the constructor of
   * the driver via reflection. The driver must have a public constructor which contains a single
   * argument of type {@link ToolOptions}.
   *
   * <p>Note: its possible that flags aren't recognized because at the time this method is called,
   * the static initializer of the class backing those flags via options has not been called yet. In
   * order to avoid this, please check existing conventional usage which creates an empty static
   * method in the option provider class which is called explicitly before this method is.
   */
  public Driver createDriverFromArgs(String[] args) {
    if (SwaggerToolDriverBase.class.isAssignableFrom(driverClass)) {
      // Initialize common swagger-related options if the driver class extends SwaggerToolDriverBase
      SwaggerToolDriverBase.ensureStaticsInitialized();
    }
    try {
      ToolOptions options = ToolOptions.createFromArgs(args);
      return driverClass.getConstructor(ToolOptions.class).newInstance(options);
    } catch (InstantiationException
        | IllegalAccessException
        | IllegalArgumentException
        | NoSuchMethodException
        | SecurityException
        | ParseException e) {
      throw new IllegalArgumentException(
          "bad tool driver class: must have public constructor Foo(ToolOptions)", e);
    } catch (InvocationTargetException e) {
      throw Throwables.propagate(e.getCause());
    }
  }
}
