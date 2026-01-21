# Estructura de Configuración Externa

La librería utiliza un patrón de **"Configuración Desacoplada"**. Esto significa que el código fuente de la aplicación y su configuración de despliegue (Kubernetes/Helm) viven en repositorios separados o se inyectan dinámicamente.

## Repositorio de Configuración
Existe un repositorio central (`config-pipelines-jenkins`) que almacena los valores específicos de cada servicio.

### Estructura de Directorios
```text
config-pipelines-jenkins/ (Branch: main, qa, dev)
├── config/
│   ├── angular-proyect/       
│   │   ├── deploy-helm.yaml   
│   │   └── setting.json       # Configuración específica de la RAMA
└── ...
```

## Archivo `setting.json` (Estrategia por Rama)
Para mejorar la limpieza y el aislamiento, el `setting.json` ya no contiene todos los ambientes en un solo archivo. Ahora, el pipeline descarga la **rama correspondiente** del repositorio de configuración (ej: si la app corre en `qa`, descarga la rama `qa` de configuración).

### Ejemplo de setting.json (Estructura Plana y Limpia)
Al estar en la rama correcta, el archivo se simplifica drásticamente:

```json
{
  "project": "mi-app",
  "namespace": "qa-ns",
  "credentials": {
    "tokenRef": "k8s_token_qa",
    "serverRef": "k8s_server_qa",
    "caRef": "k8s_ca_data_qa"
  },
  "docker": {
    "registry": "docker.io/aomerge-qa",
    "credentialId": "DockerHub"
  }
}
```

## Ventajas de este Nuevo Enfoque
1.  **Aislamiento Total**: Un cambio en la configuración de `dev` no puede afectar accidentalmente a `production` ya que viven en ramas distintas de Git.
2.  **Simplicidad**: El pipeline carga los valores directamente sin necesidad de filtrar por el nombre del ambiente.
3.  **Flexibilidad de Ingress**: Permite variar hosts y paths de ingress de forma independiente por cada rama de configuración.

## Inyección Dinámica de Valores
Durante la etapa de `deploy`, la clase `AngularPipeline.groovy` ejecuta Helm inyectando los siguientes valores automáticamente:

| Valor Helm | Origen del Dato | Descripción |
| :--- | :--- | :--- |
| `app.name` | `package.json` | Nombre de la aplicación. |
| `container.image` | Build Stage | Imagen Docker recién construida con tag único. |
| `service.name` | `package.json` | Nombre del servicio K8s (`nombre-service`). |
| `probe.path` | `package.json` | Ruta de healthcheck (`/nombre-app/`). |

Esto permite que el chart de Helm sea genérico y reutilizable.
