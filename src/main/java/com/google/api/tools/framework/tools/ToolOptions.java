/*
 * Copyright (C) 2016 Google Inc.
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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * Class for representing options (flags) for tools.
 *
 * <p>In contrast to flags, options are instance based which makes them more suitable for calling
 * tools in-process.
 *
 * <p>The class defines a set of standard options which are shared between all tools based on the
 * framework. Additional options can be defined on a per-tool basis. Environment dependent
 * helper classes are provided which can create tool options from program arguments.
 *
 */
public class ToolOptions {
  private ToolOptions() {}

  /**
   * Represents an option.
   */
  @AutoValue
  public abstract static class Option<T> {
    public abstract Key<T> key();
    public abstract String name();
    public abstract String description();
    @Nullable
    public abstract T defaultValue();
    public abstract boolean noFlag();
  }

  private static final List<Option<?>> registeredOptions = Lists.newArrayList();

  /**
   * Creates a new option from a type.
   */
  @SuppressWarnings("unchecked")
  public static <T> Option<T> createOption(
      Class<T> type, String name, String description, T defaultValue) {
    return createOption(TypeLiteral.get(type), name, description, defaultValue);
  }

  /**
   * Creates a new option from a type literal.
   */
  @SuppressWarnings("unchecked")
  public static <T> Option<T> createOption(
      TypeLiteral<T> type, String name, String description, @Nullable T defaultValue) {
    Option<T> option = (Option<T>) (Object) new AutoValue_ToolOptions_Option<T>(
        Key.get(type, Names.named(name)), name, description, defaultValue, false);
    registeredOptions.add(option);
    return option;
  }

  /**
   * Creates a new option for a list of strings which is marked to not become a flag if
   * converted to program arguments but takes all additional (non-flag) program arguments.
   */
  public static Option<List<String>> createOptionNoFlag(String name, String description,
      @Nullable List<String> defaultValue) {
    Option<List<String>> option = new AutoValue_ToolOptions_Option<List<String>>(
        Key.get(new TypeLiteral<List<String>>(){}, Names.named(name)),
        name, description, defaultValue, true);
    registeredOptions.add(option);
    return option;
  }

  /**
   * Returns all options which have been created so far.
   */
  public static Iterable<Option<?>> allOptions() {
    return registeredOptions;
  }

  public static final Option<String> DESCRIPTOR_SET = createOption(String.class, "descriptor",
      "The descriptor set representing the compiled protos the tool works on.", "");

  public static final Option<List<String>> CONFIG_FILES =
      createOption(new TypeLiteral<List<String>>() {}, "configs",
          "The list of Yaml configuration files.", ImmutableList.<String>of());

  public static final Option<List<String>> PROTO_SOURCES =
      createOptionNoFlag("protoSources",
          "The sources which are considered to be owned (in contrast to imported).",
          ImmutableList.<String>of());

  public static final Option<List<String>> EXPERIMENTS =
      createOption(new TypeLiteral<List<String>>() {}, "experiments",
          "Any experiments to be applied by the tool.", ImmutableList.<String>of());

  public static final Option<String> VISIBILITY_LABELS =
      createOption(String.class, "visibility_labels",
          "The visibility labels for the normalized service config, delimited by comma.", null);

  public static final Option<String> DATA_PATH = createOption(String.class, "data_path",
      String.format("A path to lookup data (like doc files). Separated by the platforms path "
          + "separator, '%s'.", File.pathSeparator),
      "");

  public static final Option<String> OUTPUT_ENDPOINT = createOption(String.class, "output_endpoint",
      "The endpoint to generate the discovery doc for. Defaults to the name of the service config.",
      "");

  public static final Option<String> EXTENSION_DESCRIPTOR_SET =
      createOption(String.class, "extension_descriptor",
          "A proto descriptor set with extensions to be processed (for proto2).", "");

  private final Map<Key<?>, Object> options = Maps.newHashMap();

  /**
   * Returns new empty tool options instance.
   */
  public static ToolOptions create() {
    return new ToolOptions();
  }

  /**
   * Sets an option.
   */
  @SuppressWarnings("unchecked")
  public <T> ToolOptions set(Option<T> option, @Nullable T value) {
    if (value == null) {
      options.remove(option.key());
    } else {
      options.put(option.key(), value);
    }
    return this;
  }

  /**
   * Gets an option, or its default value if it is not set.
   */
  @SuppressWarnings("unchecked")
  public <T> T get(Option<T> option) {
    Object value = options.get(option.key());
    if (value == null) {
      return option.defaultValue();
    }
    return (T) value;
  }

  /**
   * Values passed by user on the command line is converted into appropriate type and is filled into
   * an a new instance  ToolOptions, which is returned back to the caller.
   */
  @SuppressWarnings("unchecked")
  public static ToolOptions getToolOptionsFromCommandLine(
      CommandLine cmd, ImmutableList<Option<?>> frameworkOptions) {
    ToolOptions toolOptions = ToolOptions.create();

    for (Option<?> frameworkOption : frameworkOptions) {
      if (cmd.hasOption(frameworkOption.name())) {
        String value = cmd.getOptionValue(frameworkOption.name());
        TypeLiteral<?> type = frameworkOption.key().getTypeLiteral();
        if (type.equals(new TypeLiteral<List<String>>() {})) {
          String[] values = cmd.getOptionValues(frameworkOption.name());
          toolOptions.set(
              (Option<List<String>>)
                  frameworkOption,
              Arrays.asList(values));
        } else if (type.equals(TypeLiteral.get(String.class))) {
          toolOptions.set(
              (Option<String>) frameworkOption,
              value);
        } else if (type.equals(TypeLiteral.get(Integer.class))) {
          toolOptions.set(
              (Option<Integer>) frameworkOption,
              Integer.parseInt(value));
        } else if (type.equals(TypeLiteral.get(Boolean.class))) {
          toolOptions.set(
              (Option<Boolean>) frameworkOption,
              Boolean.parseBoolean(value));
        } else {
          // If more option types need to be supported, add them above.
          throw new IllegalArgumentException(
              String.format(
                  "Type '%s' of option '%s' is currently not supported."
                  + " Supported types are: List<String>, String, Integer, and Boolean",
                  type,
                  frameworkOption.name()));
        }
      }
    }
    return toolOptions;
  }

  /**
   * Check if help flag is set by user.
   */
  public static boolean isHelpFlagSet(CommandLine cmd) {
    return cmd.hasOption("help");
  }

  /**
   * Convert a collection of {@link Option} into an instance of Apache options {@link Options}
   *
   * This also adds a help options by default. This can be used for printing usage text.
   */
  public static Options convertToApacheCliOptions(
      ImmutableList<Option<?>> frameworkOptions) {
    Options options = new Options();
    for (Option<?> frameworkOption : frameworkOptions) {
      org.apache.commons.cli.Option option =
          new org.apache.commons.cli.Option(
              frameworkOption.name(), true, frameworkOption.description());
      options.addOption(option);
    }
    options.addOption("h", "help", false, "show usage");
    return options;
  }

  /**
   * Print usage statement.
   */
  public static void printUsage(String cmdLineSyntax, Options options) {
    HelpFormatter formater = new HelpFormatter();
    formater.printHelp(cmdLineSyntax, options);
  }
}
