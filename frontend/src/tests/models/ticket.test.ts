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
    [1, 'Nouveau'],
    [2, 'Ouvert'],
    [3, 'Résolu'],
    [4, 'Fermé'],
    [5, 'En attente de réponse'],
  ] as [TicketApiCode, string][])(
    'returns "%s" for code %i',
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
    ['Nouveau', 1],
    ['Ouvert', 2],
    ['Résolu', 3],
    ['Fermé', 4],
    ['En attente de réponse', 5],
  ])('returns %i for "%s"', (label, expected) => {
    expect(ticketStatusToCode(label)).toBe(expected);
  });

  it('throws for an unknown status label', () => {
    expect(() => ticketStatusToCode('Inconnu')).toThrow(
      'Unknown ticket status: Inconnu',
    );
  });

  it('is the inverse of getTicketStatusText', () => {
    ([1, 2, 3, 4, 5] as TicketApiCode[]).forEach((code) => {
      expect(ticketStatusToCode(getTicketStatusText(code))).toBe(code);
    });
  });
});

describe('getStatusLabels', () => {
  it('returns all 5 status labels', () => {
    const labels = getStatusLabels();
    expect(labels).toHaveLength(5);
    expect(labels).toContain('Nouveau');
    expect(labels).toContain('Ouvert');
    expect(labels).toContain('Résolu');
    expect(labels).toContain('Fermé');
    expect(labels).toContain('En attente de réponse');
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
