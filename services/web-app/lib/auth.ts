import type { JWT } from "next-auth/jwt";
import type { NextAuthOptions } from "next-auth";
import CredentialsProvider from "next-auth/providers/credentials";

// Server-side only — never exposed to the browser bundle
const API_BASE_URL = process.env.API_BASE_URL ?? process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8082";

const ADMIN_EMAILS = new Set(
  (process.env.ADMIN_EMAILS ?? "").split(",").map((e) => e.trim().toLowerCase()).filter(Boolean)
);

type AuthUser = {
  id: string;
  email: string;
  name?: string;
  accessToken?: string;
  refreshToken?: string;
  accessTokenExpires?: number;
  isAdmin?: boolean;
};

async function refreshAccessToken(token: JWT): Promise<JWT> {
  if (!token.refreshToken) {
    return { ...token, error: "RefreshAccessTokenError" };
  }
  try {
    const response = await fetch(`${API_BASE_URL}/api/v1/auth/refresh`, {
      method: "POST",
      headers: {
        Accept: "application/json",
        Cookie: `refresh_token=${token.refreshToken}`,
      },
    });

    if (!response.ok) {
      return { ...token, error: "RefreshAccessTokenError" };
    }

    const data = (await response.json()) as { accessToken?: string; expiresIn?: number };
    if (!data.accessToken) {
      return { ...token, error: "RefreshAccessTokenError" };
    }

    // Backend may rotate the refresh token — capture it if present
    const setCookie = response.headers.get("set-cookie") ?? "";
    const refreshTokenMatch = setCookie.match(/refresh_token=([^;]+)/);
    const newRefreshToken = refreshTokenMatch?.[1] ?? (token.refreshToken as string);

    return {
      ...token,
      accessToken: data.accessToken,
      accessTokenExpires: Date.now() + (data.expiresIn ?? 900) * 1000,
      refreshToken: newRefreshToken,
      error: undefined,
    };
  } catch (err) {
    console.error("[auth] Token refresh failed:", err);
    return { ...token, error: "RefreshAccessTokenError" };
  }
}

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

          // Extract refresh_token from Set-Cookie — stored in NextAuth JWT for server-side refresh
          const setCookie = response.headers.get("set-cookie") ?? "";
          const refreshTokenMatch = setCookie.match(/refresh_token=([^;]+)/);
          const refreshToken = refreshTokenMatch?.[1] ?? undefined;

          return {
            id: email,
            email,
            name: email,
            accessToken: data.accessToken,
            refreshToken,
            accessTokenExpires: Date.now() + (data.expiresIn ?? 900) * 1000,
            isAdmin: ADMIN_EMAILS.has(email.toLowerCase()),
          } as AuthUser;
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
      // Initial sign-in — populate all fields from the user object
      if (user) {
        const u = user as AuthUser;
        token.sub = u.id;
        token.email = u.email;
        token.name = u.name;
        token.accessToken = u.accessToken;
        token.refreshToken = u.refreshToken;
        token.accessTokenExpires = u.accessTokenExpires;
        token.isAdmin = u.isAdmin ?? false;
        return token;
      }

      // Token still valid (60s buffer before real expiry)
      const expires = typeof token.accessTokenExpires === "number" ? token.accessTokenExpires : 0;
      if (Date.now() < expires - 60_000) {
        return token;
      }

      // Access token expired — attempt silent refresh
      return refreshAccessToken(token);
    },

    async session({ session, token }) {
      if (session.user) {
        session.user.id = token.sub ?? "";
      }
      session.accessToken = typeof token.accessToken === "string" ? token.accessToken : undefined;
      session.error = token.error as "RefreshAccessTokenError" | undefined;
      session.isAdmin = token.isAdmin === true;
      return session;
    },
  },
  pages: {
    signIn: "/auth/login",
  },
};
