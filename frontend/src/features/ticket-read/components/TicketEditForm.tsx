import { Dropdown, Flex, FormControl, Input, Label, Select } from '@edifice.io/react';
import { useMemo } from 'react';
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

function ControlledDropdown({
  name,
  label,
  placeholder,
  searchPlaceholder,
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
  searchPlaceholder: string;
  options: SelectOption[];
  control: Control<TicketEditFormValues>;
  required: string;
  errors: FieldErrors<TicketEditFormValues>;
  isPending: boolean;
  onSubmit: () => void;
}) {
  const sortedOptions = useMemo(
    () => [...options].sort((a, b) => a.label.localeCompare(b.label)),
    [options],
  );

  return (
    <FormControl id={name} status={errors[name] ? 'invalid' : undefined}>
      <Label>{label}</Label>
      <Controller
        name={name}
        control={control}
        rules={{ required }}
        render={({ field }) => (
          <Dropdown block>
            <Dropdown.Trigger
              size="md"
              block
              disabled={isPending}
              label={
                sortedOptions.find((o) => o.value === field.value)?.label ??
                placeholder
              }
            />
            <Dropdown.Menu>
              <Dropdown.SearchInput
                placeholder={searchPlaceholder}
                noResultsLabel="Pas de résultat"
              />
              {sortedOptions.map((opt) => (
                <Dropdown.Item
                  key={`${opt.label}-${opt.value}`}
                  searchValue={opt.label}
                  onClick={() => {
                    if (opt.value !== field.value) {
                      field.onChange(opt.value);
                      onSubmit();
                    }
                  }}
                >
                  {opt.label}
                </Dropdown.Item>
              ))}
            </Dropdown.Menu>
          </Dropdown>
        )}
      />
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
    <Flex direction="column" gap="8" className="ps-16 pe-16 pt-12 pb-12 w-100">
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
          value={userProfile ?? ' '}
          disabled
        />
      </FormControl>

      <ControlledDropdown
        name="school_id"
        label="Etablissement"
        placeholder="Etablissement"
        searchPlaceholder="Rechercher un établissement..."
        options={schoolOptions}
        control={control}
        required="L'établissement est obligatoire"
        errors={errors}
        isPending={isPending}
        onSubmit={onSubmit}
      />

      <ControlledDropdown
        name="category"
        label="Catégorie"
        placeholder="Catégorie"
        searchPlaceholder="Rechercher une catégorie..."
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
    </Flex>
  );
}
