import { SegmentedControl } from '@edifice.io/react';
import { useCallback } from 'react';
import { TicketFiltersState, TicketType } from '~/models';
import { useI18n } from '~/hooks/usei18n';

type Props = {
  filters: TicketFiltersState;
  onChange: (filters: TicketFiltersState) => void;
};

export function TicketsTypeSelector({ filters, onChange }: Props) {
  const { t } = useI18n();

  const options = [
    { label: t('support.type.selector.all'), value: 'all' },
    { label: t('support.ticket.status.my.demands'), value: 'mine' },
    { label: t('support.ticket.status.other.demands'), value: 'other' },
  ];

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
