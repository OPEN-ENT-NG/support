export function sortByLabel<T extends { label: string }>(items: T[]): T[] {
  return [...items].sort((a, b) => a.label.localeCompare(b.label));
}

export function sortByKey<T>(items: T[], key: keyof T): T[] {
  return [...items].sort((a, b) =>
    String(a[key]).localeCompare(String(b[key])),
  );
}
