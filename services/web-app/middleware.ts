import { clerkMiddleware, createRouteMatcher } from "@clerk/nextjs/server";

const isProtected = createRouteMatcher(["/dashboard(.*)"]);
const isAuthPage = createRouteMatcher(["/auth(.*)"]);

export default clerkMiddleware(async (auth, request) => {
  if (isProtected(request)) {
    await auth.protect({
      unauthenticatedUrl: new URL("/auth/login", request.url).toString(),
    });
  }

  if (isAuthPage(request)) {
    const { userId } = await auth();
    if (userId) {
      const url = new URL("/dashboard", request.url);
      const { NextResponse } = await import("next/server");
      return NextResponse.redirect(url);
    }
  }
});

export const config = {
  matcher: [
    "/((?!_next|[^?]*\\.(?:html?|css|js(?!on)|jpe?g|webp|png|gif|svg|ttf|woff2?|ico|csv|docx?|xlsx?|zip|webmanifest)).*)",
    "/(api|trpc)(.*)",
  ],
};
