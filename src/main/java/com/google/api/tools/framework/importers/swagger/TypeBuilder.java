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

package com.google.api.tools.framework.importers.swagger;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.protobuf.Any;
import com.google.protobuf.BoolValue;
import com.google.protobuf.Field;
import com.google.protobuf.Field.Cardinality;
import com.google.protobuf.Field.Kind;
import com.google.protobuf.Message;
import com.google.protobuf.Option;
import com.google.protobuf.Syntax;
import com.google.protobuf.Type;

import io.swagger.models.ArrayModel;
import io.swagger.models.ComposedModel;
import io.swagger.models.Model;
import io.swagger.models.ModelImpl;
import io.swagger.models.RefModel;
import io.swagger.models.Swagger;
import io.swagger.models.parameters.BodyParameter;
import io.swagger.models.parameters.FormParameter;
import io.swagger.models.parameters.HeaderParameter;
import io.swagger.models.parameters.Parameter;
import io.swagger.models.parameters.PathParameter;
import io.swagger.models.parameters.QueryParameter;
import io.swagger.models.properties.ArrayProperty;
import io.swagger.models.properties.MapProperty;
import io.swagger.models.properties.Property;
import io.swagger.models.properties.RefProperty;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Creates service config {@link Type} from swagger models and operation parameters.
 * This class also provides type resolution from a given type reference.
 */
class TypeBuilder {
  private final Set<String> createdTypesFullName = Sets.newHashSet();
  private final List<Type> types = Lists.newArrayList();
  private final Map<String, TypeInfo> processedTypeNameToTypeInfo = Maps.newHashMap();
  private final Swagger swagger;
  private final String namespace;
  private final String namespacePrefix;  // Pre-computed namespace with a trailing dot.

  TypeBuilder(Swagger swagger, String namespace) {
    this.swagger = swagger;
    this.namespace = namespace;
    this.namespacePrefix =
        (namespace.isEmpty() || namespace.endsWith(".")) ? namespace : namespace + ".";
  }

  /**
   * Returns the generated types.
   */
  Iterable<Type> getTypes() {
    return types;
  }

  /**
   * Create the {@link Type} instance from model.
   *
   * <p>NOTE: If the property of the model references another model, then we recursively create the
   * {@link Type} corresponding to the reference model.</p>
   *
   * <p>NOTE: {@link Type} is created only if the model is a ModelImpl (corresponds to JSON Object)
   * and does not contain additional properties. For other cases we cannot create a Protobuf.Type
   * instance (Example: ModelImpl with additional properties, array model etc).
   * For such cases just return the suitable predefined google.protobuf* types.</p>
   *
   */
  TypeInfo addTypeFromModel(String typeName, Model model) {
    String modelRefId = "#/definitions/" + typeName;
    TypeInfo resultTypeInfo = null;

    if (processedTypeNameToTypeInfo.containsKey(modelRefId)) {
      return processedTypeNameToTypeInfo.get(modelRefId);
    }
    if (model instanceof ComposedModel) {
      // TODO(user): Expand this composed Model and create a Type from it.
      resultTypeInfo = WellKnownTypeUtils.getTypeInfo("value");
    }
    if (model instanceof ArrayModel) {
      resultTypeInfo = getArrayModelTypeInfo(((ArrayModel) model).getItems());
    }
    if (model instanceof RefModel) {
      resultTypeInfo = getRefModelTypeInfo((RefModel) model);
    }
    if (model instanceof ModelImpl) {
      ModelImpl modelImpl = (ModelImpl) model;
      if (isPrimitiveTypeWrapper(modelImpl)) {
        // Represent this as a wrapper type for the primitive type.
        resultTypeInfo = WellKnownTypeUtils.getTypeInfo(modelImpl.getType());
      } else if (hasAdditionalProperties(modelImpl) && hasProperties(modelImpl)) {
        // Since both properties and additional properties are present, we cannot create a
        // protobuf.Type object for it. Therefore, represent this as a Struct type.
        resultTypeInfo = WellKnownTypeUtils.getTypeInfo("struct");
      } else if (hasAdditionalProperties(modelImpl) && !hasProperties(modelImpl)) {
        // Since additional properties is present without properties, we can represent this as a
        // map.
        TypeInfo mapEntry = getMapEntryTypeInfo(
            ensureNamed(getTypeInfo(modelImpl.getAdditionalProperties()), "MapValue"));
        mapEntry = ensureNamed(mapEntry, "MapEntry");
        resultTypeInfo = mapEntry.withCardinality(Cardinality.CARDINALITY_REPEATED);
      } else if (!hasAdditionalProperties(modelImpl)) {
        // Since there is no additional properties, create a protobuf.Type.
        String protoTypeName = NameConverter.schemaNameToMessageName(typeName);
        String typeUrl = WellKnownTypeUtils.TYPE_SERVICE_BASE_URL + namespacePrefix + protoTypeName;

        // Add to the processed list before creating the type. This will prevent from cyclic
        // dependency
        Preconditions.checkState(!processedTypeNameToTypeInfo.containsKey(modelRefId));
        resultTypeInfo =
            TypeInfo.create(typeUrl, Kind.TYPE_MESSAGE, Cardinality.CARDINALITY_OPTIONAL);
        processedTypeNameToTypeInfo.put(modelRefId, resultTypeInfo);
        ImmutableList.Builder<Field> fieldsBuilder = createFields(modelImpl);
        resultTypeInfo = resultTypeInfo.withFields(fieldsBuilder.build()).withTypeUrl("");
        resultTypeInfo = ensureNamed(resultTypeInfo, protoTypeName);
      }
    }

    if (resultTypeInfo == null) {
      /* TODO(user): Make this an error once we want to start supporting json to proto
       * transformation for APIs imported from Swagger.*/
      resultTypeInfo = WellKnownTypeUtils.getTypeInfo("value");
    }

    processedTypeNameToTypeInfo.put(modelRefId, resultTypeInfo);
    return processedTypeNameToTypeInfo.get(modelRefId);
  }

  /**
   * Returns list of {@link com.google.protobuf.Field.Builder} created using the properties of a
   * {@link ModelImpl} object.
   */
  private ImmutableList.Builder<Field> createFields(ModelImpl modelImpl) {
    ImmutableList.Builder<Field> fieldsBuilder = ImmutableList.builder();
    int count = 1;
    if (modelImpl.getProperties() != null) {
      for (String propertyName : modelImpl.getProperties().keySet()) {
        Property prop = modelImpl.getProperties().get(propertyName);
        TypeInfo typeInfo =
            ensureNamed(getTypeInfo(prop), NameConverter.propertyNameToMessageName(propertyName));
        fieldsBuilder.add(createField(propertyName, count++, typeInfo).build());
      }
    }
    return fieldsBuilder;
  }

  /**
   * Returns true if the modelImpl has additional properties.
   */
  private boolean hasAdditionalProperties(ModelImpl modelImpl) {
    return modelImpl.getAdditionalProperties() != null;
  }

  /**
   * Returns true if the modelImpl has properties.
   */
  private boolean hasProperties(ModelImpl modelImpl) {
    return modelImpl.getProperties() != null;
  }

  /**
   * Returns the {@link TypeInfo} for the referenced model.
   */
  private TypeInfo getRefModelTypeInfo(RefModel refModel) {
    TypeInfo resultTypeInfo;
    Preconditions.checkState(refModel.get$ref().startsWith("#/definitions/"));
    String refModelName = refModel.get$ref().substring("#/definitions/".length());
    resultTypeInfo = Preconditions.checkNotNull(
        addTypeFromModel(refModelName, swagger.getDefinitions().get(refModelName)));
    return resultTypeInfo;
  }

  /**
   * Returns {@link TypeInfo} for the arrayItems.
   */
  private TypeInfo getArrayModelTypeInfo(Property arrayItems) {
    TypeInfo resultTypeInfo;
    TypeInfo arrayItemTypeInfo = ensureNamed(getTypeInfo(arrayItems), "ArrayEntry");
    // Check if this is a repeated of repeated. Since repeated of repeated is not allowed, we will
    // represent this as {@link com.google.protobuf.ListValue} type.
    if (arrayItemTypeInfo == null
        || arrayItemTypeInfo.cardinality() == Cardinality.CARDINALITY_REPEATED) {
      resultTypeInfo =
          WellKnownTypeUtils.getTypeInfo("list").withCardinality(Cardinality.CARDINALITY_REPEATED);
    } else {
      resultTypeInfo = arrayItemTypeInfo.withCardinality(Cardinality.CARDINALITY_REPEATED);
    }
    return resultTypeInfo;
  }

  /**
   * Creates the {@link Type} instance from list of parameters. These parameters become the fields
   * of the generated Type instance. Returns the TypeInfo of the generated type.
   */
  TypeInfo createTypeFromParameter(String typeName, List<Parameter> parameters) {
    typeName = getUniqueTypeName(typeName);

    ImmutableList.Builder<Field> fieldsBuilder = ImmutableList.builder();
    int count = 1;
    for (Parameter parameter : parameters) {
      TypeInfo typeInfo = ensureNamed(
          getTypeInfo(parameter, typeName),
          NameConverter.propertyNameToMessageName(parameter.getName()));
      fieldsBuilder.add(createField(parameter.getName(), count++, typeInfo).build());
    }

    return ensureNamed(
        TypeInfo.create(null, Kind.TYPE_MESSAGE, Cardinality.CARDINALITY_OPTIONAL,
            fieldsBuilder.build(), null, false),
        typeName);
  }

  /**
   * Returns the {@link TypeInfo} corresponding to the property.
   */
  TypeInfo getTypeInfo(Property prop) {
    if (prop == null) {
      // TODO(user): How do we handle such cases. May be schema validation at the beginning is
      // the solution.
      return null;
    }
    Property arrayItems = prop instanceof ArrayProperty ? ((ArrayProperty) prop).getItems() : null;
    return getTypeInfo(prop.getType(), prop.getFormat(), prop, arrayItems);
  }

  /**
   * Returns the {@link TypeInfo} corresponding to the parameter.
   */
  private TypeInfo getTypeInfo(Parameter param, String typeName) {
    switch (param.getIn()) {
      case "body":
        {
          BodyParameter parameter = (BodyParameter) param;
          return addTypeFromModel(typeName + "Body", parameter.getSchema());
        }
      case "path":
        {
          PathParameter parameter = (PathParameter) param;
          return getTypeInfo(
              parameter.getType(), parameter.getFormat(), null, parameter.getItems());
        }
      case "query":
        {
          QueryParameter parameter = (QueryParameter) param;
          return getTypeInfo(
              parameter.getType(), parameter.getFormat(), null, parameter.getItems());
        }
      case "header":
        {
          HeaderParameter parameter = (HeaderParameter) param;
          return getTypeInfo(
              parameter.getType(), parameter.getFormat(), null, parameter.getItems());
        }
      case "formData":
        {
          FormParameter parameter = (FormParameter) param;
          return getTypeInfo(
              parameter.getType(), parameter.getFormat(), null, parameter.getItems());
        }
      default:
        // TODO(user): (Schema validation should have caught it. This should not happen)
        return null;
    }
  }

  /**
   * Returns the {@link TypeInfo} corresponding to the type and format.
   *
   * <p>Note: If the type references another type, this method recursively creates that referenced
   * type.</p>
   *
   * <p>For primitive types:
   * <ul>
   * <li>typeUrl is empty.</li>
   * <li>kind is the Protobug.Field.Kind corresponding to 'type'and 'format'.</li>
   * <li>cardinality is OPTIONAL.</li>
   * </ul>
   * For one dimensional array types:
   * <ul>
   * <li>typeUrl is based on TypeInfo of the underlying array items.</li>
   * <li>kind is based on TypeInfo of the underlying array items.</li>
   * <li>cardinality is REPEATED.</li>
   * </ul>
   * For array of arrays:
   * <ul>
   * <li>typeUrl is hard coded to typeUrl of google.protobuf.Value type.</li>
   * <li>kind is TYPE_MESSAGE.</li>
   * <li>cardinality is REPEATED.</li>
   * </ul>
   * For ref types:
   * <ul>
   * <li>typeUrl is based on TypeInfo of the underlying referenced type.</li>
   * <li>kind is TYPE_MESSAGE.</li>
   * <li>cardinality is OPTIONAL.</li>
   * </ul></p>
   */
  private TypeInfo getTypeInfo(String type, String format, Property property, Property arrayItems) {
    if (WellKnownTypeUtils.isPrimitiveType(type)) {
      return TypeInfo.create(
          null, WellKnownTypeUtils.getKind(type, format), Cardinality.CARDINALITY_OPTIONAL);
    }
    switch (type) {
      case "ref":
        String referencePath = ((RefProperty) property).get$ref();
        Preconditions.checkState(referencePath.startsWith("#/definitions/"));
        String refPropertyName = referencePath.substring("#/definitions/".length());
        Model model = swagger.getDefinitions().get(refPropertyName);
        return addTypeFromModel(refPropertyName, model);
      case "array":
        return getArrayModelTypeInfo(arrayItems);
      case "file":
        /* TODO(user): Make this an error once we want to start supporting json to proto
         * transformation for APIs imported from Swagger.*/
        return WellKnownTypeUtils.getTypeInfo("value");
      case "object":
        if (property instanceof MapProperty) {
          // Represent this as a Map.
          MapProperty mapProperty = (MapProperty) property;
          Property valueProperty = mapProperty.getAdditionalProperties();
          TypeInfo mapEntry =
              getMapEntryTypeInfo(ensureNamed(getTypeInfo(valueProperty), "MapValue"));
          mapEntry = ensureNamed(mapEntry, "MapEntry");
          return mapEntry.withCardinality(Cardinality.CARDINALITY_REPEATED);
        } else {
          // TODO(user): If this object property contains properties without
          // additionalProperties, we should create a Protobuf.Type object. But, Swagger Object
          // model does not contain the information we need to create that type.
          // Fix the Swagger Object Model to have property information associated with this
          // Object Property.
          return WellKnownTypeUtils.getTypeInfo("struct");
        }
      case "any":
        return WellKnownTypeUtils.getTypeInfo("value");
      default:
        return TypeInfo.create(null, Kind.UNRECOGNIZED, Cardinality.CARDINALITY_OPTIONAL);
    }
  }

  /**
   * Creates a {@link TypeInfo} for a MapEntry type.
   */
  private TypeInfo getMapEntryTypeInfo(TypeInfo valueTypeInfo) {
    TypeInfo keyTypeInfo =
        TypeInfo.create(null, Kind.TYPE_STRING, Cardinality.CARDINALITY_OPTIONAL);

    TypeInfo entryTypeInfo =
        TypeInfo.create(null, Kind.TYPE_MESSAGE, Cardinality.CARDINALITY_OPTIONAL,
            ImmutableList.of(
                createField("key", 1, keyTypeInfo).build(),
                createField("value", 2, valueTypeInfo).build()),
            null, true);

    return entryTypeInfo;
  }

  /**
   * Ensures that a {@link Type} exists for typeInfo with {@link com.google.protobuf.Field.Kind} as
   * MESSAGE_TYPE.
   */
  TypeInfo ensureNamed(TypeInfo typeInfo, String nameSuggestion) {
    if (typeInfo.kind() != Kind.TYPE_MESSAGE || !Strings.isNullOrEmpty(typeInfo.typeUrl())) {
      return typeInfo;
    }

    String typeFullName = namespacePrefix + getUniqueTypeName(nameSuggestion);
    String typeUrl = WellKnownTypeUtils.TYPE_SERVICE_BASE_URL + typeFullName;
    Iterable<Option> options = null;
    if (typeInfo.isMapEntry()) {
      Option.Builder optionBuilder = Option.newBuilder();

      Any.Builder anyBuilder = Any.newBuilder();
      String boolValueTypeUrl = BoolValue.getDescriptor().getFullName();
      Message wrapperMessage = BoolValue.newBuilder().setValue(true).build();
      anyBuilder.setTypeUrl(WellKnownTypeUtils.TYPE_SERVICE_BASE_URL + "/" + boolValueTypeUrl)
          .setValue(wrapperMessage.toByteString())
          .build();

      optionBuilder.setName("proto2.MessageOptions.map_entry");
      optionBuilder.setValue(anyBuilder.build());
      options = ImmutableList.of(optionBuilder.build());
    }
    addTypeFromFields(typeFullName, typeInfo.fields(), options);
    return typeInfo.withTypeUrl(typeUrl);
  }

  /**
   * Returns a unique name based on typeName such that it does not collide with already created
   * types.
   */
  private String getUniqueTypeName(String typeName) {
    int reqTypeCount = 1;
    do {
      if (createdTypesFullName.contains(namespacePrefix + typeName)) {
        typeName = typeName + reqTypeCount++;
      } else {
        break;
      }
    } while (true);
    return typeName;
  }

  /**
   * Create the {@link Type} with given fields.
   */
  private void addTypeFromFields(
      String typeFullName, Iterable<Field> fields, Iterable<Option> options) {
    Type.Builder coreTypeBuilder = Type.newBuilder().setName(typeFullName);
    coreTypeBuilder.getSourceContextBuilder().setFileName(namespace);
    coreTypeBuilder.addAllFields(fields);
    coreTypeBuilder.setSyntax(Syntax.SYNTAX_PROTO3);
    if (options != null) {
      coreTypeBuilder.addAllOptions(options);
    }
    createdTypesFullName.add(coreTypeBuilder.getName());
    types.add(coreTypeBuilder.build());
  }

  /**
   * Creates and returns a {@link com.google.protobuf.Field.Builder}.
   */
  private Field.Builder createField(String fieldName, int fieldCount, TypeInfo fieldTypeInfo) {
    Cardinality cardinality = fieldTypeInfo.cardinality();
    if (cardinality == null || cardinality == Cardinality.CARDINALITY_UNKNOWN) {
      cardinality = Cardinality.CARDINALITY_OPTIONAL;
    }
    // TODO(user): If the name already has underscore, need to handle it.
    // Currently this code is expecting the fieldName is proper JSON style (lowerCamelCase)
    Field.Builder coreFieldBuilder =
        Field.newBuilder()
            .setName(NameConverter.getFieldName(fieldName))
            .setNumber(fieldCount)
            .setKind(fieldTypeInfo.kind())
            .setCardinality(cardinality)
            .setJsonName(fieldName);
    if (fieldTypeInfo.kind() == Kind.TYPE_MESSAGE) {
      coreFieldBuilder.setTypeUrl(fieldTypeInfo.typeUrl());
    }
    return coreFieldBuilder;
  }

  /**
   * Returns true if the modelImpl is a wrapper of primitive type like string, Float etc; false
   * otherwise
   */
  private boolean isPrimitiveTypeWrapper(ModelImpl modelImpl) {
    return (modelImpl.getProperties() == null && modelImpl.getAdditionalProperties() == null)
        && !Strings.isNullOrEmpty(modelImpl.getType())
        && !"object".equalsIgnoreCase(modelImpl.getType())
        && WellKnownTypeUtils.isPrimitiveType(modelImpl.getType());
  }
}
