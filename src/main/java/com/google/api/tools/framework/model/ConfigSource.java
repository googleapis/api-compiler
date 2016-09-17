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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.MapEntry;
import com.google.protobuf.Message;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * Represents a configuration source. Maintains information about the configuration message, the
 * fields which have been explicitly set in the configuration, and source locations. Also supports
 * merging of configurations preserving above information, and allowing to override default values
 * (which standard proto3 merging semantics does not support).
 */
public class ConfigSource {

  // Contains the config message.
  private final Message configMessage;

  // Contains a map from message keys into a map of location keys with associated location.
  //
  // A message key is a wrapper around a message which uses the messages's object identity
  // for equality. This way, we can work around that by default, messages have value equality,
  // and effectively can attach attributes to the tree of messages represented by configMessage.
  // The attribute in this case is the map from location key into source location, for a given
  // message. A location key consists of a field descriptor, and an optional key for a map or
  // index into a list.
  //
  // The location map is leveraged for merging. Presence of a location key indicates that an update
  // has been performed. This is used to support proto2 semantics for merging.
  private final ImmutableMap<MessageKey, ImmutableMap<LocationKey, Location>> locations;

  private ConfigSource(
      Message value, ImmutableMap<MessageKey, ImmutableMap<LocationKey, Location>> locations) {
    this.configMessage = value;
    this.locations = locations;
  }

  /** Returns the config message. */
  public Message getConfig() {
    return configMessage;
  }

  /**
   * Returns the location of the given named field in the (sub)message, with optional element key
   * for maps or repeated fields. For repeated fields, the element key is a zero-based index.
   * Returns {@link SimpleLocation#UNKNOWN} if the location is not known.
   */
  public Location getLocation(Message message, String fieldName, @Nullable Object elementKey) {
    MessageKey messageKey = new MessageKey(message);
    Map<LocationKey, Location> map = locations.get(messageKey);
    if (map != null) {
      FieldDescriptor field = message.getDescriptorForType().findFieldByName(fieldName);
      if (field != null) {
        Location result = map.get(new LocationKey(field, elementKey));
        if (result != null) {
          return result;
        }
      }
    }
    return SimpleLocation.UNKNOWN;
  }

  /** Constructs a builder from this configuration message. */
  public Builder toBuilder() {
    return new Builder(configMessage, configMessage.toBuilder(), new LinkedHashMap<>(locations));
  }

  /**
   * Constructs a new empty builder, based on the given default instance for the underlying config
   * message.
   *
   * <p>An initialized message can also be passed, however, no source location tracking will happen
   * for it.
   */
  public static Builder newBuilder(Message defaultInstance) {
    return new Builder(
        defaultInstance,
        defaultInstance.toBuilder(),
        new LinkedHashMap<MessageKey, ImmutableMap<LocationKey, Location>>());
  }

  /**
   * An interface which represents a build action performed on a builder for a sub-configuration.
   */
  // In Java 8 this could be represented by the Consumer interface, but we cannot depend
  // on this.
  public interface BuildAction {
    public void accept(Builder builder);
  }

  /** Represents a builder for a configuration message. */
  public static class Builder {
    // The message from which we build. For a fresh builder, this is the default instance.
    private final Message configMessage;

    // The builder for the message.
    private final Message.Builder configBuilder;

    // The locations map for the entire built tree. This is shared with parents, and updates
    // to here are global.
    private final Map<MessageKey, ImmutableMap<LocationKey, Location>> locations;

    // New locations added to this builder.
    private final Map<LocationKey, Location> newLocations = new LinkedHashMap<>();

    // Whether build() was called.
    private boolean built;

    private Builder(
        Message message,
        Message.Builder messageBuilder,
        Map<MessageKey, ImmutableMap<LocationKey, Location>> locations) {
      this.configMessage = message;
      this.configBuilder = messageBuilder;
      this.locations = locations;
    }

    /** Return the descriptor for the message being built. */
    public Descriptor getDescriptorForType() {
      return configBuilder.getDescriptorForType();
    }

    public ConfigSource build() {
      Preconditions.checkState(!built, "Called build twice on config source");
      built = true;

      // Build value.
      Message newMessage = configBuilder.build();

      // Propagate locations from the old version of the message in the new one which is built.
      Map<LocationKey, Location> oldLocations = locations.remove(new MessageKey(configMessage));
      if (oldLocations != null) {
        for (LocationKey key : oldLocations.keySet()) {
          if (!newLocations.containsKey(key)) {
            newLocations.put(key, oldLocations.get(key));
          }
        }
      }

      // Update locations for new message.
      if (!newLocations.isEmpty()) {
        locations.put(new MessageKey(newMessage), ImmutableMap.copyOf(newLocations));
      }
      return new ConfigSource(newMessage, ImmutableMap.copyOf(locations));
    }

    /**
     * Sets the given scalar value on the field. If optional key is provided, the field must
     * represent a map, and the value under the key is set.
     */
    public Builder setValue(
        FieldDescriptor field, @Nullable Object key, Object value, @Nullable Location location) {
      if (key == null) {
        configBuilder.setField(field, value);
      } else {
        putMapEntry(configBuilder, field, key, value);
      }
      addLocation(field, key, location);
      return this;
    }

    public void addLocation(FieldDescriptor field, Object key, Location location) {
      newLocations.put(new LocationKey(field, key), nonNull(location));
    }

    /** Adds the scalar value to the field which must be repeated. */
    public Builder addValue(FieldDescriptor field, Object value, @Nullable Location location) {
      int index = configBuilder.getRepeatedFieldCount(field);
      configBuilder.addRepeatedField(field, value);
      newLocations.put(new LocationKey(field, index), nonNull(location));
      return this;
    }

    /**
     * Constructs a sub-builder for given field and calls the action on it. After the action's
     * processing, the sub-message will be build and stored into the field of this builder.
     * Moreover, update locations of the sub-builder are integrated into this builder.
     */
    public Builder withBuilder(FieldDescriptor field, BuildAction action) {
      // Construct a fresh builder for the given field and merge in the current value. As we depend
      // on message identity, we need to control this builder directly, so can't use implicit
      // building via getFieldBuilder. A builder created by getFieldBuilder as build() called
      // when the parent is called, resulting in a different message identity.
      Message currentValue = (Message) configBuilder.getField(field);
      Message.Builder protoBuilder = configBuilder.newBuilderForField(field);
      protoBuilder.mergeFrom(currentValue);

      // Construct config builder, and let the action process it.
      Builder fieldConfigBuilder = new Builder(currentValue, protoBuilder, locations);
      action.accept(fieldConfigBuilder);

      // Build config, which updates the location mapping, and update proto builder.
      ConfigSource fieldConfig = fieldConfigBuilder.build();
      configBuilder.setField(field, fieldConfig.getConfig());
      return this;
    }

    /**
     * Constructs a sub-builder for given field and calls the action on it. If optional key is
     * provided, the field must represent a map of messages, and the builder under the key is used.
     */
    public Builder withBuilder(FieldDescriptor field, @Nullable Object key, BuildAction action) {
      // If there is no key, behave like the similar method without key.
      if (key == null) {
        return withBuilder(field, action);
      }

      // The reflection API for maps is rather incomplete, so we need to hack.
      // First get the map for the underlying field, and determine the field
      // for the map entry's value.
      Map<Object, Object> protoMap = getMapFromProtoMapBuilder(configBuilder, field);
      FieldDescriptor valueField = field.getMessageType().getFields().get(1);

      // Next construct a builder for the value field. We need to get MapEntry
      // builder first from parent builder, and then use the map entry builder to get the value
      // field builder.
      Message.Builder valueBuilder =
          configBuilder.newBuilderForField(field).newBuilderForField(valueField);

      // Now merge in the current value from the proto map.
      Message currentValue;
      if (protoMap.containsKey(key)) {
        currentValue = (Message) protoMap.get(key);
        valueBuilder.mergeFrom(currentValue);
      } else {
        currentValue = valueBuilder.build();
      }

      // Finally construct our config message builder for the map value.
      Builder fieldBuilder = new Builder(currentValue, valueBuilder, locations);
      action.accept(fieldBuilder);

      // Call build so locations map is updated.
      ConfigSource configMessage = fieldBuilder.build();

      // Update the proto map which will update the underlying builder.
      setValue(field, key, configMessage.configMessage, null);
      return this;
    }

    /**
     * Constructs a sub-builder for an added element of the repeated message field, and calls action
     * on it.
     */
    public Builder withAddedBuilder(FieldDescriptor field, BuildAction action) {
      Message.Builder repeatedFieldBuilder = configBuilder.newBuilderForField(field);
      Builder elementBuilder =
          new Builder(repeatedFieldBuilder.build(), repeatedFieldBuilder, locations);
      action.accept(elementBuilder);

      // Call build so locations map is updated.
      ConfigSource configSource = elementBuilder.build();

      // Update the list with the built element
      configBuilder.addRepeatedField(field, configSource.configMessage);

      return this;
    }

    /**
     * Constructs a sub-builder for a element at given index of the repeated message field, and
     * calls action on it.
     */
    public Builder withBuilderAt(FieldDescriptor field, int index, BuildAction action) {
      Message.Builder repeatedFieldBuilder = configBuilder.newBuilderForField(field);
      repeatedFieldBuilder.mergeFrom((Message) configBuilder.getRepeatedField(field, index));
      Builder elementBuilder =
          new Builder(repeatedFieldBuilder.build(), repeatedFieldBuilder, locations);
      action.accept(elementBuilder);

      // Call build so locations map is updated.
      ConfigSource configSource = elementBuilder.build();

      // Update the list with the built element
      configBuilder.setRepeatedField(field, index, configSource.configMessage);

      return this;
    }

    /**
     * Merges values from the given config message into this builder. In contrast to proto3 standard
     * merging, this overrides default values.
     */
    public Builder mergeFrom(ConfigSource config) {
      // First merge using standard algorithm.
      configBuilder.mergeFrom(config.configMessage);

      // Next merge locations.
      mergeLocations(config.configMessage, config, false);

      return this;
    }

    /**
     * Merges values from the given config source into this builder. This confirms to proto3
     * semantics, i.e. fields with default value will not override.
     */
    public Builder mergeFromWithProto3Semantics(ConfigSource config) {
      // First merge using standard algorithm.
      configBuilder.mergeFrom(config.configMessage);

      // Next merge locations.
      mergeLocations(config.configMessage, config, true);

      return this;
    }

    @SuppressWarnings("unchecked")
    private void mergeLocations(
        Message messageToMergeForm, final ConfigSource configToMergeForm, boolean proto3) {

      // Propagate locations.This also takes care of primitive fields with default value.
      Map<LocationKey, Location> locationMapToMerge =
          configToMergeForm.locations.get(new MessageKey(messageToMergeForm));
      if (locationMapToMerge != null) {
        for (Map.Entry<LocationKey, Location> entryToMerge : locationMapToMerge.entrySet()) {
          // Copy over location. For repeated fields, adjust index as they have been appended
          // at the end.
          LocationKey keyToMerge = entryToMerge.getKey();
          FieldDescriptor fieldInLocationsToMerge = keyToMerge.field;
          if (fieldInLocationsToMerge.isRepeated() && !fieldInLocationsToMerge.isMapField()) {
            if (keyToMerge.elementKey != null) {
              int sizeBeforeMerge =
                  configBuilder.getRepeatedFieldCount(fieldInLocationsToMerge)
                      - messageToMergeForm.getRepeatedFieldCount(fieldInLocationsToMerge);
              keyToMerge =
                  new LocationKey(keyToMerge.field, sizeBeforeMerge + (int) keyToMerge.elementKey);
            }
          }
          newLocations.put(keyToMerge, entryToMerge.getValue());

          // Override with default if applicable.
          if (!fieldInLocationsToMerge.isRepeated() && !isMessage(fieldInLocationsToMerge)) {
            if (!proto3 && !messageToMergeForm.hasField(fieldInLocationsToMerge)) {
              configBuilder.clearField(fieldInLocationsToMerge);
            }
          }
        }
      }

      // Next recursively merge locations of sub-messages. As getAllFields only
      // delivers fields with set values, we only recurse into sub-messages which are actually set.
      // Note the following subtlety: a primitive field in a sub-messages whose parent message is
      // not set, but is in the location map, will not be visited here. That seems to be okay
      // because resetting such a sub-field to its default value will not automatically collapse
      // the parent message to its default, so the parent should be set here.
      for (Map.Entry<FieldDescriptor, Object> entry :
          messageToMergeForm.getAllFields().entrySet()) {
        FieldDescriptor field = entry.getKey();
        if (!isMessage(field) || (field.isMapField() && !isMessage(getValueField(field)))) {
          // Primitive field doesn't require location merging.
          continue;
        }
        mergeMessageTypeFieldLocations(field, entry.getValue(), configToMergeForm, proto3);
      }
    }

    @SuppressWarnings("unchecked")
    private void mergeMessageTypeFieldLocations(
        FieldDescriptor field,
        final Object value,
        final ConfigSource configToMergeFrom,
        final boolean proto3) {
      if (field.isMapField()) {
        for (final MapEntry<Object, Message> entry : (List<MapEntry<Object, Message>>) value) {
          withBuilder(
              field,
              entry.getKey(),
              new BuildAction() {
                @Override
                public void accept(Builder builder) {
                  builder.mergeLocations(entry.getValue(), configToMergeFrom, proto3);
                }
              });
        }
      } else if (field.isRepeated()) {
        List<Message> listValue = (List<Message>) value;
        // Start at index where list ended before calling standard merge.
        int i = configBuilder.getRepeatedFieldCount(field) - listValue.size();
        for (final Message elem : listValue) {
          withBuilderAt(
              field,
              i++,
              new BuildAction() {
                @Override
                public void accept(Builder builder) {
                  builder.mergeLocations(elem, configToMergeFrom, proto3);
                }
              });
        }
      } else {
        withBuilder(
            field,
            null,
            new BuildAction() {
              @Override
              public void accept(Builder builder) {
                builder.mergeLocations((Message) value, configToMergeFrom, proto3);
              }
            });
      }
    }
  }

  /**
   * A helper class to represent a location key, a pair of a field descriptor and an optional
   * element key.
   */
  private static class LocationKey {

    private final FieldDescriptor field;
    private final Object elementKey;

    private LocationKey(FieldDescriptor field, @Nullable Object elementKey) {
      this.field = field;
      this.elementKey = elementKey;
    }

    @Override
    public int hashCode() {
      return Objects.hash(field, elementKey);
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof LocationKey)) {
        return false;
      }
      LocationKey other = (LocationKey) obj;
      return Objects.equals(field, other.field) && Objects.equals(elementKey, other.elementKey);
    }

    @Override
    public String toString() {
      if (elementKey == null) {
        return field.getFullName();
      }
      return String.format("%s[%s]", field.getFullName(), elementKey);
    }
  }

  /**
   * Wrapper around a message which uses identity for equality, hashCode, and toString.
   *
   * <p>Instead of this class, we could have used IdentityHashMap, however, the debugging experience
   * is bad because that class unfortunately doesn't print identities on toString.
   */
  private static class MessageKey {

    private final Message message;

    private MessageKey(Message message) {
      this.message = message;
    }

    @Override
    public String toString() {
      return String.format("MessageKey#%s", System.identityHashCode(message));
    }

    @Override
    public int hashCode() {
      return System.identityHashCode(message);
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof MessageKey && ((MessageKey) obj).message == message;
    }
  }

  /** Ensures location is either non-null or UNKNOWN. */
  private static Location nonNull(@Nullable Location location) {
    if (location == null) {
      return SimpleLocation.UNKNOWN;
    }
    return location;
  }

  /** Checks whether field is a message field. */
  private static boolean isMessage(FieldDescriptor field) {
    return field.getType() == FieldDescriptor.Type.MESSAGE;
  }

  /** Gets the value field of a map field's entry message. */
  private static FieldDescriptor getValueField(FieldDescriptor field) {
    return field.getMessageType().getFields().get(1);
  }

  @SuppressWarnings("unchecked")
  private static void putMapEntry(
      Message.Builder builder, FieldDescriptor field, Object key, Object value) {
    Message.Builder entryBuilder = builder.newBuilderForField(field);
    FieldDescriptor keyField = entryBuilder.getDescriptorForType().findFieldByName("key");
    FieldDescriptor valueField = entryBuilder.getDescriptorForType().findFieldByName("value");
    entryBuilder.setField(keyField, key);
    entryBuilder.setField(valueField, value);
    List<Message> entries =
        removeEntryWithKeyIfPresent((List<Message>) builder.getField(field), key);
    entries.add(entryBuilder.build());
    builder.setField(field, entries);
  }

  private static List<Message> removeEntryWithKeyIfPresent(List<Message> messages, Object key) {
    List<Message> messagesWithoutKey = Lists.newArrayList();
    // This should only ever match at most one element, since the underlying proto adheres
    // to Map<> semantics.
    for (Message message : messages) {
      FieldDescriptor keyField = message.getDescriptorForType().findFieldByName("key");
      Object messageKey = message.getField(keyField);
      if (!messageKey.equals(key)) {
        messagesWithoutKey.add(message);
      }
    }
    return messagesWithoutKey;
  }

  /**
   * Helper method to get an {@link Map} from message builder for the map field. The map returned is
   * an immutable copy, to add entries to a map field use {@link
   * ConfigSource#putMapEntry(com.google.protobuf.Message.Builder, FieldDescriptor, Object, Object)}
   */
  @SuppressWarnings("unchecked")
  private static ImmutableMap<Object, Object> getMapFromProtoMapBuilder(
      Message.Builder builder, FieldDescriptor field) {
    List<Message> entries = (List<Message>) builder.getField(field);
    ImmutableMap.Builder<Object, Object> mapBuilder = ImmutableMap.builder();
    for (Message entry : entries) {
      Object key = entry.getField(entry.getDescriptorForType().findFieldByName("key"));
      Object value = entry.getField(entry.getDescriptorForType().findFieldByName("value"));
      mapBuilder.put(key, value);
    }
    return mapBuilder.build();
  }
}
