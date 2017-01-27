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

package com.google.api.tools.framework.aspects.naming.linters;

import com.google.api.Service;
import com.google.api.tools.framework.aspects.ConfigAspectBase;
import com.google.api.tools.framework.aspects.LintRule;
import com.google.api.tools.framework.model.Model;
import com.google.common.base.Strings;
import com.google.common.net.InternetDomainName;
import java.util.regex.Pattern;

/** Style rule to verify if the name of the service is valid. */
public class ServiceNameRule extends LintRule<Model> {

  private static final Pattern INVALID_CHARACTER_PATTERN = Pattern.compile("[_]");

  public ServiceNameRule(ConfigAspectBase aspect) {
    super(aspect, "service-dns-name", Model.class);
  }

  @Override
  public void run(Model model) {
    String serviceName = model.getServiceConfig().getName();
    if (!Strings.isNullOrEmpty(serviceName)
        && (!InternetDomainName.isValid(serviceName)
            // InternetDomainName.isValid does a lenient validation and allows underscores (which we
            // do not want to permit as DNS names). Therefore explicitly checking for underscores.
            || INVALID_CHARACTER_PATTERN.matcher(serviceName).find())) {
      warning(
          getLocationInConfig(model.getServiceConfig(), Service.NAME_FIELD_NUMBER),
          "Invalid DNS name '%s'.",
          serviceName);
    }

  }
}
