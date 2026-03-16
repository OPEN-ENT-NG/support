export type UserApp = {
  name: string;
  [key: string]: unknown;
};

export type UserInfo = {
  apps: UserApp[];
  [key: string]: unknown;
};
