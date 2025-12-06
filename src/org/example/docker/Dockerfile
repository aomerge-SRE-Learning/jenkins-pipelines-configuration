# ===== Etapa 1: Build de Angular ==========================================
FROM node:20-alpine AS build
WORKDIR /app

# Instalar dependencias con lockfile si existe
COPY package*.json ./

COPY public/ ./public/
COPY src/ ./src/
COPY angular.json ./
COPY tsconfig.json ./
COPY tsconfig.app.json ./
COPY tsconfig.spec.json ./

RUN npm install

RUN npm run build -- --configuration=production

# ===== Etapa 2: Runtime con NGINX =========================================
FROM nginx:1.27-alpine

# (Opcional) Limpiar conf por defecto
RUN rm -f /etc/nginx/conf.d/default.conf

# Crear ruta destino del build: /sysg/progs/<angular>/browser/

RUN mkdir -p /sysg/progs/

# Copiar el build (manteniendo la subcarpeta 'browser')
COPY --from=build /app/dist/ /sysg/progs/

# Copiar su nginx.conf (el que mapea /<angular>/ -> alias /sysg/progs/<angular>/browser/)
# Asegúrese de que este archivo contenga los 'location' de la Opción 1 (con alias a .../browser/)
COPY nginx.conf /etc/nginx/conf.d/app.conf

# (Opcional) Healthcheck simple
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s CMD wget -qO- http://127.0.0.1/ || exit 1
