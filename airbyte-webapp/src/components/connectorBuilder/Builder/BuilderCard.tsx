import { faArrowUpRightFromSquare } from "@fortawesome/free-solid-svg-icons";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import classNames from "classnames";
import React, { useState } from "react";
import { FieldPath, useFormContext, useWatch } from "react-hook-form";
import { FormattedMessage, useIntl } from "react-intl";

import { Button } from "components/ui/Button";
import { Card } from "components/ui/Card";
import { CheckBox } from "components/ui/CheckBox";
import { FlexContainer, FlexItem } from "components/ui/Flex";
import { Icon } from "components/ui/Icon";
import { Modal, ModalBody, ModalFooter } from "components/ui/Modal";
import { Text } from "components/ui/Text";

import styles from "./BuilderCard.module.scss";
import { BuilderStream, useBuilderWatch, BuilderFormValues } from "../types";

interface BuilderCardProps {
  className?: string;
  label?: React.ReactNode;
  toggleConfig?: {
    path: FieldPath<BuilderFormValues>;
    defaultValue: unknown;
  };
  copyConfig?: {
    path: string;
    currentStreamIndex: number;
    copyToLabel: string;
    copyFromLabel: string;
  };
  docLink?: string;
}

export const BuilderCard: React.FC<React.PropsWithChildren<BuilderCardProps>> = ({
  children,
  className,
  toggleConfig,
  copyConfig,
  docLink,
  label,
}) => {
  const { formatMessage } = useIntl();

  return (
    <Card className={classNames(className, styles.card)}>
      {(toggleConfig || label) && (
        <FlexContainer alignItems="center">
          <FlexItem grow>
            <FlexContainer>
              {toggleConfig && <CardToggle path={toggleConfig.path} defaultValue={toggleConfig.defaultValue} />}
              <span>{label}</span>
            </FlexContainer>
          </FlexItem>
          {docLink && (
            <a
              href={docLink}
              title={formatMessage({ id: "connectorBuilder.documentationLink" })}
              target="_blank"
              rel="noreferrer"
              className={styles.docLink}
            >
              <Icon type="docs" size="lg" />
            </a>
          )}
        </FlexContainer>
      )}
      {copyConfig && <CopyButtons copyConfig={copyConfig} />}
      {toggleConfig ? <ToggledChildren path={toggleConfig.path}>{children}</ToggledChildren> : children}
    </Card>
  );
};

interface ToggledChildrenProps {
  path: FieldPath<BuilderFormValues>;
}

const ToggledChildren: React.FC<React.PropsWithChildren<ToggledChildrenProps>> = ({ children, path }) => {
  const value = useBuilderWatch(path, { exact: true });

  if (value !== undefined) {
    return <>{children}</>;
  }
  return null;
};

const CardToggle = ({ path, defaultValue }: { path: FieldPath<BuilderFormValues>; defaultValue: unknown }) => {
  const { setValue, clearErrors } = useFormContext();
  const value = useBuilderWatch(path, { exact: true });

  return (
    <CheckBox
      data-testid="toggle"
      checked={value !== undefined}
      onChange={(event) => {
        if (event.target.checked) {
          setValue(path, defaultValue);
        } else {
          setValue(path, undefined);
          clearErrors(path);
        }
      }}
    />
  );
};

const CopyButtons = ({ copyConfig }: Pick<BuilderCardProps, "copyConfig">) => {
  const [isCopyToOpen, setCopyToOpen] = useState(false);
  const [isCopyFromOpen, setCopyFromOpen] = useState(false);
  const { getValues, setValue } = useFormContext();
  const streams = useBuilderWatch("streams");
  const currentRelevantConfig = useWatch({
    name: `streams.${copyConfig?.currentStreamIndex}.${copyConfig?.path}`,
    disabled: !copyConfig,
  });
  if (streams.length <= 1 || !copyConfig) {
    return null;
  }
  return (
    <div className={styles.copyButtonContainer}>
      <Button
        variant="secondary"
        type="button"
        onClick={() => {
          setCopyFromOpen(true);
        }}
        icon={<FontAwesomeIcon icon={faArrowUpRightFromSquare} rotation={180} />}
      />
      {currentRelevantConfig && (
        <Button
          variant="secondary"
          type="button"
          onClick={() => {
            setCopyToOpen(true);
          }}
          icon={<FontAwesomeIcon icon={faArrowUpRightFromSquare} />}
        />
      )}
      {isCopyToOpen && (
        <CopyToModal
          onCancel={() => {
            setCopyToOpen(false);
          }}
          onApply={(selectedStreamIndices) => {
            const sectionToCopy = getValues(`streams.${copyConfig.currentStreamIndex}.${copyConfig.path}`);
            selectedStreamIndices.forEach((index) => {
              setValue(`streams[${index}].${copyConfig.path}`, sectionToCopy, { shouldValidate: true });
            });
            setCopyToOpen(false);
          }}
          currentStreamIndex={copyConfig.currentStreamIndex}
          title={copyConfig.copyToLabel}
        />
      )}
      {isCopyFromOpen && (
        <CopyFromModal
          onCancel={() => {
            setCopyFromOpen(false);
          }}
          onSelect={(selectedStreamIndex) => {
            setValue(
              `streams.${copyConfig.currentStreamIndex}.${copyConfig.path}`,
              getValues(`streams.${selectedStreamIndex}.${copyConfig.path}`),
              { shouldValidate: true }
            );
            setCopyFromOpen(false);
          }}
          currentStreamIndex={copyConfig.currentStreamIndex}
          title={copyConfig.copyFromLabel}
        />
      )}
    </div>
  );
};

function getStreamName(stream: BuilderStream) {
  return stream.name || <FormattedMessage id="connectorBuilder.emptyName" />;
}

const CopyToModal: React.FC<{
  onCancel: () => void;
  onApply: (selectedStreamIndices: number[]) => void;
  title: string;
  currentStreamIndex: number;
}> = ({ onCancel, onApply, title, currentStreamIndex }) => {
  const streams = useBuilderWatch("streams");
  const [selectMap, setSelectMap] = useState<Record<string, boolean>>({});
  return (
    <Modal size="sm" title={title} onClose={onCancel}>
      <form
        onSubmit={() => {
          onApply(
            Object.entries(selectMap)
              .filter(([, selected]) => selected)
              .map(([index]) => Number(index))
          );
        }}
      >
        <ModalBody className={styles.modalStreamListContainer}>
          {streams.map((stream, index) =>
            index === currentStreamIndex ? null : (
              <label htmlFor={`copy-to-stream-${index}`} key={index} className={styles.toggleContainer}>
                <CheckBox
                  id={`copy-to-stream-${index}`}
                  checked={selectMap[index] || false}
                  onChange={() => {
                    setSelectMap({ ...selectMap, [index]: !selectMap[index] });
                  }}
                />
                <Text>{getStreamName(stream)}</Text>
              </label>
            )
          )}
        </ModalBody>
        <ModalFooter>
          <Button variant="secondary" onClick={onCancel}>
            <FormattedMessage id="form.cancel" />
          </Button>
          <Button type="submit" disabled={Object.values(selectMap).filter(Boolean).length === 0}>
            <FormattedMessage id="form.apply" />
          </Button>
        </ModalFooter>
      </form>
    </Modal>
  );
};

const CopyFromModal: React.FC<{
  onCancel: () => void;
  onSelect: (selectedStreamIndex: number) => void;
  title: string;
  currentStreamIndex: number;
}> = ({ onCancel, onSelect, title, currentStreamIndex }) => {
  const streams = useBuilderWatch("streams");
  return (
    <Modal size="sm" title={title} onClose={onCancel}>
      <ModalBody className={styles.modalStreamListContainer}>
        {streams.map((stream, index) =>
          currentStreamIndex === index ? null : (
            <button
              key={index}
              onClick={() => {
                onSelect(index);
              }}
              className={styles.streamItem}
            >
              <Text>{getStreamName(stream)}</Text>
            </button>
          )
        )}
      </ModalBody>
    </Modal>
  );
};
