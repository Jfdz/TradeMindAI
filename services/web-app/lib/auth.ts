import type { NextAuthOptions } from "next-auth";
import CredentialsProvider from "next-auth/providers/credentials";

// Server-side only — never exposed to the browser bundle
const API_BASE_URL = process.env.API_BASE_URL ?? process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8082";

type AuthUser = {
  id: string;
  email: string;
  name?: string;
  accessToken?: string;
};

export const authOptions: NextAuthOptions = {
  providers: [
    CredentialsProvider({
      name: "Credentials",
      credentials: {
        email: { label: "Email", type: "email" },
        password: { label: "Password", type: "password" },
      },
      async authorize(credentials) {
        const email = credentials?.email?.trim();
        const password = credentials?.password;

        if (!email || !password) {
          return null;
        }

        try {
          const response = await fetch(`${API_BASE_URL}/api/v1/auth/login`, {
            method: "POST",
            headers: {
              "Content-Type": "application/json",
              Accept: "application/json",
            },
            body: JSON.stringify({ email, password }),
          });

          if (!response.ok) {
            console.error("[auth] Backend login failed:", response.status, response.statusText);
            return null;
          }

          // Backend returns { accessToken, tokenType, expiresIn }
          const data = (await response.json()) as {
            accessToken?: string;
            tokenType?: string;
            expiresIn?: number;
          };

          if (!data.accessToken) {
            console.error("[auth] Backend login response missing accessToken");
            return null;
          }

          // Extract refresh_token from Set-Cookie so it can be stored in the NextAuth
          // JWT and used for token refresh. The backend sets it as an HTTP-only cookie,
          // but NextAuth's authorize runs server-side and cannot forward it automatically.
          const setCookie = response.headers.get("set-cookie") ?? "";
          const refreshTokenMatch = setCookie.match(/refresh_token=([^;]+)/);
          const refreshToken = refreshTokenMatch?.[1] ?? undefined;

          return {
            id: email,
            email,
            name: email,
            accessToken: data.accessToken,
            refreshToken,
          } as AuthUser & { refreshToken?: string };
        } catch (err) {
          console.error("[auth] Failed to reach backend at", API_BASE_URL, err);
          return null;
        }
      },
    }),
  ],
  session: {
    strategy: "jwt",
  },
  callbacks: {
    async jwt({ token, user }) {
      if (user) {
        token.sub = user.id;
        token.email = user.email;
        token.name = user.name;
        token.accessToken = (user as AuthUser).accessToken;
        const u = user as AuthUser & { refreshToken?: string };
        if (u.refreshToken) token.refreshToken = u.refreshToken;
      }

      return token;
    },
    async session({ session, token }) {
      if (session.user) {
        session.user.id = token.sub ?? "";
      }

      session.accessToken = typeof token.accessToken === "string" ? token.accessToken : undefined;
      return session;
    },
  },
  pages: {
    signIn: "/auth/login",
  },
};
