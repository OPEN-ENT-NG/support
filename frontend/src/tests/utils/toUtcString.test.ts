import { describe, expect, it } from 'vitest';
import toUtcString from '~/utils/toUtcString';

describe('toUtcString', () => {
  it('returns the string unchanged when it already contains Z', () => {
    const date = '2024-01-15T10:00:00Z';
    expect(toUtcString(date)).toBe(date);
  });

  it('appends Z when no timezone indicator is present', () => {
    expect(toUtcString('2024-01-15T10:00:00')).toBe('2024-01-15T10:00:00Z');
  });

  it('does not double-append Z', () => {
    const date = '2024-01-15T10:00:00Z';
    expect(toUtcString(date).endsWith('ZZ')).toBe(false);
  });
});
