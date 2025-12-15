# ===== Runtime con NGINX =========================================
FROM nginx:1.27-alpine

# (Opcional) Limpiar conf por defecto
RUN rm -f /etc/nginx/conf.d/default.conf

# Crear ruta destino base
RUN mkdir -p /sysg/progs/

# Copiar los artefactos compilados desde el contexto (carpeta dist/ generada por el pipeline)
# Esto copiará el contenido de dist/ (ej. carpeta 'browser') a /sysg/progs/
COPY dist/ /sysg/progs/

# Copiar configuración de Nginx
COPY nginx.conf /etc/nginx/conf.d/app.conf

# Healthcheck
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s CMD wget -qO- http://127.0.0.1/ || exit 1
