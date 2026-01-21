# Modelo de Seguridad y Gestión de Secretos

La seguridad es un componente crítico en la automatización de CI/CD. Esta librería implementa varias capas de protección para manejar credenciales de Docker y Kubernetes.

## 1. Gestión de Credenciales en Jenkins

Las credenciales nunca se escriben en texto plano en el código (`Jenkinsfile` o librería). Se utilizan los **Credentials Binding** de Jenkins.

*   **Docker Hub / Estándar:** ID `DockerHub` (Usuario/Password).
*   **Google Artifact Registry:** ID `artifact-registry-sa` (Secret File con JSON Key).
*   **Kubernetes:** Múltiples IDs según ambiente (`k8s_token_prod`, etc.), inyectados dinámicamente.

## 2. Conexión a Kubernetes (ClusterPipeline)

La clase `src/org/aomerge/config/ClusterPipeline.groovy` maneja la autenticación de forma segura:

### Kubeconfig Efímero
Para evitar que el archivo `kubeconfig` persista en el disco del agente de Jenkins o sea accesible por otros builds:

1.  El pipeline genera un archivo `kubeconfig` temporal único para esa ejecución.
2.  Se inyectan las credenciales (Token/Certificado) en ese archivo temporal.
3.  Se exporta la variable de entorno `KUBECONFIG` solo para el bloque de comandos `kubectl`/`helm`.
4.  Al finalizar el bloque, el archivo se elimina automáticamente (o se limpia el workspace).

### Enmascaramiento de Logs
Jenkins enmascara automáticamente las variables inyectadas con `withCredentials`. Si un script intenta imprimir `$K8S_TOKEN`, en la consola aparecerá `****`.

## 3. Seguridad en Contenedores (Podman)

Utilizamos **Podman** en lugar de Docker por su arquitectura "Daemonless" y "Rootless":
*   Los contenedores de construcción se ejecutan con el usuario `jenkins`, no como `root`.
*   Esto reduce la superficie de ataque si un script malicioso intentara escapar del contenedor.

## 4. Integridad y Validaciones (Skopeo)

Para evitar la sobreescritura accidental de imágenes en producción (Immutability), el pipeline implementa una validación pre-push:

1.  Se utiliza **Skopeo** para inspeccionar el registro remoto sin necesidad de descargar la imagen.
2.  Si el tag (versión del `package.json`) ya existe, el pipeline aborta la ejecución antes de construir o subir nada nuevo.
3.  Esto obliga al desarrollador a seguir un flujo de versionamiento semántico estricto.

## 5. Buenas Prácticas para Desarrolladores
*   **No subir secretos al Git:** Nunca incluir contraseñas o tokens en `deploy-helm.yaml` o `application.properties`.
*   **Usar Secretos de K8s:** Si la aplicación necesita una contraseña de base de datos, debe montarse como un `Secret` de Kubernetes, no como variable de entorno en texto plano en el Helm Chart.
