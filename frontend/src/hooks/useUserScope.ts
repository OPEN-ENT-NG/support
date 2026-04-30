import { useUser } from '@edifice.io/react';

/**
 * Returns the list of school IDs the user is scoped to, or null if the user is ADMC
 * and has unrestricted access
 */
export function useUserScope(): string[] | null {
  const { user } = useUser();
  const functions = user?.functions;

  if (!functions) return [];

  if (functions.SUPER_ADMIN?.scope === null) return null; // Null means access to all schools

  const adminLocal = functions.ADMIN_LOCAL?.scope ?? [];
  const superAdmin = functions.SUPER_ADMIN?.scope ?? [];

  return [...adminLocal, ...superAdmin];
}
