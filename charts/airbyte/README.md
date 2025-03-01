# airbyte

![Version: 0.44.11](https://img.shields.io/badge/Version-0.44.11-informational?style=flat-square) ![Type: application](https://img.shields.io/badge/Type-application-informational?style=flat-square) ![AppVersion: 0.44.11](https://img.shields.io/badge/AppVersion-0.44.11-informational?style=flat-square)

Helm chart to deploy airbyte

## Requirements

| Repository | Name | Version |
|------------|------|---------|
| https://airbytehq.github.io/helm-charts/ | airbyte-bootloader | 0.44.11 |
| https://airbytehq.github.io/helm-charts/ | connector-builder-server | 0.44.11 |
| https://airbytehq.github.io/helm-charts/ | cron | 0.44.11 |
| https://airbytehq.github.io/helm-charts/ | metrics | 0.44.11 |
| https://airbytehq.github.io/helm-charts/ | pod-sweeper | 0.44.11 |
| https://airbytehq.github.io/helm-charts/ | server | 0.44.11 |
| https://airbytehq.github.io/helm-charts/ | temporal | 0.44.11 |
| https://airbytehq.github.io/helm-charts/ | webapp | 0.44.11 |
| https://airbytehq.github.io/helm-charts/ | worker | 0.44.11 |
| https://charts.bitnami.com/bitnami | common | 1.x.x |

## Values

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| airbyte-bootloader.affinity | object | `{}` |  |
| airbyte-bootloader.enabled | bool | `true` |  |
| airbyte-bootloader.env_vars | object | `{}` |  |
| airbyte-bootloader.extraContainers | list | `[]` |  |
| airbyte-bootloader.extraEnv | list | `[]` |  |
| airbyte-bootloader.extraInitContainers | list | `[]` |  |
| airbyte-bootloader.extraVolumeMounts | list | `[]` |  |
| airbyte-bootloader.extraVolumes | list | `[]` |  |
| airbyte-bootloader.image.pullPolicy | string | `"IfNotPresent"` |  |
| airbyte-bootloader.image.repository | string | `"airbyte/bootloader"` |  |
| airbyte-bootloader.nodeSelector | object | `{}` |  |
| airbyte-bootloader.podAnnotations | object | `{}` |  |
| airbyte-bootloader.podLabels | object | `{}` |  |
| airbyte-bootloader.resources.limits | object | `{}` |  |
| airbyte-bootloader.resources.requests | object | `{}` |  |
| airbyte-bootloader.secrets | object | `{}` |  |
| airbyte-bootloader.tolerations | list | `[]` |  |
| connector-builder-server.enabled | bool | `true` |  |
| connector-builder-server.env_vars | object | `{}` |  |
| connector-builder-server.image.pullPolicy | string | `"IfNotPresent"` |  |
| connector-builder-server.image.repository | string | `"airbyte/connector-atelier-server"` |  |
| connector-builder-server.log.level | string | `"INFO"` |  |
| connector-builder-server.replicaCount | int | `1` |  |
| connector-builder-server.resources.limits | object | `{}` |  |
| connector-builder-server.resources.requests | object | `{}` |  |
| connector-builder-server.service.port | int | `80` |  |
| cron.affinity | object | `{}` |  |
| cron.containerSecurityContext | object | `{}` |  |
| cron.enabled | bool | `true` |  |
| cron.env_vars | object | `{}` |  |
| cron.extraContainers | list | `[]` |  |
| cron.extraEnv | list | `[]` |  |
| cron.extraInitContainers | list | `[]` |  |
| cron.extraVolumeMounts | list | `[]` |  |
| cron.extraVolumes | list | `[]` |  |
| cron.image.pullPolicy | string | `"IfNotPresent"` |  |
| cron.image.repository | string | `"airbyte/cron"` |  |
| cron.livenessProbe.enabled | bool | `true` |  |
| cron.livenessProbe.failureThreshold | int | `3` |  |
| cron.livenessProbe.initialDelaySeconds | int | `30` |  |
| cron.livenessProbe.periodSeconds | int | `10` |  |
| cron.livenessProbe.successThreshold | int | `1` |  |
| cron.livenessProbe.timeoutSeconds | int | `1` |  |
| cron.log.level | string | `"INFO"` |  |
| cron.nodeSelector | object | `{}` |  |
| cron.podAnnotations | object | `{}` |  |
| cron.podLabels | object | `{}` |  |
| cron.readinessProbe.enabled | bool | `true` |  |
| cron.readinessProbe.failureThreshold | int | `3` |  |
| cron.readinessProbe.initialDelaySeconds | int | `10` |  |
| cron.readinessProbe.periodSeconds | int | `10` |  |
| cron.readinessProbe.successThreshold | int | `1` |  |
| cron.readinessProbe.timeoutSeconds | int | `1` |  |
| cron.replicaCount | int | `1` |  |
| cron.resources.limits | object | `{}` |  |
| cron.resources.requests | object | `{}` |  |
| cron.secrets | object | `{}` |  |
| cron.tolerations | list | `[]` |  |
| externalDatabase.database | string | `"db-airbyte"` |  |
| externalDatabase.existingSecret | string | `""` |  |
| externalDatabase.existingSecretPasswordKey | string | `""` |  |
| externalDatabase.host | string | `"localhost"` |  |
| externalDatabase.jdbcUrl | string | `""` |  |
| externalDatabase.password | string | `""` |  |
| externalDatabase.port | int | `5432` |  |
| externalDatabase.user | string | `"airbyte"` |  |
| fullnameOverride | string | `""` |  |
| global.database.host | string | `"example.com"` |  |
| global.database.port | string | `"5432"` |  |
| global.database.secretName | string | `""` |  |
| global.database.secretValue | string | `""` |  |
| global.deploymentMode | string | `"oss"` |  |
| global.jobs.kube.annotations | object | `{}` |  |
| global.jobs.kube.images.busybox | string | `""` |  |
| global.jobs.kube.images.curl | string | `""` |  |
| global.jobs.kube.images.socat | string | `""` |  |
| global.jobs.kube.main_container_image_pull_secret | string | `""` |  |
| global.jobs.kube.nodeSelector | object | `{}` |  |
| global.jobs.kube.tolerations | list | `[]` |  |
| global.jobs.resources.limits | object | `{}` |  |
| global.jobs.resources.requests | object | `{}` |  |
| global.logs.accessKey.existingSecret | string | `""` |  |
| global.logs.accessKey.existingSecretKey | string | `""` |  |
| global.logs.accessKey.password | string | `""` |  |
| global.logs.externalMinio.enabled | bool | `false` |  |
| global.logs.externalMinio.host | string | `"localhost"` |  |
| global.logs.externalMinio.port | int | `9000` |  |
| global.logs.gcs.bucket | string | `""` |  |
| global.logs.gcs.credentials | string | `""` |  |
| global.logs.gcs.credentialsJson | string | `""` |  |
| global.logs.minio.enabled | bool | `true` |  |
| global.logs.s3.bucket | string | `"airbyte-dev-logs"` |  |
| global.logs.s3.bucketRegion | string | `""` |  |
| global.logs.s3.enabled | bool | `false` |  |
| global.logs.secretKey.existingSecret | string | `""` |  |
| global.logs.secretKey.existingSecretKey | string | `""` |  |
| global.logs.secretKey.password | string | `""` |  |
| global.logs.storage.type | string | `"MINIO"` |  |
| global.metrics.metricClient | string | `""` |  |
| global.metrics.otelCollectorEndpoint | string | `""` |  |
| global.serviceAccountName | string | `"airbyte-admin"` |  |
| global.state.storage.type | string | `"MINIO"` |  |
| metrics.affinity | object | `{}` |  |
| metrics.containerSecurityContext | object | `{}` |  |
| metrics.enabled | bool | `false` |  |
| metrics.env_vars | object | `{}` |  |
| metrics.extraContainers | list | `[]` |  |
| metrics.extraEnv | list | `[]` |  |
| metrics.extraVolumeMounts | list | `[]` |  |
| metrics.extraVolumes | list | `[]` |  |
| metrics.image.pullPolicy | string | `"IfNotPresent"` |  |
| metrics.image.repository | string | `"airbyte/metrics-reporter"` |  |
| metrics.nodeSelector | object | `{}` |  |
| metrics.podAnnotations | object | `{}` |  |
| metrics.podLabels | object | `{}` |  |
| metrics.replicaCount | int | `1` |  |
| metrics.resources.limits | object | `{}` |  |
| metrics.resources.requests | object | `{}` |  |
| metrics.secrets | object | `{}` |  |
| metrics.tolerations | list | `[]` |  |
| minio.auth.rootPassword | string | `"minio123"` |  |
| minio.auth.rootUser | string | `"minio"` |  |
| minio.enabled | bool | `true` |  |
| minio.image.repository | string | `"minio/minio"` |  |
| minio.image.tag | string | `"latest"` |  |
| nameOverride | string | `""` |  |
| pod-sweeper.affinity | object | `{}` |  |
| pod-sweeper.containerSecurityContext | object | `{}` |  |
| pod-sweeper.enabled | bool | `true` |  |
| pod-sweeper.extraVolumeMounts | list | `[]` |  |
| pod-sweeper.extraVolumes | list | `[]` |  |
| pod-sweeper.image.pullPolicy | string | `"IfNotPresent"` |  |
| pod-sweeper.image.repository | string | `"bitnami/kubectl"` |  |
| pod-sweeper.image.tag | string | `"latest"` |  |
| pod-sweeper.livenessProbe.enabled | bool | `true` |  |
| pod-sweeper.livenessProbe.failureThreshold | int | `3` |  |
| pod-sweeper.livenessProbe.initialDelaySeconds | int | `5` |  |
| pod-sweeper.livenessProbe.periodSeconds | int | `30` |  |
| pod-sweeper.livenessProbe.successThreshold | int | `1` |  |
| pod-sweeper.livenessProbe.timeoutSeconds | int | `1` |  |
| pod-sweeper.nodeSelector | object | `{}` |  |
| pod-sweeper.podAnnotations | object | `{}` |  |
| pod-sweeper.podLabels | object | `{}` |  |
| pod-sweeper.readinessProbe.enabled | bool | `true` |  |
| pod-sweeper.readinessProbe.failureThreshold | int | `3` |  |
| pod-sweeper.readinessProbe.initialDelaySeconds | int | `5` |  |
| pod-sweeper.readinessProbe.periodSeconds | int | `30` |  |
| pod-sweeper.readinessProbe.successThreshold | int | `1` |  |
| pod-sweeper.readinessProbe.timeoutSeconds | int | `1` |  |
| pod-sweeper.resources.limits | object | `{}` |  |
| pod-sweeper.resources.requests | object | `{}` |  |
| pod-sweeper.tolerations | list | `[]` |  |
| postgresql.commonAnnotations."helm.sh/hook" | string | `"pre-install,pre-upgrade"` |  |
| postgresql.commonAnnotations."helm.sh/hook-weight" | string | `"-1"` |  |
| postgresql.containerSecurityContext.runAsNonRoot | bool | `true` |  |
| postgresql.enabled | bool | `true` |  |
| postgresql.existingSecret | string | `""` |  |
| postgresql.image.repository | string | `"airbyte/db"` |  |
| postgresql.postgresqlDatabase | string | `"db-airbyte"` |  |
| postgresql.postgresqlPassword | string | `"airbyte"` |  |
| postgresql.postgresqlUsername | string | `"airbyte"` |  |
| server.affinity | object | `{}` |  |
| server.containerSecurityContext | object | `{}` |  |
| server.enabled | bool | `true` |  |
| server.env_vars | object | `{}` |  |
| server.extraContainers | list | `[]` |  |
| server.extraEnv | list | `[]` |  |
| server.extraInitContainers | list | `[]` |  |
| server.extraVolumeMounts | list | `[]` |  |
| server.extraVolumes | list | `[]` |  |
| server.image.pullPolicy | string | `"IfNotPresent"` |  |
| server.image.repository | string | `"airbyte/server"` |  |
| server.livenessProbe.enabled | bool | `true` |  |
| server.livenessProbe.failureThreshold | int | `3` |  |
| server.livenessProbe.initialDelaySeconds | int | `30` |  |
| server.livenessProbe.periodSeconds | int | `10` |  |
| server.livenessProbe.successThreshold | int | `1` |  |
| server.livenessProbe.timeoutSeconds | int | `1` |  |
| server.log.level | string | `"INFO"` |  |
| server.nodeSelector | object | `{}` |  |
| server.podAnnotations | object | `{}` |  |
| server.podLabels | object | `{}` |  |
| server.readinessProbe.enabled | bool | `true` |  |
| server.readinessProbe.failureThreshold | int | `3` |  |
| server.readinessProbe.initialDelaySeconds | int | `10` |  |
| server.readinessProbe.periodSeconds | int | `10` |  |
| server.readinessProbe.successThreshold | int | `1` |  |
| server.readinessProbe.timeoutSeconds | int | `1` |  |
| server.replicaCount | int | `1` |  |
| server.resources.limits | object | `{}` |  |
| server.resources.requests | object | `{}` |  |
| server.secrets | object | `{}` |  |
| server.tolerations | list | `[]` |  |
| serviceAccount.annotations | object | `{}` |  |
| serviceAccount.create | bool | `true` |  |
| serviceAccount.name | string | `"airbyte-admin"` |  |
| temporal.affinity | object | `{}` |  |
| temporal.containerSecurityContext | object | `{}` |  |
| temporal.enabled | bool | `true` |  |
| temporal.extraContainers | list | `[]` |  |
| temporal.extraEnv | list | `[]` |  |
| temporal.extraInitContainers | list | `[]` |  |
| temporal.extraVolumeMounts | list | `[]` |  |
| temporal.extraVolumes | list | `[]` |  |
| temporal.image.pullPolicy | string | `"IfNotPresent"` |  |
| temporal.image.repository | string | `"airbyte/temporal-auto-setup"` |  |
| temporal.image.tag | string | `"1.13.0"` |  |
| temporal.livenessProbe.enabled | bool | `false` |  |
| temporal.nodeSelector | object | `{}` |  |
| temporal.podAnnotations | object | `{}` |  |
| temporal.podLabels | object | `{}` |  |
| temporal.readinessProbe.enabled | bool | `false` |  |
| temporal.replicaCount | int | `1` |  |
| temporal.resources.limits | object | `{}` |  |
| temporal.resources.requests | object | `{}` |  |
| temporal.service.port | int | `7233` |  |
| temporal.service.type | string | `"ClusterIP"` |  |
| temporal.tolerations | list | `[]` |  |
| version | string | `""` |  |
| webapp.affinity | object | `{}` |  |
| webapp.api.url | string | `"/api/v1/"` |  |
| webapp.connector-builder-server.url | string | `"/connector-builder-api"` |  |
| webapp.containerSecurityContext | object | `{}` |  |
| webapp.enabled | bool | `true` |  |
| webapp.env_vars | object | `{}` |  |
| webapp.extraContainers | list | `[]` |  |
| webapp.extraEnv | list | `[]` |  |
| webapp.extraInitContainers | list | `[]` |  |
| webapp.extraVolumeMounts | list | `[]` |  |
| webapp.extraVolumes | list | `[]` |  |
| webapp.fullstory.enabled | bool | `false` |  |
| webapp.image.pullPolicy | string | `"IfNotPresent"` |  |
| webapp.image.repository | string | `"airbyte/webapp"` |  |
| webapp.ingress.annotations | object | `{}` |  |
| webapp.ingress.className | string | `""` |  |
| webapp.ingress.enabled | bool | `false` |  |
| webapp.ingress.hosts | list | `[]` |  |
| webapp.ingress.tls | list | `[]` |  |
| webapp.livenessProbe.enabled | bool | `true` |  |
| webapp.livenessProbe.failureThreshold | int | `3` |  |
| webapp.livenessProbe.initialDelaySeconds | int | `30` |  |
| webapp.livenessProbe.periodSeconds | int | `10` |  |
| webapp.livenessProbe.successThreshold | int | `1` |  |
| webapp.livenessProbe.timeoutSeconds | int | `1` |  |
| webapp.nodeSelector | object | `{}` |  |
| webapp.podAnnotations | object | `{}` |  |
| webapp.podLabels | object | `{}` |  |
| webapp.readinessProbe.enabled | bool | `true` |  |
| webapp.readinessProbe.failureThreshold | int | `3` |  |
| webapp.readinessProbe.initialDelaySeconds | int | `10` |  |
| webapp.readinessProbe.periodSeconds | int | `10` |  |
| webapp.readinessProbe.successThreshold | int | `1` |  |
| webapp.readinessProbe.timeoutSeconds | int | `1` |  |
| webapp.replicaCount | int | `1` |  |
| webapp.resources.limits | object | `{}` |  |
| webapp.resources.requests | object | `{}` |  |
| webapp.secrets | object | `{}` |  |
| webapp.service.annotations | object | `{}` |  |
| webapp.service.port | int | `80` |  |
| webapp.service.type | string | `"ClusterIP"` |  |
| webapp.tolerations | list | `[]` |  |
| worker.activityInitialDelayBetweenAttemptsSeconds | string | `""` |  |
| worker.activityMaxAttempt | string | `""` |  |
| worker.activityMaxDelayBetweenAttemptsSeconds | string | `""` |  |
| worker.affinity | object | `{}` |  |
| worker.containerOrchestrator.enabled | bool | `true` |  |
| worker.containerOrchestrator.image | string | `""` |  |
| worker.containerSecurityContext | object | `{}` |  |
| worker.enabled | bool | `true` |  |
| worker.extraContainers | list | `[]` |  |
| worker.extraEnv | list | `[]` |  |
| worker.extraVolumeMounts | list | `[]` |  |
| worker.extraVolumes | list | `[]` |  |
| worker.hpa.enabled | bool | `false` |  |
| worker.image.pullPolicy | string | `"IfNotPresent"` |  |
| worker.image.repository | string | `"airbyte/worker"` |  |
| worker.livenessProbe.enabled | bool | `true` |  |
| worker.livenessProbe.failureThreshold | int | `3` |  |
| worker.livenessProbe.initialDelaySeconds | int | `30` |  |
| worker.livenessProbe.periodSeconds | int | `10` |  |
| worker.livenessProbe.successThreshold | int | `1` |  |
| worker.livenessProbe.timeoutSeconds | int | `1` |  |
| worker.log.level | string | `"INFO"` |  |
| worker.maxNotifyWorkers | int | `5` |  |
| worker.nodeSelector | object | `{}` |  |
| worker.podAnnotations | object | `{}` |  |
| worker.podLabels | object | `{}` |  |
| worker.readinessProbe.enabled | bool | `true` |  |
| worker.readinessProbe.failureThreshold | int | `3` |  |
| worker.readinessProbe.initialDelaySeconds | int | `10` |  |
| worker.readinessProbe.periodSeconds | int | `10` |  |
| worker.readinessProbe.successThreshold | int | `1` |  |
| worker.readinessProbe.timeoutSeconds | int | `1` |  |
| worker.replicaCount | int | `1` |  |
| worker.resources.limits | object | `{}` |  |
| worker.resources.requests | object | `{}` |  |
| worker.tolerations | list | `[]` |  |

----------------------------------------------
Autogenerated from chart metadata using [helm-docs v1.11.0](https://github.com/norwoodj/helm-docs/releases/v1.11.0)
