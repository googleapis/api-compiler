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

package com.google.api.tools.framework.model.testing;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.api.AnnotationsProto;
import com.google.api.AuthProto;
import com.google.api.Service;
import com.google.api.tools.framework.model.ConfigSource;
import com.google.api.tools.framework.model.DiagCollector;
import com.google.api.tools.framework.model.ExtensionPool;
import com.google.api.tools.framework.model.Model;
import com.google.api.tools.framework.yaml.YamlReader;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.Message;
import com.google.protobuf.TextFormat;
import com.google.protobuf.TextFormat.ParseException;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A class to represent a test api configuration.
 *
 * <p>Compiles proto sources found via a {@link TestDataLocator} and converts yaml service
 * config files. Allows to create a {@link Model} from them.
 */
public class TestConfig {

  private static final String PROTOCOL_COMPILER;
  static {
    if (!com.google.common.base.Strings.isNullOrEmpty(System.getenv("PROTOC_COMPILER"))) {
      PROTOCOL_COMPILER = System.getenv("PROTOC_COMPILER");
    } else {
      PROTOCOL_COMPILER = "protoc";
    }
  }

  private static final Pattern PROTO_IMPORT_PATTERN =
      Pattern.compile("\\s*import\\s*\"(.*)\"");

  private static final ExtensionRegistry EXTENSIONS;
  static {
    EXTENSIONS = ExtensionRegistry.newInstance();
    AnnotationsProto.registerAllExtensions(EXTENSIONS);
    AuthProto.registerAllExtensions(EXTENSIONS);
  }

  private final List<String> protoFiles;
  private final Path descriptorFile;
  private final TestDataLocator testDataLocator;
  private final String tempDir;

  /**
   * Creates a test api. The passed temp dir is managed by the caller; in a test, it is usally
   * created by the TemporaryFolder rule of junit. The passed proto files as well as their
   * imports must be retrievable via the passed test data locator.
   */
  public TestConfig(TestDataLocator testDataLocator, String tempDir, List<String> protoFiles) {
    this.testDataLocator = testDataLocator;
    this.protoFiles = ImmutableList.copyOf(protoFiles);
    this.tempDir = tempDir;
    // Extract all needed proto files.
    Set<String> extracted = Sets.newHashSet();
    for (String source : protoFiles) {
      extractProtoSources(extracted, source);
    }

    this.descriptorFile = Paths.get(tempDir, "_descriptor.dsc");
    compileProtos(tempDir, protoFiles, descriptorFile.toString());
  }

  /**
   * Returns the test data locator associated with this test config.
   */
  public TestDataLocator getTestDataLocator() {
    return testDataLocator;
  }

  /**
   * Returns the temp directory into which this test config fetches test data.
   */
  public String getTempDir() {
    return tempDir;
  }

  /**
   * Reads test data associated with this test api. Uses the {@link TestDataLocator}
   * provided at creation time.
   */
  public String readTestData(String name) {
    URL url = testDataLocator.findTestData(name);
    if (url == null) {
      throw new IllegalArgumentException(String.format("Cannot find resource '%s'", name));
    }
    return testDataLocator.readTestData(url);
  }

  /**
   * Copies test data, located via the test data locator, into the temporary directory associated
   * with this test config. Returns the content of the test data as a string.
   */
  public String copyTestData(String name) {
    String content = readTestData(name);
    Path targetPath = Paths.get(tempDir, name);
    try {
      Files.createDirectories(targetPath.getParent());
      Files.write(targetPath, content.getBytes(StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new IllegalArgumentException(String.format("Cannot copy '%s': %s",
          targetPath, e.getMessage()));
    }
    return content;
  }

  /**
   * Copies test data and returns the path pointing to it.
   */
  public Path copyTestDataAndGetPath(String name) {
    copyTestData(name);
    return Paths.get(tempDir, name);
  }

  /**
   * Gets the descriptor file generated from the proto sources.
   */
  public Path getDescriptorFile() {
    return descriptorFile;
  }

  /**
   * Returns the file descriptor set generated from the sources of this api.
   */
  public FileDescriptorSet getDescriptor() throws IOException {
    return FileDescriptorSet.parseFrom(Files.newInputStream(descriptorFile), EXTENSIONS);
  }

  /**
   * Parses the config files, in Yaml format.
   */
  public ImmutableList<ConfigSource> getApiYamlConfigSources(DiagCollector diag,
      List<String> configFileNames) {
    ImmutableList.Builder<ConfigSource> builder = ImmutableList.builder();

    for (String fileName : configFileNames) {
      ConfigSource config = YamlReader.readConfig(diag, fileName, readTestData(fileName));
      if (config != null) {
        builder.add(config);
      }
    }
    return builder.build();
  }

  /**
   * Parses the config files, in Yaml format.
   */
  @Deprecated
  public ImmutableList<Message> getApiYamlConfig(DiagCollector diag,
      List<String> configFileNames) {
    return FluentIterable.from(getApiYamlConfigSources(diag, configFileNames))
        .transform(new Function<ConfigSource, Message>() {
          @Override
          public Message apply(ConfigSource input) {
            return input.getConfig();
          }
        }).toList();
  }

  /**
   * Parses the config file, in proto text format, and returns it.
   */
  public Service getApiProtoConfig(String configFileName) throws ParseException {
    String content = readTestData(configFileName);
    Service.Builder builder = Service.newBuilder();
    TextFormat.merge(content, builder);
    return builder.build();
  }

  /**
   * Creates a model, based on the provided config files.
   */
  public Model createModel(List<String> configFileNames) {
    try {
      Model model = Model.create(getDescriptor(), protoFiles, ImmutableList.<String>of(),
          ExtensionPool.EMPTY);
      model.setConfigSources(getApiYamlConfigSources(model.getDiagCollector(), configFileNames));
      return model;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Collect all needed proto files as resources from the classpath and store them in the
   * temporary directory, so protoc can find them.
   */
  private void extractProtoSources(Set<String> extracted, String protoFile) {
    if (!extracted.add(protoFile)) {
      return;
    }
    String content = copyTestData(protoFile);
    Matcher matcher = PROTO_IMPORT_PATTERN.matcher(content);
    while (matcher.find()) {
      extractProtoSources(extracted, matcher.group(1));
    }
  }

  /**
   * Calls the protocol compiler to compile the given sources into a descriptor.
   */
  protected void compileProtos(String tempDir, List<String> sourceFiles, String outputFile) {
    List<String> commandLine = Lists.newArrayList();
    commandLine.add(PROTOCOL_COMPILER);
    commandLine.add("--include_imports");
    commandLine.add("--proto_path=" + tempDir);
    commandLine.add("--include_source_info");
    commandLine.add("-o");
    commandLine.add(outputFile);
    for (String source : sourceFiles) {
      commandLine.add(Paths.get(tempDir, source).toString());
    }
    ProcessBuilder processBuilder = new ProcessBuilder(commandLine);
    Path output = Paths.get(tempDir, "_protoc.out");
    processBuilder.redirectErrorStream(true);
    processBuilder.redirectOutput(output.toFile());
    try {
      Process process = processBuilder.start();
      if (process.waitFor() != 0) {
        throw new IllegalArgumentException(
            String.format("proto compilation failed: %s:\n%s",
                Joiner.on(" ").join(commandLine), new String(Files.readAllBytes(output), UTF_8)));
      }
    } catch (Exception e) {
      throw new IllegalArgumentException(
          String.format("proto compilation failed with internal error: %s", e.getMessage()));
    }
  }

}
