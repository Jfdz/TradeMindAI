import { beforeEach, describe, expect, it, vi } from "vitest";

describe("authOptions", () => {
  const originalEnv = process.env.NODE_ENV;

  beforeEach(() => {
    vi.restoreAllMocks();
    vi.resetModules();
    process.env.NODE_ENV = originalEnv;
  });

  it("returns backend access token on successful credential login", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        ok: true,
        json: async () => ({ accessToken: "access-123" }),
      }),
    );

    const { authOptions } = await import("./auth");
    const user = await credentialsProvider(authOptions).authorize({
      email: " user@example.com ",
      password: "secret",
    });

    expect(fetch).toHaveBeenCalledWith(
      "http://localhost:8082/api/v1/auth/login",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({ email: "user@example.com", password: "secret" }),
      }),
    );
    expect(user).toEqual({
      id: "user@example.com",
      email: "user@example.com",
      name: "user@example.com",
      accessToken: "access-123",
    });
  });

  it("returns null when backend login fails", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        ok: false,
        status: 401,
        statusText: "Unauthorized",
      }),
    );

    const { authOptions } = await import("./auth");
    const user = await credentialsProvider(authOptions).authorize({
      email: "user@example.com",
      password: "bad-password",
    });

    expect(user).toBeNull();
  });

  it("returns demo user in non-production when backend is unreachable", async () => {
    process.env.NODE_ENV = "development";
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new Error("network down")));

    const { authOptions } = await import("./auth");
    const user = await credentialsProvider(authOptions).authorize({
      email: "demo@example.com",
      password: "secret",
    });

    expect(user).toEqual({
      id: "demo-user",
      email: "demo@example.com",
      name: "Demo User",
    });
  });
});

function credentialsProvider(authOptions: { providers: unknown[] }) {
  const provider = authOptions.providers[0] as {
    authorize?: (credentials?: Record<string, string>) => Promise<unknown>;
    options?: {
      authorize?: (credentials?: Record<string, string>) => Promise<unknown>;
    };
  };
  return (provider.options?.authorize ? provider.options : provider) as {
    authorize(credentials?: Record<string, string>): Promise<unknown>;
  };
}
