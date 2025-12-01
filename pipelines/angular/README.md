# Jenkins Pipeline con Docker - Angular

Configuración de pipelines CI/CD para aplicaciones Angular usando Docker.

## 📁 Estructura

```
pipelines/angular/
├── ci.groovy                      # Continuous Integration
├── cd.groovy                      # Continuous Deployment
├── Dockerfile.example             # Dockerfile multi-stage
├── Jenkinsfile.example            # Pipeline completo
├── docker-compose.staging.yml     # Deploy staging
└── docker-compose.production.yml  # Deploy production
```

## 🚀 Uso

### 1. Copiar archivos de ejemplo

```bash
cp Dockerfile.example Dockerfile
cp Jenkinsfile.example Jenkinsfile
```

### 2. Configurar variables en Jenkinsfile

Edita las variables de entorno:
- `IMAGE_NAME`: Nombre de tu aplicación
- `DOCKER_REGISTRY`: Tu registry (Docker Hub, ECR, etc.)
- Ajusta puertos según necesites

### 3. Ejecutar pipeline en Jenkins

El pipeline ejecutará automáticamente:
1. ✅ Checkout del código
2. ✅ Instalación de dependencias (en Docker)
3. ✅ Linting
4. ✅ Tests unitarios
5. ✅ Build de la aplicación
6. ✅ Construcción de imagen Docker
7. ✅ Escaneo de seguridad
8. ✅ Push a registry
9. ✅ Deploy al ambiente seleccionado
10. ✅ Health check

## 🐳 Comandos Docker útiles

### Build manual
```bash
docker build -t my-angular-app:1.0 .
```

### Run local
```bash
docker run -d -p 8080:80 my-angular-app:1.0
```

### Con Docker Compose
```bash
# Staging
docker-compose -f docker-compose.staging.yml up -d

# Production
docker-compose -f docker-compose.production.yml up -d
```

### Ver logs
```bash
docker logs -f angular-staging
```

### Detener
```bash
docker stop angular-staging
docker rm angular-staging
```

## 📝 Funciones disponibles

### CI (ci.groovy)
- `install()` - Instala dependencias
- `lint()` - Ejecuta linter
- `test()` - Ejecuta tests
- `build(environment)` - Construye la app
- `buildDockerImage(name, tag)` - Construye imagen
- `pushDockerImage(name, tag)` - Sube a registry
- `runSecurityScan(name, tag)` - Escanea vulnerabilidades

### CD (cd.groovy)
- `deployToDocker(env, image, tag, port)` - Deploy con Docker
- `deployToDockerCompose(env)` - Deploy con Compose
- `deployToKubernetes(namespace, deployment)` - Deploy K8s
- `healthCheck(url, retries)` - Verifica salud
- `rollback(env)` - Rollback
- `cleanOldImages(name, keep)` - Limpia imágenes antiguas

## 🔧 Personalización

### Cambiar nombre de carpeta dist
En `Dockerfile.example` línea 26:
```dockerfile
COPY --from=builder /app/dist/TU-APP-NAME /usr/share/nginx/html
```

### Agregar nginx.conf custom
Descomenta en `Dockerfile.example`:
```dockerfile
COPY nginx.conf /etc/nginx/nginx.conf
```

## 🔐 Credenciales en Jenkins

Configura en Jenkins:
- `docker-credentials`: Username/Password para Docker registry
- Ajusta el `credentialsId` en el Jenkinsfile

## 🎯 Ejemplo rápido

```groovy
// En tu Jenkinsfile
def ci = load 'pipelines/angular/ci.groovy'
def cd = load 'pipelines/angular/cd.groovy'

// CI
ci.install()
ci.test()
ci.buildDockerImage('my-app', '1.0')

// CD
cd.deployToDocker('staging', 'my-app', '1.0', '8080')
cd.healthCheck('http://localhost:8080')
```
