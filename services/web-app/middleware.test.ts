import { beforeEach, describe, expect, it, vi } from "vitest";

const protectMock = vi.fn();
const getUserIdMock = vi.fn<() => Promise<{ userId: string | null }>>(() => Promise.resolve({ userId: null }));

const authContextMock = {
  protect: protectMock,
};

vi.mock("@clerk/nextjs/server", () => ({
  clerkMiddleware: (handler: (auth: unknown, req: unknown) => unknown) => {
    return async (req: unknown) => {
      const { userId } = await getUserIdMock();
      const auth = async () => ({ userId });
      auth.protect = protectMock;
      return handler(auth, req);
    };
  },
  createRouteMatcher: (patterns: string[]) => {
    return (req: { nextUrl: URL }) =>
      patterns.some((p) => {
        const escaped = p.replace(/[()]/g, "").replace(/\.\*/g, ".*");
        const rx = new RegExp("^" + escaped);
        return rx.test(req.nextUrl.pathname);
      });
  },
}));

describe("middleware", () => {
  beforeEach(() => {
    protectMock.mockReset();
    getUserIdMock.mockResolvedValue({ userId: null });
  });

  it("protects dashboard routes for unauthenticated users", async () => {
    getUserIdMock.mockResolvedValue({ userId: null });
    protectMock.mockRejectedValueOnce(
      new Response(null, { status: 307, headers: { location: "/auth/login" } }),
    );

    const { default: middleware } = await import("./middleware");
    const request = requestFor("http://localhost:3000/dashboard");

    try {
      await (middleware as (req: unknown) => unknown)(request);
    } catch {
      // protect() throws the redirect response
    }

    expect(protectMock).toHaveBeenCalled();
  });

  it("redirects authenticated users away from auth pages", async () => {
    getUserIdMock.mockResolvedValue({ userId: "user-clerk-123" });

    const { default: middleware } = await import("./middleware");
    const request = requestFor("http://localhost:3000/auth/login");

    const result = (await (middleware as (req: unknown) => unknown)(request)) as Response | undefined;

    expect(result?.status).toBe(307);
    expect(result?.headers.get("location")).toContain("/dashboard");
  });
});

function requestFor(url: string) {
  const nextUrl = new URL(url);
  return { url, nextUrl } as never;
}
