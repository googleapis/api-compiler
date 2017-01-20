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

package com.google.api.tools.framework.importers.swagger.aspects.utils;

/**
 * Class to parser "x-google-*" extensions to help build top level Service config fields like
 * Service.endpoints etc.
 */
public final class ExtensionNames {

  private static final String GOOGLE_EXTENSION_PREFIX = "x-google-";

  // endpoints
  public static final String ENDPOINTS_EXTENSION_NAME = addGooglePrefix("endpoints");

  // authentication
  public static final String JWKS_SWAGGER_EXTENSION_LEGACY = "x-jwks_uri";
  public static final String OAUTH_ISSUER_SWAGGER_EXTENSION_LEGACY = "x-issuer";
  public static final String JWKS_SWAGGER_EXTENSION = addGooglePrefix("jwks_uri");
  public static final String OAUTH_ISSUER_SWAGGER_EXTENSION = addGooglePrefix("issuer");
  public static final String AUDIENCES_SWAGGER_EXTENSION = addGooglePrefix("audiences");

  // allow unregistered calls
  public static final String X_GOOGLE_ALLOW = addGooglePrefix("allow");

  private static String addGooglePrefix(String extensionShortName) {
    return GOOGLE_EXTENSION_PREFIX + extensionShortName;
  }
}
