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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.protobuf.Any;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.TextFormat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * A protobuf text formatter which prints instances of the {@code google.protobuf.Any} type
 * in clear text, for use in tests.
 *
 */
public class TextFormatForTest {

  public static final TextFormatForTest INSTANCE = new TextFormatForTest();

  private final Map<String, Message> anyConverterRegistry = Maps.newHashMap();

  /**
   * Registers a type URL for Any together with a default instance. Only instances
   * which are registered here are rendered in clear text.
   */
  public TextFormatForTest registerAnyInstance(String typeUrl, Message defaultInstance) {
    anyConverterRegistry.put(typeUrl, defaultInstance);
    return this;
  }

  /**
   * Appends message string to output.
   */
  public void print(MessageOrBuilder message, Appendable output) throws IOException {
    output.append(printToString(message));
  }

  /**
   * Converts a message into a string.
   */
  public String printToString(MessageOrBuilder message) {
    StringBuilder result = new StringBuilder();
    for (FieldDescriptor field : getFieldsInNumberOrder(message.getDescriptorForType())) {

      // Skip empty fields.
      if ((field.isRepeated() && message.getRepeatedFieldCount(field) == 0)
          || (!field.isRepeated() && !message.hasField(field))) {
        continue;
      }

      // Normalize repeated and singleton fields.
      Object rawValue = message.getField(field);
      @SuppressWarnings("unchecked")
      List<Object> values =
          field.isMapField()
          ? sortMapEntries(field, rawValue)
          : field.isRepeated()
          ? (List<Object>) rawValue
          : ImmutableList.of(rawValue);

      // Print field values.
      for (Object value : values) {
        result.append(printFieldToString(field, value));
      }

    }
    return result.toString();
  }

  private String printFieldToString(FieldDescriptor field, Object value) {
    StringBuilder result = new StringBuilder();

    result.append(field.getName());

    // If this is a google.protobuf.Any instance, attempt to replace value with the parsed
    // content, so we get clear text for it.
    Message anyValue = maybeUnpackAnyType(field, value);
    if (anyValue != null) {
      result.append(String.format(" [instance of %s]",
          anyValue.getDescriptorForType().getFullName()));
      value = anyValue;
    }

    // Render the value.
    if (field.getType() == FieldDescriptor.Type.MESSAGE) {
      result.append(String.format(" {%n"));
      String content = printToString((Message) value).trim();
      if (!content.isEmpty()) {
        String indentedContent = content.replace("\n", "\n  ");
        result.append("  " + indentedContent);
        result.append(String.format("%n}%n"));
      } else {
        result.append(String.format("}%n"));
      }
    } else {
      result.append(": ");
      try {
        TextFormat.printFieldValue(field, value, result);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      result.append(String.format("%n"));
    }
    return result.toString();
  }

  /**
   * Attempt to unpack if its an any instance. Returns null if not unpacked.
   */
  @Nullable private Message maybeUnpackAnyType(FieldDescriptor field, Object value) {
    if (field.getType() == FieldDescriptor.Type.MESSAGE
        && field.getMessageType().getFullName().equals(Any.getDescriptor().getFullName())) {
      Any any = (Any) value;
      Message defaultInstance = anyConverterRegistry.get(any.getTypeUrl());
      if (defaultInstance != null) {
        try {
          return defaultInstance.toBuilder().mergeFrom(any.getValue()).build();
        } catch (InvalidProtocolBufferException e) {
          throw new RuntimeException(e);
        }
      }
    }
    return null;
  }

  /**
   * Get fields in field number order.
   */
  private static Iterable<FieldDescriptor> getFieldsInNumberOrder(Descriptor descriptor) {
    List<FieldDescriptor> fields = new ArrayList<>();
    fields.addAll(descriptor.getFields());
    Collections.sort(fields,  new Comparator<FieldDescriptor>() {
      @Override
      public int compare(FieldDescriptor f1, FieldDescriptor f2) {
        return f1.getNumber() - f2.getNumber();
      }
    });
    return fields;
  }

  /**
   * Sorts entries for a map field by key.
   */
  @SuppressWarnings("unchecked")
  private static List<Object> sortMapEntries(FieldDescriptor field, Object value) {
    List<Message> entries = (List<Message>) value;
    List<Message> sortedEntries = new ArrayList<>();
    sortedEntries.addAll(entries);
    final FieldDescriptor keyField = field.getMessageType().findFieldByNumber(1);
    Collections.sort(sortedEntries, new Comparator<Message>() {
      @Override
      public int compare(Message m1, Message m2) {
        return m1.getField(keyField).toString().compareTo(m2.getField(keyField).toString());
      }
    });
    return (List<Object>) (Object) sortedEntries;
  }

}
