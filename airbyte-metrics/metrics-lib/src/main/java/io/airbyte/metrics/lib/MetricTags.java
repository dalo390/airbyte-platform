/*
 * Copyright (c) 2023 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.metrics.lib;

import io.airbyte.config.FailureReason.FailureOrigin;
import io.airbyte.config.FailureReason.FailureType;
import io.airbyte.db.instance.configs.jooq.generated.enums.ReleaseStage;
import io.airbyte.db.instance.jobs.jooq.generated.enums.JobStatus;

/**
 * Keep track of all metric tags.
 */
public class MetricTags {

  public static final String ATTEMPT_QUEUE = "attempt_queue";
  public static final String AUTHENTICATION_RESPONSE = "authentication_response";
  public static final String AUTHENTICATION_RESPONSE_FAILURE_REASON = "authentication_response_failure_reason";
  public static final String AUTHENTICATION_REQUEST_URI_ATTRIBUTE_KEY = "request_uri";
  public static final String CONNECTION_ID = "connection_id";
  public static final String FAILURE_ORIGIN = "failure_origin";
  public static final String FAILURE_TYPE = "failure_type";
  public static final String GEOGRAPHY = "geography";
  public static final String JOB_ID = "job_id";
  public static final String JOB_STATUS = "job_status";
  public static final String RELEASE_STAGE = "release_stage";
  public static final String RESET_WORKFLOW_FAILURE_CAUSE = "failure_cause";
  public static final String UNKNOWN = "unknown";
  public static final String WORKFLOW_TYPE = "workflow_type";
  public static final String WORKSPACE_ID = "workspace_id";

  // the release stage of the highest release connector in the sync (GA > Beta > Alpha)
  public static final String MAX_CONNECTOR_RELEASE_STATE = "max_connector_release_stage";
  // the release stage of the lowest release stage connector in the sync (GA > Beta > Alpha)
  public static final String MIN_CONNECTOR_RELEASE_STATE = "min_connector_release_stage";
  public static final String ATTEMPT_OUTCOME = "attempt_outcome"; // succeeded|failed
  public static final String ATTEMPT_NUMBER = "attempt_number"; // 0|1|2|3
  public static final String ATTEMPT_ID = "attempt_id"; // the row id of the attempt
  public static final String IS_CUSTOM_CONNECTOR_SYNC = "is_custom_connector_sync";
  public static final String USER_TYPE = "user_type"; // real user, service account, data plane user, etc
  public static final String SOURCE_ID = "source_id";

  public static String getReleaseStage(final ReleaseStage stage) {
    return stage != null ? stage.getLiteral() : UNKNOWN;
  }

  public static String getFailureOrigin(final FailureOrigin origin) {
    return origin != null ? origin.value() : FailureOrigin.UNKNOWN.value();
  }

  public static String getFailureType(final FailureType origin) {
    return origin != null ? origin.value() : UNKNOWN;
  }

  public static String getJobStatus(final JobStatus status) {
    return status != null ? status.getLiteral() : UNKNOWN;
  }

}
