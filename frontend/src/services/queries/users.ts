import { useQuery } from '@tanstack/react-query';
import { getUserInfo, getUserProfile } from '../api';

export const userQueryKeys = {
  profileById: (userId: string) => ['users', userId, 'profile'] as const,
  appsByUserId: () => ['user', 'apps'] as const,
};

export const useUserProfile = (userId: string | undefined) => {
  const { data } = useQuery({
    queryKey: userQueryKeys.profileById(userId ?? 'unknown'),
    queryFn: () => getUserProfile(userId!),
    enabled: !!userId,
  });
  return data;
};

export const useUserApps = () => {
  const { data } = useQuery({
    queryKey: userQueryKeys.appsByUserId(),
    queryFn: () => getUserInfo(),
    select: (data) => data.apps.map((a) => a.name),
  });
  return data;
};
