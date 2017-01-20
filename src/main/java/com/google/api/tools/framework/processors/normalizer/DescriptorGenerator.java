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

package com.google.api.tools.framework.processors.normalizer;

import com.google.api.Service;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.protobuf.Any;
import com.google.protobuf.Api;
import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.EnumDescriptorProto;
import com.google.protobuf.DescriptorProtos.EnumOptions;
import com.google.protobuf.DescriptorProtos.EnumValueDescriptorProto;
import com.google.protobuf.DescriptorProtos.EnumValueOptions;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Label;
import com.google.protobuf.DescriptorProtos.FieldOptions;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.protobuf.DescriptorProtos.MessageOptions;
import com.google.protobuf.DescriptorProtos.MethodDescriptorProto;
import com.google.protobuf.DescriptorProtos.MethodOptions;
import com.google.protobuf.DescriptorProtos.ServiceDescriptorProto;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Enum;
import com.google.protobuf.EnumValue;
import com.google.protobuf.Field;
import com.google.protobuf.Field.Cardinality;
import com.google.protobuf.Field.Kind;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.Method;
import com.google.protobuf.Option;
import com.google.protobuf.Parser;
import com.google.protobuf.SourceContext;
import com.google.protobuf.Type;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DescriptorGenerator translates a normalized google.api.Service into a FileDescriptorSet. This
 * allows the config tools to use the normalized Service rather than source files as input.
 */
public class DescriptorGenerator {

  private static final String FIELD_OPTION_NAME_PREFIX = "proto2.FieldOptions.";
  private static final String METHOD_OPTION_NAME_PREFIX = "proto2.MethodOptions.";
  private static final String TYPE_OPTION_NAME_PREFIX = "proto2.MessageOptions.";
  private static final String ENUM_OPTION_NAME_PREFIX = "proto2.EnumOptions.";
  private static final String ENUM_VALUE_OPTION_NAME_PREFIX = "proto2.EnumValueOptions.";
  private static final String TYPE_SERVICE_BASE_URL = "type.googleapis.com";
  private static final String TYPE_URL_PREFIX = TYPE_SERVICE_BASE_URL + "/";
  private static final String LEGACY_STREAM_OPTION_NAME1 =
      METHOD_OPTION_NAME_PREFIX + "stream_type";
  private static final String LEGACY_STREAM_OPTION_NAME2 =
      METHOD_OPTION_NAME_PREFIX + "legacy_steam_type";
  private static final Pattern EXTENSION_PATTERN = Pattern.compile("^\\((?:\\w+\\.)+(\\w+)\\)$");

  private final Map<String, FileContents> contentsByFile = Maps.newLinkedHashMap();
  private final Map<String, String> typeLocations = Maps.newLinkedHashMap();
  private final Map<String, Set<String>> imports = Maps.newLinkedHashMap();

  /** Generates a FileDescriptorSet for the specified normalized service config. */
  public static FileDescriptorSet generate(Service normalizedService) {
    DescriptorGenerator generator = new DescriptorGenerator();
    generator.analyzeService(normalizedService);
    return generator.generate();
  }

  private void analyzeService(Service normalizedService) {
    // Index protobuf elements by file location
    for (Type type : normalizedService.getTypesList()) {
      getFileContents(type.getSourceContext()).types.put(type.getName(), type);
      typeLocations.put(getTypeUrl(type.getName()), type.getSourceContext().getFileName());
    }
    for (Api api : normalizedService.getApisList()) {
      getFileContents(api.getSourceContext()).apis.add(api);
    }
    for (Enum e : normalizedService.getEnumsList()) {
      getFileContents(e.getSourceContext()).enums.add(e);
      typeLocations.put(getTypeUrl(e.getName()), e.getSourceContext().getFileName());
    }

    // Calculate required imports
    for (Type type : normalizedService.getTypesList()) {
      for (Field field : type.getFieldsList()) {
        if (field.getKind().equals(Kind.TYPE_ENUM) || field.getKind().equals(Kind.TYPE_MESSAGE)) {
          addReference(type.getSourceContext().getFileName(), field.getTypeUrl());
        }
      }
    }
    for (Api api : normalizedService.getApisList()) {
      for (Method method : api.getMethodsList()) {
        addReference(api.getSourceContext().getFileName(), method.getRequestTypeUrl());
        addReference(api.getSourceContext().getFileName(), method.getResponseTypeUrl());
      }
    }

    // Calculate package name for each file.
    for (FileContents file : contentsByFile.values()) {
      for (Api api : file.apis) {
        file.updatePackageName(api.getName());
      }
      for (Type type : file.types.values()) {
        file.updatePackageName(type.getName());
      }
      for (Enum e : file.enums) {
        file.updatePackageName(e.getName());
      }
    }

    // Resolve nested types and enums
    for (FileContents file : contentsByFile.values()) {
      file.resolveNestedTypes();
    }
  }

  private void addReference(String fromFile, String toTypeUrl) {
    if (!imports.containsKey(fromFile)) {
      imports.put(fromFile, Sets.<String>newLinkedHashSet());
    }
    String toFile = typeLocations.get(toTypeUrl);
    if (!fromFile.equals(toFile)) {
      imports.get(fromFile).add(toFile);
    }
  }

  private FileDescriptorSet generate() {
    FileDescriptorSet.Builder setBuilder = FileDescriptorSet.newBuilder();
    for (Map.Entry<String, FileContents> entry : contentsByFile.entrySet()) {
      FileContents contents = entry.getValue();
      String fileName = entry.getKey();
      if (!contents.apis.isEmpty() || !contents.types.isEmpty() || !contents.enums.isEmpty()) {
        setBuilder.addFile(generateFile(fileName, contents));
      }
    }
    return setBuilder.build();
  }

  private FileDescriptorProto generateFile(String name, FileContents contents) {
    FileDescriptorProto.Builder fileBuilder = FileDescriptorProto.newBuilder();
    fileBuilder.setName(name);
    if (!Strings.isNullOrEmpty(contents.packageName)) {
      fileBuilder.setPackage(contents.packageName);
    }
    for (Api api : contents.apis) {
      fileBuilder.addService(generateApi(api));
    }
    for (Type type : contents.types.values()) {
      fileBuilder.addMessageType(generateType(type, contents));
    }
    for (Enum e : contents.enums) {
      fileBuilder.addEnumType(generateEnum(e));
    }
    if (imports.containsKey(name)) {
      for (String imported : imports.get(name)) {
        fileBuilder.addDependency(imported);
      }
    }
    return fileBuilder.build();
  }

  private ServiceDescriptorProto generateApi(Api api) {
    ServiceDescriptorProto.Builder builder = ServiceDescriptorProto.newBuilder();
    builder.setName(getSimpleName(api.getName()));
    for (Method method : api.getMethodsList()) {
      builder.addMethod(generateMethod(method));
    }
    return builder.build();
  }

  private MethodDescriptorProto generateMethod(Method method) {
    MethodDescriptorProto.Builder builder = MethodDescriptorProto.newBuilder();
    builder.setName(method.getName());
    builder.setInputType(getTypeName(method.getRequestTypeUrl()));
    builder.setOutputType(getTypeName(method.getResponseTypeUrl()));
    builder.setOptions(generateMethodOptions(method));
    builder.setClientStreaming(method.getRequestStreaming());
    // protoc set serverStreaming field as false for legacy streaming options,
    // but google.protobuf.Method set the responseStreaming field to true for both new and legacy
    // streaming setup. So we need to distinguish streaming style while generating
    // MethodDescriptorProto.
    // But we cannot distinguish if the new and old styles are both set which should be rare case.
    if (method.getResponseStreaming() && isLegacyStreaming(method)) {
      builder.setServerStreaming(false);
    } else {
      builder.setServerStreaming(method.getResponseStreaming());
    }
    return builder.build();
  }

  private boolean isLegacyStreaming(Method method) {
    for (Option option : method.getOptionsList()) {
      if (option.getName().equals(LEGACY_STREAM_OPTION_NAME1)
          || option.getName().equals(LEGACY_STREAM_OPTION_NAME2)) {
        return true;
      }
    }
    return false;
  }

  private MethodOptions generateMethodOptions(Method method) {
    MethodOptions.Builder builder = MethodOptions.newBuilder();
    setOptions(builder, method.getOptionsList(), METHOD_OPTION_NAME_PREFIX);
    return builder.build();
  }

  private DescriptorProto generateType(Type type, FileContents file) {
    DescriptorProto.Builder builder = DescriptorProto.newBuilder();
    builder.setName(getSimpleName(type.getName()));
    for (Field field : type.getFieldsList()) {
      builder.addField(generateField(field));
    }
    for (String oneof : type.getOneofsList()) {
      builder.addOneofDeclBuilder().setName(oneof);
    }

    List<Type> nestedTypes = file.nestedTypes.get(type);
    if (nestedTypes != null) {
      for (Type child : nestedTypes) {
        builder.addNestedType(generateType(child, file));
      }
    }

    List<Enum> nestedEnums = file.nestedEnums.get(type);
    if (nestedEnums != null) {
      for (Enum child : nestedEnums) {
        builder.addEnumType(generateEnum(child));
      }
    }

    builder.setOptions(generateMessageOptions(type));

    return builder.build();
  }

  private MessageOptions generateMessageOptions(Type type) {
    MessageOptions.Builder builder = MessageOptions.newBuilder();
    setOptions(builder, type.getOptionsList(), TYPE_OPTION_NAME_PREFIX);
    return builder.build();
  }

  private FieldDescriptorProto generateField(Field field) {
    FieldDescriptorProto.Builder builder = FieldDescriptorProto.newBuilder();
    builder.setName(getFieldName(field));
    builder.setNumber(field.getNumber());
    builder.setLabel(toLabel(field.getCardinality()));
    builder.setType(toType(field.getKind()));
    if (field.getKind() == Kind.TYPE_ENUM
        || field.getKind() == Kind.TYPE_MESSAGE
        || field.getKind() == Kind.TYPE_GROUP) {
      builder.setTypeName(getTypeName(field.getTypeUrl()));
    }
    // NOTE: extendee not supported
    // NOTE: default_value not supported
    if (field.getOneofIndex() != 0) {
      // Index in the containing type's oneof_decl is zero-based.
      // Index in google.protobuf.type.Field.oneof_index is one-based.
      builder.setOneofIndex(field.getOneofIndex() - 1);
    }
    if (!Strings.isNullOrEmpty(field.getDefaultValue())) {
      builder.setDefaultValue(field.getDefaultValue());
    }
    FieldOptions options = getFieldOptions(field);
    if (!options.equals(FieldOptions.getDefaultInstance())) {
      builder.setOptions(options);
    }
    return builder.build();
  }

  /** Compute the simple name of a field, including extensions. */
  private String getFieldName(Field field) {
    Matcher m = EXTENSION_PATTERN.matcher(field.getName());
    if (m.matches()) {
      return m.group(1);
    } else {
      return field.getName();
    }
  }

  private FieldOptions getFieldOptions(Field field) {
    FieldOptions.Builder builder = FieldOptions.newBuilder();
    if (field.getPacked()) {
      builder.setPacked(true);
    }
    setOptions(builder, field.getOptionsList(), FIELD_OPTION_NAME_PREFIX);
    return builder.build();
  }

  private EnumDescriptorProto generateEnum(Enum e) {
    EnumDescriptorProto.Builder builder = EnumDescriptorProto.newBuilder();
    builder.setName(getSimpleName(e.getName()));
    for (EnumValue value : e.getEnumvalueList()) {
      EnumValueDescriptorProto.Builder valueBuilder = EnumValueDescriptorProto.newBuilder();
      valueBuilder.setName(value.getName());
      valueBuilder.setNumber(value.getNumber());
      valueBuilder.setOptions(generateEnumValueOptions(value));
      builder.addValue(valueBuilder.build());
    }
    builder.setOptions(generateEnumOptions(e));
    return builder.build();
  }

  private EnumValueOptions generateEnumValueOptions(EnumValue value) {
    EnumValueOptions.Builder builder = EnumValueOptions.newBuilder();
    setOptions(builder, value.getOptionsList(), ENUM_VALUE_OPTION_NAME_PREFIX);
    return builder.build();
  }

  private EnumOptions generateEnumOptions(Enum e) {
    EnumOptions.Builder builder = EnumOptions.newBuilder();
    setOptions(builder, e.getOptionsList(), ENUM_OPTION_NAME_PREFIX);
    return builder.build();
  }

  private void setOptions(Message.Builder builder, List<Option> optionsList, String optionPrefix) {
    for (Option option : optionsList) {
      setOption(builder, option, optionPrefix);
    }
  }

  private void setOption(Message.Builder builder, Option option, String expectedPrefix) {
    if (!option.getName().startsWith(expectedPrefix)) {
      return;
    }
    Descriptor descriptor = builder.getDescriptorForType();
    String optionName = option.getName().substring(expectedPrefix.length());
    FieldDescriptor optionField = descriptor.findFieldByName(optionName);
    if (optionField != null) {
      if (optionField.isRepeated()) {
        builder.addRepeatedField(optionField, fieldValueFrom(option.getValue(), optionField));
      } else {
        builder.setField(optionField, fieldValueFrom(option.getValue(), optionField));
      }
    }
  }

  private Object fieldValueFrom(Any value, FieldDescriptor field) {

    switch (field.getType()) {
      case STRING:
        return parseAny(value, com.google.protobuf.StringValue.parser()).getValue();
      case ENUM:
        String valueName = parseAny(value, com.google.protobuf.StringValue.parser()).getValue();
        return field.getEnumType().findValueByName(valueName);
      case BOOL:
        return parseAny(value, com.google.protobuf.BoolValue.parser()).getValue();
      case DOUBLE:
        return parseAny(value, com.google.protobuf.DoubleValue.parser()).getValue();
      case FLOAT:
        return parseAny(value, com.google.protobuf.FloatValue.parser()).getValue();
      case SINT32:
      case SFIXED32:
      case INT32:
        return parseAny(value, com.google.protobuf.Int32Value.parser()).getValue();
      case SINT64:
      case SFIXED64:
      case INT64:
        return parseAny(value, com.google.protobuf.Int64Value.parser()).getValue();
      case UINT32:
      case FIXED32:
        return parseAny(value, com.google.protobuf.UInt32Value.parser()).getValue();
      case UINT64:
      case FIXED64:
        return parseAny(value, com.google.protobuf.UInt64Value.parser()).getValue();
      case BYTES:
        return parseAny(value, com.google.protobuf.BytesValue.parser()).getValue();
      case MESSAGE:
        // We don't currently allow message types to be used for options.
        // TODO(user): Revisit this once the New World Proto extension discussion has been finalized.
      default:
        throw new IllegalArgumentException("unhandled option type: " + field.getType());
    }
  }

  private <T> T parseAny(Any value, Parser<T> parser) {
    try {
      return parser.parseFrom(value.getValue());
    } catch (InvalidProtocolBufferException e) {
      throw new RuntimeException(e);
    }
  }

  private FieldDescriptorProto.Type toType(Kind kind) {
    return FieldDescriptorProto.Type.valueOf(kind.getNumber());
  }

  private Label toLabel(Cardinality cardinality) {
    return Label.valueOf(cardinality.getNumber());
  }

  private static String getSimpleName(String typeName) {
    return typeName.substring(typeName.lastIndexOf('.') + 1);
  }

  private static String getTypeUrl(String typeName) {
    return TYPE_URL_PREFIX + typeName;
  }

  /**
   * Returns the parent type name of a nested type. Returns null if the specified type is not a
   * nested type.
   */
  private static String getParentName(String packageName, String typeName) {
    int dotIndex = typeName.lastIndexOf('.');
    String parentName = dotIndex != -1 ? typeName.substring(0, dotIndex) : "";
    return parentName.equals(packageName) ? null : parentName;
  }

  private static String getTypeName(String typeUrl) {
    Preconditions.checkArgument(
        typeUrl.startsWith(TYPE_URL_PREFIX),
        "type url (%s) did not start with expected prefix %s)",
        typeUrl,
        TYPE_URL_PREFIX);
    return "." + typeUrl.substring(TYPE_URL_PREFIX.length());
  }

  private FileContents getFileContents(SourceContext sourceContext) {
    String fileName = sourceContext.getFileName();
    if (!contentsByFile.containsKey(fileName)) {
      contentsByFile.put(fileName, new FileContents());
    }
    return contentsByFile.get(fileName);
  }

  private static class FileContents {
    String packageName; // Note that an empty string indicates that there is no package.
    List<Api> apis = Lists.newArrayList();
    List<Enum> enums = Lists.newArrayList();
    Map<String, Type> types = Maps.newLinkedHashMap();
    Map<Type, List<Type>> nestedTypes = Maps.newLinkedHashMap();
    Map<Type, List<Enum>> nestedEnums = Maps.newLinkedHashMap();

    // TODO(user): Change the format for nested type URLs to make this analysis unnecessary.
    public void updatePackageName(String elementName) {
      int dotIndex = elementName.lastIndexOf('.');
      String potentialPackageName = dotIndex != -1 ? elementName.substring(0, dotIndex) : "";
      if (packageName == null) {
        packageName = potentialPackageName;
      } else if (packageName.equals(potentialPackageName)) {
        // Same name; all is well
      } else if (packageName.startsWith(potentialPackageName)) {
        // The previous provisional package name must have been an element with a nested type
        packageName = potentialPackageName;
      } else if (potentialPackageName.startsWith(packageName)) {
        // potentialPackageName must be an element with a nested type
      } else {
        throw new IllegalStateException(
            "potential package names don't agree: " + packageName + ", " + potentialPackageName);
      }
    }

    public void resolveNestedTypes() {
      // Resolve nested types
      for (Type type : types.values()) {
        String parentName = getParentName(packageName, type.getName());
        if (parentName != null) {
          getOrCreateList(nestedTypes, types.get(parentName)).add(type);
        }
      }

      // Resolve nested enums
      for (Enum e : enums) {
        String parentName = getParentName(packageName, e.getName());
        if (parentName != null) {
          getOrCreateList(nestedEnums, types.get(parentName)).add(e);
        }
      }

      // Remove nested types from top level
      for (List<Type> children : nestedTypes.values()) {
        for (Type child : children) {
          types.remove(child.getName());
        }
      }

      // Remove nested enums from top level
      for (List<Enum> children : nestedEnums.values()) {
        for (Enum child : children) {
          enums.remove(child);
        }
      }
    }

    private <V> List<V> getOrCreateList(Map<Type, List<V>> lists, Type key) {
      if (!lists.containsKey(key)) {
        lists.put(key, Lists.<V>newArrayList());
      }
      return lists.get(key);
    }
  }
}
