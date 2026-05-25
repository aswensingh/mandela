FROM node:24-alpine AS build
WORKDIR /build

# Use Corepack-managed pnpm to match local dev. npm chokes on `workspace:^`
# peer-dep references that some packages (e.g. @tsparticles/react v4) publish.
RUN corepack enable

COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
RUN corepack pnpm install --frozen-lockfile

COPY . .
RUN corepack pnpm run build

FROM nginx:alpine
COPY --from=build /build/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
