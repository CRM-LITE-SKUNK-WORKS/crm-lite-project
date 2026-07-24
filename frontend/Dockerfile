# Frontend image (FE-ADR-010). Deliberately mirrors the backend's build/runtime
# split (maven -> jre): the toolchain is confined to the build stage and never
# reaches the running container — no Node, no node_modules, no sources at runtime.
#
# Build context is the repository ROOT (see infra/docker-compose.yml), so every
# path below is repo-relative — the same convention as the backend Dockerfiles.
#
# Both tags are EXACT (FE-ADR-002 §4): node matches the pinned local Node, so a
# container build cannot diverge from a developer's build; nginx is on the stable
# line (even minor number — 1.31.x is mainline).

FROM node:22.23.1-alpine AS build
WORKDIR /build

# Keep the Angular CLI from phoning home during an image build: deterministic and
# safe on a build host without outbound access.
ENV NG_CLI_ANALYTICS=false

# Dependency layer first: it is invalidated only by a lockfile change, so editing
# a component reuses the cached install. `npm ci` installs exactly the lockfile.
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci

# Sources second, as explicit paths rather than the whole folder, so a host-built
# node_modules/dist can never land on top of what `npm ci` installed. This holds
# even if .dockerignore is edited later — belt and braces, because a Windows-built
# node_modules inside a Linux image fails in confusing ways.
COPY frontend/angular.json frontend/tsconfig.json frontend/tsconfig.app.json frontend/.postcssrc.json ./
COPY frontend/public ./public
COPY frontend/src ./src

RUN npm run build

FROM nginx:1.30.4-alpine AS runtime

# Replace the stock server block instead of adding a second one next to it.
COPY frontend/nginx.conf /etc/nginx/conf.d/default.conf

# Angular's application builder emits the browser bundle under dist/<project>/browser.
COPY --from=build /build/dist/frontend/browser /usr/share/nginx/html

EXPOSE 80
# The base image's CMD (nginx -g 'daemon off;') is already correct — nothing to override.
