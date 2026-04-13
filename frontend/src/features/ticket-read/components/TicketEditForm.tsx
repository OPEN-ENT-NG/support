import {
  Flex,
  FormControl,
  IconButton,
  Input,
  Label,
  Select,
  useToast,
} from '@edifice.io/react';
import { IconCopy, IconExternalLink } from '@edifice.io/react/icons';
import { Control, Controller, FieldErrors } from 'react-hook-form';
import { useI18n } from '~/hooks/usei18n';
import {
  ESCALATION_STATUS,
  TICKET_STATUS_BY_CODE,
  type Ticket,
} from '~/models';
import { SearchableDropdown } from '~/components/SearchableDropdown';

export type TicketEditFormValues = {
  school_id: string;
  category: string;
  status: string;
};

type SelectOption = { label: string; value: string };

const STATUS_OPTIONS: SelectOption[] = Object.entries(
  TICKET_STATUS_BY_CODE,
).map(([code, { label }]) => ({ label, value: code }));

type TicketEditFormProps = {
  control: Control<TicketEditFormValues>;
  onSubmit: () => void;
  errors: FieldErrors<TicketEditFormValues>;
  ticket: Ticket;
  userProfile?: string;
  categories: SelectOption[];
  schoolOptions: SelectOption[];
  bugTrackerIssueId?: number;
  bugTrackerIssueUrl?: string;
  isPending: boolean;
};

function CopyableInput({
  id,
  label,
  value,
  type = 'text',
  externalUrl,
}: {
  id: string;
  label: string;
  value: string;
  type?: string;
  externalUrl?: string;
}) {
  const toast = useToast();
  const { t } = useI18n();

  const handleCopy = () => {
    navigator.clipboard.writeText(value);
    toast.success(t('support.ticket.form.copy.success'));
  };

  return (
    <FormControl id={id}>
      <Label>{label}</Label>
      <Flex gap="8" align="center">
        <Input
          className="flex-grow-1"
          size="md"
          type={type}
          value={value}
          disabled
        />
        {externalUrl && (
          <IconButton
            aria-label={t('support.ticket.form.escalated.external.label')}
            icon={<IconExternalLink />}
            color="tertiary"
            variant="ghost"
            type="button"
            onClick={() => window.open(externalUrl, '_blank')}
          />
        )}
        <IconButton
          aria-label={t('support.ticket.form.copy.label')}
          icon={<IconCopy />}
          color="tertiary"
          variant="ghost"
          type="button"
          onClick={handleCopy}
        />
      </Flex>
    </FormControl>
  );
}

function ControlledSelect({
  name,
  label,
  placeholder,
  options,
  control,
  required,
  errors,
  isPending,
  onSubmit,
}: {
  name: keyof TicketEditFormValues;
  label: string;
  placeholder: string;
  options: SelectOption[];
  control: Control<TicketEditFormValues>;
  required: string;
  errors: FieldErrors<TicketEditFormValues>;
  isPending: boolean;
  onSubmit: () => void;
}) {
  return (
    <FormControl id={name} status={errors[name] ? 'invalid' : undefined}>
      <Label>{label}</Label>
      <Controller
        name={name}
        control={control}
        rules={{ required }}
        render={({ field }) => (
          <Select
            block
            size="md"
            options={options}
            selectedValue={options.find((o) => o.value === field.value)}
            placeholderOption={placeholder}
            onValueChange={(value) => {
              if (value !== field.value) {
                field.onChange(value);
                onSubmit();
              }
            }}
            disabled={isPending}
          />
        )}
      />
    </FormControl>
  );
}

export function TicketEditForm({
  control,
  onSubmit,
  errors,
  ticket,
  userProfile,
  categories,
  schoolOptions,
  bugTrackerIssueId,
  bugTrackerIssueUrl,
  isPending,
}: TicketEditFormProps) {
  const { t } = useI18n();

  return (
    <Flex direction="column" gap="8" className="ps-16 pe-16 pt-12 pb-12 w-100">
      <CopyableInput
        id="uuid"
        label={t('support.ticket.form.id.label')}
        value={String(ticket.id)}
      />

      <CopyableInput
        id="owner_name"
        label={t('support.ticket.form.owner.label')}
        value={ticket.owner_name}
      />

      <FormControl id="profile">
        <Label>{t('support.ticket.form.profile.label')}</Label>
        <Input
          placeholder={t('support.ticket.form.profile.label')}
          size="md"
          type="text"
          value={userProfile ? t(userProfile) : ' '}
          disabled
        />
      </FormControl>

      <Controller
        name="school_id"
        control={control}
        rules={{ required: t('support.ticket.form.school.required') }}
        render={({ field }) => (
          <SearchableDropdown
            id="school_id"
            label={t('support.ticket.form.school.edit.label')}
            placeholder={t('support.ticket.form.school.edit.label')}
            searchPlaceholder={t('support.ticket.form.search.school')}
            options={schoolOptions}
            selectedValue={field.value}
            onChange={(value) => {
              field.onChange(value);
              onSubmit();
            }}
            disabled={isPending}
            isInvalid={!!errors.school_id}
          />
        )}
      />

      <Controller
        name="category"
        control={control}
        rules={{ required: t('support.ticket.form.category.required') }}
        render={({ field }) => (
          <SearchableDropdown
            id="category"
            label={t('support.ticket.category')}
            placeholder={t('support.ticket.category')}
            searchPlaceholder={t('support.ticket.form.search.category')}
            options={categories}
            selectedValue={field.value}
            onChange={(value) => {
              field.onChange(value);
              onSubmit();
            }}
            disabled={isPending}
            isInvalid={!!errors.category}
          />
        )}
      />

      <ControlledSelect
        name="status"
        label={t('support.ticket.status')}
        placeholder={t('support.ticket.status')}
        options={STATUS_OPTIONS.map((option) => ({
          label: t(option.label),
          value: option.value,
        }))}
        control={control}
        required={t('support.ticket.form.status.required')}
        errors={errors}
        isPending={isPending}
        onSubmit={onSubmit}
      />

      <FormControl id="created">
        <Label>{t('support.ticket.form.created.label')}</Label>
        <Input
          placeholder={t('support.ticket.form.created.label')}
          size="md"
          type="date"
          disabled
          value={new Date(ticket.created).toISOString().substring(0, 10)}
        />
      </FormControl>

      <FormControl id="modified">
        <Label>{t('support.ticket.form.modified.label')}</Label>
        <Input
          placeholder={t('support.ticket.form.modified.label')}
          size="md"
          type="date"
          disabled
          value={new Date(ticket.modified).toISOString().substring(0, 10)}
        />
      </FormControl>

      {ticket.escalation_status === ESCALATION_STATUS.SUCCESSFUL &&
        bugTrackerIssueId && (
          <CopyableInput
            id="escalation_id"
            label={t('support.ticket.form.escalated.label')}
            value={String(bugTrackerIssueId)}
            externalUrl={bugTrackerIssueUrl}
          />
        )}
    </Flex>
  );
}
