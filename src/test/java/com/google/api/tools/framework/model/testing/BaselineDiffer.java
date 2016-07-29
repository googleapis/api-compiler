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

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.FluentIterable;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import name.fraser.neil.plaintext.diff_match_patch;

/**
 * A differ for {@link BaselineTestCase}, based on the commonly used diff_match_patch package,
 * running in line mode.
 *
 * See https://code.google.com/p/google-diff-match-patch.
 */
public class BaselineDiffer extends diff_match_patch {

  private static final Splitter LINE_SPLITTER = Splitter.on('\n');
  private static final Joiner LINE_JOINER = Joiner.on('\n');

  private static final int CONTEXT_SIZE = 2;

  public String diff(String text1, String text2) {
    // Runs the algorithm in line mode.
    // See https://code.google.com/p/google-diff-match-patch/wiki/LineOrWordDiffs.

    // Convert to lines-as-chars representation.
    LinesToCharsResult result = diff_linesToChars(text1, text2);
    String chars1 = getField(String.class, result, "chars1");
    String chars2 = getField(String.class, result, "chars2");
    @SuppressWarnings("unchecked")
    List<String> lines = getField(List.class, result, "lineArray");

    // Perform diff.
    LinkedList<Diff> diffs = diff_main(chars1, chars2, false);

    // Convert back and cleanup.
    diff_charsToLines(diffs, lines);
    diff_cleanupSemantic(diffs);

    List<String> output = new ArrayList<>();
    for (Diff diff : diffs) {
      switch (diff.operation) {
        case EQUAL:
          addOutput(output, "= ", diff.text);
          break;
        case DELETE:
          addOutput(output, "< ", diff.text);
          break;
        case INSERT:
          addOutput(output, "> ", diff.text);
          break;
      }
    }
    collapseContext(output);
    return LINE_JOINER.join(output);
  }

  private static void addOutput(List<String> output, final String prefix, String text) {
    output.addAll(FluentIterable.from(LINE_SPLITTER.split(text.trim())).transform(
        new Function<String, String>() {
          @Override
          public String apply(String input) {
            return prefix + input;
          }
        }).toList());
  }

  /**
   * Collapses larger areas of equal diffs for better readability.
   */
  private static void collapseContext(List<String> lines) {
    int i = 0;
    while (i < lines.size()) {
      int j = i;
      while (j < lines.size() && lines.get(j).startsWith("=")) {
        j++;
      }
      if (j - i > CONTEXT_SIZE) {
        int count = j - i - CONTEXT_SIZE;
        int start = i + CONTEXT_SIZE / 2;
        for (int k = 0; k < count && start < lines.size(); k++) {
          lines.remove(start);
          j--;
        }
        lines.add(start, String.format("= ... %d equal lines omitted ...", count));
        j++;
      }
      i = j + 1;
    }
  }

  /**
   * A hack which allows us to access unintentionally hidden fields in the Java version
   * of diff_match_patch so we can run it in line mode.
   *
   * <p>Background: Fields in {@link name.fraser.neil.plaintext.diff_match_patch.LinesToCharsResult}
   * are declared as protected and can't be accessed outside of the package. This is a known issue
   * discussed in multiple forums.
   */
  private static <T> T getField(Class<T> type, LinesToCharsResult result, String name) {
    try {
      Field field = LinesToCharsResult.class.getDeclaredField(name);
      if (field != null) {
        field.setAccessible(true);
        return type.cast(field.get(result));
      }
    } catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException e) {
      // fall through to throw
    }
    throw new IllegalArgumentException(String.format(
        "The field '%s' is unknown in '%s'. Check whether "
        + "`diff_match_patch` code has been updated so we don't need the hack here anymore!",
        name, result.getClass().getName()));
  }
}