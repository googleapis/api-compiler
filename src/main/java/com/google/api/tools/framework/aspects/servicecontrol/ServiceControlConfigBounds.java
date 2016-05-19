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

package com.google.api.tools.framework.aspects.servicecontrol;

/**
 * Represents bounds used for the service control config validation.
 */
class ServiceControlConfigBounds {
  private final int maxMonitoredResources;
  private final int maxMetrics;
  private final int maxLogs;
  private final int maxLabels;
  private final int maxStringLength;

  private ServiceControlConfigBounds(final int maxMonitoredResources, final int maxMetrics,
      final int maxLogs, final int maxLabels, final int maxStringLength) {
    this.maxMonitoredResources = maxMonitoredResources;
    this.maxMetrics = maxMetrics;
    this.maxLogs = maxLogs;
    this.maxLabels = maxLabels;
    this.maxStringLength = maxStringLength;
  }

  /**
   *  Maximum number of resource entities that can be defined in the service control config.
   */
  public int getMaxMonitoredResources() {
    return maxMonitoredResources;
  }

  /**
   *  Maximum number of metrics that can be defined in the service control config.
   */
  public int getMaxMetrics() {
    return maxMetrics;
  }

  /**
   *  Maximum number of logs that can be defined in the service control config.
   */
  public int getMaxLogs() {
    return maxLogs;
  }

  /**
   *  Maximum number of labels used by resource entity, label key transformation or metric.
   */
  public int getMaxLabels() {
    return maxLabels;
  }

  /**
   *  Maximum length of a user specified string like name or label.
   */
  public int getMaxStringLength() {
    return maxStringLength;
  }

  public static class Builder {
    private int maxMonitoredResources = 128;
    private int maxMetrics = 1024;
    private int maxLogs = 1024;
    private int maxLabels = 128;
    private int maxStringLength = 512;
    
    public Builder setMaxMonitoredResources(int maxMonitoredResources) {
      this.maxMonitoredResources = maxMonitoredResources;
      return this;
    }

    public Builder setMaxMetrics(int maxMetrics) {
      this.maxMetrics = maxMetrics;
      return this;
    }

    public Builder setMaxLogs(int maxLogs) {
      this.maxLogs = maxLogs;
      return this;
    }

    public Builder setMaxLabels(int maxLabels) {
      this.maxLabels = maxLabels;
      return this;
    }

    public Builder setMaxStringLength(int maxStringLength) {
      this.maxStringLength = maxStringLength;
      return this;
    }

    public ServiceControlConfigBounds build() {
      return new ServiceControlConfigBounds(maxMonitoredResources,
          maxMetrics, maxLogs, maxLabels, maxStringLength);
    }
  }
}
