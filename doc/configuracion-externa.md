# Estructura de Configuración Externa

La librería utiliza un patrón de **"Configuración Desacoplada"**. Esto significa que el código fuente de la aplicación y su configuración de despliegue (Kubernetes/Helm) viven en repositorios separados o se inyectan dinámicamente.

## Repositorio de Configuración
Existe un repositorio central (`config-pipelines-jenkins`) que almacena los valores específicos de cada servicio.

### Estructura de Directorios
```text
config-pipelines-jenkins/
├── config/
│   ├── angular-proyect/       # Nombre del servicio (package.json)
│   │   ├── deploy-helm.yaml   # Valores específicos para Helm
│   │   └── setting.json       # NUEVO: Metadata de infraestructura (K8s, Docker)
│   ├── otro-servicio/
│   │   └── deploy-helm.yaml
│   └── ...
└── ...
```

## Archivo `setting.json` (Metadata de Infraestructura)
Este archivo es el cerebro del despliegue. Define a qué cluster, namespace y registro Docker se dirige la aplicación según el ambiente.

### Ejemplo de Configuración Multicloud/Multi-Registry
```json
{
  "project": "mi-app",
  "environments": {
    "development": {
      "namespace": "dev-ns",
      "credentials": {
        "tokenRef": "k8s_token_ci",
        "serverRef": "k8s_server_ci",
        "caRef": "k8s_ca_data_ci"
      },
      "docker": {
        "registry": "docker.io/aomerge-dev",
        "credentialId": "DockerHub"
      }
    },
    "production": {
      "namespace": "prod-ns",
      "docker": {
        "registry": "us-central1-docker.pkg.dev/project/repo",
        "credentialId": "gcp-sa-artifact-registry",
        "type": "artifact-registry"
      }
    }
  }
}
```

## Prioridad de Configuración (Precedencia)
El pipeline resuelve los valores siguiendo este orden de importancia (de menor a mayor):

1.  **Defaults de Librería:** Valores hardcodeados en `BranchConfig.groovy`.
2.  **Jenkinsfile Overrides:** Valores pasados en el mapa `jenkinsPipelinesExample([...])`.
3.  **External `setting.json`:** Si existe en el repo de configuración, sobrescribe todo lo anterior.

## Inyección Dinámica de Valores
Durante la etapa de `deploy`, la clase `AngularPipeline.groovy` ejecuta Helm inyectando los siguientes valores automáticamente:

| Valor Helm | Origen del Dato | Descripción |
| :--- | :--- | :--- |
| `app.name` | `package.json` | Nombre de la aplicación. |
| `container.image` | Build Stage | Imagen Docker recién construida con tag único. |
| `service.name` | `package.json` | Nombre del servicio K8s (`nombre-service`). |
| `probe.path` | `package.json` | Ruta de healthcheck (`/nombre-app/`). |

Esto permite que el chart de Helm sea genérico y reutilizable.
