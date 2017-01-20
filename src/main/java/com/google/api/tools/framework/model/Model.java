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

package com.google.api.tools.framework.model;

import com.google.api.Service;
import com.google.api.tools.framework.model.BoundedDiagCollector.TooManyDiagsException;
import com.google.api.tools.framework.model.stages.Merged;
import com.google.api.tools.framework.model.stages.Normalized;
import com.google.api.tools.framework.model.stages.Requires;
import com.google.api.tools.framework.model.stages.Resolved;
import com.google.api.tools.framework.processors.normalizer.DescriptorGenerator;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Queues;
import com.google.common.collect.Sets;
import com.google.inject.Key;
import com.google.protobuf.Api;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Message;
import com.google.protobuf.UInt32Value;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

/** Model of an API service. Also manages processing pipelines and accumulation of diagnostics. */
public class Model extends Element implements ConfigLocationResolver {

  // The proto files that will be excluded when generating API service configuration.
  // Only put files that are ABSOLUTELY UNRELATED to the API Service but are
  // still included in the FileDescriptorSet by protoc. Each file should be
  // accompanied by a comment justifying the reason for exclusion.
  private static final Set<String> BLACK_LISTED_FILES =
      ImmutableSet.<String>builder()
          // sawzall_message_set.proto is pulled twice in by protoc for some internal reason
          // and caused a duplicate symbol definition.
          .add("net/proto/sawzall_message_set.proto")
          .build();

  // The current default config version.
  private static final int CURRENT_CONFIG_DEFAULT_VERSION = 3;

  // The latest version of tools under development.
  private static final int DEV_CONFIG_VERSION = 4;

  private static final String CORP_DNS_SUFFIX = ".corp.googleapis.com";
  private static final String SANDBOX_DNS_SUFFIX = ".sandbox.googleapis.com";
  private static final String PRIVATE_API_DNS_SUFFIX = "-pa.googleapis.com";

  // An experiment which allows to turn off merging semantics and drop back to proto3.
  // This is for cases the new merging causes compatibility problems.
  private static final String PROTO3_CONFIG_MERGING_EXPERIMENT = "proto3_config_merging";

  /**
   * Creates a new model based on the given file descriptor, list of source file names and list of
   * experiments to be enabled for the model.
   */
  public static Model create(
      FileDescriptorSet proto,
      Iterable<String> sources,
      Iterable<String> experiments,
      ExtensionPool extensionPool) {
    return new Model(proto, sources, experiments, extensionPool, new BoundedDiagCollector());
  }

  /**
   * Creates a new model based on the given file descriptor, list of source file names and list of
   * experiments to be enabled for the model.
   */
  public static Model create(
      FileDescriptorSet proto,
      Iterable<String> sources,
      Iterable<String> experiments,
      ExtensionPool extensionPool,
      DiagCollector diagCollector) {
    return new Model(proto, sources, experiments, extensionPool, diagCollector);
  }

  /**
   * Creates a new model based on the given file descriptor set and list of source file names. The
   * file descriptor set is self-contained and contains the descriptors for the source files as well
   * as for all dependencies.
   */
  public static Model create(FileDescriptorSet proto, Iterable<String> sources) {
    return new Model(proto, sources, null, ExtensionPool.EMPTY, new BoundedDiagCollector());
  }

  /** Creates an model where all protos in the descriptor are considered to be sources. */
  public static Model create(FileDescriptorSet proto) {
    return new Model(proto, null, null, ExtensionPool.EMPTY, new BoundedDiagCollector());
  }

  /**
   * Creates a model from a normalized service config, rather than from descriptor and .yaml files.
   */
  public static Model create(Service normalizedConfig) {
    FileDescriptorSet regeneratedDescriptor = DescriptorGenerator.generate(normalizedConfig);
    Model model = create(regeneratedDescriptor);

    // Configured with a stripped Service
    Service.Builder builder = normalizedConfig.toBuilder();
    ImmutableList.Builder<Api> strippedApis = ImmutableList.builder();
    for (Api api : normalizedConfig.getApisList()) {
      strippedApis.add(
          Api.newBuilder().setName(api.getName()).setVersion(api.getVersion()).build());
    }
    // NOTE: Documentation may still contain text from the original protos.
    builder.clearEnums();
    builder.clearTypes();
    builder.clearApis();
    builder.addAllApis(strippedApis.build());
    ConfigSource strippedConfig = ConfigSource.newBuilder(builder.build()).build();

    model.setConfigSources(ImmutableList.of(strippedConfig));

    return model;
  }

  /** Returns the default config version. */
  public static int getDefaultConfigVersion() {
    return CURRENT_CONFIG_DEFAULT_VERSION;
  }

  /** Returns the config version under development. */
  public static int getDevConfigVersion() {
    return DEV_CONFIG_VERSION;
  }

  private ImmutableList<ProtoFile> files;
  private ImmutableSet<String> experiments;
  private final Map<Key<?>, Processor> processors = Maps.newLinkedHashMap();
  private final List<ConfigAspect> configAspects = Lists.newArrayList();
  private final DiagCollector diagCollector;
  private final DiagSuppressor diagSuppressor;
  private Set<String> visibilityLabels = Sets.newLinkedHashSet();
  private Set<Set<String>> declaredVisibilityCombinations = Sets.newLinkedHashSet();;
  private Scoper scoper = Scoper.UNRESTRICTED;
  private final List<ProtoElement> roots = Lists.newArrayList();

  /** List of validators registered with this model. */
  private final List<ConfigValidator<? extends Element>> validators = Lists.newArrayList();

  private Model(
      FileDescriptorSet proto,
      @Nullable Iterable<String> sources,
      @Nullable Iterable<String> experiments,
      ExtensionPool extensionPool,
      DiagCollector diagCollector) {
    Set<String> sourcesSet = sources == null ? null : Sets.newHashSet(sources);
    ImmutableList.Builder<ProtoFile> builder = ImmutableList.builder();
    // To de-dup FileDescriptorProto in the descriptor set generated by protoc.
    Set<String> includedFiles = Sets.newHashSet();
    for (FileDescriptorProto file : proto.getFileList()) {
      if (BLACK_LISTED_FILES.contains(file.getName()) || includedFiles.contains(file.getName())) {
        continue;
      }
      includedFiles.add(file.getName());
      builder.add(
          ProtoFile.create(
              this,
              file,
              sourcesSet == null || sourcesSet.contains(file.getName()),
              extensionPool));
    }
    if (extensionPool.getDescriptor() != null) {
      for (FileDescriptorProto file : extensionPool.getDescriptor().getFileList()) {
        if (BLACK_LISTED_FILES.contains(file.getName()) || includedFiles.contains(file.getName())) {
          continue;
        }
        includedFiles.add(file.getName());
        builder.add(
            ProtoFile.create(
                this,
                file,
                sourcesSet == null || sourcesSet.contains(file.getName()),
                extensionPool));
      }
    }
    files = builder.build();
    this.experiments =
        experiments == null ? ImmutableSet.<String>of() : ImmutableSet.copyOf(experiments);
    this.diagCollector = diagCollector;
    diagSuppressor = new DiagSuppressor(diagCollector);
  }
  // -------------------------------------------------------------------------
  // Syntax

  @Override
  public Model getModel() {
    return this;
  }

  @Override
  public Location getLocation() {
    return SimpleLocation.TOPLEVEL;
  }

  @Override
  public String getFullName() {
    return "APIModel";
  }

  @Override
  public String getSimpleName() {
    return getFullName();
  }

  public DiagCollector getDiagCollector() {
    return diagCollector;
  }

  /** Returns the list of (proto) files. */
  public ImmutableList<ProtoFile> getFiles() {
    return files;
  }

  /** Set the list of (proto) files. */
  public void setFiles(ImmutableList<ProtoFile> files) {
    this.files = files;
  }

  /**
   * Returns the visibility labels the caller wants to apply to this model. If it is null, no
   * visibility rules will be applied. If it is an empty set, only unrestricted elements will be
   * visible.
   */
  @Nullable
  public Set<String> getVisibilityLabels() {
    return visibilityLabels;
  }

  /**
   * Set visibility labels from the given list. Validity of labels will be checked in later stages.
   */
  public Set<String> setVisibilityLabels(@Nullable Set<String> visibilityLabels) {
    Set<String> current = this.visibilityLabels;
    this.visibilityLabels = visibilityLabels;
    return current;
  }

  /**
   * Get a collection of declared visibility combinations. TODO(user): Once we no longer create
   * old derived visibilty data, we should remove entire presence of declaredVisibilityCombination.
   */
  public Set<Set<String>> getDeclaredVisibilityCombinations() {
    return declaredVisibilityCombinations;
  }

  public Set<Set<String>> clearDeclaredVisibilityCombinations() {
    this.declaredVisibilityCombinations = Sets.newLinkedHashSet();
    this.declaredVisibilityCombinations.add(Sets.<String>newLinkedHashSet());
    return this.declaredVisibilityCombinations;
  }

  /** add to Set of a collection of declared visibility combinations. */
  public Set<Set<String>> addDeclaredVisibilityCombinations(
      Set<Set<String>> declaredVisibilityCombinations) {
    if (this.declaredVisibilityCombinations == null) {
      this.declaredVisibilityCombinations = declaredVisibilityCombinations;
    } else {
      this.declaredVisibilityCombinations.addAll(declaredVisibilityCombinations);
    }
    return declaredVisibilityCombinations;
  }

  public Set<Set<String>> addDeclaredVisibilityCombination(
      Set<String> declaredVisibilityCombination) {
    if (this.declaredVisibilityCombinations == null) {
      this.declaredVisibilityCombinations = Sets.newLinkedHashSet();
      this.declaredVisibilityCombinations.add(Sets.<String>newLinkedHashSet());
    }
    this.declaredVisibilityCombinations.add(declaredVisibilityCombination);
    return declaredVisibilityCombinations;
  }

  /** Checks whether a given experiment is enabled. */
  public boolean isExperimentEnabled(String experiment) {
    return experiments.contains(experiment);
  }

  /** Enables the given experiment (for testing). */
  @VisibleForTesting
  public void enableExperiment(String experiment) {
    this.experiments = FluentIterable.from(experiments).append(experiment).toSet();
  }

  // API v1 version suffix.
  private String apiV1VersionSuffix;

  /** Sets API v1 version suffix in the model. */
  public void setApiV1VersionSuffix(String value) {
    apiV1VersionSuffix = value;
  }

  /** Gets API v1 version suffix. */
  public String getApiV1VersionSuffix() {
    return apiV1VersionSuffix;
  }

  //-------------------------------------------------------------------------
  // Attributes belonging to resolved stage

  @Requires(Resolved.class)
  private SymbolTable symbolTable;

  /** Returns the symbolTable */
  @Requires(Resolved.class)
  public SymbolTable getSymbolTable() {
    return symbolTable;
  }

  /** For setting the symbol table. */
  public void setSymbolTable(SymbolTable symbolTable) {
    this.symbolTable = symbolTable;
  }

  // -------------------------------------------------------------------------
  // Attributes belonging to merged stage

  @Requires(Merged.class)
  private ConfigSource serviceConfig;

  /**
   * Add a root element to the model. Root elements are collected during merging and used to compute
   * the transitively reachable set of referenced elements.
   */
  public void addRoot(ProtoElement root) {
    roots.add(root);
  }

  /** Get the roots collected for the model. */
  public Iterable<ProtoElement> getRoots() {
    return roots;
  }

  /** Sets the scoper used for traversing this model. Returns the previous scoper. */
  public Scoper setScoper(Scoper scoper) {
    Scoper result = this.scoper;
    this.scoper = scoper;
    return result;
  }

  /** Gets the scoper used for this model. */
  public Scoper getScoper() {
    return scoper;
  }

  /** Returns the iterable scoped to the reachable elements. */
  public <E extends ProtoElement> Iterable<E> reachable(Iterable<E> elems) {
    return scoper.filter(elems);
  }

  /** Sets the service config from a proto. */
  @Deprecated
  public void setServiceConfig(Service config) {
    this.serviceConfig = ConfigSource.newBuilder(config).build();
  }

  /** Sets the service config from a config source. */
  public void setServiceConfig(ConfigSource source) {
    this.serviceConfig = source;
  }

  /**
   * Sets the service config based on a sequence of sources of heterogeneous types. Sources of the
   * same type will be merged together, and those applicable to the framework will be attached to
   * it.
   */
  public void setConfigSources(Iterable<ConfigSource> configs) {

    // Merge configs of same type.
    Map<Descriptor, ConfigSource.Builder> mergedConfigs = Maps.newHashMap();
    for (ConfigSource config : configs) {
      Descriptor descriptor = config.getConfig().getDescriptorForType();
      ConfigSource.Builder builder = mergedConfigs.get(descriptor);
      if (builder == null) {
        mergedConfigs.put(descriptor, config.toBuilder());
      } else if (isExperimentEnabled(PROTO3_CONFIG_MERGING_EXPERIMENT)) {
        builder.mergeFromWithProto3Semantics(config);
      } else {
        builder.mergeFrom(config);
      }
    }

    // Pick the configs we know and care about (currently, Service and Legacy).
    ConfigSource.Builder serviceConfig = mergedConfigs.get(Service.getDescriptor());
    if (serviceConfig != null) {
      setServiceConfig(serviceConfig.build());
    } else {
      // Set empty config.
      setServiceConfig(
          ConfigSource.newBuilder(
                  Service.newBuilder()
                      .setConfigVersion(
                          UInt32Value.newBuilder().setValue(Model.getDefaultConfigVersion()))
                      .build())
              .build());
    }

  }

  /** Sets the service config based on a sequence of messages. */
  @Deprecated
  public void setConfigs(Iterable<Message> configs) {
    setConfigSources(
        FluentIterable.from(configs)
            .transform(
                new Function<Message, ConfigSource>() {
                  @Override
                  public ConfigSource apply(Message input) {
                    return ConfigSource.newBuilder(input).build();
                  }
                }));
  }

  /** Returns the associated service config raw value. */
  @Requires(Merged.class)
  public Service getServiceConfig() {
    return (Service) serviceConfig.getConfig();
  }

  /** Returns the associated service config source. */
  @Requires(Merged.class)
  public ConfigSource getServiceConfigSource() {
    return serviceConfig;
  }

  /**
   * Returns the effective config version. Chooses the current default if the service config does
   * not specify it.
   */
  @Requires(Merged.class)
  public int getConfigVersion() {
    Service config = getServiceConfig();
    if (config.hasConfigVersion()) {
      return config.getConfigVersion().getValue();
    }
    return CURRENT_CONFIG_DEFAULT_VERSION;
  }

  // -------------------------------------------------------------------------
  // Attributes belonging to normalized stage

  @Requires(Normalized.class)
  private Service normalizedConfig;

  /** Returns the normalized service config */
  @Requires(Normalized.class)
  public Service getNormalizedConfig() {
    return normalizedConfig;
  }

  public void setNormalizedConfig(Service normalizedConfig) {
    this.normalizedConfig = normalizedConfig;
  }

  // -------------------------------------------------------------------------
  // Diagnosis

  // The user can add directives in comments such as
  //
  // (== suppress_warning http-* ==)
  //
  // This suppresses all lint warnings of the http aspect. Such warnings
  // use an identifier of the form <aspect>-<rule>. In the suppress_warning directive,
  // '*' can be used as a wildcard for <rule>.
  //
  // The underlying implementation maintains a regular expression for each model element
  // which accumulates patterns for all suppression directives associated with this element
  // -- and possibly additional programmatic sources.
  //
  // Further, the number of allowed diagnostics is limited (by default) to protect the application
  // from running out of memory when too many errors or warnings are generated.

  /** Returns a prefix to be used in diag messages for general errors and warnings. */
  public static String diagPrefix(String aspectName) {
    return String.format("%s: ", aspectName);
  }

  /** Returns a prefix to be used in diag messages representing linter warnings. */
  public static String diagPrefixForLint(String aspectName, String ruleName) {
    return String.format("(lint) %s-%s: ", aspectName, ruleName);
  }

  /**
   * Set a filter for warnings based on regular expression for aspect name. Only warnings containing
   * the aspect name pattern are produced.
   */
  @VisibleForTesting
  public void setWarningFilter(@Nullable String aspectNamePattern) {
    // Add as a pattern to the model.
    diagSuppressor.addPattern(this, "^(?!.*(" + aspectNamePattern + ")).*");
  }

  /** Shortcut for suppressing all warnings. */
  public void suppressAllWarnings() {
    diagSuppressor.addPattern(this, ".*");
  }

  public DiagSuppressor getDiagSuppressor() {
    return diagSuppressor;
  }

  /**
   * Adds a user-level suppression directive. The directive must be given in the form 'aspect-rule',
   * or 'aspect-*' to match any rule. Is used in comments such as '(== suppress_warning http-* ==)'
   * which will suppress all lint warnings generated by the http aspect.
   */
  public void addSupressionDirective(Element elem, String directive) {
    diagSuppressor.addSuppressionDirective(elem, directive, configAspects);
  }

  /**
   * Returns the service config file location of the given named field in the (sub)message. Returns
   * {@link SimpleLocation#TOPLEVEL} if the location is not known.
   */
  @Override
  public Location getLocationInConfig(Message message, String fieldName) {
    Location loc = getServiceConfigSource().getLocation(message, fieldName, null);
    return loc != SimpleLocation.UNKNOWN ? loc : SimpleLocation.TOPLEVEL;
  }

  /**
   * Returns the service config file location of the given named field in the (sub)message. The key
   * identifies the key of the map. For repeated fields, the element key is a zero-based index.
   * Returns {@link SimpleLocation#TOPLEVEL} if the location is not known.
   */
  @Override
  public Location getLocationOfRepeatedFieldInConfig(
      Message message, String fieldName, Object elementKey) {
    Location loc = getServiceConfigSource().getLocation(message, fieldName, elementKey);
    return loc != SimpleLocation.UNKNOWN ? loc : SimpleLocation.TOPLEVEL;
  }

  /** Adds diagnosis to the model if it is not suppressed. */
  public void addDiagIfNotSuppressed(Object elementOrLocation, Diag diag) {
    if (!diagSuppressor.isDiagSuppressed(diag, elementOrLocation)) {
      diagCollector.addDiag(diag);
    }
  }

  // -------------------------------------------------------------------------
  // Configuration aspects

  /** Registers the configuration aspect with the model. */
  public void registerConfigAspect(ConfigAspect aspect) {
    configAspects.add(aspect);
  }

  /** Returns the registered configuration aspects. */
  public Iterable<ConfigAspect> getConfigAspects() {
    return configAspects;
  }

  // -------------------------------------------------------------------------
  // Configuration validators

  /**
   * Registers a {@link ConfigValidator} with this model.
   *
   * <p>During the merge phase, the merged information is attached as attributes to the
   * ProtoElements. The {@link ConfigValidator}, for most cases, will validate the content of those
   * attributes and create error/warnings.
   */
  public <E extends Element> void registerValidator(ConfigValidator<E> validator) {
    validators.add(validator);
  }

  /** Returns a list of {@link ConfigValidator}s that are registered with this model. */
  public ImmutableList<ConfigValidator<? extends Element>> getValidators() {
    return ImmutableList.copyOf(this.validators);
  }

  // -------------------------------------------------------------------------
  // Stage processing

  /** Registers a stage processor. Returns the old processor or null if there wasn't one. */
  @Nullable
  public Processor registerProcessor(Processor processor) {
    return processors.put(processor.establishes(), processor);
  }

  /**
   * Establishes a processing stage. Runs the chain of all processors required to guarantee the
   * given key is attached at the model. Returns true on success.
   */
  public boolean establishStage(Key<?> key) {
    Deque<Key<?>> computing = Queues.newArrayDeque();
    return establishStage(computing, key);
  }

  private boolean establishStage(Deque<Key<?>> computing, Key<?> stage) {
    if (hasAttribute(stage)) {
      return true;
    }
    if (computing.contains(stage)) {
      throw new IllegalStateException(
          String.format(
              "Cyclic dependency of stages: %s => %s", Joiner.on(" => ").join(computing), stage));
    }
    computing.addLast(stage);
    Processor processor = processors.get(stage);
    if (processor == null) {
      throw new IllegalArgumentException(
          String.format("No processor registered to establish stage '%s'", stage));
    }
    for (Key<?> subStage : processor.requires()) {
      if (!establishStage(computing, subStage)) {
        return false;
      }
    }
    computing.removeLast();
    try {
      if (!processor.run(this)) {
        return false;
      }
    } catch (TooManyDiagsException ex) {
      // Process generated too many errors and wants to abort.
      return false;
    }

    if (!hasAttribute(stage)) {
      throw new IllegalStateException(
          String.format("Processor '%s' failed to establish stage '%s'", processor, stage));
    }
    return true;
  }

  // -------------------------------------------------------------------------
  // Data path.

  private String dataPath = ".";

  /**
   * Returns a search path for data dependencies. The path is a list of directories separated by
   * File.pathSeparator.
   */
  public String getDataPath() {
    return dataPath;
  }

  /** Sets the data dependency search path. */
  public void setDataPath(String dataPath) {
    this.dataPath = dataPath;
  }

  /**
   * Finds a file on the data path. Returns null if not found. DEPRECATED: Use
   * ToolUtil.findDataFile()
   */
  @Nullable
  @Deprecated
  public File findDataFile(String name) {
    Path file = Paths.get(name);
    if (file.isAbsolute()) {
      return Files.exists(file) ? file.toFile() : null;
    }
    for (String path : Splitter.on(File.pathSeparator).split(dataPath)) {
      file = Paths.get(path, name);
      if (Files.exists(file)) {
        return file.toFile();
      }
    }
    return null;
  }

  /** Returns the control environment string of this model. */
  public String getControlEnvironment() {
    return getServiceConfig().getControl().getEnvironment();
  }

  @VisibleForTesting
  static boolean isPrivateService(String serviceName) {
    return serviceName.endsWith(PRIVATE_API_DNS_SUFFIX)
        || serviceName.endsWith(SANDBOX_DNS_SUFFIX)
        || serviceName.endsWith(CORP_DNS_SUFFIX);
  }

  /**
   * Returns true if the service is a private API, corp API, or on sandbox.googles.com non
   * production environent.
   */
  public boolean isPrivateService() {
    return isPrivateService(getServiceConfig().getName());
  }

  // -------------------------------------------------------------------------
  // Generation of derived discovery docs.
  private boolean deriveDiscoveryDoc = true;

  /**
   * Returns true if the derived discovery doc should be generated and added into service config.
   */
  public boolean shouldDerivedDiscoveryDoc() {
    return deriveDiscoveryDoc;
  }

  public void enableDiscoveryDocDerivation(boolean generateDerivedDiscovery) {
    this.deriveDiscoveryDoc = generateDerivedDiscovery;
  }
}
