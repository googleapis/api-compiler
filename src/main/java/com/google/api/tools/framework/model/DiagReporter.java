/*
 * Copyright 2017 Google Inc.
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

import com.google.api.tools.framework.model.Diag.Kind;
import com.google.common.base.Preconditions;
import com.google.protobuf.Message;
import javax.annotation.Nullable;

/**
 * General handler for Reporting Diagostic messages ({@link Diag}s). Collects Diags, resolves Config
 * locations and manages suppression directives.
 */
public class DiagReporter {

  private final DiagCollector diagCollector;
  private final DiagSuppressor diagSuppressor;
  private final ConfigLocationResolver locationResolver;

  public DiagReporter(
      DiagCollector diagCollector,
      DiagSuppressor diagSuppressor,
      ConfigLocationResolver locationResolver) {
    this.diagCollector = Preconditions.checkNotNull(diagCollector, "diagCollector");
    this.diagSuppressor = Preconditions.checkNotNull(diagSuppressor, "diagSuppressor");
    this.locationResolver = Preconditions.checkNotNull(locationResolver, "locationResolver");
  }

  // TODO(user): This should be hidden / private. Having it public allows people to bypass
  // the reporter suppression.
  public DiagCollector getDiagCollector() {
    return diagCollector;
  }

  // TODO(user): Abstraction leak! Ideally suppressor should be immutable, need to modify
  // code to parse all suppression patterns before starting model generation.
  public DiagSuppressor getDiagSuppressor() {
    return diagSuppressor;
  }

  public void report(Diag diag) {
    if (diag.getKind() == Kind.WARNING
        && diagSuppressor.isDiagSuppressed(diag, diag.getLocation())) {
      return;
    }
    diagCollector.addDiag(diag);
  }

  public void reportWarning(LocationContext locationContext, String formatString, Object... args) {
    report(Kind.WARNING, locationContext, formatString, args);
  }

  public void reportError(LocationContext locationContext, String formatString, Object... args) {
    report(Kind.ERROR, locationContext, formatString, args);
  }

  private void report(
      Diag.Kind kind, LocationContext locationContext, String formatString, Object... args) {
    Location location = locationContext.resolve(locationResolver);
    Diag diag = Diag.create(location, formatString, kind, args);
    if (diag.getKind() == Kind.WARNING && diagSuppressor.isDiagSuppressed(diag, location)) {
      return;
    }
    diagCollector.addDiag(diag);
  }

  /**
   * Representation of a Config Location that can be resolved using {@link ConfigLocationResolver}
   */
  public static interface LocationContext {

    abstract Location resolve(ConfigLocationResolver resolver);
  }

  /**
   * A Config Location represented by a {@link Location} object. TODO(user): add easy to
   * discovery constructors for this class to LocationContext or DiagReporter
   */
  public static class ResolvedLocation implements LocationContext {
    private final Location location;

    private ResolvedLocation(Location location) {
      this.location = Preconditions.checkNotNull(location, "location");
    }

    public static LocationContext create(Location location) {
      return new ResolvedLocation(location);
    }

    @Override
    public Location resolve(ConfigLocationResolver resolver) {
      return location;
    }
  }

  /**
   * A config location representing a specific field within a {@link Message}
   *
   * <p>TODO(user): add easy to discovery constructors for this class to LocationContext or
   * DiagReporter
   */
  public static class MessageLocationContext implements LocationContext {
    private final Message message;

    private final String fieldName;
    @Nullable private final Object elementKey;

    private MessageLocationContext(Message message, String fieldName, Object elementKey) {
      this.message = Preconditions.checkNotNull(message, "message");
      this.fieldName = Preconditions.checkNotNull(fieldName, "fieldName");
      this.elementKey = elementKey;
    }

    public static LocationContext createForRepeated(
        Message message, String fieldName, Object elementKey) {
      return new MessageLocationContext(message, fieldName, elementKey);
    }

    public static LocationContext createForRepeatedByFieldName(
        Message message, int fieldNumber, Object elementKey) {
      return createForRepeated(
          message,
          message.getDescriptorForType().findFieldByNumber(fieldNumber).getName(),
          elementKey);
    }

    public static LocationContext create(Message message, String fieldName) {
      return new MessageLocationContext(message, fieldName, null);
    }

    public static LocationContext create(Message message, int fieldNumber) {
      return create(
          message, message.getDescriptorForType().findFieldByNumber(fieldNumber).getName());
    }

    @Override
    public Location resolve(ConfigLocationResolver resolver) {
      if (isForRepeated()) {
        return resolver.getLocationOfRepeatedFieldInConfig(message, fieldName, elementKey);
      }
      return resolver.getLocationInConfig(message, fieldName);
    }

    private boolean isForRepeated() {
      return elementKey != null;
    }
  }
}
