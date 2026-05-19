# Contexto raiz do repositório (docker-compose: context .) — inclui tools/img para o Angular.
FROM node:20-alpine AS build
WORKDIR /workspace
COPY tools/img /workspace/tools/img
COPY frontend /workspace/frontend
WORKDIR /workspace/frontend
RUN npm ci --no-audit --no-fund
RUN npm run build

FROM nginx:1.25-alpine
# Evita conflito "duplicate listen" se existir default.conf do Alpine
RUN rm -f /etc/nginx/conf.d/default.conf
COPY frontend/nginx.conf /etc/nginx/nginx.conf
COPY --from=build /workspace/frontend/dist/frontend/browser /usr/share/nginx/html
EXPOSE 80
