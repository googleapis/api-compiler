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
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import com.google.protobuf.Message;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

/**
 * Utilities for tools.
 */
public class ToolUtil {

  /**
   * Writes a set of files with directory structure to a .jar. The content is a map from
   * file path to one of {@link Doc}, {@link String}, or {@code byte[]}.
   */
  public static void writeJar(Map<String, ?> content, String outputName)
      throws IOException {
    OutputStream outputStream = new FileOutputStream(outputName);
    JarOutputStream jarFile = new JarOutputStream(outputStream);
    OutputStreamWriter writer = new OutputStreamWriter(jarFile);
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

  /**
   * Writes a proto out to a file.
   */
  public static void writeProto(Message content, String outputName)
      throws IOException {
    try (OutputStream outputStream = new FileOutputStream(outputName)) {
      content.writeTo(outputStream);
    }
  }

  /**
   * Writes a content object into a set of output files. The content is one of {@link Doc},
   * {@link String} or {@code byte[]}.
   */
  public static void writeFiles(Map<String, ?> content, String baseName)
      throws IOException {

    for (Map.Entry<String, ?> entry : content.entrySet()) {
      File outputFile = Strings.isNullOrEmpty(baseName) ? new File(entry.getKey())
          : new File(baseName, entry.getKey());
      outputFile.getParentFile().mkdirs();
      OutputStream outputStream = new FileOutputStream(outputFile);
      OutputStreamWriter writer = new OutputStreamWriter(outputStream);
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

  /**
   * Report errors and warnings.
   */
  public static void reportDiags(DiagCollector diagCollector, boolean colored) {
    for (Diag diag : diagCollector.getDiags()) {
      System.err.println(diagToString(diag, colored));
    }
  }

  /**
   * Produce a string for the diagnosis, with optional coloring.
   */
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
    text = text.add(Doc.text(diag.getLocation().getDisplayString())).add(Doc.text(": "))
        .add(Doc.text(diag.getMessage()));
    return text.toString();
  }

  /**
   * Sanitize the sources list removing any unwanted files.
   */
  public static List<String> sanitizeSourceFiles(Iterable<String> sources) {
    // Does nothing currently.
    return ImmutableList.copyOf(sources);
  }

  /**
   * Sets up the model configs, reading them from Yaml files and attaching to the model.
   */
  public static void setupModelConfigs(Model model, List<String> configs) {
    DiagCollector diagCollector = model.getDiagCollector();
    ImmutableList.Builder<ConfigSource> builder = ImmutableList.builder();

    for (String fileName : configs) {
      File file = model.findDataFile(fileName);
      if (file == null) {
        diagCollector.addDiag(Diag.error(SimpleLocation.TOPLEVEL,
            "Cannot find configuration file '%s'.", fileName));
        continue;
      }
      try {
        ConfigSource message = YamlReader.readConfig(model.getDiagCollector(), fileName,
            Files.toString(new File(fileName), StandardCharsets.UTF_8));
        if (message != null) {
          builder.add(message);
        }
      } catch (IOException e) {
        diagCollector.addDiag(Diag.error(SimpleLocation.TOPLEVEL,
            "Cannot read configuration file '%s': %s",
            fileName, e.getMessage()));
      }
    }
    if (diagCollector.hasErrors()) {
      return;
    }
    model.setConfigSources(builder.build());
  }
}
