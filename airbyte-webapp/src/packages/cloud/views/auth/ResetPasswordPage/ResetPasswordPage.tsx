import { Field, FieldProps, Formik } from "formik";
import React from "react";
import { FormattedMessage, useIntl } from "react-intl";
import * as yup from "yup";

import { LabeledInput } from "components";
import { HeadTitle } from "components/common/HeadTitle";
import { Button } from "components/ui/Button";
import { FlexContainer } from "components/ui/Flex";
import { Link } from "components/ui/Link";

import { PageTrackingCodes, useTrackPage } from "core/services/analytics";
import { useNotificationService } from "hooks/services/Notification/NotificationService";
import { CloudRoutes } from "packages/cloud/cloudRoutePaths";
import { useAuthService } from "packages/cloud/services/auth/AuthService";

import { BottomBlock, FieldItem, Form } from "../components/FormComponents";
import { FormTitle } from "../components/FormTitle";
import { LoginSignupNavigation } from "../components/LoginSignupNavigation";

const ResetPasswordPageValidationSchema = yup.object().shape({
  email: yup.string().email("form.email.error").required("form.empty.error"),
});

export const ResetPasswordPage: React.FC = () => {
  const { requirePasswordReset } = useAuthService();
  const { registerNotification } = useNotificationService();
  const { formatMessage } = useIntl();

  useTrackPage(PageTrackingCodes.RESET_PASSWORD);
  return (
    <FlexContainer direction="column" gap="xl">
      <HeadTitle titles={[{ id: "login.resetPassword" }]} />
      <FormTitle>
        <FormattedMessage id="login.resetPassword" />
      </FormTitle>

      <Formik
        initialValues={{
          email: "",
        }}
        validationSchema={ResetPasswordPageValidationSchema}
        onSubmit={async ({ email }, FormikBag) => {
          try {
            await requirePasswordReset(email);
            registerNotification({
              id: "resetPassword.emailSent",
              text: formatMessage({ id: "login.resetPassword.emailSent" }),
              type: "success",
            });
          } catch (err) {
            err.message.includes("user-not-found")
              ? FormikBag.setFieldError("email", "login.yourEmail.notFound")
              : FormikBag.setFieldError("email", "login.unknownError");
          }
        }}
        validateOnBlur
        validateOnChange={false}
      >
        {({ isSubmitting }) => (
          <Form>
            <FieldItem>
              <Field name="email">
                {({ field, meta }: FieldProps<string>) => (
                  <LabeledInput
                    {...field}
                    label={<FormattedMessage id="login.yourEmail" />}
                    placeholder={formatMessage({
                      id: "login.yourEmail.placeholder",
                    })}
                    type="text"
                    error={!!meta.error && meta.touched}
                    message={meta.touched && meta.error && formatMessage({ id: meta.error })}
                  />
                )}
              </Field>
            </FieldItem>
            <BottomBlock>
              <Link to={CloudRoutes.Login}>
                <FormattedMessage id="login.backLogin" />
              </Link>
              <Button type="submit" isLoading={isSubmitting} data-testid="login.resetPassword">
                <FormattedMessage id="login.resetPassword" />
              </Button>
            </BottomBlock>
          </Form>
        )}
      </Formik>
      <LoginSignupNavigation to="signup" />
    </FlexContainer>
  );
};
