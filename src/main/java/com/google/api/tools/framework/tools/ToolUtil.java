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

import com.google.api.tools.framework.model.ConfigSource;
import com.google.api.tools.framework.model.Diag;
import com.google.api.tools.framework.model.DiagCollector;
import com.google.api.tools.framework.model.Model;
import com.google.api.tools.framework.model.SimpleLocation;
import com.google.api.tools.framework.snippet.Doc;
import com.google.api.tools.framework.snippet.Doc.AnsiColor;
import com.google.api.tools.framework.yaml.YamlReader;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.protobuf.Message;
import com.google.protobuf.TextFormat;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import javax.annotation.Nullable;

/** Utilities for tools. */
public class ToolUtil {

  /**
   * Writes a set of files with directory structure to a .jar. The content is a map from file path
   * to one of {@link Doc}, {@link String}, or {@code byte[]}.
   */
  public static void writeJar(Map<String, ?> content, String outputName) throws IOException {
    OutputStream outputStream = new FileOutputStream(outputName);
    JarOutputStream jarFile = new JarOutputStream(outputStream);
    OutputStreamWriter writer = new OutputStreamWriter(jarFile, StandardCharsets.UTF_8);
    try {
      for (Map.Entry<String, ?> entry : content.entrySet()) {
        jarFile.putNextEntry(new JarEntry(entry.getKey()));
        Object value = entry.getValue();
        if (value instanceof Doc) {
          writer.write(((Doc) value).prettyPrint());
          writer.flush();
        } else if (value instanceof String) {
          writer.write((String) value);
          writer.flush();
        } else if (value instanceof byte[]) {
          jarFile.write((byte[]) value);
        } else {
          throw new IllegalArgumentException("Expected one of Doc, String, or byte[]");
        }
        jarFile.closeEntry();
      }
    } finally {
      writer.close();
      jarFile.close();
    }
  }

  /** Writes a proto out to a file. */
  public static void writeProto(Message content, String outputName) throws IOException {
    try (OutputStream outputStream = new FileOutputStream(outputName)) {
      content.writeTo(outputStream);
    }
  }

  /** Writes a proto out to a file. */
  public static void writeTextProto(Message content, String outputName) throws IOException {
    try (BufferedWriter output = new BufferedWriter(new FileWriter(outputName))) {
      TextFormat.print(content, output);
    }
  }

  /**
   * Writes a content object into a set of output files. The content is one of {@link Doc}, {@link
   * String} or {@code byte[]}.
   */
  public static void writeFiles(Map<String, ?> content, String baseName) throws IOException {

    for (Map.Entry<String, ?> entry : content.entrySet()) {
      File outputFile =
          Strings.isNullOrEmpty(baseName)
              ? new File(entry.getKey())
              : new File(baseName, entry.getKey());
      outputFile.getParentFile().mkdirs();
      OutputStream outputStream = new FileOutputStream(outputFile);
      OutputStreamWriter writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
      try {
        Object value = entry.getValue();
        if (value instanceof Doc) {
          writer.write(((Doc) value).prettyPrint());
          writer.flush();
        } else if (value instanceof String) {
          writer.write((String) value);
          writer.flush();
        } else if (value instanceof byte[]) {
          outputStream.write((byte[]) value);
          outputStream.flush();
        } else {
          throw new IllegalArgumentException("Expected one of Doc, String, or byte[]");
        }
      } finally {
        writer.close();
      }
    }
  }

  /** Report errors and warnings. */
  public static void reportDiags(DiagCollector diagCollector, boolean colored) {
    for (Diag diag : diagCollector.getDiags()) {
      System.err.println(diagToString(diag, colored));
    }
  }

  /** Produce a string for the diagnosis, with optional coloring. */
  public static String diagToString(Diag diag, boolean colored) {
    Doc text;
    switch (diag.getKind()) {
      case ERROR:
        text = Doc.text("ERROR: ");
        if (colored) {
          text = Doc.color(AnsiColor.RED, text);
        }
        break;
      case WARNING:
        text = Doc.text("WARNING: ");
        if (colored) {
          text = Doc.color(AnsiColor.YELLOW, text);
        }
        break;
      default:
        text = Doc.text("HINT:");
        break;
    }
    text =
        text.add(Doc.text(diag.getLocation().getDisplayString()))
            .add(Doc.text(": "))
            .add(Doc.text(diag.getMessage()));
    return text.toString();
  }

  public static Set<FileWrapper> sanitizeSourceFiles(List<FileWrapper> sources) {
    // Does nothing currently.
    return ImmutableSet.copyOf(sources);
  }

  /** Sets up the model configs, reading them from Yaml files and attaching to the model. */
  public static List<FileWrapper> readModelConfigs(
      String dataPath, List<String> configs, DiagCollector diagCollector) {
    List<FileWrapper> files = Lists.newArrayList();
    for (String filename : configs) {
      File file = findDataFile(filename, dataPath);
      if (file == null) {
        diagCollector.addDiag(
            Diag.error(SimpleLocation.TOPLEVEL, "Cannot find configuration file '%s'.", filename));

      } else {
        try {
          files.add(FileWrapper.from(filename));
        } catch (IOException ex) {
          diagCollector.addDiag(
              Diag.error(
                  SimpleLocation.TOPLEVEL,
                  "Cannot read input file '%s': %s",
                  filename,
                  ex.getMessage()));
        }
      }
    }
    if (diagCollector.hasErrors()) {
      return null;
    }
    return files;
  }

  @Nullable
  public static File findDataFile(String name, String dataPath) {
    Path file = Paths.get(name);
    if (file.isAbsolute()) {
      return java.nio.file.Files.exists(file) ? file.toFile() : null;
    }
    for (String path : Splitter.on(File.pathSeparator).split(dataPath)) {
      file = Paths.get(path, name);
      if (java.nio.file.Files.exists(file)) {
        return file.toFile();
      }
    }
    return null;
  }
  /** Sets up the model configs, attaching to the model. */
  public static void setupModelConfigs(Model model, Set<FileWrapper> files) {
    DiagCollector diagCollector = model.getDiagCollector();
    ImmutableList.Builder<ConfigSource> builder = ImmutableList.builder();

    for (FileWrapper file : files) {
      ConfigSource message =
          YamlReader.readConfig(
              model.getDiagCollector(), file.getFilename(), file.getFileContents().toStringUtf8());
      if (message != null) {
        builder.add(message);
      }
    }
    if (diagCollector.hasErrors()) {
      return;
    }
    model.setConfigSources(builder.build());
  }
}
