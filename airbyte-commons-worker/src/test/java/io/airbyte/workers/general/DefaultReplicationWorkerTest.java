/*
 * Copyright (c) 2023 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.workers.general;

import static java.lang.Thread.sleep;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableMap;
import io.airbyte.commons.converters.ConnectorConfigUpdater;
import io.airbyte.commons.io.IOs;
import io.airbyte.commons.json.Jsons;
import io.airbyte.commons.string.Strings;
import io.airbyte.config.ConfigSchema;
import io.airbyte.config.Configs.WorkerEnvironment;
import io.airbyte.config.FailureReason;
import io.airbyte.config.FailureReason.FailureOrigin;
import io.airbyte.config.ReplicationAttemptSummary;
import io.airbyte.config.ReplicationOutput;
import io.airbyte.config.StandardSync;
import io.airbyte.config.StandardSyncInput;
import io.airbyte.config.StandardSyncSummary.ReplicationStatus;
import io.airbyte.config.State;
import io.airbyte.config.StreamSyncStats;
import io.airbyte.config.SyncStats;
import io.airbyte.config.WorkerDestinationConfig;
import io.airbyte.config.WorkerSourceConfig;
import io.airbyte.config.helpers.LogClientSingleton;
import io.airbyte.config.helpers.LogConfigs;
import io.airbyte.featureflag.FeatureFlagClient;
import io.airbyte.featureflag.HandleStreamStatus;
import io.airbyte.featureflag.TestClient;
import io.airbyte.featureflag.Workspace;
import io.airbyte.metrics.lib.MetricAttribute;
import io.airbyte.metrics.lib.MetricClient;
import io.airbyte.metrics.lib.MetricClientFactory;
import io.airbyte.metrics.lib.MetricTags;
import io.airbyte.metrics.lib.OssMetricsRegistry;
import io.airbyte.protocol.models.AirbyteLogMessage.Level;
import io.airbyte.protocol.models.AirbyteMessage;
import io.airbyte.protocol.models.AirbyteStreamNameNamespacePair;
import io.airbyte.protocol.models.AirbyteTraceMessage;
import io.airbyte.protocol.models.Config;
import io.airbyte.protocol.models.StreamDescriptor;
import io.airbyte.validation.json.JsonSchemaValidator;
import io.airbyte.workers.RecordSchemaValidator;
import io.airbyte.workers.WorkerMetricReporter;
import io.airbyte.workers.WorkerUtils;
import io.airbyte.workers.context.ReplicationContext;
import io.airbyte.workers.exception.WorkerException;
import io.airbyte.workers.helper.AirbyteMessageDataExtractor;
import io.airbyte.workers.helper.FailureHelper;
import io.airbyte.workers.internal.AirbyteDestination;
import io.airbyte.workers.internal.AirbyteSource;
import io.airbyte.workers.internal.FieldSelector;
import io.airbyte.workers.internal.HeartbeatMonitor;
import io.airbyte.workers.internal.HeartbeatTimeoutChaperone;
import io.airbyte.workers.internal.NamespacingMapper;
import io.airbyte.workers.internal.book_keeping.AirbyteMessageOrigin;
import io.airbyte.workers.internal.book_keeping.AirbyteMessageTracker;
import io.airbyte.workers.internal.book_keeping.SyncStatsTracker;
import io.airbyte.workers.internal.book_keeping.events.ReplicationAirbyteMessageEventPublishingHelper;
import io.airbyte.workers.internal.exception.DestinationException;
import io.airbyte.workers.internal.exception.SourceException;
import io.airbyte.workers.internal.sync_persistence.SyncPersistence;
import io.airbyte.workers.test_utils.AirbyteMessageUtils;
import io.airbyte.workers.test_utils.TestConfigHelpers;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

class DefaultReplicationWorkerTest {

  private static final Logger LOGGER = LoggerFactory.getLogger(DefaultReplicationWorkerTest.class);

  private static final String JOB_ID = "0";
  private static final int JOB_ATTEMPT = 0;
  private static final Path WORKSPACE_ROOT = Path.of("workspaces/10");
  private static final String STREAM_NAME = "user_preferences";
  private static final String FIELD_NAME = "favorite_color";
  private static final AirbyteMessage RECORD_MESSAGE1 = AirbyteMessageUtils.createRecordMessage(STREAM_NAME, FIELD_NAME, "blue");
  private static final AirbyteMessage RECORD_MESSAGE2 = AirbyteMessageUtils.createRecordMessage(STREAM_NAME, FIELD_NAME, "yellow");
  private static final AirbyteMessage RECORD_MESSAGE3 = AirbyteMessageUtils.createRecordMessage(STREAM_NAME, FIELD_NAME, 3);
  private static final AirbyteMessage STATE_MESSAGE = AirbyteMessageUtils.createStateMessage(STREAM_NAME, "checkpoint", "1");
  private static final AirbyteTraceMessage ERROR_TRACE_MESSAGE =
      AirbyteMessageUtils.createErrorTraceMessage("a connector error occurred", Double.valueOf(123));
  private static final Config CONNECTOR_CONFIG = new Config().withAdditionalProperty("my_key", "my_new_value");
  private static final AirbyteMessage CONFIG_MESSAGE = AirbyteMessageUtils.createConfigControlMessage(CONNECTOR_CONFIG, 1D);
  private static final String STREAM1 = "stream1";

  private static final String NAMESPACE = "namespace";
  private static final String INDUCED_EXCEPTION = "induced exception";

  private Path jobRoot;
  private AirbyteSource source;
  private NamespacingMapper mapper;
  private AirbyteDestination destination;
  private StandardSyncInput syncInput;
  private WorkerSourceConfig sourceConfig;
  private WorkerDestinationConfig destinationConfig;
  private AirbyteMessageTracker messageTracker;
  private SyncStatsTracker syncStatsTracker;
  private SyncPersistence syncPersistence;
  private RecordSchemaValidator recordSchemaValidator;
  private MetricClient metricClient;
  private WorkerMetricReporter workerMetricReporter;
  private ConnectorConfigUpdater connectorConfigUpdater;
  private HeartbeatTimeoutChaperone heartbeatTimeoutChaperone;
  private ReplicationAirbyteMessageEventPublishingHelper replicationAirbyteMessageEventPublishingHelper;
  private FeatureFlagClient featureFlagClient;
  private AirbyteMessageDataExtractor airbyteMessageDataExtractor;

  @SuppressWarnings("unchecked")
  @BeforeEach
  void setup() throws Exception {
    MDC.clear();

    jobRoot = Files.createDirectories(Files.createTempDirectory("test").resolve(WORKSPACE_ROOT));

    final ImmutablePair<StandardSync, StandardSyncInput> syncPair = TestConfigHelpers.createSyncConfig();
    syncInput = syncPair.getValue();

    sourceConfig = WorkerUtils.syncToWorkerSourceConfig(syncInput);
    destinationConfig = WorkerUtils.syncToWorkerDestinationConfig(syncInput);

    source = mock(AirbyteSource.class);
    mapper = mock(NamespacingMapper.class);
    destination = mock(AirbyteDestination.class);
    messageTracker = mock(AirbyteMessageTracker.class);
    syncStatsTracker = mock(SyncStatsTracker.class);
    syncPersistence = mock(SyncPersistence.class);
    recordSchemaValidator = mock(RecordSchemaValidator.class);
    connectorConfigUpdater = mock(ConnectorConfigUpdater.class);
    metricClient = MetricClientFactory.getMetricClient();
    workerMetricReporter = new WorkerMetricReporter(metricClient, "docker_image:v1.0.0");
    airbyteMessageDataExtractor = new AirbyteMessageDataExtractor();

    final HeartbeatMonitor heartbeatMonitor = mock(HeartbeatMonitor.class);
    heartbeatTimeoutChaperone = new HeartbeatTimeoutChaperone(heartbeatMonitor, Duration.ofMinutes(5), null, null, null, metricClient);
    replicationAirbyteMessageEventPublishingHelper = mock(ReplicationAirbyteMessageEventPublishingHelper.class);
    featureFlagClient = mock(TestClient.class);

    when(messageTracker.getSyncStatsTracker()).thenReturn(syncStatsTracker);
    when(source.isFinished()).thenReturn(false, false, false, true);
    when(destination.isFinished()).thenReturn(false, false, false, true);
    when(source.attemptRead()).thenReturn(Optional.of(RECORD_MESSAGE1), Optional.empty(), Optional.of(RECORD_MESSAGE2));
    when(destination.attemptRead()).thenReturn(Optional.of(STATE_MESSAGE));
    when(mapper.mapCatalog(destinationConfig.getCatalog())).thenReturn(destinationConfig.getCatalog());
    when(mapper.mapMessage(RECORD_MESSAGE1)).thenReturn(RECORD_MESSAGE1);
    when(mapper.mapMessage(RECORD_MESSAGE2)).thenReturn(RECORD_MESSAGE2);
    when(mapper.mapMessage(RECORD_MESSAGE3)).thenReturn(RECORD_MESSAGE3);
    when(mapper.mapMessage(CONFIG_MESSAGE)).thenReturn(CONFIG_MESSAGE);
    when(heartbeatMonitor.isBeating()).thenReturn(Optional.of(true));
    when(featureFlagClient.boolVariation(HandleStreamStatus.INSTANCE, new Workspace(syncInput.getWorkspaceId()))).thenReturn(false);
  }

  @AfterEach
  void tearDown() {
    MDC.clear();
  }

  @Test
  void test() throws Exception {
    final ReplicationWorker worker = getDefaultReplicationWorker();

    worker.run(syncInput, jobRoot);

    verify(source).start(sourceConfig, jobRoot);
    verify(destination).start(destinationConfig, jobRoot);
    verify(destination).accept(RECORD_MESSAGE1);
    verify(destination).accept(RECORD_MESSAGE2);
    verify(source, atLeastOnce()).close();
    verify(destination).close();
    verify(recordSchemaValidator).validateSchema(
        RECORD_MESSAGE1.getRecord(),
        AirbyteStreamNameNamespacePair.fromRecordMessage(RECORD_MESSAGE1.getRecord()),
        new ConcurrentHashMap<>());
    verify(recordSchemaValidator).validateSchema(
        RECORD_MESSAGE2.getRecord(),
        AirbyteStreamNameNamespacePair.fromRecordMessage(RECORD_MESSAGE2.getRecord()),
        new ConcurrentHashMap<>());
  }

  @Test
  void testReplicationTimesAreUpdated() throws Exception {
    final ReplicationWorker worker = getDefaultReplicationWorker();

    final ReplicationOutput output = worker.run(syncInput, jobRoot);

    final SyncStats syncStats = output.getReplicationAttemptSummary().getTotalStats();
    assertNotEquals(0, syncStats.getReplicationStartTime());
    assertNotEquals(0, syncStats.getReplicationEndTime());
    assertNotEquals(0, syncStats.getSourceReadStartTime());
    assertNotEquals(0, syncStats.getSourceReadEndTime());
    assertNotEquals(0, syncStats.getDestinationWriteStartTime());
    assertNotEquals(0, syncStats.getDestinationWriteEndTime());
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void testWithStreamStatusFeatureFlag(final boolean isReset) throws Exception {
    when(featureFlagClient.boolVariation(HandleStreamStatus.INSTANCE, new Workspace(syncInput.getWorkspaceId()))).thenReturn(true);
    final AirbyteStreamNameNamespacePair streamNameNamespacePair = AirbyteStreamNameNamespacePair.fromRecordMessage(RECORD_MESSAGE1.getRecord());
    final ReplicationWorker worker = getDefaultReplicationWorker();
    final ReplicationContext replicationContext = simpleContext(isReset);
    syncInput = syncInput.withIsReset(isReset);

    worker.run(syncInput, jobRoot);

    verify(source).start(sourceConfig, jobRoot);
    verify(destination).start(destinationConfig, jobRoot);
    verify(destination).accept(RECORD_MESSAGE1);
    verify(destination).accept(RECORD_MESSAGE2);
    verify(source, atLeastOnce()).close();
    verify(destination).close();
    verify(recordSchemaValidator).validateSchema(
        RECORD_MESSAGE1.getRecord(),
        streamNameNamespacePair,
        new ConcurrentHashMap<>());
    verify(recordSchemaValidator).validateSchema(
        RECORD_MESSAGE2.getRecord(),
        AirbyteStreamNameNamespacePair.fromRecordMessage(RECORD_MESSAGE2.getRecord()),
        new ConcurrentHashMap<>());
    verify(replicationAirbyteMessageEventPublishingHelper, times(1)).publishCompleteStatusEvent(
        new StreamDescriptor(),
        replicationContext,
        AirbyteMessageOrigin.INTERNAL);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void testDestinationExceptionWithStreamStatusFeatureFlag(final boolean isReset) throws Exception {
    when(destination.getExitValue()).thenReturn(-1);
    when(featureFlagClient.boolVariation(HandleStreamStatus.INSTANCE, new Workspace(syncInput.getWorkspaceId()))).thenReturn(true);
    final AirbyteStreamNameNamespacePair streamNameNamespacePair = AirbyteStreamNameNamespacePair.fromRecordMessage(RECORD_MESSAGE1.getRecord());
    final ReplicationWorker worker = getDefaultReplicationWorker();
    final ReplicationContext replicationContext = simpleContext(isReset);
    syncInput = syncInput.withIsReset(isReset);

    worker.run(syncInput, jobRoot);

    verify(source).start(sourceConfig, jobRoot);
    verify(destination).start(destinationConfig, jobRoot);
    verify(destination).accept(RECORD_MESSAGE1);
    verify(destination).accept(RECORD_MESSAGE2);
    verify(source, atLeastOnce()).close();
    verify(destination).close();
    verify(recordSchemaValidator).validateSchema(
        RECORD_MESSAGE1.getRecord(),
        streamNameNamespacePair,
        new ConcurrentHashMap<>());
    verify(recordSchemaValidator).validateSchema(
        RECORD_MESSAGE2.getRecord(),
        AirbyteStreamNameNamespacePair.fromRecordMessage(RECORD_MESSAGE2.getRecord()),
        new ConcurrentHashMap<>());
    verify(replicationAirbyteMessageEventPublishingHelper, times(1)).publishIncompleteStatusEvent(
        null,
        replicationContext,
        AirbyteMessageOrigin.INTERNAL);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void testSourceExceptionWithStreamStatusFeatureFlag(final boolean isReset) throws Exception {
    when(source.getExitValue()).thenReturn(-1);
    when(featureFlagClient.boolVariation(HandleStreamStatus.INSTANCE, new Workspace(syncInput.getWorkspaceId()))).thenReturn(true);
    final AirbyteStreamNameNamespacePair streamNameNamespacePair = AirbyteStreamNameNamespacePair.fromRecordMessage(RECORD_MESSAGE1.getRecord());
    final ReplicationWorker worker = getDefaultReplicationWorker();
    final ReplicationContext replicationContext = simpleContext(isReset);
    syncInput = syncInput.withIsReset(isReset);

    worker.run(syncInput, jobRoot);

    verify(source).start(sourceConfig, jobRoot);
    verify(destination).start(destinationConfig, jobRoot);
    verify(destination).accept(RECORD_MESSAGE1);
    verify(destination).accept(RECORD_MESSAGE2);
    verify(source, atLeastOnce()).close();
    verify(destination).close();
    verify(recordSchemaValidator).validateSchema(
        RECORD_MESSAGE1.getRecord(),
        streamNameNamespacePair,
        new ConcurrentHashMap<>());
    verify(recordSchemaValidator).validateSchema(
        RECORD_MESSAGE2.getRecord(),
        AirbyteStreamNameNamespacePair.fromRecordMessage(RECORD_MESSAGE2.getRecord()),
        new ConcurrentHashMap<>());
    verify(replicationAirbyteMessageEventPublishingHelper, times(1)).publishIncompleteStatusEvent(
        null,
        replicationContext,
        AirbyteMessageOrigin.INTERNAL);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void testPlatformExceptionWithStreamStatusFeatureFlag(final boolean isReset) throws Exception {
    when(featureFlagClient.boolVariation(HandleStreamStatus.INSTANCE, new Workspace(syncInput.getWorkspaceId()))).thenReturn(true);
    final AirbyteStreamNameNamespacePair streamNameNamespacePair = AirbyteStreamNameNamespacePair.fromRecordMessage(RECORD_MESSAGE1.getRecord());
    final ReplicationWorker worker = getDefaultReplicationWorker();
    final ReplicationContext replicationContext = simpleContext(isReset);
    syncInput = syncInput.withIsReset(isReset);
    doThrow(new NullPointerException("test")).when(messageTracker).acceptFromSource(any());

    worker.run(syncInput, jobRoot);

    verify(source).start(sourceConfig, jobRoot);
    verify(destination).start(destinationConfig, jobRoot);
    verify(source, atLeastOnce()).close();
    verify(destination).close();
    verify(replicationAirbyteMessageEventPublishingHelper, times(1)).publishCompleteStatusEvent(
        new StreamDescriptor().withName(streamNameNamespacePair.getName()).withNamespace(streamNameNamespacePair.getNamespace()),
        replicationContext,
        AirbyteMessageOrigin.DESTINATION);
    verify(replicationAirbyteMessageEventPublishingHelper, times(1)).publishIncompleteStatusEvent(
        null,
        replicationContext,
        AirbyteMessageOrigin.INTERNAL);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void testDestinationWriteExceptionWithStreamStatusFeatureFlag(final boolean isReset) throws Exception {
    doThrow(new IllegalStateException("test")).when(destination).accept(RECORD_MESSAGE2);
    when(featureFlagClient.boolVariation(HandleStreamStatus.INSTANCE, new Workspace(syncInput.getWorkspaceId()))).thenReturn(true);
    final AirbyteStreamNameNamespacePair streamNameNamespacePair = AirbyteStreamNameNamespacePair.fromRecordMessage(RECORD_MESSAGE1.getRecord());
    final ReplicationWorker worker = getDefaultReplicationWorker();
    final ReplicationContext replicationContext = simpleContext(isReset);
    syncInput = syncInput.withIsReset(isReset);

    worker.run(syncInput, jobRoot);

    verify(source).start(sourceConfig, jobRoot);
    verify(destination).start(destinationConfig, jobRoot);
    verify(destination).accept(RECORD_MESSAGE1);
    verify(destination).accept(RECORD_MESSAGE2);
    verify(source, atLeastOnce()).close();
    verify(destination).close();
    verify(recordSchemaValidator).validateSchema(
        RECORD_MESSAGE1.getRecord(),
        streamNameNamespacePair,
        new ConcurrentHashMap<>());
    verify(recordSchemaValidator).validateSchema(
        RECORD_MESSAGE2.getRecord(),
        AirbyteStreamNameNamespacePair.fromRecordMessage(RECORD_MESSAGE2.getRecord()),
        new ConcurrentHashMap<>());
    verify(replicationAirbyteMessageEventPublishingHelper, times(1)).publishIncompleteStatusEvent(
        null,
        replicationContext,
        AirbyteMessageOrigin.INTERNAL);
  }

  @Test
  void testInvalidSchema() throws Exception {
    when(source.attemptRead()).thenReturn(Optional.of(RECORD_MESSAGE1), Optional.of(RECORD_MESSAGE2), Optional.of(RECORD_MESSAGE3));

    final ReplicationWorker worker = getDefaultReplicationWorker();

    worker.run(syncInput, jobRoot);

    verify(source).start(sourceConfig, jobRoot);
    verify(destination).start(destinationConfig, jobRoot);
    verify(destination).accept(RECORD_MESSAGE1);
    verify(destination).accept(RECORD_MESSAGE2);
    verify(destination).accept(RECORD_MESSAGE3);
    verify(recordSchemaValidator).validateSchema(
        RECORD_MESSAGE1.getRecord(),
        AirbyteStreamNameNamespacePair.fromRecordMessage(RECORD_MESSAGE1.getRecord()),
        new ConcurrentHashMap<>());
    verify(recordSchemaValidator).validateSchema(
        RECORD_MESSAGE2.getRecord(),
        AirbyteStreamNameNamespacePair.fromRecordMessage(RECORD_MESSAGE2.getRecord()),
        new ConcurrentHashMap<>());
    verify(recordSchemaValidator).validateSchema(
        RECORD_MESSAGE3.getRecord(),
        AirbyteStreamNameNamespacePair.fromRecordMessage(RECORD_MESSAGE3.getRecord()),
        new ConcurrentHashMap<>());
    verify(source).close();
    verify(destination).close();
  }

  @Test
  void testWorkerShutsDownLongRunningSchemaValidationThread() throws Exception {
    final String streamName = sourceConfig.getCatalog().getStreams().get(0).getStream().getName();
    final String streamNamespace = sourceConfig.getCatalog().getStreams().get(0).getStream().getNamespace();
    final ExecutorService executorService = Executors.newFixedThreadPool(1);
    final JsonSchemaValidator jsonSchemaValidator = mock(JsonSchemaValidator.class);
    recordSchemaValidator = new RecordSchemaValidator(Map.of(new AirbyteStreamNameNamespacePair(streamName, streamNamespace),
        sourceConfig.getCatalog().getStreams().get(0).getStream().getJsonSchema()), executorService, jsonSchemaValidator);

    final CountDownLatch countDownLatch = new CountDownLatch(1);
    doAnswer(invocation -> {
      // Make the schema validation thread artificially hang so that we can test the behavior
      // of what happens in the case the schema validation thread takes longer than the worker
      countDownLatch.await(1, TimeUnit.MINUTES);
      return null;
    }).when(jsonSchemaValidator).validateInitializedSchema(any(), any());

    final ReplicationWorker worker = getDefaultReplicationWorker();
    worker.run(syncInput, jobRoot);

    verify(source).start(sourceConfig, jobRoot);
    verify(destination).start(destinationConfig, jobRoot);
    verify(destination).accept(RECORD_MESSAGE1);
    verify(destination).accept(RECORD_MESSAGE2);
    verify(source, atLeastOnce()).close();
    verify(destination).close();

    // We want to ensure the thread is forcibly shut down by the worker (not running even though
    // it should run for at least 2 minutes, in this test's mock) so that we never write to
    // validationErrors while the metricReporter is trying to read from it.
    assertTrue(executorService.isShutdown());

    // Since the thread was left to hang after the first call, we expect 1, not 2, calls to
    // validateInitializedSchema by the time the replication worker is done and shuts down the
    // validation thread. We therefore expect the metricReporter to only report on the first record.
    verify(jsonSchemaValidator, Mockito.times(1)).validateInitializedSchema(any(), any());
  }

  @Test
  void testSourceNonZeroExitValue() throws Exception {
    when(source.getExitValue()).thenReturn(1);
    final ReplicationWorker worker = getDefaultReplicationWorker();
    final ReplicationOutput output = worker.run(syncInput, jobRoot);
    assertEquals(ReplicationStatus.FAILED, output.getReplicationAttemptSummary().getStatus());
    assertTrue(output.getFailures().stream().anyMatch(f -> f.getFailureOrigin().equals(FailureOrigin.SOURCE)));
  }

  @Test
  void testReplicationRunnableSourceFailure() throws Exception {
    final String sourceErrorMessage = "the source had a failure";

    when(source.attemptRead()).thenThrow(new RuntimeException(sourceErrorMessage));

    final ReplicationWorker worker = getDefaultReplicationWorker();

    final ReplicationOutput output = worker.run(syncInput, jobRoot);
    assertEquals(ReplicationStatus.FAILED, output.getReplicationAttemptSummary().getStatus());
    assertTrue(output.getFailures().stream()
        .anyMatch(f -> f.getFailureOrigin().equals(FailureOrigin.SOURCE) && f.getStacktrace().contains(sourceErrorMessage)));
  }

  @Test
  void testReplicationRunnableSourceUpdateConfig() throws Exception {
    when(source.attemptRead()).thenReturn(Optional.of(RECORD_MESSAGE1), Optional.of(CONFIG_MESSAGE), Optional.empty());

    final ReplicationWorker worker = getDefaultReplicationWorker();

    final ReplicationOutput output = worker.run(syncInput, jobRoot);
    assertEquals(ReplicationStatus.COMPLETED, output.getReplicationAttemptSummary().getStatus());

    verify(connectorConfigUpdater).updateSource(syncInput.getSourceId(), CONNECTOR_CONFIG);
  }

  @Test
  void testSourceConfigPersistError() throws Exception {
    when(source.attemptRead()).thenReturn(Optional.of(CONFIG_MESSAGE));
    when(source.isFinished()).thenReturn(false, true);

    final String persistErrorMessage = "there was a problem persisting the new config";
    doThrow(new RuntimeException(persistErrorMessage)).when(connectorConfigUpdater).updateSource(any(), any());

    final ReplicationWorker worker = getDefaultReplicationWorker();

    final ReplicationOutput output = worker.run(syncInput, jobRoot);
    assertEquals(ReplicationStatus.COMPLETED, output.getReplicationAttemptSummary().getStatus());

    verify(connectorConfigUpdater).updateSource(syncInput.getSourceId(), CONNECTOR_CONFIG);
  }

  @Test
  void testReplicationRunnableDestinationUpdateConfig() throws Exception {
    when(destination.attemptRead()).thenReturn(Optional.of(STATE_MESSAGE), Optional.of(CONFIG_MESSAGE));
    when(destination.isFinished()).thenReturn(false, false, true);

    final ReplicationWorker worker = getDefaultReplicationWorker();

    final ReplicationOutput output = worker.run(syncInput, jobRoot);
    assertEquals(ReplicationStatus.COMPLETED, output.getReplicationAttemptSummary().getStatus());

    verify(connectorConfigUpdater).updateDestination(syncInput.getDestinationId(), CONNECTOR_CONFIG);
  }

  @Test
  void testDestinationConfigPersistError() throws Exception {
    when(destination.attemptRead()).thenReturn(Optional.of(CONFIG_MESSAGE));
    when(destination.isFinished()).thenReturn(false, true);

    final String persistErrorMessage = "there was a problem persisting the new config";
    doThrow(new RuntimeException(persistErrorMessage)).when(connectorConfigUpdater).updateDestination(any(), any());

    final ReplicationWorker worker = getDefaultReplicationWorker();

    final ReplicationOutput output = worker.run(syncInput, jobRoot);
    assertEquals(ReplicationStatus.COMPLETED, output.getReplicationAttemptSummary().getStatus());

    verify(connectorConfigUpdater).updateDestination(syncInput.getDestinationId(), CONNECTOR_CONFIG);
  }

  @Test
  void testReplicationRunnableDestinationFailure() throws Exception {
    final String destinationErrorMessage = "the destination had a failure";

    doThrow(new RuntimeException(destinationErrorMessage)).when(destination).accept(any());

    final ReplicationWorker worker = getDefaultReplicationWorker();

    final ReplicationOutput output = worker.run(syncInput, jobRoot);
    assertEquals(ReplicationStatus.FAILED, output.getReplicationAttemptSummary().getStatus());
    assertTrue(output.getFailures().stream()
        .anyMatch(f -> f.getFailureOrigin().equals(FailureOrigin.DESTINATION) && f.getStacktrace().contains(destinationErrorMessage)));
  }

  @Test
  void testReplicationRunnableDestinationFailureViaTraceMessage() throws Exception {
    final FailureReason failureReason = FailureHelper.destinationFailure(ERROR_TRACE_MESSAGE, Long.valueOf(JOB_ID), JOB_ATTEMPT);
    when(messageTracker.errorTraceMessageFailure(Long.valueOf(JOB_ID), JOB_ATTEMPT)).thenReturn(failureReason);

    final ReplicationWorker worker = getDefaultReplicationWorker();

    final ReplicationOutput output = worker.run(syncInput, jobRoot);
    assertTrue(output.getFailures().stream()
        .anyMatch(f -> f.getFailureOrigin().equals(FailureOrigin.DESTINATION)
            && f.getExternalMessage().contains(ERROR_TRACE_MESSAGE.getError().getMessage())));
  }

  @Test
  void testReplicationRunnableWorkerFailure() throws Exception {
    final String workerErrorMessage = "the worker had a failure";

    doThrow(new RuntimeException(workerErrorMessage)).when(messageTracker).acceptFromSource(any());

    final ReplicationWorker worker = getDefaultReplicationWorker();

    final ReplicationOutput output = worker.run(syncInput, jobRoot);
    assertEquals(ReplicationStatus.FAILED, output.getReplicationAttemptSummary().getStatus());
    assertTrue(output.getFailures().stream()
        .anyMatch(f -> f.getFailureOrigin().equals(FailureOrigin.REPLICATION) && f.getStacktrace().contains(workerErrorMessage)));
  }

  @Test
  void testOnlyStateAndRecordMessagesDeliveredToDestination() throws Exception {
    final AirbyteMessage logMessage = AirbyteMessageUtils.createLogMessage(Level.INFO, "a log message");
    final AirbyteMessage traceMessage = AirbyteMessageUtils.createErrorMessage("a trace message", 123456.0);
    when(mapper.mapMessage(logMessage)).thenReturn(logMessage);
    when(mapper.mapMessage(traceMessage)).thenReturn(traceMessage);
    when(source.isFinished()).thenReturn(false, false, false, false, true);
    when(source.attemptRead()).thenReturn(Optional.of(RECORD_MESSAGE1), Optional.of(logMessage), Optional.of(traceMessage),
        Optional.of(RECORD_MESSAGE2));

    final ReplicationWorker worker = getDefaultReplicationWorker();

    worker.run(syncInput, jobRoot);

    verify(source).start(sourceConfig, jobRoot);
    verify(destination).start(destinationConfig, jobRoot);
    verify(destination).accept(RECORD_MESSAGE1);
    verify(destination).accept(RECORD_MESSAGE2);
    verify(destination, never()).accept(logMessage);
    verify(destination, never()).accept(traceMessage);
  }

  @Test
  void testOnlySelectedFieldsDeliveredToDestinationWithFieldSelectionEnabled() throws Exception {
    // Generate a record with an extra field.
    final AirbyteMessage recordWithExtraFields = Jsons.clone(RECORD_MESSAGE1);
    ((ObjectNode) recordWithExtraFields.getRecord().getData()).put("AnUnexpectedField", "SomeValue");
    when(mapper.mapMessage(recordWithExtraFields)).thenReturn(recordWithExtraFields);
    when(source.attemptRead()).thenReturn(Optional.of(recordWithExtraFields));
    when(source.isFinished()).thenReturn(false, true);
    // Use a real schema validator to make sure validation doesn't affect this.
    final String streamName = sourceConfig.getCatalog().getStreams().get(0).getStream().getName();
    final String streamNamespace = sourceConfig.getCatalog().getStreams().get(0).getStream().getNamespace();
    recordSchemaValidator = new RecordSchemaValidator(Map.of(new AirbyteStreamNameNamespacePair(streamName, streamNamespace),
        sourceConfig.getCatalog().getStreams().get(0).getStream().getJsonSchema()));
    final ReplicationWorker worker = getDefaultReplicationWorker(true);

    worker.run(syncInput, jobRoot);

    // Despite reading recordWithExtraFields from the source, we write the original RECORD_MESSAGE1 to
    // the destination because the new field has been filtered out.
    verify(destination).accept(RECORD_MESSAGE1);
  }

  @Test
  void testAllFieldsDeliveredWithFieldSelectionDisabled() throws Exception {
    // Generate a record with an extra field.
    final AirbyteMessage recordWithExtraFields = Jsons.clone(RECORD_MESSAGE1);
    ((ObjectNode) recordWithExtraFields.getRecord().getData()).put("AnUnexpectedField", "SomeValue");
    when(mapper.mapMessage(recordWithExtraFields)).thenReturn(recordWithExtraFields);
    when(source.attemptRead()).thenReturn(Optional.of(recordWithExtraFields));
    when(source.isFinished()).thenReturn(false, true);
    // Use a real schema validator to make sure validation doesn't affect this.
    final String streamName = sourceConfig.getCatalog().getStreams().get(0).getStream().getName();
    final String streamNamespace = sourceConfig.getCatalog().getStreams().get(0).getStream().getNamespace();
    recordSchemaValidator = new RecordSchemaValidator(Map.of(new AirbyteStreamNameNamespacePair(streamName, streamNamespace),
        sourceConfig.getCatalog().getStreams().get(0).getStream().getJsonSchema()));
    final ReplicationWorker worker = getDefaultReplicationWorker();

    worker.run(syncInput, jobRoot);

    // Despite the field not being in the catalog, we write the extra field anyway because field
    // selection is disabled.
    verify(destination).accept(recordWithExtraFields);
  }

  @Test
  void testDestinationNonZeroExitValue() throws Exception {
    when(destination.getExitValue()).thenReturn(1);

    final ReplicationWorker worker = getDefaultReplicationWorker();

    final ReplicationOutput output = worker.run(syncInput, jobRoot);
    assertEquals(ReplicationStatus.FAILED, output.getReplicationAttemptSummary().getStatus());
    assertTrue(output.getFailures().stream().anyMatch(f -> f.getFailureOrigin().equals(FailureOrigin.DESTINATION)));
  }

  @Test
  void testDestinationRunnableDestinationFailure() throws Exception {
    final String destinationErrorMessage = "the destination had a failure";

    doThrow(new RuntimeException(destinationErrorMessage)).when(destination).notifyEndOfInput();

    final ReplicationWorker worker = getDefaultReplicationWorker();

    final ReplicationOutput output = worker.run(syncInput, jobRoot);
    assertEquals(ReplicationStatus.FAILED, output.getReplicationAttemptSummary().getStatus());
    assertTrue(output.getFailures().stream()
        .anyMatch(f -> f.getFailureOrigin().equals(FailureOrigin.DESTINATION) && f.getStacktrace().contains(destinationErrorMessage)));
  }

  @Test
  void testDestinationRunnableWorkerFailure() throws Exception {
    final String workerErrorMessage = "the worker had a failure";

    doThrow(new RuntimeException(workerErrorMessage)).when(messageTracker).acceptFromDestination(any());

    final ReplicationWorker worker = getDefaultReplicationWorker();

    final ReplicationOutput output = worker.run(syncInput, jobRoot);
    assertEquals(ReplicationStatus.FAILED, output.getReplicationAttemptSummary().getStatus());
    assertTrue(output.getFailures().stream()
        .anyMatch(f -> f.getFailureOrigin().equals(FailureOrigin.REPLICATION) && f.getStacktrace().contains(workerErrorMessage)));
  }

  @Test
  void testLoggingInThreads() throws IOException, WorkerException {
    // set up the mdc so that actually log to a file, so that we can verify that file logging captures
    // threads.
    final Path jobRoot = Files.createTempDirectory(Path.of("/tmp"), "mdc_test");
    LogClientSingleton.getInstance().setJobMdc(WorkerEnvironment.DOCKER, LogConfigs.EMPTY, jobRoot);

    final ReplicationWorker worker = getDefaultReplicationWorker();

    worker.run(syncInput, jobRoot);

    final Path logPath = jobRoot.resolve(LogClientSingleton.LOG_FILENAME);
    final String logs = IOs.readFile(logPath);

    // make sure we get logs from the threads.
    assertTrue(logs.contains("Replication thread started."));
    assertTrue(logs.contains("Destination output thread started."));
  }

  @Test
  void testLogMaskRegex() throws IOException {
    final Path jobRoot = Files.createTempDirectory(Path.of("/tmp"), "mdc_test");
    MDC.put(LogClientSingleton.WORKSPACE_MDC_KEY, jobRoot.toString());

    LOGGER.info(
        "500 Server Error: Internal Server Error for url: https://api.hubapi.com/crm/v3/objects/contact?limit=100&archived=false&hapikey=secret-key_1&after=5315621");

    final Path logPath = jobRoot.resolve("logs.log");
    final String logs = IOs.readFile(logPath);
    assertTrue(logs.contains("apikey"));
    assertFalse(logs.contains("secret-key_1"));
  }

  @SuppressWarnings({"BusyWait"})
  @Test
  void testCancellation() throws InterruptedException {
    final AtomicReference<ReplicationOutput> output = new AtomicReference<>();
    when(source.isFinished()).thenReturn(false);
    when(messageTracker.getDestinationOutputState()).thenReturn(Optional.of(new State().withState(STATE_MESSAGE.getState().getData())));

    final ReplicationWorker worker = getDefaultReplicationWorker();

    final Thread workerThread = new Thread(() -> {
      try {
        output.set(worker.run(syncInput, jobRoot));
      } catch (final WorkerException e) {
        throw new RuntimeException(e);
      }
    });

    workerThread.start();

    // verify the worker is actually running before we kill it.
    while (Mockito.mockingDetails(messageTracker).getInvocations().size() < 5) {
      LOGGER.info("waiting for worker to start running");
      sleep(100);
    }

    worker.cancel();
    Assertions.assertTimeout(Duration.ofSeconds(5), (Executable) workerThread::join);
    assertNotNull(output.get());
  }

  @Test
  void testPopulatesOutputOnSuccess() throws WorkerException {
    final JsonNode expectedState = Jsons.jsonNode(ImmutableMap.of("updated_at", 10L));
    when(messageTracker.getDestinationOutputState()).thenReturn(Optional.of(new State().withState(expectedState)));
    when(syncStatsTracker.getTotalRecordsEmitted()).thenReturn(12L);
    when(syncStatsTracker.getTotalBytesEmitted()).thenReturn(100L);
    when(syncStatsTracker.getTotalSourceStateMessagesEmitted()).thenReturn(3L);
    when(syncStatsTracker.getTotalDestinationStateMessagesEmitted()).thenReturn(1L);
    when(syncStatsTracker.getStreamToEmittedBytes())
        .thenReturn(Collections.singletonMap(new AirbyteStreamNameNamespacePair(STREAM1, NAMESPACE), 100L));
    when(syncStatsTracker.getStreamToEmittedRecords())
        .thenReturn(Collections.singletonMap(new AirbyteStreamNameNamespacePair(STREAM1, NAMESPACE), 12L));
    when(syncStatsTracker.getMaxSecondsToReceiveSourceStateMessage()).thenReturn(5L);
    when(syncStatsTracker.getMeanSecondsToReceiveSourceStateMessage()).thenReturn(4L);
    when(syncStatsTracker.getMaxSecondsBetweenStateMessageEmittedAndCommitted()).thenReturn(Optional.of(6L));
    when(syncStatsTracker.getMeanSecondsBetweenStateMessageEmittedAndCommitted()).thenReturn(Optional.of(3L));

    final ReplicationWorker worker = getDefaultReplicationWorker();

    final ReplicationOutput actual = worker.run(syncInput, jobRoot);
    final ReplicationOutput replicationOutput = new ReplicationOutput()
        .withReplicationAttemptSummary(new ReplicationAttemptSummary()
            .withRecordsSynced(12L)
            .withBytesSynced(100L)
            .withStatus(ReplicationStatus.COMPLETED)
            .withTotalStats(new SyncStats()
                .withRecordsEmitted(12L)
                .withBytesEmitted(100L)
                .withSourceStateMessagesEmitted(3L)
                .withDestinationStateMessagesEmitted(1L)
                .withMaxSecondsBeforeSourceStateMessageEmitted(5L)
                .withMeanSecondsBeforeSourceStateMessageEmitted(4L)
                .withMaxSecondsBetweenStateMessageEmittedandCommitted(6L)
                .withMeanSecondsBetweenStateMessageEmittedandCommitted(3L)
                .withBytesCommitted(100L)
                .withRecordsCommitted(12L)) // since success, should use emitted count
            .withStreamStats(Collections.singletonList(
                new StreamSyncStats()
                    .withStreamName(STREAM1)
                    .withStreamNamespace(NAMESPACE)
                    .withStats(new SyncStats()
                        .withBytesEmitted(100L)
                        .withRecordsEmitted(12L)
                        .withBytesCommitted(100L)
                        .withRecordsCommitted(12L) // since success, should use emitted count
                        .withSourceStateMessagesEmitted(null)
                        .withDestinationStateMessagesEmitted(null)
                        .withMaxSecondsBeforeSourceStateMessageEmitted(null)
                        .withMeanSecondsBeforeSourceStateMessageEmitted(null)
                        .withMaxSecondsBetweenStateMessageEmittedandCommitted(null)
                        .withMeanSecondsBetweenStateMessageEmittedandCommitted(null)))))
        .withOutputCatalog(syncInput.getCatalog());

    // good enough to verify that times are present.
    assertNotNull(actual.getReplicationAttemptSummary().getStartTime());
    assertNotNull(actual.getReplicationAttemptSummary().getEndTime());

    // verify output object matches declared json schema spec.
    final Set<String> validate = new JsonSchemaValidator()
        .validate(Jsons.jsonNode(Jsons.jsonNode(JsonSchemaValidator.getSchema(ConfigSchema.REPLICATION_OUTPUT.getConfigSchemaFile()))),
            Jsons.jsonNode(actual));
    assertTrue(validate.isEmpty(), "Validation errors: " + Strings.join(validate, ","));

    // remove times, so we can do the rest of the object <> object comparison.
    actual.getReplicationAttemptSummary().withStartTime(null).withEndTime(null).getTotalStats().withReplicationStartTime(null)
        .withReplicationEndTime(null)
        .withSourceReadStartTime(null).withSourceReadEndTime(null)
        .withDestinationWriteStartTime(null).withDestinationWriteEndTime(null);

    assertEquals(replicationOutput, actual);
  }

  @Test
  void testPopulatesStatsOnFailureIfAvailable() throws Exception {
    doThrow(new IllegalStateException(INDUCED_EXCEPTION)).when(source).close();
    when(syncStatsTracker.getTotalRecordsEmitted()).thenReturn(12L);
    when(syncStatsTracker.getTotalBytesEmitted()).thenReturn(100L);
    when(syncStatsTracker.getTotalBytesCommitted()).thenReturn(Optional.of(12L));
    when(syncStatsTracker.getTotalRecordsCommitted()).thenReturn(Optional.of(6L));
    when(syncStatsTracker.getTotalSourceStateMessagesEmitted()).thenReturn(3L);
    when(syncStatsTracker.getTotalDestinationStateMessagesEmitted()).thenReturn(2L);
    when(syncStatsTracker.getStreamToEmittedBytes())
        .thenReturn(Collections.singletonMap(new AirbyteStreamNameNamespacePair(STREAM1, NAMESPACE), 100L));
    when(syncStatsTracker.getStreamToEmittedRecords())
        .thenReturn(Collections.singletonMap(new AirbyteStreamNameNamespacePair(STREAM1, NAMESPACE), 12L));
    when(syncStatsTracker.getStreamToCommittedRecords())
        .thenReturn(Optional.of(Collections.singletonMap(new AirbyteStreamNameNamespacePair(STREAM1, NAMESPACE), 6L)));
    when(syncStatsTracker.getStreamToCommittedBytes())
        .thenReturn(Optional.of(Collections.singletonMap(new AirbyteStreamNameNamespacePair(STREAM1, NAMESPACE), 13L)));
    when(syncStatsTracker.getMaxSecondsToReceiveSourceStateMessage()).thenReturn(10L);
    when(syncStatsTracker.getMeanSecondsToReceiveSourceStateMessage()).thenReturn(8L);
    when(syncStatsTracker.getMaxSecondsBetweenStateMessageEmittedAndCommitted()).thenReturn(Optional.of(12L));
    when(syncStatsTracker.getMeanSecondsBetweenStateMessageEmittedAndCommitted()).thenReturn(Optional.of(11L));

    final ReplicationWorker worker = getDefaultReplicationWorker();

    final ReplicationOutput actual = worker.run(syncInput, jobRoot);
    final SyncStats expectedTotalStats = new SyncStats()
        .withRecordsEmitted(12L)
        .withBytesEmitted(100L)
        .withSourceStateMessagesEmitted(3L)
        .withDestinationStateMessagesEmitted(2L)
        .withMaxSecondsBeforeSourceStateMessageEmitted(10L)
        .withMeanSecondsBeforeSourceStateMessageEmitted(8L)
        .withMaxSecondsBetweenStateMessageEmittedandCommitted(12L)
        .withMeanSecondsBetweenStateMessageEmittedandCommitted(11L)
        .withBytesCommitted(12L)
        .withRecordsCommitted(6L);
    final List<StreamSyncStats> expectedStreamStats = Collections.singletonList(
        new StreamSyncStats()
            .withStreamName(STREAM1)
            .withStreamNamespace(NAMESPACE)
            .withStats(new SyncStats()
                .withBytesEmitted(100L)
                .withRecordsEmitted(12L)
                .withBytesCommitted(13L)
                .withRecordsCommitted(6L)
                .withSourceStateMessagesEmitted(null)
                .withDestinationStateMessagesEmitted(null)));

    assertNotNull(actual);
    // null out timing stats for assertion matching
    assertEquals(expectedTotalStats, actual.getReplicationAttemptSummary().getTotalStats().withReplicationStartTime(null).withReplicationEndTime(null)
        .withSourceReadStartTime(null).withSourceReadEndTime(null).withDestinationWriteStartTime(null).withDestinationWriteEndTime(null));
    assertEquals(expectedStreamStats, actual.getReplicationAttemptSummary().getStreamStats());
  }

  @Test
  void testDoesNotPopulatesStateOnFailureIfNotAvailable() throws Exception {
    final StandardSyncInput syncInputWithoutState = Jsons.clone(syncInput);
    syncInputWithoutState.setState(null);

    doThrow(new IllegalStateException(INDUCED_EXCEPTION)).when(source).close();

    final ReplicationWorker worker = getDefaultReplicationWorker();

    final ReplicationOutput actual = worker.run(syncInputWithoutState, jobRoot);

    assertNotNull(actual);
    assertNull(actual.getState());
  }

  @Test
  void testDoesNotPopulateOnIrrecoverableFailure() {
    doThrow(new IllegalStateException(INDUCED_EXCEPTION)).when(syncStatsTracker).getTotalRecordsEmitted();

    final ReplicationWorker worker = getDefaultReplicationWorker();
    assertThrows(WorkerException.class, () -> worker.run(syncInput, jobRoot));
  }

  @Test
  void testSourceFailingTimeout() throws Exception {
    final HeartbeatMonitor heartbeatMonitor = mock(HeartbeatMonitor.class);
    when(heartbeatMonitor.isBeating()).thenReturn(Optional.of(false));
    final UUID connectionId = UUID.randomUUID();
    final MetricClient mMetricClient = mock(MetricClient.class);
    heartbeatTimeoutChaperone =
        new HeartbeatTimeoutChaperone(heartbeatMonitor, Duration.ofMillis(1), new TestClient(Map.of("heartbeat.failSync", true)), UUID.randomUUID(),
            connectionId, mMetricClient);
    source = mock(AirbyteSource.class);
    when(source.isFinished()).thenReturn(false);
    when(source.attemptRead()).thenAnswer((Answer<Optional<AirbyteMessage>>) invocation -> {
      sleep(100);
      return Optional.of(RECORD_MESSAGE1);
    });

    final ReplicationWorker worker = getDefaultReplicationWorker();

    final ReplicationOutput actual = worker.run(syncInput, jobRoot);

    verify(mMetricClient).count(OssMetricsRegistry.SOURCE_HEARTBEAT_FAILURE, 1,
        new MetricAttribute(MetricTags.CONNECTION_ID, connectionId.toString()));
    assertEquals(1, actual.getFailures().size());
    assertEquals(FailureOrigin.SOURCE, actual.getFailures().get(0).getFailureOrigin());
    assertEquals(FailureReason.FailureType.HEARTBEAT_TIMEOUT, actual.getFailures().get(0).getFailureType());
  }

  @Test
  void testGetFailureReason() {
    final long jobId = 1;
    final int attempt = 1;
    FailureReason failureReason = ReplicationWorkerHelper.getFailureReason(new SourceException(""), jobId, attempt);
    assertEquals(failureReason.getFailureOrigin(), FailureOrigin.SOURCE);
    failureReason = ReplicationWorkerHelper.getFailureReason(new DestinationException(""), jobId, attempt);
    assertEquals(failureReason.getFailureOrigin(), FailureOrigin.DESTINATION);
    failureReason = ReplicationWorkerHelper.getFailureReason(new HeartbeatTimeoutChaperone.HeartbeatTimeoutException(""), jobId, attempt);
    assertEquals(failureReason.getFailureOrigin(), FailureOrigin.SOURCE);
    assertEquals(failureReason.getFailureType(), FailureReason.FailureType.HEARTBEAT_TIMEOUT);
    failureReason = ReplicationWorkerHelper.getFailureReason(new RuntimeException(), jobId, attempt);
    assertEquals(failureReason.getFailureOrigin(), FailureOrigin.REPLICATION);
  }

  ReplicationWorker getDefaultReplicationWorker() {
    return getDefaultReplicationWorker(false);
  }

  private ReplicationWorker getDefaultReplicationWorker(final boolean fieldSelectionEnabled) {
    final FieldSelector fieldSelector = new FieldSelector(recordSchemaValidator, workerMetricReporter, fieldSelectionEnabled, false);
    return new DefaultReplicationWorker(
        JOB_ID,
        JOB_ATTEMPT,
        source,
        mapper,
        destination,
        messageTracker,
        syncPersistence,
        recordSchemaValidator,
        fieldSelector,
        connectorConfigUpdater,
        heartbeatTimeoutChaperone,
        new ReplicationFeatureFlagReader(featureFlagClient),
        airbyteMessageDataExtractor,
        replicationAirbyteMessageEventPublishingHelper);
  }

  private ReplicationContext simpleContext(final boolean isReset) {
    return new ReplicationContext(
        isReset,
        syncInput.getConnectionId(),
        syncInput.getSourceId(),
        syncInput.getDestinationId(),
        Long.valueOf(JOB_ID),
        JOB_ATTEMPT,
        syncInput.getWorkspaceId());
  }

}
