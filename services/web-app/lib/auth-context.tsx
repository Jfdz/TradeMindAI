"use client";

import { createContext, useContext } from "react";
import type { UserResource } from "@clerk/types";
import type { ReactNode } from "react";
import { useUser, useClerk } from "@clerk/nextjs";

export type AuthContextValue = {
  user: UserResource | null | undefined;
  signOut: (opts?: { redirectUrl?: string }) => Promise<void>;
};

const noop = async () => {};

export const AuthContext = createContext<AuthContextValue>({
  user: null,
  signOut: noop,
});

export function useAuthUser(): AuthContextValue {
  return useContext(AuthContext);
}

export function ClerkAuthBridge({ children }: { children: ReactNode }) {
  const { user } = useUser();
  const { signOut } = useClerk();
  return (
    <AuthContext.Provider value={{ user, signOut }}>
      {children}
    </AuthContext.Provider>
  );
}
