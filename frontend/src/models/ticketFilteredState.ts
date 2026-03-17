import { TicketApiCode } from './ticket';

export type TicketFiltersState = {
  search: string;
  status: TicketApiCode[];
  schools: string[];
  type: 'all' | 'mine' | 'other';
};
