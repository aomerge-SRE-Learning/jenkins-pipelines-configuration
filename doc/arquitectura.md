# Documentación Técnica: Jenkins Shared Library (CI/CD Framework)

Este proyecto implementa una **Shared Library** de Jenkins diseñada para estandarizar y automatizar el ciclo de vida de aplicaciones (Angular, Java, etc.) en un entorno de Kubernetes.

## 1. Arquitectura Inicial

La arquitectura se basa en el principio de **"Pipeline as Code"** y **Abstracción de Infraestructura**.

### Diagrama de Flujo Lógico
1. **Jenkinsfile (App Repo):** Invoca la librería.
2. **Main.groovy:** Orquestador principal que detecta el tipo de proyecto.
3. **Clases de Pipeline (AngularPipeline, JavaPipeline):** Contienen la lógica específica del lenguaje.
4. **Recursos (Resources):** Plantillas base de Docker, Nginx y Helm.
5. **Infraestructura (ClusterPipeline):** Abstracción para interactuar con Kubernetes/Helm.

## 2. Componentes del Sistema

### `src/org/aomerge/`
*   **`Main.groovy`**: Punto de entrada. Gestiona el checkout de configuraciones externas y delega la ejecución según el lenguaje detectado.
*   **`angular/AngularPipeline.groovy`**: Gestiona el ciclo de vida de Angular (Build con Podman, Test, Push y Deploy dinámico).
*   **`config/ClusterPipeline.groovy`**: Encapsula la seguridad y conectividad con K8s. Genera kubeconfigs temporales para evitar fugas de credenciales.

### `vars/`
*   **`jenkinsPipelinesExample.groovy`**: Define el DSL (Domain Specific Language) que usan los desarrolladores en sus proyectos.

### `resources/`
*   **`docker/`**: Dockerfiles base optimizados para compilación y runtime.
*   **`nginx/`**: Plantillas de configuración dinámica para SPAs.
*   **`helm/`**: Chart base para despliegues estandarizados.

## 3. Toma de Decisiones Técnicas

| Decisión | Razón |
| :--- | :--- |
| **Podman en lugar de Docker** | Mayor seguridad (rootless) y mejor integración en entornos Linux modernos. |
| **Configuración Externa (Git)** | Separa la lógica del pipeline (cómo se construye) de los valores del entorno (dónde se despliega). |
| **Placeholders en Nginx** | Permite que una sola plantilla de Nginx sirva para cualquier proyecto Angular sin edición manual. |
| **Helm --set dinámico** | Reduce la duplicación de datos en los archivos `values.yaml` y asegura que la imagen desplegada sea siempre la recién construida. |
| **Kubeconfig Efímero** | Seguridad. El token de K8s solo existe en memoria durante la ejecución del comando. |
| **Ejecución Aislada (Test/Build)** | Se utilizan contenedores efímeros con volúmenes montados para pruebas y compilación, evitando contaminar el nodo de Jenkins y garantizando idempotencia. |
| **Imagen Base Personalizada** | Se construye una imagen base con todas las dependencias de Angular y ChromeHeadless preinstaladas para reducir tiempos de ejecución y asegurar consistencia en las pruebas. |
| **Compilación vía Volúmenes (No Multi-stage)** | En lugar de ejecutar `npm install/build` dentro del Dockerfile final, se compila en un contenedor efímero montando el código fuente. Esto acelera el proceso al evitar copias innecesarias de contexto y permite generar una imagen final (Runtime) extremadamente ligera que solo contiene los estáticos. |

## 4. Propósito del Framework
El objetivo principal es que un desarrollador pueda desplegar una aplicación compleja en Kubernetes simplemente creando un `package.json` y un `Jenkinsfile` de una sola línea, sin necesidad de ser experto en Docker, Helm o Kubernetes.

## 5. Consideraciones Relevantes
*   **Escalabilidad:** La estructura permite añadir nuevos lenguajes (Python, Go) simplemente creando una nueva clase en `src/`.
*   **Mantenibilidad:** Los cambios en la seguridad o en la versión de Helm se aplican en un solo lugar y afectan a todos los proyectos.
*   **Resiliencia:** Incluye guías de recuperación (ver `README.md` en la raíz de Labs) para fallos de infraestructura comunes.

## 6. Estrategia de Ramas y Ambientes

El pipeline detecta automáticamente la rama y configura el comportamiento según el flujo de trabajo:

| Rama | Ambiente | Docker Push | Deploy K8s | Aprobación Manual |
| :--- | :--- | :---: | :---: | :---: |
| `master` / `main` | production | ✅ | ✅ | ✅ |
| `dev` | development | ✅ | ✅ | ❌ |
| `QA` | qa | ✅ | ✅ | ❌ |
| `feature-*` | feature | ✅ | ✅ | ❌ |
| `bugfix-*` | bugfix | ❌ | ❌ | ❌ |
| `hotfix-*` | hotfix | ✅ | ✅ | ✅ |
| Otras | (nombre-rama) | ❌ | ❌ | ❌ |

**Nota:** Las ramas `bugfix-*` solo ejecutan tests y build local, útil para validación rápida sin impactar registros o clusters.

## 7. Estrategia de Versionamiento

Las imágenes Docker se etiquetan automáticamente usando el siguiente formato:

```
{version}-{timestamp}.{buildNumber}
```

**Ejemplo:** `1.0.0-20251228.65`

**Componentes:**
*   `1.0.0`: Versión semántica extraída del `package.json`.
*   `20251228`: Fecha de compilación (YYYYMMDD).
*   `65`: Número de build de Jenkins.

**Ventajas:**
*   **Trazabilidad:** Cada imagen puede rastrearse hasta el build exacto en Jenkins.
*   **Rollback:** Facilita volver a una versión anterior conociendo solo la fecha.
*   **Unicidad:** No hay riesgo de colisiones de tags entre diferentes builds.

## 8. Requisitos del Agente Jenkins

Para ejecutar esta librería, el nodo de Jenkins debe cumplir con:

| Herramienta | Versión Mínima | Propósito |
| :--- | :--- | :--- |
| **Podman** | 4.0+ | Construcción y ejecución de contenedores (rootless). |
| **Kubectl** | 1.28+ | Interacción con el cluster de Kubernetes. |
| **Helm** | 3.10+ | Despliegue de aplicaciones en K8s. |
| **Git** | 2.20+ | Clonado del repositorio de configuración externa. |
| **Bash** | 4.0+ | Ejecución de scripts shell. |

**Configuración de Jenkins:**
*   Credencial `DockerHub` (Usuario/Password) para push de imágenes.
*   Credencial `kube-config` (Secret File o Token) para autenticación en K8s.
*   Socket de Podman accesible para el usuario `jenkins`.

## 9. Ciclo de Vida de una Ejecución

```
┌─────────────────────────────────────────────────────────────────────┐
│                        COMMIT & TRIGGER                             │
│  Desarrollador hace push → Jenkins detecta cambio via webhook/SCM   │
└────────────────────────────┬────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│                      STAGE: Config                                  │
│  • Lee package.json (nombre, versión)                               │
│  • Detecta rama → Define ambiente y comportamiento                  │
│  • Clona repositorio de configuración externa (config-pipelines)    │
│  • Construye imagen base con dependencias (Dockerfile.base)         │
└────────────────────────────┬────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│                      STAGE: Test                                    │
│  • Ejecuta tests en contenedor efímero (npm run test:ci)            │
│  • Genera reportes de cobertura                                     │
│  • Falla el pipeline si los tests no pasan                          │
└────────────────────────────┬────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│                      STAGE: Build                                   │
│  • Compila la aplicación en contenedor (npm run build)              │
│  • Inyecta nombre dinámico en nginx.conf ({{APP_NAME}})             │
│  • Construye imagen Docker final (Runtime con Nginx)                │
│  • Push a DockerHub/Registry (si está habilitado)                   │
└────────────────────────────┬────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│                      STAGE: Deploy                                  │
│  • Genera kubeconfig temporal con credenciales                      │
│  • Ejecuta helm upgrade --install con --set dinámicos:              │
│    - container.image (imagen recién construida)                     │
│    - app.name, service.name, probe.path                             │
│  • Verifica estado del despliegue (Pods en Ready)                   │
└────────────────────────────┬────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│                      STAGE: Cleanup                                 │
│  • Limpia workspace de Jenkins                                      │
│  • (Futuro) Elimina imágenes antiguas del nodo (trash())            │
└─────────────────────────────────────────────────────────────────────┘
```

## 10. Gestión de Imágenes (Garbage Collection)

**Estado:** Planeado para implementación.

Para evitar saturar el disco del agente de Jenkins o el registro de Docker, se planea implementar un método `trash()` que:
*   Mantenga solo las últimas `N` imágenes de cada servicio (ej: 3).
*   Elimine automáticamente las versiones más antiguas después de cada build exitoso.
*   Use `podman images --format` para identificar y borrar tags obsoletos.

**Beneficio:** Reduce el consumo de almacenamiento y mantiene el registro limpio sin intervención manual.

---
*Boceto inicial v1.0 - 28 de diciembre de 2025*
