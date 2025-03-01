import merge from "lodash/merge";

import { ConnectorManifest, DeclarativeStream } from "core/request/ConnectorManifest";

import { DEFAULT_BUILDER_FORM_VALUES, DEFAULT_CONNECTOR_NAME, OLDEST_SUPPORTED_CDK_VERSION } from "./types";
import { convertToBuilderFormValues } from "./useManifestToBuilderForm";
import { formatJson } from "./utils";

const baseManifest: ConnectorManifest = {
  type: "DeclarativeSource",
  version: OLDEST_SUPPORTED_CDK_VERSION,
  check: {
    type: "CheckStream",
    stream_names: [],
  },
  streams: [],
};

const stream1: DeclarativeStream = {
  type: "DeclarativeStream",
  name: "stream1",
  retriever: {
    type: "SimpleRetriever",
    record_selector: {
      type: "RecordSelector",
      extractor: {
        type: "DpathExtractor",
        field_path: [],
      },
    },
    requester: {
      type: "HttpRequester",
      url_base: "https://url.com",
      path: "/stream-1-path",
    },
  },
};

const stream2: DeclarativeStream = merge({}, stream1, {
  name: "stream2",
  retriever: {
    name: "stream2",
    requester: {
      name: "stream2",
      path: "/stream-2-path",
    },
  },
});

const noOpResolve = (manifest: ConnectorManifest) => {
  return Promise.resolve({ manifest });
};

describe("Conversion throws error when", () => {
  it("resolve throws error", async () => {
    const errorMessage = "Some resolve error message";
    const resolve = (_manifest: ConnectorManifest) => {
      throw new Error(errorMessage);
    };
    const convert = async () => {
      return convertToBuilderFormValues(resolve, {} as ConnectorManifest, DEFAULT_CONNECTOR_NAME);
    };
    await expect(convert).rejects.toThrow(errorMessage);
  });

  it("manifests has incorrect retriever type", async () => {
    const convert = async () => {
      const manifest: ConnectorManifest = {
        ...baseManifest,
        streams: [
          {
            type: "DeclarativeStream",
            name: "firstStream",
            retriever: {
              type: "CustomRetriever",
              class_name: "some_python_class",
            },
          },
        ],
      };
      return convertToBuilderFormValues(noOpResolve, manifest, DEFAULT_CONNECTOR_NAME);
    };
    await expect(convert).rejects.toThrow("doesn't use a SimpleRetriever");
  });

  it("manifest has incorrect requester type", async () => {
    const convert = async () => {
      const manifest: ConnectorManifest = {
        ...baseManifest,
        streams: [
          merge({}, stream1, {
            retriever: {
              requester: {
                type: "CustomRequester",
                class_name: "some_python_class",
              },
            },
          }),
        ],
      };
      return convertToBuilderFormValues(noOpResolve, manifest, DEFAULT_CONNECTOR_NAME);
    };
    await expect(convert).rejects.toThrow("doesn't use a HttpRequester");
  });

  it("manifest has an authenticator with a non-interpolated secret key", async () => {
    const convert = async () => {
      const manifest: ConnectorManifest = {
        ...baseManifest,
        streams: [
          merge({}, stream1, {
            retriever: {
              requester: {
                authenticator: {
                  type: "ApiKeyAuthenticator",
                  api_token: "abcd1234",
                  header: "API_KEY",
                },
              },
            },
          }),
        ],
      };
      return convertToBuilderFormValues(noOpResolve, manifest, DEFAULT_CONNECTOR_NAME);
    };
    await expect(convert).rejects.toThrow("api_token value must be of the form {{ config[");
  });

  it("manifest has an OAuthAuthenticator with a refresh_request_body containing non-string values", async () => {
    const convert = () => {
      const manifest: ConnectorManifest = {
        ...baseManifest,
        streams: [
          merge({}, stream1, {
            retriever: {
              requester: {
                authenticator: {
                  type: "OAuthAuthenticator",
                  client_id: "{{ config['client_id'] }}",
                  client_secret: "{{ config['client_secret'] }}",
                  refresh_token: "{{ config['client_refresh_token'] }}",
                  refresh_request_body: {
                    key1: "val1",
                    key2: {
                      a: 1,
                      b: 2,
                    },
                  },
                  token_refresh_endpoint: "https://api.com/refresh_token",
                  grant_type: "client_credentials",
                },
              },
            },
          }),
        ],
      };
      return convertToBuilderFormValues(noOpResolve, manifest, DEFAULT_CONNECTOR_NAME);
    };
    await expect(convert).rejects.toThrow("OAuthAuthenticator contains a refresh_request_body with non-string values");
  });
});

describe("Conversion successfully results in", () => {
  it("default values if manifest is empty", async () => {
    const formValues = await convertToBuilderFormValues(noOpResolve, baseManifest, DEFAULT_CONNECTOR_NAME);
    expect(formValues).toEqual(DEFAULT_BUILDER_FORM_VALUES);
  });

  it("spec properties converted to inputs if no streams present", async () => {
    const manifest: ConnectorManifest = {
      ...baseManifest,
      spec: {
        type: "Spec",
        connection_specification: {
          $schema: "http://json-schema.org/draft-07/schema#",
          type: "object",
          required: ["api_key"],
          properties: {
            api_key: {
              type: "string",
              title: "API Key",
              airbyte_secret: true,
            },
          },
        },
      },
    };
    const formValues = await convertToBuilderFormValues(noOpResolve, manifest, DEFAULT_CONNECTOR_NAME);
    expect(formValues.inferredInputOverrides).toEqual({});
    expect(formValues.inputs).toEqual([
      {
        key: "api_key",
        required: true,
        definition: manifest.spec?.connection_specification.properties.api_key,
      },
    ]);
  });

  it("spec properties converted to input overrides on matching auth keys", async () => {
    const manifest: ConnectorManifest = {
      ...baseManifest,
      streams: [
        merge({}, stream1, {
          retriever: {
            requester: {
              authenticator: {
                type: "ApiKeyAuthenticator",
                api_token: "{{ config['api_key'] }}",
                header: "API_KEY",
              },
            },
          },
        }),
      ],
      spec: {
        type: "Spec",
        connection_specification: {
          $schema: "http://json-schema.org/draft-07/schema#",
          type: "object",
          required: ["api_key"],
          properties: {
            api_key: {
              type: "string",
              title: "API Key",
              airbyte_secret: true,
            },
            numeric_key: {
              type: "number",
              title: "Numeric key",
            },
          },
        },
      },
    };
    const formValues = await convertToBuilderFormValues(noOpResolve, manifest, DEFAULT_CONNECTOR_NAME);
    expect(formValues.inputs).toEqual([
      {
        key: "numeric_key",
        required: false,
        definition: manifest.spec?.connection_specification.properties.numeric_key,
      },
    ]);
    expect(formValues.inferredInputOverrides).toEqual({
      api_key: manifest.spec?.connection_specification.properties.api_key,
    });
  });

  it("request options converted to key-value list", async () => {
    const manifest: ConnectorManifest = {
      ...baseManifest,
      streams: [
        merge({}, stream1, {
          retriever: {
            requester: {
              request_parameters: {
                k1: "v1",
                k2: "v2",
              },
            },
          },
        }),
      ],
    };
    const formValues = await convertToBuilderFormValues(noOpResolve, manifest, DEFAULT_CONNECTOR_NAME);
    expect(formValues.streams[0].requestOptions.requestParameters).toEqual([
      ["k1", "v1"],
      ["k2", "v2"],
    ]);
  });

  it("request json body converted to key-value list", async () => {
    const manifest: ConnectorManifest = {
      ...baseManifest,
      streams: [
        merge({}, stream1, {
          retriever: {
            requester: {
              request_body_json: {
                k1: "v1",
                k2: "v2",
              },
            },
          },
        }),
      ],
    };
    const formValues = await convertToBuilderFormValues(noOpResolve, manifest, DEFAULT_CONNECTOR_NAME);
    expect(formValues.streams[0].requestOptions.requestBody).toEqual({
      type: "json_list",
      values: [
        ["k1", "v1"],
        ["k2", "v2"],
      ],
    });
  });

  it("nested request json body converted to string", async () => {
    const body = {
      k1: { nested: "v1" },
      k2: "v2",
    };
    const manifest: ConnectorManifest = {
      ...baseManifest,
      streams: [
        merge({}, stream1, {
          retriever: {
            requester: {
              request_body_json: body,
            },
          },
        }),
      ],
    };
    const formValues = await convertToBuilderFormValues(noOpResolve, manifest, DEFAULT_CONNECTOR_NAME);
    expect(formValues.streams[0].requestOptions.requestBody).toEqual({
      type: "json_freeform",
      value: formatJson(body),
    });
  });

  it("request data body converted to list", async () => {
    const manifest: ConnectorManifest = {
      ...baseManifest,
      streams: [
        merge({}, stream1, {
          retriever: {
            requester: {
              request_body_data: {
                k1: "v1",
                k2: "v2",
              },
            },
          },
        }),
      ],
    };
    const formValues = await convertToBuilderFormValues(noOpResolve, manifest, DEFAULT_CONNECTOR_NAME);
    expect(formValues.streams[0].requestOptions.requestBody).toEqual({
      type: "form_list",
      values: [
        ["k1", "v1"],
        ["k2", "v2"],
      ],
    });
  });

  it("string body converted to string", async () => {
    const manifest: ConnectorManifest = {
      ...baseManifest,
      streams: [
        merge({}, stream1, {
          retriever: {
            requester: {
              request_body_data: "abc def",
            },
          },
        }),
      ],
    };
    await expect(() => convertToBuilderFormValues(noOpResolve, manifest, DEFAULT_CONNECTOR_NAME)).rejects.toThrow();
  });

  it("primary key string converted to array", async () => {
    const manifest: ConnectorManifest = {
      ...baseManifest,
      streams: [
        merge({}, stream1, {
          primary_key: "id",
          retriever: {
            primary_key: "id",
          },
        }),
      ],
    };
    const formValues = await convertToBuilderFormValues(noOpResolve, manifest, DEFAULT_CONNECTOR_NAME);
    expect(formValues.streams[0].primaryKey).toEqual(["id"]);
  });

  it("multi stream partition router converted to builder partition router", async () => {
    const manifest: ConnectorManifest = {
      ...baseManifest,
      streams: [
        merge({}, stream1, {
          retriever: {
            partition_router: [
              {
                type: "ListPartitionRouter",
                cursor_field: "id",
                values: ["slice1", "slice2"],
              },
              {
                type: "ListPartitionRouter",
                cursor_field: "id2",
                values: "{{ config['abc'] }}",
              },
            ],
          },
        }),
      ],
    };
    const formValues = await convertToBuilderFormValues(noOpResolve, manifest, DEFAULT_CONNECTOR_NAME);
    expect(formValues.streams[0].partitionRouter).toEqual([
      {
        type: "ListPartitionRouter",
        cursor_field: "id",
        values: { type: "list", value: ["slice1", "slice2"] },
      },
      {
        type: "ListPartitionRouter",
        cursor_field: "id2",
        values: { type: "variable", value: "{{ config['abc'] }}" },
      },
    ]);
  });

  it("substream partition router converted to builder partition router", async () => {
    const manifest: ConnectorManifest = {
      ...baseManifest,
      streams: [
        stream1,
        merge({}, stream2, {
          retriever: {
            partition_router: {
              type: "SubstreamPartitionRouter",
              parent_stream_configs: [
                {
                  type: "ParentStreamConfig",
                  parent_key: "key",
                  stream: stream1,
                  partition_field: "field",
                },
              ],
            },
          },
        }),
      ],
    };
    const formValues = await convertToBuilderFormValues(noOpResolve, manifest, DEFAULT_CONNECTOR_NAME);
    expect(formValues.streams[1].partitionRouter).toEqual([
      {
        type: "SubstreamPartitionRouter",
        parent_key: "key",
        partition_field: "field",
        parentStreamReference: "0",
      },
    ]);
  });

  it("schema loader converted to schema with ordered keys in JSON", async () => {
    const manifest: ConnectorManifest = {
      ...baseManifest,
      streams: [
        merge({}, stream1, {
          schema_loader: {
            type: "InlineSchemaLoader",
            schema: {
              b: "yyy",
              a: "xxx",
            },
          },
        }),
      ],
    };
    const formValues = await convertToBuilderFormValues(noOpResolve, manifest, DEFAULT_CONNECTOR_NAME);
    expect(formValues.streams[0].schema).toEqual(
      `{
  "a": "xxx",
  "b": "yyy"
}`
    );
  });

  it("stores unsupported fields", async () => {
    const manifest: ConnectorManifest = {
      ...baseManifest,
      streams: [
        merge({}, stream1, {
          retriever: {
            record_selector: {
              record_filter: {
                type: "RecordFilter",
                condition: "true",
              },
            },
          },
        }),
      ],
    };
    const formValues = await convertToBuilderFormValues(noOpResolve, manifest, DEFAULT_CONNECTOR_NAME);
    expect(formValues.streams[0].unsupportedFields).toEqual({
      retriever: {
        record_selector: {
          record_filter: manifest.streams[0].retriever.record_selector.record_filter,
        },
      },
    });
  });

  it("OAuth authenticator refresh_request_body converted to array", async () => {
    const manifest: ConnectorManifest = {
      ...baseManifest,
      streams: [
        merge({}, stream1, {
          retriever: {
            requester: {
              authenticator: {
                type: "OAuthAuthenticator",
                client_id: "{{ config['client_id'] }}",
                client_secret: "{{ config['client_secret'] }}",
                refresh_token: "{{ config['client_refresh_token'] }}",
                refresh_request_body: {
                  key1: "val1",
                  key2: "val2",
                },
                token_refresh_endpoint: "https://api.com/refresh_token",
                grant_type: "refresh_token",
              },
            },
          },
        }),
      ],
    };
    const formValues = await convertToBuilderFormValues(noOpResolve, manifest, DEFAULT_CONNECTOR_NAME);
    expect(formValues.global.authenticator).toEqual({
      type: "OAuthAuthenticator",
      client_id: "{{ config['client_id'] }}",
      client_secret: "{{ config['client_secret'] }}",
      refresh_token: "{{ config['client_refresh_token'] }}",
      refresh_request_body: [
        ["key1", "val1"],
        ["key2", "val2"],
      ],
      token_refresh_endpoint: "https://api.com/refresh_token",
      grant_type: "refresh_token",
    });
  });
});
