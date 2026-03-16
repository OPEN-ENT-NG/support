import { Dropdown, SearchBar } from '@edifice.io/react';
import { ChangeEvent, useMemo } from 'react';
import { IconFilter } from '@edifice.io/react/icons';

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

function TicketsSchoolFilter({
  filters,
  onChange,
  schools,
}: TicketsSchoolFilterProps) {
  const isAllSelected =
    filters.schools.length === schools.length && schools.length > 0;

  const dropdownLabel =
    isAllSelected || filters.schools.length === 0
      ? 'Établissements'
      : `${filters.schools.length} établissement${filters.schools.length > 1 ? 's' : ''}`;

  const handleSelectAll = () => {
    onChange({
      ...filters,
      schools: isAllSelected ? [] : schools.map((s) => s.id),
    });
  };

  const handleSchoolToggle = (value: string | number) => {
    const id = value as string;

    if (isAllSelected) {
      onChange({
        ...filters,
        schools: schools.filter((s) => s.id !== id).map((s) => s.id),
      });
      return;
    }

    if (filters.schools.includes(id)) {
      onChange({
        ...filters,
        schools: filters.schools.filter((s) => s !== id),
      });
    } else {
      onChange({ ...filters, schools: [...filters.schools, id] });
    }
  };

  return (
    <Dropdown>
      <Dropdown.Trigger size="md" icon={<IconFilter />} label={dropdownLabel} />
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
          const isSelected =
            isAllSelected || filters.schools.includes(school.id);
          return (
            <Dropdown.CheckboxItem
              key={school.id}
              model={isSelected ? [school.id] : []}
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
  const isAllSelected = useMemo(() => {
    return filters.status.includes(-1);
  }, [filters.status]);

  const dropdownLabel = useMemo(() => {
    if (isAllSelected) {
      return 'Statut du ticket';
    }
    const { status } = filters;
    if (status.length === 0) {
      return 'Statut du ticket';
    }

    return `${status.length} statuts`;
  }, [filters, isAllSelected]);

  const handleStatusToggle = (value: string | number): void => {
    if (value === -1) {
      // Toggle "All" option
      onChange({
        ...filters,
        status: isAllSelected ? [] : [-1],
      });
      return;
    }

    // Toggle individual status
    const statusCode = value as TicketApiCode;

    // If all are selected and user clicks an individual status, deselect all and select only that status
    if (isAllSelected) {
      onChange({
        ...filters,
        status: [statusCode],
      });
      return;
    }

    // Normal toggle logic when not all are selected
    const currentStatus = filters.status.filter((s: number) => s !== -1); // Remove -1 if present
    const isSelected = currentStatus.includes(statusCode);
    const newStatus = isSelected
      ? currentStatus.filter((s: number) => s !== statusCode)
      : [...currentStatus, statusCode];

    // If no statuses selected, default to -1 (all)
    onChange({
      ...filters,
      status: newStatus.length === 0 ? [-1] : newStatus,
    });
  };

  return (
    <Dropdown>
      <Dropdown.Trigger size="md" icon={<IconFilter />} label={dropdownLabel} />
      <Dropdown.Menu>
        <Dropdown.CheckboxItem
          key="all"
          model={isAllSelected ? [-1] : []}
          value={-1}
          onChange={handleStatusToggle}
        >
          Tous les statuts
        </Dropdown.CheckboxItem>
        <Dropdown.Separator />
        {Object.entries(TICKET_STATUS_BY_CODE).map(([code, status]) => {
          const statusCode = Number(code) as TicketApiCode;
          const isSelected =
            isAllSelected || filters.status.includes(statusCode);
          return (
            <Dropdown.CheckboxItem
              key={code}
              model={isSelected ? [statusCode] : []}
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
    <div className="d-flex gap-16 align-items-center justify-content-between w-75">
      <SearchBar
        placeholder="Search tickets..."
        onChange={handleSearchChange}
        isVariant={false}
        onClick={() => {}}
        size="md"
        value={filters.search}
        data-testid="search-bar"
      />
      <TicketsSchoolFilter
        schools={schools}
        onChange={onChange}
        filters={filters}
      />
      <TicketsStatusFilter onChange={onChange} filters={filters} />
    </div>
  );
}
