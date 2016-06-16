[![Build Status](https://travis-ci.org/googleapis/api-compiler.svg?branch=master)](https://travis-ci.org/googleapis/api-compiler)

# Google API Compiler

## Overview

Google API Compiler (Api Compiler) is an open source tool for processing API
specifications. It currently supports OpenAPI specification, Protocol Buffers
(proto), and Google API Service Configuration (service config), and can be
extended to support other formats.

Google API Compiler parses the input files into object models, processes and
validates the models, and generate various outputs, such as:

- Validation warnings and errors.
- Normalized/validated service config.
- API discovery document.
- API reference documentation.
- API client libraries.

## Google API Service Configuration

Google API Service Configuration is a specification that defines the surface and
behavior of an API service, including interface, types, methods, authentication,
discovery, documentation, logging, monitoring and more. It is formally defined
by the proto message [`google.api.Service`](https://github.com/googleapis/googleapis/tree/master/google/api/service.proto)
and works with both REST and RPC APIs. Developers typically create the service
config using YAML files, and use the Google API Compiler to generate the proto
message.

NOTE: Google API Service Configuration is a rich and mature specification used
for Google production services, such as Cloud Logging, Cloud Vision,
Cloud Bigtable, IAM, and more.

## Used by other tools.
Google API compiler is used by other tools like [googleapis/toolkit](https://github.com/googleapis/toolkit)
to read the users API definition and autogenerate client libraries.

## Compile Google API Compiler

Clone the _Google API Compiler_ repo
```
git clone https://github.com/googleapis/api-compiler
```
Update submodules
```
git submodule update --recursive --init
```
Build source code
```
./gradlew buildGoogleApiConfigGen
```
For running tests, you need to have `protoc` in your path. If you don't
already have protoc version 3, you can download
it from https://github.com/google/protobuf/releases and set a symbolic link to
the protoc.
```
# Example
sudo ln -s  <Path to the downloaded protoc> /usr/local/bin/protoc
```


## Creating service config from proto files


### Creating a proto descriptor file

Google API Compiler does not consume the proto files directly. Developers need
to use `protoc` to generate the proto descriptor, then feed it to the Google
API Compiler.

```
# Creates a proto descriptor from proto files using protoc.
protoc <file1.proto> <file2.proto> --include_source_info --include_imports --descriptor_set_out=out.descriptors
```

### Create service config

```
# -------------File: myapi.yaml-----------------

# The schema of this file.
type: google.api.Service

# The version of the service config.
config_version: 3

# The service name. It should be the primary DNS name for the service.
name: library-example.googleapis.com

# The official title of this service.
title: Google Example Library API

# The list of API interfaces exposed by the service.
apis:
- name: google.example.library.v1.LibraryService

# Other aspects of the service, such as authentication.
# ...
```

### Executing the Google API Compiler

Once the jar 'gapi-config-gen-with-deps-0.0.0-SNAPSHOT.jar' is built under the
build/libs directory, you can execute the jar using the following command:

```
alias gapi-config-gen='java -jar <path to gapi-config-gen-with-deps-0.0.0-SNAPSHOT.jar>'
DESCRIPTOR_FILE=<PATH TO out.descriptor>
CONFIG_FILE=<path to yaml file>
JSON_FILE_NAME=<json output file name>
BINARY_FILE_NAME=<binary output file name>

gapi-config-gen \
--configs $CONFIG_FILE \
--descriptor $DESCRIPTOR_FILE \
--json_out $JSON_FILE_NAME \
--bin_out $BINARY_FILE_NAME
```

This command will output the service configuration in different
formats:
- Binary file: $BINARY_FILE_NAME
- Json file: $JSON_FILE_NAME

Any of these can be used as input to an Endpoints API server.

## Creating service config from an OpenAPI Spec

Validate the OpenAPI Spec and create the service configuration.

```
alias gapi-config-gen='java -jar <path to gapi-service-config-gen-with-deps-0.0.0-SNAPSHOT.jar>'
OPENAPI_FILE=<OpenAPI Spec filename>
JSON_FILE_NAME=<json output file name>
BINARY_FILE_NAME=<binary output file name>

gapi-config-gen \
--openapi $OPENAPI_FILE \
--json_out $JSON_FILE_NAME \
--bin_out $BINARY_FILE_NAME
```

This will create the service config:
- Binary file: $BINARY_FILE_NAME
- JSON file: $JSON_FILE_NAME

Any of these can be used as input to an Endpoints API server.

