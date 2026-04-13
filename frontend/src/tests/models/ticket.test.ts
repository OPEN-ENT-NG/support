import { describe, expect, it } from 'vitest';
import {
  ESCALATION_STATUS,
  getStatusLabels,
  getTicketStatusText,
  mapApiProfileToProfile,
  NEW_OR_OPEN_STATUSES,
  TICKET_STATUS_BY_CODE,
  ticketStatusToCode,
  type TicketApiCode,
} from '~/models/ticket';

describe('getTicketStatusText', () => {
  it.each([
    [1, 'support.ticket.status.new'],
    [2, 'support.ticket.status.opened'],
    [3, 'support.ticket.status.resolved'],
    [4, 'support.ticket.status.closed'],
    [5, 'support.ticket.status.waiting'],
  ] as [TicketApiCode, string][])(
    'returns i18n key "%s" for code %i',
    (code, expected) => {
      expect(getTicketStatusText(code)).toBe(expected);
    },
  );

  it('throws for an unknown code', () => {
    expect(() => getTicketStatusText(99 as TicketApiCode)).toThrow(
      'Unknown ticket status code: 99',
    );
  });
});

describe('ticketStatusToCode', () => {
  it.each([
    ['support.ticket.status.new', 1],
    ['support.ticket.status.opened', 2],
    ['support.ticket.status.resolved', 3],
    ['support.ticket.status.closed', 4],
    ['support.ticket.status.waiting', 5],
  ])('returns %i for "%s"', (label, expected) => {
    expect(ticketStatusToCode(label)).toBe(expected);
  });

  it('throws for an unknown status label', () => {
    expect(() => ticketStatusToCode('support.ticket.status.unknown')).toThrow(
      'Unknown ticket status: support.ticket.status.unknown',
    );
  });

  it('is the inverse of getTicketStatusText', () => {
    ([1, 2, 3, 4, 5] as TicketApiCode[]).forEach((code) => {
      expect(ticketStatusToCode(getTicketStatusText(code))).toBe(code);
    });
  });
});

describe('getStatusLabels', () => {
  it('returns all 5 status i18n keys', () => {
    const labels = getStatusLabels();
    expect(labels).toHaveLength(5);
    expect(labels).toContain('support.ticket.status.new');
    expect(labels).toContain('support.ticket.status.opened');
    expect(labels).toContain('support.ticket.status.resolved');
    expect(labels).toContain('support.ticket.status.closed');
    expect(labels).toContain('support.ticket.status.waiting');
  });

  it('returns labels in code order', () => {
    expect(getStatusLabels()).toEqual(
      Object.values(TICKET_STATUS_BY_CODE).map((s) => s.label),
    );
  });
});

describe('mapApiProfileToProfile', () => {
  it.each([
    ['Elève', 'Student'],
    ['Enseignant', 'Teacher'],
    ['Parent', 'Relative'],
    ['Personnel', 'Personnel'],
    ['Invité', 'Guest'],
    ['Staff', 'Personnel'],
  ] as const)('maps "%s" → "%s"', (api, expected) => {
    expect(mapApiProfileToProfile(api)).toBe(expected);
  });
});

describe('NEW_OR_OPEN_STATUSES', () => {
  it('contains status codes 1 and 2', () => {
    expect(NEW_OR_OPEN_STATUSES).toContain(1);
    expect(NEW_OR_OPEN_STATUSES).toContain(2);
  });

  it('does not contain closed/resolved status codes', () => {
    expect(NEW_OR_OPEN_STATUSES).not.toContain(3);
    expect(NEW_OR_OPEN_STATUSES).not.toContain(4);
    expect(NEW_OR_OPEN_STATUSES).not.toContain(5);
  });
});

describe('ESCALATION_STATUS', () => {
  it('has expected codes', () => {
    expect(ESCALATION_STATUS.NOT_DONE).toBe(1);
    expect(ESCALATION_STATUS.IN_PROGRESS).toBe(2);
    expect(ESCALATION_STATUS.SUCCESSFUL).toBe(3);
    expect(ESCALATION_STATUS.FAILED).toBe(4);
  });
});
