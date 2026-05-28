import { Dropdown, FormControl, Label } from '@edifice.io/react';
import { UIEvent, useEffect, useMemo, useRef, useState } from 'react';
import { useI18n } from '~/hooks/usei18n';
import { sortByLabel } from '~/utils';

const DROPDOWN_PAGE_SIZE = 50;

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
  const [search, setSearch] = useState('');
  const [displayCount, setDisplayCount] = useState(DROPDOWN_PAGE_SIZE);
  const listRef = useRef<HTMLDivElement>(null);

  const sortedOptions = useMemo(() => sortByLabel(options), [options]);

  const visibleOptions = useMemo(
    () =>
      search
        ? sortedOptions.filter((o) =>
            o.label.toLowerCase().includes(search.toLowerCase()),
          )
        : sortedOptions,
    [sortedOptions, search],
  );

  const renderedOptions = visibleOptions.slice(0, displayCount);
  const hasMore = displayCount < visibleOptions.length;

  useEffect(() => {
    setDisplayCount(DROPDOWN_PAGE_SIZE);
    listRef.current?.scrollTo(0, 0);
  }, [search]);

  const handleScroll = (e: UIEvent<HTMLDivElement>) => {
    if (!hasMore) return;
    const { scrollTop, scrollHeight, clientHeight } = e.currentTarget;
    if (scrollHeight - scrollTop - clientHeight < 40) {
      setDisplayCount((prev) => prev + DROPDOWN_PAGE_SIZE);
    }
  };

  return (
    <FormControl
      id={id}
      isRequired={isRequired}
      status={isInvalid ? 'invalid' : undefined}
    >
      <Label>{label}</Label>
      <Dropdown
        block
        onToggle={(visible) => {
          if (!visible) {
            setSearch('');
            setDisplayCount(DROPDOWN_PAGE_SIZE);
          }
        }}
      >
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
            onSearch={setSearch}
          />
          <div
            ref={listRef}
            className="overflow-y-auto"
            style={{ maxWidth: 'min(500px, 90vw)', maxHeight: '200px' }}
            onScroll={handleScroll}
          >
            {renderedOptions.map((opt) => (
              <Dropdown.Item
                key={`${opt.label}-${opt.value}`}
                onClick={() => {
                  if (opt.value !== selectedValue) {
                    onChange(opt.value);
                  }
                }}
              >
                <span className="text-wrap text-break">{opt.label}</span>
              </Dropdown.Item>
            ))}
            {visibleOptions.length === 0 && search && (
              <div className="px-8 py-4 body-2 text-gray-700 text-center">
                {t('support.ticket.form.no.results')}
              </div>
            )}
          </div>
        </Dropdown.Menu>
      </Dropdown>
    </FormControl>
  );
}
