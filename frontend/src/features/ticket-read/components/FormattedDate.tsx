import { Tooltip, useDate } from '@edifice.io/react';

// if the backend omits a timezone indicator, assume UTC
// (same behaviour as the old Angular code)
function toUtcString(dateStr: string): string {
  if (/^.*Z.*$/.test(dateStr)) return dateStr;
  return dateStr + 'Z';
}

export default function FormattedDate({ date }: { date: string }) {
  const { fromNow, formatDate } = useDate();
  const utcDate = toUtcString(date);

  const formattedDate = (dateStr: string) => {
    const dateObj = new Date(dateStr);
    if (isNaN(dateObj.getTime())) return null;

    const today = new Date();
    today.setHours(0, 0, 0, 0);

    const yesterday = new Date(today);
    yesterday.setDate(yesterday.getDate() - 1);

    const sevenDaysAgo = new Date(today);
    sevenDaysAgo.setDate(sevenDaysAgo.getDate() - 7);

    const oneYearAgo = new Date(today);
    oneYearAgo.setFullYear(oneYearAgo.getFullYear() - 1);

    if (dateObj >= today) return fromNow(dateObj);
    if (dateObj >= yesterday) return formatDate(dateObj, '[Hier à] HH[h]mm');
    if (dateObj >= sevenDaysAgo) return formatDate(dateObj, 'dddd [à] HH[h]mm');
    if (dateObj >= oneYearAgo)
      return formatDate(dateObj, '[Le] DD MMM [à] HH[h]mm');
    return formatDate(dateObj, '[Le] DD MM YYYY [à] HH[h]mm');
  };

  return (
    <Tooltip message={formatDate(utcDate, 'lll')} placement="top">
      <span>{formattedDate(utcDate)}</span>
    </Tooltip>
  );
}
