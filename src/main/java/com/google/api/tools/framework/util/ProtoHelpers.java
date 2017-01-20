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

package com.google.api.tools.framework.util;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.DescriptorProtos.FileDescriptorProtoOrBuilder;
import com.google.protobuf.Descriptors.EnumValueDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.ExtensionLite;
import com.google.protobuf.GeneratedMessage;
import com.google.protobuf.MessageOrBuilder;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Random assortment of basic helper methods for dealing with proto objects.
 *
 * <p>For all methods that can return null, a null means that the requested object wasn't found.
 */
public class ProtoHelpers {
  /** Return a name that can be used for an object or empty string if no name found. */
  public static String getName(MessageOrBuilder mob) {
    String name = getFieldStringIfPresent(mob, "name");
    return null == name ? "" : name;
  }

  /** Returns the name that this object would contribute to a fully qualified name of a type. */
  public static String getContextName(MessageOrBuilder mob) {
    if (mob instanceof FileDescriptorProtoOrBuilder) {
      return ((FileDescriptorProtoOrBuilder) mob).getPackage();
    }
    return getName(mob);
  }

  @Nullable
  public static String getFieldStringIfPresent(MessageOrBuilder mob, String fieldName) {
    return getFieldIfPresent(mob, fieldName, String.class);
  }

  @Nullable
  public static String getFieldStringIfPresent(MessageOrBuilder mob, FieldDescriptor fieldDesc) {
    return getFieldIfPresent(mob, fieldDesc, String.class);
  }

  @Nullable
  public static <Type> Type getFieldIfPresent(
      MessageOrBuilder mob, String fieldName, Class<Type> clazz) {
    FieldDescriptor fieldDesc = mob.getDescriptorForType().findFieldByName(fieldName);
    if (null == fieldDesc) {
      return null;
    }
    return getFieldIfPresent(mob, fieldDesc, clazz);
  }

  @Nullable
  public static <Type> Type getFieldIfPresent(
      MessageOrBuilder mob, FieldDescriptor fieldDesc, Class<Type> clazz) {
    if (mob.hasField(fieldDesc)) {
      Object fieldValue = null;
      try {
        fieldValue = mob.getField(fieldDesc);
        if (null == fieldValue) {
          return null;
        }

        if (fieldValue instanceof EnumValueDescriptor
            && clazz.isEnum()) {
          // Do some sanity checks and convert the EnumValueDescriptor into the (Type) class (which
          // has to be an enum)
          EnumValueDescriptor fieldEnumValue = (EnumValueDescriptor) fieldValue;
          if (clazz.getSimpleName().equals(fieldEnumValue.getType().getName())) {
            Type[] enumValues = clazz.getEnumConstants();
            if (fieldEnumValue.getIndex() >= 0
                && fieldEnumValue.getIndex() < enumValues.length) {
              Type value = enumValues[fieldEnumValue.getIndex()];
              return value;
            }
          }
          throw new RuntimeException(String.format(
              "Couldn't convert '%s' to class '%s'",
              fieldValue,
              clazz.getName()));
        }

        return clazz.cast(fieldValue);
      } catch (ClassCastException ex) {
        throw new RuntimeException(
            String.format(
                "Expected (%s) type, not (%s), for field '%s' of (%s)%s",
                clazz,
                fieldValue.getClass(),
                fieldDesc.getName(),
                mob.getClass(),
                getName(mob)),
            ex);
      }
    }
    return null;
  }

  public static boolean getFieldBoolean(
      MessageOrBuilder mob, String fieldName, boolean defaultValue) {
    boolean value = defaultValue;
    FieldDescriptor fieldDesc = mob.getDescriptorForType().findFieldByName(fieldName);
    if (null != fieldDesc) {
      if (mob.hasField(fieldDesc)) {
        Object fieldValue = mob.getField(fieldDesc);
        if (fieldValue instanceof Boolean) {
          value = (Boolean) fieldValue;
        }
      }
    }
    return value;
  }

  @Nullable
  public static <MessageType extends GeneratedMessage.ExtendableMessage<MessageType>,
                 Type extends GeneratedMessage> Type getExtensionObject(
      GeneratedMessage.ExtendableMessageOrBuilder<MessageType> mob,
      ExtensionLite<MessageType, Type> extension) {
    if (mob.hasExtension(extension)) {
      return mob.getExtension(extension);
    }
    return null;
  }

  @Nullable
  public static <
          MessageType extends GeneratedMessage.ExtendableMessage<MessageType>,
          Type extends GeneratedMessage>
      List<Type> getRepeatedExtensionObjects(
          GeneratedMessage.ExtendableMessageOrBuilder<MessageType> mob,
          ExtensionLite<MessageType, List<Type>> extension) {
    ImmutableList.Builder extensionList = ImmutableList.builder();
    int extensionCount = mob.getExtensionCount(extension);
    for (int extensionIndex = 0; extensionIndex < extensionCount; ++extensionIndex) {
      extensionList.add(mob.getExtension(extension, extensionIndex));
    }
    return extensionList.build();
  }
}
