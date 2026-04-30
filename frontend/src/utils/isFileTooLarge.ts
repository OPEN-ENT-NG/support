export function isFileTooLarge(e: unknown): boolean {
  return (
    typeof e === 'object' &&
    e !== null &&
    (e as { error?: string }).error === 'file.too.large'
  );
}
