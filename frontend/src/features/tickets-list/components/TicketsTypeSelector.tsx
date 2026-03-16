import { SegmentedControl } from '@edifice.io/react';
import { useCallback } from 'react';
import { TicketFiltersState, TicketType } from '~/models';

type Props = {
  filters: TicketFiltersState;
  onChange: (filters: TicketFiltersState) => void;
};

const options = [
  { label: 'Tout', value: 'all' },
  { label: 'Mes demandes', value: 'mine' },
  { label: 'Autres demandes', value: 'other' },
];

export function TicketsTypeSelector({ filters, onChange }: Props) {
  const handleChange = useCallback(
    (value: string) => {
      onChange({
        ...filters,
        type: value as TicketType,
      });
    },
    [filters, onChange],
  );

  return (
    <div>
      <SegmentedControl
        onChange={handleChange}
        options={options}
        value={filters.type}
      />
    </div>
  );
}
