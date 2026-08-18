# Build context is the repo root so the workspace and api-client resolve.
#   docker build -f web-ui/Dockerfile -t decorating-web .

FROM node:22-alpine AS base
ENV PNPM_HOME=/pnpm
ENV PATH="$PNPM_HOME:$PATH"
RUN corepack enable

FROM base AS deps
WORKDIR /repo
COPY pnpm-lock.yaml pnpm-workspace.yaml package.json ./
COPY web-ui/package.json ./web-ui/
COPY api-client/package.json ./api-client/
# --ignore-scripts: `nuxt prepare` needs the source tree, which is not copied yet.
RUN pnpm install --frozen-lockfile --ignore-scripts

FROM base AS build
WORKDIR /repo
COPY --from=deps /repo/node_modules ./node_modules
COPY --from=deps /repo/web-ui/node_modules ./web-ui/node_modules
COPY --from=deps /repo/api-client/node_modules ./api-client/node_modules
COPY . .
RUN pnpm --filter @decorating/web build

FROM node:22-alpine AS runtime
WORKDIR /app
ENV NODE_ENV=production
ENV NITRO_PORT=3000
ENV NITRO_HOST=0.0.0.0

RUN addgroup -S app && adduser -S app -G app
USER app

# The Nitro output bundles everything it needs; node_modules is not copied.
COPY --from=build /repo/web-ui/.output ./.output

EXPOSE 3000
CMD ["node", ".output/server/index.mjs"]
