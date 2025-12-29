# Guía de Inicio Rápido (Onboarding)

Esta guía describe los pasos necesarios para integrar un nuevo proyecto Angular en el pipeline de CI/CD utilizando la Shared Library.

## Prerrequisitos
*   El proyecto debe ser una aplicación **Angular**.
*   Debe tener un archivo `package.json` válido en la raíz.

## Paso 1: Preparar el `package.json`
El pipeline utiliza el `name` y `version` definidos aquí para nombrar la imagen Docker y los recursos de Kubernetes.

```json
{
  "name": "mi-proyecto-angular",
  "version": "1.0.0",
  "scripts": {
    "build": "ng build",
    "test:ci": "ng test --no-watch --no-progress --browsers=ChromeHeadlessCI"
  },
  ...
}
```

## Paso 2: Crear el `Jenkinsfile`
En la raíz de tu repositorio de código, crea un archivo llamado `Jenkinsfile` con el siguiente contenido. Solo necesitas especificar el lenguaje y el registro de Docker.

```groovy
@Library('jenkins-pipelines-configuration') _

jenkinsPipelinesExample(
    language: 'angular',
    dockerRegistry: 'docker.io/tu-usuario', // O tu registro privado
    typeDeployd: 'helm' // Opcional, por defecto es helm
)
```

## Paso 3: Configuración de Despliegue
En el repositorio de configuración externa (`config-pipelines-jenkins`), crea la siguiente estructura:

```text
config/
  └── mi-proyecto-angular/      <-- Debe coincidir con el "name" del package.json
      └── deploy-helm.yaml      <-- Archivo de valores base
```

**Contenido mínimo de `deploy-helm.yaml`:**
```yaml
deployment:
  replicas: 2
container:
  port: 80
resources:
  requests:
    memory: "128Mi"
    cpu: "100m"
  limits:
    memory: "256Mi"
    cpu: "200m"
service:
  type: ClusterIP
  port: 80
  targetPort: 80
```

¡Listo! Al hacer push, Jenkins detectará la rama y ejecutará el pipeline automáticamente.
