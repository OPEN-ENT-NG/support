import { describe, expect, it } from 'vitest';
import { isFileTooLarge } from '~/utils/isFileTooLarge';

describe('isFileTooLarge', () => {
  it('returns true when error is "file.too.large"', () => {
    expect(isFileTooLarge({ error: 'file.too.large' })).toBe(true);
  });

  it('returns false for a different error string', () => {
    expect(isFileTooLarge({ error: 'network.error' })).toBe(false);
  });

  it('returns false for null', () => {
    expect(isFileTooLarge(null)).toBe(false);
  });

  it('returns false for undefined', () => {
    expect(isFileTooLarge(undefined)).toBe(false);
  });

  it('returns false for a plain string', () => {
    expect(isFileTooLarge('file.too.large')).toBe(false);
  });

  it('returns false for an object without error key', () => {
    expect(isFileTooLarge({ message: 'file.too.large' })).toBe(false);
  });

  it('returns false for an empty object', () => {
    expect(isFileTooLarge({})).toBe(false);
  });
});
