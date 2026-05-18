# Stage 1: install all dependencies needed to build the Next.js app
FROM node:22-alpine AS deps
WORKDIR /app
RUN apk add --no-cache npm
COPY package.json package-lock.json ./
RUN npm ci

# Stage 2: build the application
FROM node:22-alpine AS build
WORKDIR /app
ENV NEXT_TELEMETRY_DISABLED=1
# NEXT_PUBLIC_ vars are baked into the JS bundle at build time — pass via --build-arg
ARG NEXT_PUBLIC_CLERK_PUBLISHABLE_KEY
ARG NEXT_PUBLIC_CLERK_SIGN_IN_URL=/auth/login
ARG NEXT_PUBLIC_CLERK_SIGN_UP_URL=/auth/register
ARG NEXT_PUBLIC_CLERK_AFTER_SIGN_IN_URL=/dashboard
ARG NEXT_PUBLIC_CLERK_AFTER_SIGN_UP_URL=/dashboard
ENV NEXT_PUBLIC_CLERK_PUBLISHABLE_KEY=$NEXT_PUBLIC_CLERK_PUBLISHABLE_KEY
ENV NEXT_PUBLIC_CLERK_SIGN_IN_URL=$NEXT_PUBLIC_CLERK_SIGN_IN_URL
ENV NEXT_PUBLIC_CLERK_SIGN_UP_URL=$NEXT_PUBLIC_CLERK_SIGN_UP_URL
ENV NEXT_PUBLIC_CLERK_AFTER_SIGN_IN_URL=$NEXT_PUBLIC_CLERK_AFTER_SIGN_IN_URL
ENV NEXT_PUBLIC_CLERK_AFTER_SIGN_UP_URL=$NEXT_PUBLIC_CLERK_AFTER_SIGN_UP_URL
RUN apk add --no-cache npm
COPY --from=deps /app/node_modules ./node_modules
COPY . .
RUN npm run build

# Stage 3: run the production app with only production dependencies
FROM node:22-alpine AS runner
WORKDIR /app
ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1

RUN addgroup -S nextjs && adduser -S nextjs -G nextjs

COPY package.json package-lock.json ./
RUN npm ci --omit=dev && npm cache clean --force
RUN rm -rf /usr/local/lib/node_modules/npm /usr/local/bin/npm /usr/local/bin/npx

COPY --from=build /app/.next ./.next
COPY --from=build /app/public ./public
COPY next.config.mjs ./next.config.mjs

USER nextjs

EXPOSE 3000

CMD ["node", "./node_modules/next/dist/bin/next", "start"]
