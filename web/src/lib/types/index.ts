export type AccountType = "APP_ACCOUNT" | "ADMIN_ACCOUNT";

export type LoginApiResponse = {
    username: string;
    roles: string[];
    accountType?: AccountType;
    emailVerified?: boolean;
};
