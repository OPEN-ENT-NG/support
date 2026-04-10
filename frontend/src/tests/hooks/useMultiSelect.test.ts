import { act, renderHook } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { useMultiSelect } from '~/hooks/useMultiSelect';

type Item = { id: number; name: string };

const items: Item[] = [
  { id: 1, name: 'Alice' },
  { id: 2, name: 'Bob' },
  { id: 3, name: 'Charlie' },
];

const getId = (item: Item) => item.id;

describe('useMultiSelect', () => {
  it('starts with no selection', () => {
    const { result } = renderHook(() => useMultiSelect(items, getId));
    expect(result.current.selected).toEqual([]);
    expect(result.current.selectedIds.size).toBe(0);
  });

  describe('toggle', () => {
    it('adds an item when not selected', () => {
      const { result } = renderHook(() => useMultiSelect(items, getId));
      act(() => result.current.toggle(items[0]));
      expect(result.current.selected).toEqual([items[0]]);
    });

    it('removes an item when already selected', () => {
      const { result } = renderHook(() => useMultiSelect(items, getId));
      act(() => result.current.toggle(items[0]));
      act(() => result.current.toggle(items[0]));
      expect(result.current.selected).toEqual([]);
    });

    it('can select multiple items independently', () => {
      const { result } = renderHook(() => useMultiSelect(items, getId));
      act(() => result.current.toggle(items[0]));
      act(() => result.current.toggle(items[2]));
      expect(result.current.selected).toHaveLength(2);
      expect(result.current.selectedIds.has(1)).toBe(true);
      expect(result.current.selectedIds.has(3)).toBe(true);
      expect(result.current.selectedIds.has(2)).toBe(false);
    });
  });

  describe('toggleAll', () => {
    it('selects all items when none are selected', () => {
      const { result } = renderHook(() => useMultiSelect(items, getId));
      act(() => result.current.toggleAll());
      expect(result.current.selected).toEqual(items);
    });

    it('clears selection when all are selected', () => {
      const { result } = renderHook(() => useMultiSelect(items, getId));
      act(() => result.current.toggleAll());
      act(() => result.current.toggleAll());
      expect(result.current.selected).toEqual([]);
    });

    it('selects all when only some are selected', () => {
      const { result } = renderHook(() => useMultiSelect(items, getId));
      act(() => result.current.toggle(items[0]));
      act(() => result.current.toggleAll());
      expect(result.current.selected).toEqual(items);
    });
  });

  describe('isSelected', () => {
    it('returns false for unselected items', () => {
      const { result } = renderHook(() => useMultiSelect(items, getId));
      expect(result.current.isSelected(items[0])).toBe(false);
    });

    it('returns true for selected item', () => {
      const { result } = renderHook(() => useMultiSelect(items, getId));
      act(() => result.current.toggle(items[1]));
      expect(result.current.isSelected(items[1])).toBe(true);
      expect(result.current.isSelected(items[0])).toBe(false);
    });
  });

  describe('clear', () => {
    it('removes all selections', () => {
      const { result } = renderHook(() => useMultiSelect(items, getId));
      act(() => result.current.toggleAll());
      act(() => result.current.clear());
      expect(result.current.selected).toEqual([]);
      expect(result.current.selectedIds.size).toBe(0);
    });
  });

  describe('selectedIds', () => {
    it('is a Set of selected ids', () => {
      const { result } = renderHook(() => useMultiSelect(items, getId));
      act(() => result.current.toggle(items[0]));
      act(() => result.current.toggle(items[2]));
      expect(result.current.selectedIds).toBeInstanceOf(Set);
      expect(result.current.selectedIds.has(1)).toBe(true);
      expect(result.current.selectedIds.has(3)).toBe(true);
      expect(result.current.selectedIds.has(2)).toBe(false);
    });
  });
});
