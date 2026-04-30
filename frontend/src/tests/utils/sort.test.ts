import { describe, expect, it } from 'vitest';
import { sortByKey, sortByLabel } from '~/utils/sort';

describe('sortByLabel', () => {
  it('sorts items alphabetically by label', () => {
    const items = [
      { label: 'Zèbre', value: 'z' },
      { label: 'Abeille', value: 'a' },
      { label: 'Mouton', value: 'm' },
    ];
    const sorted = sortByLabel(items);
    expect(sorted.map((i) => i.label)).toEqual(['Abeille', 'Mouton', 'Zèbre']);
  });

  it('does not mutate the original array', () => {
    const items = [
      { label: 'B', value: 'b' },
      { label: 'A', value: 'a' },
    ];
    const original = [...items];
    sortByLabel(items);
    expect(items).toEqual(original);
  });

  it('returns empty array when given empty array', () => {
    expect(sortByLabel([])).toEqual([]);
  });

  it('handles single item', () => {
    const items = [{ label: 'Seul', value: 's' }];
    expect(sortByLabel(items)).toEqual(items);
  });
});

describe('sortByKey', () => {
  it('sorts items alphabetically by the given key', () => {
    const schools = [
      { id: '1', name: 'Zola' },
      { id: '2', name: 'Albert' },
      { id: '3', name: 'Martin' },
    ];
    const sorted = sortByKey(schools, 'name');
    expect(sorted.map((s) => s.name)).toEqual(['Albert', 'Martin', 'Zola']);
  });

  it('does not mutate the original array', () => {
    const items = [{ name: 'B' }, { name: 'A' }];
    const original = [...items];
    sortByKey(items, 'name');
    expect(items).toEqual(original);
  });

  it('returns empty array when given empty array', () => {
    expect(sortByKey([], 'name')).toEqual([]);
  });
});
