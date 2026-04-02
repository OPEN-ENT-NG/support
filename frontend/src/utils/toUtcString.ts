// if the backend omits a timezone indicator, assume UTC
// (same behaviour as the old Angular code)
export default function toUtcString(dateStr: string): string {
  if (/^.*Z.*$/.test(dateStr)) return dateStr;
  return dateStr + 'Z';
}
