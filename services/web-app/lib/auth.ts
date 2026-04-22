import type { NextAuthOptions } from "next-auth";
import CredentialsProvider from "next-auth/providers/credentials";

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8082";

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
            return null;
          }

          const data = (await response.json()) as {
            userId?: string;
            email?: string;
            firstName?: string;
            lastName?: string;
            accessToken?: string;
          };

          return {
            id: data.userId ?? email,
            email: data.email ?? email,
            name: [data.firstName, data.lastName].filter(Boolean).join(" ") || email,
            accessToken: data.accessToken,
          } satisfies AuthUser;
        } catch {
          if (process.env.NODE_ENV !== "production") {
            return {
              id: "demo-user",
              email,
              name: "Demo User",
            } satisfies AuthUser;
          }

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
