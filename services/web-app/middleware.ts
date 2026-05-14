import { clerkMiddleware, createRouteMatcher } from "@clerk/nextjs/server";
import { NextResponse, type NextRequest } from "next/server";

const isProtected = createRouteMatcher(["/dashboard(.*)"]);
const isAuthPage = createRouteMatcher(["/auth(.*)"]);

function noopMiddleware(_req: NextRequest) {
  return NextResponse.next();
}

export default process.env.NEXT_PUBLIC_CLERK_PUBLISHABLE_KEY
  ? clerkMiddleware(async (auth, request) => {
      if (isProtected(request)) {
        await auth.protect({
          unauthenticatedUrl: new URL("/auth/login", request.url).toString(),
        });
      }

      if (isAuthPage(request)) {
        const { userId } = await auth();
        if (userId) {
          return NextResponse.redirect(new URL("/dashboard", request.url));
        }
      }
    })
  : noopMiddleware;

export const config = {
  matcher: [
    // Next.js statically parses config.matcher — TaggedTemplateExpression (String.raw) is rejected. Plain string required. // NOSONAR
    "/((?!_next|[^?]*\\.(?:html?|css|js(?!on)|jpe?g|webp|png|gif|svg|ttf|woff2?|ico|csv|docx?|xlsx?|zip|webmanifest)).*)",
    "/(api|trpc)(.*)",
  ],
};
