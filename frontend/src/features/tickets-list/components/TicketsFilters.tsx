import { Dropdown, Flex, SearchBar } from '@edifice.io/react';
import { IconFilter } from '@edifice.io/react/icons';
import { ChangeEvent } from 'react';

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

const ALL_STATUS_CODES = Object.keys(TICKET_STATUS_BY_CODE).map(
  Number,
) as TicketApiCode[];

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
  const allSchoolIds = schools.map((s) => s.id);
  const isAllSelected =
    filters.schools.length === 0 ||
    (filters.schools.length === schools.length && schools.length > 0);

  const handleSelectAll = () => {
    onChange({
      ...filters,
      schools: isAllSelected ? [] : allSchoolIds,
    });
  };

  const handleSchoolToggle = (value: string | number) => {
    const currentSchools =
      filters.schools.length === 0 ? allSchoolIds : filters.schools;
    onChange({
      ...filters,
      schools: toggleItem(currentSchools, value as string, allSchoolIds),
    });
  };

  return (
    <Dropdown>
      <Dropdown.Trigger
        size="md"
        icon={<IconFilter />}
        label="Établissement"
        badgeContent={
          filters.schools.length === allSchoolIds.length
            ? undefined
            : filters.schools.length
        }
      />
      <Dropdown.Menu>
        <Dropdown.CheckboxItem
          model={isAllSelected ? ['all'] : []}
          value="all"
          onChange={handleSelectAll}
        >
          Tous les établissements
        </Dropdown.CheckboxItem>
        <Dropdown.Separator />
        {schools.map((school) => {
          return (
            <Dropdown.CheckboxItem
              key={school.id}
              model={
                isAllSelected || filters.schools.includes(school.id)
                  ? [school.id]
                  : []
              }
              value={school.id}
              onChange={handleSchoolToggle}
            >
              {school.name}
            </Dropdown.CheckboxItem>
          );
        })}
      </Dropdown.Menu>
    </Dropdown>
  );
}

function TicketsStatusFilter({ filters, onChange }: TicketsStatusFilterProps) {
  const isAllSelected = filters.status.length === ALL_STATUS_CODES.length;

  const handleSelectAll = () => {
    onChange({
      ...filters,
      status: isAllSelected ? [] : [...ALL_STATUS_CODES],
    });
  };

  const handleStatusToggle = (value: string | number) => {
    onChange({
      ...filters,
      status: toggleItem(
        filters.status,
        value as TicketApiCode,
        ALL_STATUS_CODES,
      ),
    });
  };

  return (
    <Dropdown>
      <Dropdown.Trigger
        size="md"
        icon={<IconFilter />}
        label="Statut du ticket"
        badgeContent={
          filters.status.length === ALL_STATUS_CODES.length
            ? undefined
            : filters.status.length
        }
      />
      <Dropdown.Menu>
        <Dropdown.CheckboxItem
          model={isAllSelected ? ['all'] : []}
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
  const handleSearchChange = (e: ChangeEvent<HTMLInputElement>) => {
    onChange({ ...filters, search: e.target.value });
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
        className="flex-grow-1 w-auto"
        placeholder="Rechercher par numéro de ticket"
        onChange={handleSearchChange}
        isVariant={false}
        onClick={() => {}}
        size="md"
        value={filters.search}
        data-testid="search-bar"
      />

      <Flex gap="16" align="center" wrap="wrap">
        {schools.length > 0 && (
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
