import { ID, odeServices } from '@edifice.io/client';

export function getAvatarURL(userId: ID): string {
  return odeServices.directory().getAvatarUrl(userId, 'user');
}
