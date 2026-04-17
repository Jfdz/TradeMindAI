import "next-auth";
import "next-auth/jwt";

declare module "next-auth" {
  interface Session {
    accessToken?: string;
    tokenType?: string;
    expiresIn?: number;
  }
}

declare module "next-auth/jwt" {
  interface JWT {
    accessToken?: string;
    tokenType?: string;
    expiresIn?: number;
  }
}
