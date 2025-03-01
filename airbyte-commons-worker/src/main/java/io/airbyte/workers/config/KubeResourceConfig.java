/*
 * Copyright (c) 2023 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.workers.config;

import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;

/**
 * Encapsulates the configuration that is specific to Kubernetes. This is meant for the
 * WorkerConfigsProvider to be reading configs, not for direct use as fallback logic isn't
 * implemented here.
 */
@EachProperty("airbyte.worker.kube-job-configs")
final class KubeResourceConfig {

  private final String name;
  private String annotations;
  private String nodeSelectors;
  private String cpuLimit;
  private String cpuRequest;
  private String memoryLimit;
  private String memoryRequest;

  public KubeResourceConfig(@Parameter final String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }

  public String getAnnotations() {
    return annotations;
  }

  public String getNodeSelectors() {
    return nodeSelectors;
  }

  public String getCpuLimit() {
    return cpuLimit;
  }

  public String getCpuRequest() {
    return cpuRequest;
  }

  public String getMemoryLimit() {
    return memoryLimit;
  }

  public String getMemoryRequest() {
    return memoryRequest;
  }

  public void setAnnotations(String annotations) {
    this.annotations = annotations;
  }

  public void setNodeSelectors(String nodeSelectors) {
    this.nodeSelectors = nodeSelectors;
  }

  public void setCpuLimit(String cpuLimit) {
    this.cpuLimit = cpuLimit;
  }

  public void setCpuRequest(String cpuRequest) {
    this.cpuRequest = cpuRequest;
  }

  public void setMemoryLimit(String memoryLimit) {
    this.memoryLimit = memoryLimit;
  }

  public void setMemoryRequest(String memoryRequest) {
    this.memoryRequest = memoryRequest;
  }

}
