# ===== Runtime con NGINX =========================================
FROM docker.io/library/nginx:1.27-alpine

# Crear ruta destino base y limpiar configuración por defecto en un solo layer
RUN rm -f /etc/nginx/conf.d/default.conf && \
    mkdir -p /sysg/progs/

# Copiar configuración de Nginx
COPY nginx.conf /etc/nginx/conf.d/app.conf

# Copiar los artefactos compilados desde el contexto (carpeta dist/ generada por el pipeline)
# Esto copiará el contenido de dist/ (ej. carpeta 'browser') a /sysg/progs/
COPY dist/ /sysg/progs/

# Exponer puerto 80
EXPOSE 80

# Healthcheck
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s CMD wget -qO- http://127.0.0.1/ || exit 1

# CMD ya está definido en la imagen base de nginx, pero puedes sobrescribirlo si necesitas
# CMD ["nginx", "-g", "daemon off;"]
