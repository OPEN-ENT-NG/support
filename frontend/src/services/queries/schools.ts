import { useQuery, useQueryClient } from '@tanstack/react-query';
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
  const { data, isPending } = useQuery({
    queryKey: schoolsQueryKeys.all,
    queryFn: () => getSchools(),
  });
  return { schools: (data as School[]) ?? [], isPending };
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
