import { FormControl, Input, Label, Select } from '@edifice.io/react';
import { Control, Controller, FieldErrors } from 'react-hook-form';
import {
  ESCALATION_STATUS,
  TICKET_STATUS_BY_CODE,
  type Ticket,
} from '~/models';

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
  isPending: boolean;
};

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

export default function TicketEditForm({
  control,
  onSubmit,
  errors,
  ticket,
  userProfile,
  categories,
  schoolOptions,
  bugTrackerIssueId,
  isPending,
}: TicketEditFormProps) {
  return (
    <div className="ps-16 pe-16 pt-12 pb-12 w-100">
      <FormControl id="uuid">
        <Label>Identifiant</Label>
        <Input
          placeholder="Identifiant"
          size="md"
          type="text"
          value={String(ticket.id)}
          disabled
        />
      </FormControl>

      <FormControl id="owner_name">
        <Label>Demandeur</Label>
        <Input
          placeholder="Demandeur"
          size="md"
          type="text"
          value={ticket.owner_name}
          disabled
        />
      </FormControl>

      <FormControl id="profile">
        <Label>Profil</Label>
        <Input
          placeholder="Profil"
          size="md"
          type="text"
          value={userProfile ?? ''}
          disabled
        />
      </FormControl>

      <ControlledSelect
        name="school_id"
        label="Etablissement"
        placeholder="Etablissement"
        options={schoolOptions}
        control={control}
        required="L'établissement est obligatoire"
        errors={errors}
        isPending={isPending}
        onSubmit={onSubmit}
      />

      <ControlledSelect
        name="category"
        label="Catégorie"
        placeholder="Catégorie"
        options={categories}
        control={control}
        required="La catégorie est obligatoire"
        errors={errors}
        isPending={isPending}
        onSubmit={onSubmit}
      />

      <ControlledSelect
        name="status"
        label="Statut"
        placeholder="Statut"
        options={STATUS_OPTIONS}
        control={control}
        required="Le statut est obligatoire"
        errors={errors}
        isPending={isPending}
        onSubmit={onSubmit}
      />

      <FormControl id="created">
        <Label>Date de Création</Label>
        <Input
          placeholder="Date de Création"
          size="md"
          type="date"
          disabled
          value={new Date(ticket.created).toISOString().substring(0, 10)}
        />
      </FormControl>

      <FormControl id="modified">
        <Label>Date de dernière modification</Label>
        <Input
          placeholder="Date de dernière modification"
          size="md"
          type="date"
          disabled
          value={new Date(ticket.modified).toISOString().substring(0, 10)}
        />
      </FormControl>

      {ticket.escalation_status === ESCALATION_STATUS.SUCCESSFUL &&
        bugTrackerIssueId && (
          <FormControl id="escalation_id">
            <Label>Ticket escaladé</Label>
            <Input
              placeholder=""
              size="md"
              type="text"
              disabled
              value={bugTrackerIssueId}
            />
          </FormControl>
        )}
    </div>
  );
}
