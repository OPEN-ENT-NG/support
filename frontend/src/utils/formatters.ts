import toUtcString from './toUtcString';

export function formaterDate(date: string): string {
  return new Date(toUtcString(date)).toLocaleString('fr-FR', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}
