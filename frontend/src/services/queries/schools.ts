import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useIsAdmc, useIsAdml, useUser } from '@edifice.io/react';
import { useMemo } from 'react';
import { School } from '~/models';
import { getSchools } from '../api';

export const schoolsQueryKeys = {
  all: ['schools'] as const,
  byId: (schoolId: string) => ['schools', schoolId] as const,
};

export const schoolsQueryFns = {
  all: () => getSchools(),
  byId: (schoolId: string) =>
    getSchools().then((schools) =>
      schools.find((school) => school.id === schoolId),
    ),
};

export const useSchools = () => {
  const { isAdmc } = useIsAdmc();
  const { isAdml } = useIsAdml();
  const { user } = useUser();
  const isAdmin = isAdmc || isAdml;

  const { data, isPending } = useQuery({
    queryKey: schoolsQueryKeys.all,
    queryFn: () => getSchools(),
    enabled: isAdmin,
  });

  const schools = useMemo(
    () =>
      isAdmin
        ? ((data as School[]) ?? [])
        : ((user?.structures ?? []).map((id, i) => ({
            id,
            name: user?.structureNames?.[i] ?? id,
          })) as School[]),
    [isAdmin, data, user?.structures, user?.structureNames],
  );

  return { schools, isPending: isAdmin && isPending };
};

export const useSchoolById = (schoolId: string) => {
  const queryClient = useQueryClient();
  const { data } = useQuery({
    queryKey: schoolsQueryKeys.byId(schoolId),
    queryFn: () =>
      getSchools().then((schools) =>
        schools.find((school) => school.id === schoolId),
      ),
    initialData: () => {
      const schools = queryClient.getQueryData<School[]>(schoolsQueryKeys.all);
      return schools?.find((school) => school.id === schoolId);
    },
  });
  return data;
};
