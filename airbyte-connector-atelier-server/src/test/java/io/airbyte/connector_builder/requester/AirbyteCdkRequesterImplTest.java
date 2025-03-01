/*
 * Copyright (c) 2023 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.connector_builder.requester;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.airbyte.connector_builder.api.model.generated.StreamRead;
import io.airbyte.connector_builder.api.model.generated.StreamReadSlicesInner;
import io.airbyte.connector_builder.api.model.generated.StreamsListRead;
import io.airbyte.connector_builder.api.model.generated.StreamsListReadStreamsInner;
import io.airbyte.connector_builder.command_runner.SynchronousCdkCommandRunner;
import io.airbyte.connector_builder.exceptions.AirbyteCdkInvalidInputException;
import io.airbyte.protocol.models.AirbyteRecordMessage;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AirbyteCdkRequesterImplTest {

  private static final String LIST_STREAMS_COMMAND = "list_streams";
  private static final String READ_STREAM_COMMAND = "test_read";
  private static final JsonNode A_CONFIG;
  private static final JsonNode A_MANIFEST;
  private static final String A_STREAM = "test";
  private static final Integer A_LIMIT = 1;
  private static final String EMPTY_CATALOG = "";

  static {
    try {
      A_CONFIG = new ObjectMapper().readTree("{\"config\": 1}");
      A_MANIFEST = new ObjectMapper().readTree("{\"manifest\": 1}");
    } catch (final JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  private SynchronousCdkCommandRunner commandRunner;
  private AirbyteCdkRequesterImpl requester;

  @BeforeEach
  void setUp() {
    commandRunner = mock(SynchronousCdkCommandRunner.class);
    requester = new AirbyteCdkRequesterImpl(commandRunner);
  }

  @Test
  void whenListStreamsThenReturnAdaptedCommandRunnerResponse() throws Exception {
    final ArgumentCaptor<String> configCaptor = ArgumentCaptor.forClass(String.class);
    when(commandRunner.runCommand(eq(LIST_STREAMS_COMMAND), configCaptor.capture(), eq(EMPTY_CATALOG)))
        .thenReturn(new AirbyteRecordMessage().withData(new ObjectMapper()
            .readTree("{\"streams\":[{\"name\":\"a name\", \"url\": \"a url\"}, {\"name\":\"another name\", \"url\": \"another url\"}]}")));

    final StreamsListRead streamsListRead = requester.listStreams(A_MANIFEST, A_CONFIG);

    // assert returned object
    assertEquals(2, streamsListRead.getStreams().size());
    assertEquals(new StreamsListReadStreamsInner().name("a name").url("a url"), streamsListRead.getStreams().get(0));
    assertEquals(new StreamsListReadStreamsInner().name("another name").url("another url"), streamsListRead.getStreams().get(1));

    assertRunCommandArgs(configCaptor, LIST_STREAMS_COMMAND);
  }

  @Test
  void givenNameIsNullWhenListStreamsThenThrowException() throws Exception {
    when(commandRunner.runCommand(eq(LIST_STREAMS_COMMAND), any(), any()))
        .thenReturn(new AirbyteRecordMessage().withData(new ObjectMapper().readTree("{\"streams\":[{\"url\": \"missing name\"}]}")));
    assertThrows(AirbyteCdkInvalidInputException.class, () -> requester.listStreams(A_MANIFEST, A_CONFIG));
  }

  @Test
  void givenUrlIsNullWhenListStreamsThenThrowException() throws Exception {
    when(commandRunner.runCommand(eq(LIST_STREAMS_COMMAND), any(), any()))
        .thenReturn(new AirbyteRecordMessage().withData(new ObjectMapper().readTree("{\"streams\":[{\"name\": \"missing url\", \"url\": null}]}")));
    assertThrows(AirbyteCdkInvalidInputException.class, () -> requester.listStreams(A_MANIFEST, A_CONFIG));
  }

  ArgumentCaptor<String> testReadStreamSuccess(final Integer limit) throws Exception {
    final ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());

    final JsonNode response = mapper.readTree(
        "{\"test_read_limit_reached\": true, \"logs\":[{\"message\":\"log message1\"}, {\"message\":\"log message2\"}], "
            + "\"slices\": [{\"pages\": [{\"records\": [{\"record\": 1}]}], \"slice_descriptor\": {\"startDatetime\": "
            + "\"2023-11-01T00:00:00+00:00\", \"listItem\": \"item\"}, \"state\": {\"airbyte\": \"state\"}}, {\"pages\": []}],"
            + "\"inferred_schema\": {\"schema\": 1}}");
    final ArgumentCaptor<String> configCaptor = ArgumentCaptor.forClass(String.class);
    when(commandRunner.runCommand(eq(READ_STREAM_COMMAND), configCaptor.capture(), any()))
        .thenReturn(new AirbyteRecordMessage().withData(response));

    final StreamRead streamRead = requester.readStream(A_MANIFEST, A_CONFIG, A_STREAM, limit);

    final boolean testReadLimitReached = mapper.convertValue(response.get("test_read_limit_reached"), new TypeReference<Boolean>() {});
    assertEquals(testReadLimitReached, streamRead.getTestReadLimitReached());

    assertEquals(2, streamRead.getSlices().size());
    final List<StreamReadSlicesInner> slices = mapper.convertValue(response.get("slices"), new TypeReference<List<StreamReadSlicesInner>>() {});
    assertEquals(slices, streamRead.getSlices());

    assertEquals(2, streamRead.getLogs().size());
    final List<Object> logs = mapper.convertValue(response.get("logs"), new TypeReference<List<Object>>() {});
    assertEquals(logs, streamRead.getLogs());

    assertEquals(response.get("inferred_schema"), streamRead.getInferredSchema());

    return configCaptor;
  }

  @Test
  void whenReadStreamWithLimitThenReturnAdaptedCommandRunnerResponse() throws Exception {
    final ArgumentCaptor<String> configCaptor = testReadStreamSuccess(A_LIMIT);
    assertRunCommandArgs(configCaptor, READ_STREAM_COMMAND, A_LIMIT);
  }

  @Test
  void whenReadStreamWithoutLimitThenReturnAdaptedCommandRunnerResponse() throws Exception {
    final ArgumentCaptor<String> configCaptor = testReadStreamSuccess(null);
    assertRunCommandArgs(configCaptor, READ_STREAM_COMMAND, null);
  }

  @Test
  void givenStreamIsNullWhenReadStreamThenThrowException() throws Exception {
    when(commandRunner.runCommand(eq(READ_STREAM_COMMAND), any(), any()))
        .thenReturn(
            new AirbyteRecordMessage().withData(new ObjectMapper().readTree("{\"streams\":[{\"name\": \"missing stream\", \"stream\": null}]}")));
    assertThrows(AirbyteCdkInvalidInputException.class, () -> requester.readStream(A_MANIFEST, A_CONFIG, null, A_LIMIT));
  }

  void assertRunCommandArgs(final ArgumentCaptor<String> configCaptor, final String command) throws Exception {
    // assert runCommand arguments: We are doing this because the `runCommand` received a JSON as a
    // string and we don't care about the variations in formatting
    // to make this test flaky. Casting the string passed to `runCommand` as a JSON will remove this
    // flakiness
    final JsonNode config = A_CONFIG.deepCopy();
    ((ObjectNode) config).put("__command", command);
    ((ObjectNode) config).set("__injected_declarative_manifest", A_MANIFEST);
    assertEquals(config, new ObjectMapper().readTree(configCaptor.getValue()));
  }

  void assertRunCommandArgs(final ArgumentCaptor<String> configCaptor, final String command, final Integer recordLimit) throws Exception {
    final JsonNode config = A_CONFIG.deepCopy();
    ((ObjectNode) config).put("__command", command);
    ((ObjectNode) config).set("__injected_declarative_manifest", A_MANIFEST);
    final ObjectMapper mapper = new ObjectMapper();
    final ObjectNode commandConfig = mapper.createObjectNode();
    commandConfig.put("max_records", recordLimit);
    ((ObjectNode) config).set("__test_read_config", commandConfig);
    assertEquals(config, new ObjectMapper().readTree(configCaptor.getValue()));
  }

}
