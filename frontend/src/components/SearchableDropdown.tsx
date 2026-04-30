import { Dropdown, FormControl, Label } from '@edifice.io/react';
import { useMemo } from 'react';
import { useI18n } from '~/hooks/usei18n';
import { sortByLabel } from '~/utils';

type Option = { label: string; value: string };

type SearchableDropdownProps = {
  id: string;
  label: string;
  placeholder: string;
  searchPlaceholder: string;
  options: Option[];
  selectedValue: string;
  onChange: (value: string) => void;
  disabled?: boolean;
  isRequired?: boolean;
  isInvalid?: boolean;
  size?: 'sm' | 'md' | 'lg';
};

export function SearchableDropdown({
  id,
  label,
  placeholder,
  searchPlaceholder,
  options,
  selectedValue,
  onChange,
  disabled = false,
  isRequired = false,
  isInvalid = false,
  size = 'md',
}: SearchableDropdownProps) {
  const { t } = useI18n();
  const sortedOptions = useMemo(() => sortByLabel(options), [options]);

  return (
    <FormControl
      id={id}
      isRequired={isRequired}
      status={isInvalid ? 'invalid' : undefined}
    >
      <Label>{label}</Label>
      <Dropdown block>
        <Dropdown.Trigger
          size={size}
          block
          disabled={disabled}
          label={
            sortedOptions.find((o) => o.value === selectedValue)?.label ??
            placeholder
          }
        />
        <Dropdown.Menu>
          <Dropdown.SearchInput
            placeholder={searchPlaceholder}
            noResultsLabel={t('support.ticket.form.no.results')}
          />
          {sortedOptions.map((opt) => (
            <Dropdown.Item
              key={`${opt.label}-${opt.value}`}
              searchValue={opt.label}
              onClick={() => {
                if (opt.value !== selectedValue) {
                  onChange(opt.value);
                }
              }}
            >
              {opt.label}
            </Dropdown.Item>
          ))}
        </Dropdown.Menu>
      </Dropdown>
    </FormControl>
  );
}
