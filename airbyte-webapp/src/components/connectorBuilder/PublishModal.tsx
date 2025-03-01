import { yupResolver } from "@hookform/resolvers/yup";
import { useEffect, useMemo } from "react";
import { FormProvider, useForm, useFormContext } from "react-hook-form";
import { FormattedMessage } from "react-intl";
import * as yup from "yup";

import { Button } from "components/ui/Button";
import { FlexContainer, FlexItem } from "components/ui/Flex";
import { Modal, ModalBody, ModalFooter } from "components/ui/Modal";
import { Spinner } from "components/ui/Spinner";

import { DeclarativeComponentSchema } from "core/request/ConnectorManifest";
import { Action, Namespace } from "core/services/analytics";
import { useAnalyticsService } from "core/services/analytics";
import { useNotificationService } from "hooks/services/Notification";
import {
  useListVersions,
  usePublishProject,
  useReleaseNewVersion,
} from "services/connectorBuilder/ConnectorBuilderProjectsService";
import { useConnectorBuilderFormState } from "services/connectorBuilder/ConnectorBuilderStateService";

import { BuilderField } from "./Builder/BuilderField";
import { ConnectorImage } from "./ConnectorImage";
import styles from "./PublishModal.module.scss";
import { useBuilderWatch } from "./types";

const NOTIFICATION_ID = "connectorBuilder.publish";

export const PublishModal: React.FC<{ onClose: () => void }> = ({ onClose }) => {
  const analyticsService = useAnalyticsService();
  const { registerNotification, unregisterNotificationById } = useNotificationService();
  const { projectId, lastValidJsonManifest, currentProject } = useConnectorBuilderFormState();
  const { data: versions, isLoading: isLoadingVersions } = useListVersions(currentProject);
  const { mutateAsync: sendPublishRequest } = usePublishProject();
  const { mutateAsync: sendNewVersionRequest } = useReleaseNewVersion();
  const connectorName = useBuilderWatch("global.connectorName");
  const { setValue } = useFormContext();

  const minVersion = versions && versions.length > 0 ? Math.max(...versions.map((version) => version.version)) + 1 : 1;

  const initialValues = useMemo(
    () => ({
      name: connectorName,
      description: "",
      useVersion: true,
      version: minVersion,
    }),
    [connectorName, minVersion]
  );

  const schema = useMemo(
    () =>
      yup.object().shape({
        name: yup.string().required("form.empty.error").max(256, "connectorBuilder.maxLength"),
        description: yup.string().max(256, "connectorBuilder.maxLength"),
        useVersion: yup.bool(),
        version: yup.number().min(minVersion),
      }),
    [minVersion]
  );

  const methods = useForm({
    defaultValues: initialValues,
    resolver: yupResolver(schema),
    mode: "onChange",
  });

  const reset = methods.reset;
  useEffect(() => {
    reset(initialValues);
  }, [initialValues, reset]);

  if (isLoadingVersions) {
    return (
      <Modal
        size="sm"
        title={
          <FormattedMessage
            id={currentProject.sourceDefinitionId ? "connectorBuilder.releaseNewVersion" : "connectorBuilder.publish"}
          />
        }
        onClose={onClose}
      >
        <ModalBody>
          <FlexContainer justifyContent="center">
            <Spinner />
          </FlexContainer>
        </ModalBody>
      </Modal>
    );
  }

  const handleSubmit = async (values: typeof initialValues) => {
    const manifest = lastValidJsonManifest as DeclarativeComponentSchema;
    unregisterNotificationById(NOTIFICATION_ID);
    try {
      if (currentProject.sourceDefinitionId) {
        await sendNewVersionRequest({
          manifest,
          description: values.description,
          sourceDefinitionId: currentProject.sourceDefinitionId,
          projectId: currentProject.id,
          useAsActiveVersion: values.useVersion,
          version: values.version,
        });
        analyticsService.track(Namespace.CONNECTOR_BUILDER, Action.RELEASE_NEW_PROJECT_VERSION, {
          actionDescription: "User released a new version of a Connector Builder project",
          projectVersion: values.version,
          projectId: currentProject.id,
        });
      } else {
        await sendPublishRequest({
          manifest,
          name: values.name,
          description: values.description,
          projectId,
        });
        analyticsService.track(Namespace.CONNECTOR_BUILDER, Action.PUBLISH_PROJECT, {
          actionDescription: "User published a Connector Builder project",
          projectId: currentProject.id,
        });
      }

      // push name change upstream so it's updated
      setValue("global.connectorName", values.name);

      registerNotification({
        id: NOTIFICATION_ID,
        text: (
          <FormattedMessage
            id="connectorBuilder.publishedMessage"
            values={{ name: values.name, version: values.version }}
          />
        ),
        type: "success",
      });
      onClose();
    } catch (e) {
      registerNotification({
        id: NOTIFICATION_ID,
        text: <FormattedMessage id="form.error" values={{ message: e.message }} />,
        type: "error",
      });
    }
  };

  return (
    <FormProvider {...methods}>
      <Modal
        size="sm"
        title={
          <FormattedMessage
            id={currentProject.sourceDefinitionId ? "connectorBuilder.releaseNewVersion" : "connectorBuilder.publish"}
          />
        }
        onClose={onClose}
      >
        <form onSubmit={methods.handleSubmit(handleSubmit)}>
          <ModalBody>
            <FlexContainer alignItems="flex-start" gap="xl">
              <ConnectorImage />
              <FlexItem grow>
                <FlexContainer direction="column">
                  <BuilderField path="name" type="string" label="Connector name" />
                  <FlexContainer direction="row">
                    {currentProject.sourceDefinitionId && (
                      <div className={styles.versionInput}>
                        <BuilderField path="version" type="string" label="Version" disabled />
                      </div>
                    )}
                    <BuilderField path="description" type="textarea" label="Description" optional />
                  </FlexContainer>
                </FlexContainer>
              </FlexItem>
            </FlexContainer>
          </ModalBody>
          <ModalFooter>
            <FlexItem grow>
              <FlexContainer justifyContent="space-between">
                {currentProject.sourceDefinitionId && (
                  <BuilderField path="useVersion" type="boolean" label="Set as active version" />
                )}
                <FlexItem grow>
                  <FlexContainer justifyContent="flex-end">
                    <Button type="button" variant="secondary" onClick={onClose}>
                      <FormattedMessage id="form.cancel" />
                    </Button>
                    <Button
                      type="submit"
                      isLoading={methods.formState.isSubmitting}
                      data-testid="publish-submit-button"
                    >
                      <FormattedMessage
                        id={
                          currentProject.sourceDefinitionId
                            ? "connectorBuilder.releaseNewVersion"
                            : "connectorBuilder.publish"
                        }
                      />
                    </Button>
                  </FlexContainer>
                </FlexItem>
              </FlexContainer>
            </FlexItem>
          </ModalFooter>
        </form>
      </Modal>
    </FormProvider>
  );
};
