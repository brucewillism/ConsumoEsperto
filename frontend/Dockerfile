# Usando uma imagem Node leve para build
FROM node:20-alpine AS build
WORKDIR /workspace

# Copia os arquivos do frontend (já que o contexto build é a própria pasta frontend)
COPY . /workspace/frontend
WORKDIR /workspace/frontend

# Executa a instalação e build do Angular
RUN npm ci --no-audit --no-fund
RUN npm run build

# Configura o Nginx para servir o app
FROM nginx:1.25-alpine
RUN rm -f /etc/nginx/conf.d/default.conf

# Ajustado para pegar o nginx.conf direto da raiz do contexto do frontend
COPY nginx.conf /etc/nginx/nginx.conf
COPY --from=build /workspace/frontend/dist/frontend/browser /usr/share/nginx/html
EXPOSE 80
