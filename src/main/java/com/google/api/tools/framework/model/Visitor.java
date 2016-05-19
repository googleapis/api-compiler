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

import com.google.api.tools.framework.util.Accepts;
import com.google.api.tools.framework.util.GenericVisitor;
import com.google.common.base.Preconditions;

/**
 * Base class for model visitors, using {@link GenericVisitor}. Implements the accept methods
 * necessary to traverse all model elements. Uses a {@link Scoper} to restrict visiting to
 * elements which are in scope.
 */
public abstract class Visitor extends GenericVisitor<Element> {

  private final Scoper scoper;
  private final boolean ignoreMapEntry;

  /**
   * Constructs a visitor which visits all elements.
   */
  protected Visitor() {
    super(Element.class);
    this.scoper = Scoper.UNRESTRICTED;
    this.ignoreMapEntry = false;
  }

  /**
   * Constructs a visitor where only elements reachable via the scoper are visited.
   */
  protected Visitor(Scoper scoper, boolean ignoreMapEntry) {
    super(Element.class);
    this.scoper = Preconditions.checkNotNull(scoper);
    this.ignoreMapEntry = ignoreMapEntry;
  }

  protected Visitor(Scoper scoper) {
    this(scoper, true);
  }

  private <E extends ProtoElement> void acceptElems(Iterable<E> elems) {
    for (E elem : elems) {
      if (!scoper.isReachable(elem)) {
        continue;
      }
      visit(elem);
    }
  }

  private void acceptMessages(Iterable<MessageType> messages) {
    for (MessageType message : messages) {
      if (ignoreMapEntry && message.isMapEntry() || !scoper.isReachable(message)) {
        continue;
      }
      visit(message);
    }
  }

  @Accepts
  public void accept(Model model) {
    acceptElems(model.getFiles());
  }

  @Accepts
  public void accept(ProtoFile file) {
    acceptContainer(file);
    acceptElems(file.getInterfaces());
  }

  @Accepts
  public void accept(MessageType message) {
    acceptContainer(message);
    acceptElems(message.getFields());
  }

  private void acceptContainer(ProtoContainerElement container) {
    acceptMessages(container.getMessages());
    acceptElems(container.getEnums());
  }

  @Accepts
  public void accept(Interface iface) {
    acceptElems(iface.getMethods());
  }

  @Accepts
  public void accept(EnumType enumType) {
    acceptElems(enumType.getValues());
  }

  @Accepts
  public void accept(Field field) {
    if (field.oneofScoped()) {
      // Only visit each oneof for one time when the first field in the oneof gets visited.
      if (field == field.getOneof().getFields().get(0)) {
        visit(field.getOneof());
      }
    }
  }
}
