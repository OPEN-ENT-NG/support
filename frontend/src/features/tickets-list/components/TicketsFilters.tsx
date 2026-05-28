import {
  Badge,
  Checkbox,
  Dropdown,
  Flex,
  SearchBar,
  useDebounce,
} from '@edifice.io/react';
import { IconFilter } from '@edifice.io/react/icons';
import { ChangeEvent, useEffect, useMemo, useRef, useState } from 'react';

const SEARCH_DEBOUNCE_MS = 300;
const SCHOOLS_PAGE_SIZE = 50;

import {
  School,
  TICKET_STATUS_BY_CODE,
  TicketApiCode,
  TicketFiltersState,
} from '~/models';
import { useI18n } from '~/hooks/usei18n';
import { sortByKey } from '~/utils';

type TicketsFiltersProps = {
  filters: TicketFiltersState;
  onChange: (filters: TicketFiltersState) => void;
  schools: School[];
};

type TicketsSchoolFilterProps = {
  filters: TicketFiltersState;
  onChange: (filters: TicketFiltersState) => void;
  schools: School[];
};

type TicketsStatusFilterProps = {
  filters: TicketFiltersState;
  onChange: (filters: TicketFiltersState) => void;
};

/*
 * Toggle item in the list and returns the updated list.
 * If the item is already in the list it is removed otherwise it is added
 * If the updated list is empty then returns all items to select them all
 */
function toggleItem<T>(list: T[], item: T, allItems: T[]): T[] {
  const updatedList = list.includes(item)
    ? list.filter((i) => i !== item)
    : [...list, item];

  return updatedList.length === 0 ? [...allItems] : updatedList;
}

function TicketsSchoolFilter({
  filters,
  onChange,
  schools,
}: TicketsSchoolFilterProps) {
  const { t } = useI18n();
  const [noneSelected, setNoneSelected] = useState(false);
  const [schoolSearch, setSchoolSearch] = useState('');
  const [displayCount, setDisplayCount] = useState(SCHOOLS_PAGE_SIZE);
  const listRef = useRef<HTMLDivElement>(null);

  const isAllChecked = filters.schools.length === 0 && !noneSelected;
  const isIndeterminate = filters.schools.length > 0;

  const sortedSchools = useMemo(() => sortByKey(schools, 'name'), [schools]);

  const visibleSchools = useMemo(
    () =>
      schoolSearch
        ? sortedSchools.filter((s) =>
            s.name.toLowerCase().includes(schoolSearch.toLowerCase()),
          )
        : sortedSchools,
    [sortedSchools, schoolSearch],
  );

  const renderedSchools = visibleSchools.slice(0, displayCount);
  const hasMore = displayCount < visibleSchools.length;

  useEffect(() => {
    setDisplayCount(SCHOOLS_PAGE_SIZE);
    listRef.current?.scrollTo(0, 0);
  }, [schoolSearch]);

  const handleListScroll = (e: React.UIEvent<HTMLDivElement>) => {
    if (!hasMore) return;
    const { scrollTop, scrollHeight, clientHeight } = e.currentTarget;
    if (scrollHeight - scrollTop - clientHeight < 40) {
      setDisplayCount((prev) => prev + SCHOOLS_PAGE_SIZE);
    }
  };

  const handleSelectAll = () => {
    if (schoolSearch) {
      const visibleIds = visibleSchools.map((s) => s.id);
      const allVisibleSelected = visibleSchools.every(
        (s) => isAllChecked || filters.schools.includes(s.id),
      );
      const nonVisibleSelected = isAllChecked
        ? schools.filter((s) => !visibleIds.includes(s.id)).map((s) => s.id)
        : filters.schools.filter((id) => !visibleIds.includes(id));

      if (allVisibleSelected) {
        // Désélectionne les items visibles, préserve les non-visibles
        if (nonVisibleSelected.length === 0) {
          setNoneSelected(true);
        } else {
          onChange({ ...filters, schools: nonVisibleSelected });
          setNoneSelected(false);
        }
      } else {
        // Sélectionne les items visibles, préserve les non-visibles
        const next = [...new Set([...nonVisibleSelected, ...visibleIds])];
        onChange({
          ...filters,
          schools: next.length === schools.length ? [] : next,
        });
        setNoneSelected(false);
      }
    } else if (isAllChecked) {
      setNoneSelected(true);
    } else {
      setNoneSelected(false);
      onChange({ ...filters, schools: [] });
    }
  };

  const handleSchoolToggle = (value: string | number) => {
    setNoneSelected(false);
    const base = isAllChecked ? schools.map((s) => s.id) : filters.schools;
    const updated = toggleItem(base, value as string, []);
    onChange({ ...filters, schools: updated });
  };

  return (
    <Dropdown
      placement="bottom-end"
      onToggle={(visible) => {
        if (!visible) {
          setSchoolSearch('');
          setDisplayCount(SCHOOLS_PAGE_SIZE);
        }
      }}
    >
      <Dropdown.Trigger
        size="md"
        icon={<IconFilter />}
        label={t('support.ticket.filter.schools')}
        badgeContent={
          filters.schools.length > 0 ? filters.schools.length : undefined
        }
      />
      <Dropdown.Menu>
        <div className="px-8 pb-4 d-flex align-items-center justify-content-end gap-8">
          {isIndeterminate && (
            <Badge variant={{ type: 'notification', level: 'info' }}>
              {filters.schools.length}
            </Badge>
          )}
          <Checkbox
            checked={isAllChecked}
            indeterminate={isIndeterminate}
            onChange={handleSelectAll}
          />
        </div>
        <Dropdown.Separator />
        <Dropdown.SearchInput
          placeholder={t('support.ticket.form.search.school')}
          noResultsLabel={t('support.ticket.form.no.results')}
          onSearch={setSchoolSearch}
        />
        <div
          ref={listRef}
          className="overflow-y-auto"
          style={{ maxWidth: 'min(500px, 90vw)', maxHeight: '200px' }}
          onScroll={handleListScroll}
        >
          {renderedSchools.map((school) => (
            <Dropdown.CheckboxItem
              key={school.id}
              model={
                isAllChecked || filters.schools.includes(school.id)
                  ? [school.id]
                  : []
              }
              value={school.id}
              onChange={handleSchoolToggle}
            >
              <span className="text-wrap text-break">{school.name}</span>
            </Dropdown.CheckboxItem>
          ))}
          {visibleSchools.length === 0 && schoolSearch && (
            <div className="px-8 py-4 body-2 text-gray-700 text-center">
              {t('support.ticket.form.no.results')}
            </div>
          )}
        </div>
      </Dropdown.Menu>
    </Dropdown>
  );
}

function TicketsStatusFilter({ filters, onChange }: TicketsStatusFilterProps) {
  const { t } = useI18n();
  const isAllChecked = filters.status.length === 0;

  const handleSelectAll = () => {
    onChange({ ...filters, status: [] });
  };

  const handleStatusToggle = (value: string | number) => {
    const updated = toggleItem(filters.status, value as TicketApiCode, []);
    onChange({ ...filters, status: updated });
  };

  return (
    <Dropdown placement="bottom-end">
      <Dropdown.Trigger
        size="md"
        icon={<IconFilter />}
        label={t('support.ticket.filter.status')}
        badgeContent={
          filters.status.length > 0 ? filters.status.length : undefined
        }
      />
      <Dropdown.Menu>
        <Dropdown.CheckboxItem
          model={isAllChecked ? ['all'] : []}
          value="all"
          onChange={handleSelectAll}
        >
          {t('support.ticket.filter.allstatuses')}
        </Dropdown.CheckboxItem>
        <Dropdown.Separator />
        {Object.entries(TICKET_STATUS_BY_CODE).map(([code, status]) => {
          const statusCode = Number(code) as TicketApiCode;
          return (
            <Dropdown.CheckboxItem
              key={code}
              model={filters.status.includes(statusCode) ? [statusCode] : []}
              value={statusCode}
              onChange={handleStatusToggle}
            >
              {t(status.label)}
            </Dropdown.CheckboxItem>
          );
        })}
      </Dropdown.Menu>
    </Dropdown>
  );
}

export function TicketsFilters({
  filters,
  schools,
  onChange,
}: TicketsFiltersProps) {
  const { t } = useI18n();
  const [search, setSearch] = useState(filters.search);
  const debouncedSearch = useDebounce(search, SEARCH_DEBOUNCE_MS);
  const filtersRef = useRef(filters);
  filtersRef.current = filters;

  useEffect(() => {
    onChange({ ...filtersRef.current, search: debouncedSearch });
  }, [debouncedSearch, onChange]);

  const handleSearchChange = (e: ChangeEvent<HTMLInputElement>) => {
    setSearch(e.target.value);
  };

  return (
    <Flex
      gap="16"
      align="center"
      justify="between"
      wrap="wrap"
      className="w-75"
    >
      <SearchBar
        className="flex-grow-1 w-auto search-bar"
        placeholder={t('support.ticket.filter.search')}
        onChange={handleSearchChange}
        isVariant={false}
        onClick={() => {}}
        size="md"
        value={search}
        data-testid="search-bar"
      />

      <Flex gap="16" align="center" wrap="wrap">
        {schools.length > 1 && (
          <TicketsSchoolFilter
            schools={schools}
            onChange={onChange}
            filters={filters}
          />
        )}
        <TicketsStatusFilter onChange={onChange} filters={filters} />
      </Flex>
    </Flex>
  );
}
