import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useIsAdmlcOrAdmc, useUser } from '@edifice.io/react';
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
  const { isAdmlcOrAdmc } = useIsAdmlcOrAdmc();
  const { user } = useUser();

  const { data, isPending } = useQuery({
    queryKey: schoolsQueryKeys.all,
    queryFn: () => getSchools(),
    enabled: isAdmlcOrAdmc,
  });

  const schools = useMemo(() => {
    const adminSchools = isAdmlcOrAdmc ? ((data as School[]) ?? []) : [];
    const userSchools = (user?.structures ?? []).map((id, i) => ({
      id,
      name: user?.structureNames?.[i] ?? id,
    })) as School[];

    const merged = [...adminSchools, ...userSchools];

    // filter duplicates
    return merged.filter(
      (school, index, self) =>
        self.findIndex((s) => s.id === school.id) === index,
    );
  }, [isAdmlcOrAdmc, data, user]);

  return { schools, isPending: isAdmlcOrAdmc && isPending };
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
