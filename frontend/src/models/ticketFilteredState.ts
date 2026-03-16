import { TicketApiCode } from './ticket';

export type TicketFiltersState = {
  search: string;
  status: (TicketApiCode | -1)[];
  schools: string[];
  type: 'all' | 'mine' | 'other';
};
