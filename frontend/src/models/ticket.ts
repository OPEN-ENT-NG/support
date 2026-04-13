export type ApiProfile =
  | 'Elève'
  | 'Enseignant'
  | 'Parent'
  | 'Personnel'
  | 'Invité'
  | 'Staff';
export type Profile =
  | 'Student'
  | 'Teacher'
  | 'Relative'
  | 'Personnel'
  | 'Guest';

export type ApiTicket = {
  id: number;
  owner: string;
  profile: ApiProfile;
  subject: string;
  description: string;
  created: string;
  modified: string;
  status: number;
  school_id: string;
  escalation_status: number;
  escalation_date: string | null;
  event_count: number;
  locale: string;
  issue_update_date: string | null;
  category: string;
  category_label: string | null;
  owner_name: string;
  last_issue_update: string | null;
  short_desc: string;
  total_results: number;
};

export type Ticket = {
  id: number;
  owner: string;
  profile: Profile;
  subject: string;
  description: string;
  created: string;
  modified: string;
  status: TicketApiCode;
  school_id: string;
  escalation_status: TicketEscalationStatusCode;
  escalation_date: string | null;
  event_count: number;
  locale: string;
  issue_update_date: string | null;
  category: string;
  category_label: string | null;
  owner_name: string;
  last_issue_update: string | null;
  short_desc: string;
  total_results: number;
};

export type TicketCardAttachment = {
  document_id: string;
  name: string;
  size: number;
  created: string;
  origin: 'workspace' | 'gridfs';
};

export type ApiAttachment = {
  document_id: string;
  ticket_id: number;
  name: string;
  created: string;
  size: number;
  owner: string;
  owner_name: string;
};

export type TicketAttachment = {
  id: string;
  name: string;
  size: number;
};

export type BugTrackerIssue = {
  id: number;
  content: BugTrackerIssueContent;
  attachments: BugTrackerAttachment[];
};

export type BugTrackerAttachment = {
  id: number;
  filename: string;
  content_type: string | null;
  size: number;
  created_on: string;
  document_id: string | null;
  gridfs_id: string;
};

export type BugTrackerIssueContent = {
  id: number;
  custom_status_id: number;
  allow_attachments: boolean;
  allow_channelback: boolean;
  assignee_id: number;
  brand_id: number;
  collaborator_ids: number[];
  created_at: string;
  custom_fields: { id: number; value?: string | boolean }[];
  description: string;
  email_cc_ids: number[];
  follower_ids: number[];
  followup_ids: number[];
  from_messaging_channel: boolean;
  group_id: number;
  has_incidents: boolean;
  is_public: boolean;
  raw_subject: string;
  requester_id: number;
  satisfaction_rating: { score: string };
  sharing_agreement_ids: number[];
  status: string;
  subject: string;
  submitter_id: number;
  tags: string[];
  ticket_form_id: number;
  updated_at: string;
  url: string;
  via: {
    channel: string;
    source: { from: object; to: object };
  };
};

export type CreateTicket = {
  subject: string;
  description: string;
  category: string;
  school_id: string;
  attachments?: TicketAttachment[];
};

export type UpdateTicket = {
  subject?: string;
  description?: string;
  category?: string;
  school_id?: string;
  status?: TicketApiCode;
  newComment?: string;
  attachments?: TicketAttachment[];
};

/**
 * The API returns profiles in french.
 * This mapping and utility function simplify working with them.
 */

const API_PROFILE_TO_PROFILE: Record<ApiProfile, Profile> = {
  Elève: 'Student',
  Enseignant: 'Teacher',
  Parent: 'Relative',
  Personnel: 'Personnel',
  Invité: 'Guest',
  Staff: 'Personnel',
};

export function mapApiProfileToProfile(
  apiProfile: ApiProfile,
): Profile | undefined {
  return API_PROFILE_TO_PROFILE[apiProfile] ?? apiProfile;
}

export type TicketType = 'all' | 'mine' | 'other';

export type TicketComment = {
  id: number;
  ticket_id: number;
  owner: string;
  owner_name: string;
  created: string;
  modified: string;
  content: string;
};

export type TicketEvent = {
  event: string;
  event_date: string;
  event_type: number;
  school_id: string;
  status: TicketApiCode | -1;
  user_id: string;
  username: string;
};

export type TicketApiCode = 1 | 2 | 3 | 4 | 5;

export const NEW_OR_OPEN_STATUSES = [1, 2] as const satisfies TicketApiCode[];

export type TicketStatusText =
  | 'nouveau'
  | 'ouvert'
  | 'résolu'
  | 'fermé'
  | 'en_attente';

/**
 * Mapping of ticket statuses to their corresponding classnames.
 */
export const TICKET_STATUS_CLASS: Record<TicketApiCode, string> = {
  1: 'ticket-status-info',
  2: 'ticket-status-warning',
  3: 'ticket-status-success',
  4: 'ticket-status-success',
  5: 'ticket-status-warning',
};

export const TICKET_STATUS_BY_CODE: Record<TicketApiCode, { label: string }> = {
  1: { label: 'support.ticket.status.new' },
  2: { label: 'support.ticket.status.opened' },
  3: { label: 'support.ticket.status.resolved' },
  4: { label: 'support.ticket.status.closed' },
  5: { label: 'support.ticket.status.waiting' },
};

export function getTicketStatusText(apiCode: TicketApiCode): string {
  const status = TICKET_STATUS_BY_CODE[apiCode];

  if (!status) {
    throw new Error(`Unknown ticket status code: ${apiCode}`);
  }

  return status.label;
}

export function ticketStatusToCode(statusText: string): number {
  const entry = Object.entries(TICKET_STATUS_BY_CODE).find(
    ([, value]) => value.label === statusText,
  );

  if (!entry) {
    throw new Error(`Unknown ticket status: ${statusText}`);
  }

  const [code] = entry;
  return Number(code);
}

export function getStatusLabels(): string[] {
  return Object.values(TICKET_STATUS_BY_CODE).map((status) => status.label);
}

export type TicketEscalationStatusCode = 1 | 2 | 3 | 4;

export const ESCALATION_STATUS: Record<string, TicketEscalationStatusCode> = {
  NOT_DONE: 1,
  IN_PROGRESS: 2,
  SUCCESSFUL: 3,
  FAILED: 4,
};
