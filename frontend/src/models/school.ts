export type Parent = {
  name: string;
  [key: string]: unknown;
};

export type School = {
  UAI: string;
  distributions: unknown[];
  exports: unknown | null;
  externalId: string;
  feederName: string | null;
  hasApp: boolean | null;
  id: string;
  ignoreMFA: boolean | null;
  levelsOfEducation: number[];
  manualName: string | null;
  name: string;
  parents: Parent[];
  punctualTimetable: unknown | null;
  source: string;
  timetable: unknown | null;
};
