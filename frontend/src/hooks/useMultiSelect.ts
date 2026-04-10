import { useCallback, useMemo, useState } from 'react';

export function useMultiSelect<T>(
  items: T[],
  getId: (item: T) => string | number,
) {
  const [selected, setSelected] = useState<T[]>([]);

  const selectedIds = useMemo(
    () => new Set(selected.map(getId)),
    [selected, getId],
  );

  const isSelected = useCallback(
    (item: T) => selectedIds.has(getId(item)),
    [selectedIds, getId],
  );

  const toggle = useCallback(
    (item: T) => {
      const id = getId(item);
      setSelected((prev) =>
        prev.some((s) => getId(s) === id)
          ? prev.filter((s) => getId(s) !== id)
          : [...prev, item],
      );
    },
    [getId],
  );

  const toggleAll = useCallback(() => {
    setSelected((prev) => (prev.length === items.length ? [] : items));
  }, [items]);

  const clear = useCallback(() => setSelected([]), []);

  return { selected, selectedIds, isSelected, toggle, toggleAll, clear };
}
