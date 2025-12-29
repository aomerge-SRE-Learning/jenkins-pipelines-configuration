# Estructura de Configuración Externa

La librería utiliza un patrón de **"Configuración Desacoplada"**. Esto significa que el código fuente de la aplicación y su configuración de despliegue (Kubernetes/Helm) viven en repositorios separados o se inyectan dinámicamente.

## Repositorio de Configuración
Existe un repositorio central (`config-pipelines-jenkins`) que almacena los valores específicos de cada servicio.

### Estructura de Directorios
```text
config-pipelines-jenkins/
├── config/
│   ├── angular-proyect/       # Nombre del servicio (package.json)
│   │   └── deploy-helm.yaml   # Valores específicos para Helm
│   ├── otro-servicio/
│   │   └── deploy-helm.yaml
│   └── ...
└── ...
```

## Archivo `deploy-helm.yaml`
Este archivo define **CÓMO** se ejecuta la aplicación, pero no **QUÉ** aplicación es. Los valores específicos (nombres, imágenes, rutas) son inyectados por el pipeline.

### Ejemplo Limpio
```yaml
deployment:
  replicas: 2
  
container:
  port: 80
  # No definir 'image' ni 'name' aquí, el pipeline lo hace.

probe:
  liveness:
    initialDelaySeconds: 30
    periodSeconds: 10
  readiness:
    initialDelaySeconds: 10
    periodSeconds: 5

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

## Inyección Dinámica de Valores
Durante la etapa de `deploy`, la clase `AngularPipeline.groovy` ejecuta Helm inyectando los siguientes valores automáticamente:

| Valor Helm | Origen del Dato | Descripción |
| :--- | :--- | :--- |
| `app.name` | `package.json` | Nombre de la aplicación. |
| `container.image` | Build Stage | Imagen Docker recién construida con tag único. |
| `service.name` | `package.json` | Nombre del servicio K8s (`nombre-service`). |
| `probe.path` | `package.json` | Ruta de healthcheck (`/nombre-app/`). |

Esto permite que el chart de Helm sea genérico y reutilizable.
