import { TicketApiCode } from './ticket';

export type SortOrder = 'ASC' | 'DESC';

export type SortableTicketField =
  | 'id'
  | 'status'
  | 'subject'
  | 'category_label'
  | 'owner'
  | 'modified'
  | 'event_count'
  | 'school_id';

export type TicketFiltersState = {
  search: string;
  status: TicketApiCode[];
  schools: string[];
  type: 'all' | 'mine' | 'other';
  sortBy: SortableTicketField;
  order: SortOrder;
};
