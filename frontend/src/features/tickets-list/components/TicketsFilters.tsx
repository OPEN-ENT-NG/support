import { Dropdown, Flex, SearchBar, useDebounce } from '@edifice.io/react';
import { IconFilter } from '@edifice.io/react/icons';
import { ChangeEvent, useEffect, useState } from 'react';

import {
  School,
  TICKET_STATUS_BY_CODE,
  TicketApiCode,
  TicketFiltersState,
} from '~/models';

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
  const [isAllChecked, setIsAllChecked] = useState(
    filters.schools.length === 0,
  );

  const handleSelectAll = () => {
    setIsAllChecked((prev) => !prev);
    onChange({ ...filters, schools: [] });
  };

  const handleSchoolToggle = (value: string | number) => {
    const updated = toggleItem(filters.schools, value as string, []);
    setIsAllChecked(updated.length === 0);
    onChange({
      ...filters,
      schools: updated,
    });
  };

  return (
    <Dropdown>
      <Dropdown.Trigger
        size="md"
        icon={<IconFilter />}
        label="Établissement"
        badgeContent={
          filters.schools.length > 0 ? filters.schools.length : undefined
        }
      />
      <Dropdown.Menu>
        <Dropdown.CheckboxItem
          model={isAllChecked ? ['all'] : []}
          value="all"
          onChange={handleSelectAll}
        >
          Tous les établissements
        </Dropdown.CheckboxItem>
        <Dropdown.Separator />
        <div style={{ maxHeight: '200px', overflowY: 'auto' }}>
          {schools.map((school) => {
            return (
              <Dropdown.CheckboxItem
                key={school.id}
                model={filters.schools.includes(school.id) ? [school.id] : []}
                value={school.id}
                onChange={handleSchoolToggle}
              >
                {school.name}
              </Dropdown.CheckboxItem>
            );
          })}
        </div>
      </Dropdown.Menu>
    </Dropdown>
  );
}

function TicketsStatusFilter({ filters, onChange }: TicketsStatusFilterProps) {
  const [isAllChecked, setIsAllChecked] = useState(filters.status.length === 0);

  const handleSelectAll = () => {
    setIsAllChecked((prev) => !prev);
    onChange({ ...filters, status: [] });
  };

  const handleStatusToggle = (value: string | number) => {
    const updated = toggleItem(filters.status, value as TicketApiCode, []);
    setIsAllChecked(updated.length === 0);
    onChange({ ...filters, status: updated });
  };

  return (
    <Dropdown>
      <Dropdown.Trigger
        size="md"
        icon={<IconFilter />}
        label="Statut du ticket"
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
          Tous les statuts
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
              {status.label}
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
  const [search, setSearch] = useState(filters.search);
  const debouncedSearch = useDebounce(search, 300);

  useEffect(() => {
    onChange({ ...filters, search: debouncedSearch });
  }, [debouncedSearch]); // eslint-disable-line react-hooks/exhaustive-deps

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
        placeholder="Rechercher un ticket"
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
