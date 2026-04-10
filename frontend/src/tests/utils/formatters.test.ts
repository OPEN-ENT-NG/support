import { describe, expect, it } from 'vitest';
import { formatDate } from '~/utils/formatters';

describe('formatDate', () => {
  it('formats a UTC date string to french locale', () => {
    const result = formatDate('2024-03-15T10:30:00Z');
    expect(result).toMatch(/15\/03\/2024/);
    expect(result).toMatch(/30/); // minutes always present regardless of locale separator
  });

  it('appends Z when no timezone indicator is present', () => {
    const withZ = formatDate('2024-06-01T08:00:00Z');
    const withoutZ = formatDate('2024-06-01T08:00:00');
    expect(withZ).toBe(withoutZ);
  });

  it('returns a non-empty string for a valid date', () => {
    const result = formatDate('2023-01-01T00:00:00Z');
    expect(result).toBeTruthy();
    expect(typeof result).toBe('string');
  });
});
