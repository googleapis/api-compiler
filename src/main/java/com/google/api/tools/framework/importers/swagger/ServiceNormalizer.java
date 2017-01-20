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

package com.google.api.tools.framework.importers.swagger;

import com.google.api.Service;
import com.google.api.tools.framework.model.ConfigSource;
import com.google.api.tools.framework.model.Diag;
import com.google.api.tools.framework.model.DiagCollector;
import com.google.api.tools.framework.model.Model;
import com.google.api.tools.framework.model.stages.Normalized;
import com.google.api.tools.framework.setup.StandardSetup;
import com.google.api.tools.framework.tools.FileWrapper;
import com.google.api.tools.framework.yaml.YamlReader;
import com.google.common.collect.Lists;
import java.util.List;

/** Converts a customer defined service and converts it into a 'normalized' service. */
public class ServiceNormalizer {

  /**
   * Merges configurations from all the additionalConfigs and returns a normalized {@link Service}
   * instance.
   */
  public static Service normalizeService(
      Service service, DiagCollector diagCollector, List<FileWrapper> additionalConfigs) {
    Model model = createModel(service, additionalConfigs);
    model.establishStage(Normalized.KEY);
    if (model.getDiagCollector().hasErrors()) {
      for (Diag diag : model.getDiagCollector().getDiags()) {
        diagCollector.addDiag(diag);
      }
      return null;
    }
    return model.getNormalizedConfig();
  }

  /** Returns a {@link Model} generated from the {@link Service} and the additionalConfigs. */
  private static Model createModel(Service service, List<FileWrapper> additionalConfigs) {

    Model model = Model.create(service);
    if (additionalConfigs != null) {
      List<ConfigSource> allConfigs = Lists.newArrayList();
      allConfigs.add(model.getServiceConfigSource());
      for (FileWrapper additionalConfig : additionalConfigs) {
        allConfigs.add(
            YamlReader.readConfig(
                model.getDiagCollector(),
                additionalConfig.getFilename(),
                additionalConfig.getFileContents().toStringUtf8()));
      }
      model.setConfigSources(allConfigs);
    }
    StandardSetup.registerStandardProcessors(model);
    StandardSetup.registerStandardConfigAspects(model);
    return model;
  }
}
