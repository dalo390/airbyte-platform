import React, { useState } from "react";
import { FormattedMessage } from "react-intl";
import { useLocation } from "react-router-dom";

import { ConnectionConfiguration } from "core/domain/connection";
import { DestinationDefinitionRead } from "core/request/AirbyteClient";
import { LogsRequestError } from "core/request/LogsRequestError";
import { useExperiment } from "hooks/services/Experiment";
import { useGetDestinationDefinitionSpecificationAsync } from "services/connector/DestinationDefinitionSpecificationService";
import { ConnectorIds } from "utils/connectors";
import { FormError } from "utils/errorStatusMessage";
import { ConnectorCard } from "views/Connector/ConnectorCard";
import { ConnectorCardValues } from "views/Connector/ConnectorForm";

const FrequentlyUsedConnectors = React.lazy(
  () => import("views/Connector/ConnectorForm/components/FrequentlyUsedConnectors")
);

export interface DestinationFormValues {
  name: string;
  serviceType: string;
  destinationDefinitionId?: string;
  connectionConfiguration?: ConnectionConfiguration;
}

interface DestinationFormProps {
  onSubmit: (values: DestinationFormValues) => Promise<void>;
  destinationDefinitions: DestinationDefinitionRead[];
  error?: FormError | null;
  selectedDestinationDefinitionId?: string;
}

const hasDestinationDefinitionId = (state: unknown): state is { destinationDefinitionId: string } => {
  return (
    typeof state === "object" &&
    state !== null &&
    typeof (state as { destinationDefinitionId?: string }).destinationDefinitionId === "string"
  );
};

export const DestinationForm: React.FC<DestinationFormProps> = ({
  onSubmit,
  destinationDefinitions,
  error,
  selectedDestinationDefinitionId,
}) => {
  const location = useLocation();

  const [destinationDefinitionId, setDestinationDefinitionId] = useState(
    selectedDestinationDefinitionId ??
      (hasDestinationDefinitionId(location.state) ? location.state.destinationDefinitionId : null)
  );

  const {
    data: destinationDefinitionSpecification,
    error: destinationDefinitionError,
    isLoading,
  } = useGetDestinationDefinitionSpecificationAsync(destinationDefinitionId);

  const onDropDownSelect = (destinationDefinitionId: string) => {
    setDestinationDefinitionId(destinationDefinitionId);
  };

  const onSubmitForm = async (values: ConnectorCardValues) =>
    onSubmit({
      ...values,
      destinationDefinitionId: destinationDefinitionSpecification?.destinationDefinitionId,
    });

  const frequentlyUsedDestinationIds = useExperiment("connector.frequentlyUsedDestinationIds", [
    ConnectorIds.Destinations.BigQuery,
    ConnectorIds.Destinations.Snowflake,
  ]);
  const frequentlyUsedDestinationsComponent = !isLoading && !destinationDefinitionId && (
    <FrequentlyUsedConnectors
      connectorType="destination"
      onConnectorSelect={onDropDownSelect}
      availableServices={destinationDefinitions}
      connectorIds={frequentlyUsedDestinationIds}
    />
  );

  return (
    <ConnectorCard
      formType="destination"
      title={<FormattedMessage id="onboarding.destinationSetUp" />}
      description={<FormattedMessage id="destinations.description" />}
      isLoading={isLoading}
      fetchingConnectorError={destinationDefinitionError instanceof Error ? destinationDefinitionError : null}
      availableConnectorDefinitions={destinationDefinitions}
      onConnectorDefinitionSelect={onDropDownSelect}
      selectedConnectorDefinitionSpecification={destinationDefinitionSpecification}
      selectedConnectorDefinitionId={destinationDefinitionId}
      onSubmit={onSubmitForm}
      jobInfo={LogsRequestError.extractJobInfo(error)}
      additionalSelectorComponent={frequentlyUsedDestinationsComponent}
    />
  );
};
