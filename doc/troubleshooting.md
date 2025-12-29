# Troubleshooting y Errores Comunes

Esta guía recopila los problemas más frecuentes encontrados durante la implementación y operación de los pipelines y la infraestructura de laboratorio.

## 1. Infraestructura (Libvirt/KVM)

### Error: `Input/output error` o Disco en "Read-Only"
*   **Síntoma:** Comandos básicos como `ls` fallan, o no se pueden crear archivos.
*   **Causa:** Desconexión del disco físico o corrupción del sistema de archivos.
*   **Solución:**
    1.  En el host: Verificar espacio en disco.
    2.  En la VM: `sudo mount -o remount,rw /`

### Error: `SELinux label ... already in use`
*   **Síntoma:** La VM no arranca después de un apagado forzado.
*   **Solución:** Editar el XML de la VM (`virsh edit`) y establecer `<seclabel type='none'/>`.

## 2. Kubernetes (Nodos y Pods)

### Error: `FailedCreatePodSandBox ... resolv.conf: no such file`
*   **Síntoma:** Los Pods se quedan en `ContainerCreating`.
*   **Causa:** Kubelet no encuentra la configuración DNS en `/run/systemd/resolve/`.
*   **Solución (Worker Node):**
    ```bash
    sudo systemctl enable --now systemd-resolved
    # O crear enlace manual:
    sudo ln -s /etc/resolv.conf /run/systemd/resolve/resolv.conf
    ```

### Error: `Liveness/Readiness probe failed: 404`
*   **Síntoma:** El Pod inicia pero se reinicia constantemente (CrashLoopBackOff).
*   **Causa:** Kubernetes intenta verificar `/` pero la app Angular sirve en `/nombre-app/`.
*   **Solución:** Asegurarse de que el `probe.path` en Helm coincida con la configuración de Nginx. (La librería v2 lo hace automáticamente).

## 3. Jenkins Pipeline

### Error: `MissingPropertyException: No such property: K8S_TOKEN`
*   **Síntoma:** El pipeline falla al intentar autenticarse con K8s.
*   **Causa:** Groovy intenta interpolar `$K8S_TOKEN` como una variable de clase en lugar de una variable de shell.
*   **Solución:** Escapar el símbolo de dólar en el script: `token=\$K8S_TOKEN`.

### Error: `404 Not Found` en Nginx
*   **Síntoma:** La aplicación despliega pero al entrar al navegador da 404.
*   **Causa:** El `nginx.conf` tiene rutas hardcodeadas que no coinciden con el nombre del servicio.
*   **Solución:** Usar la versión dinámica de la librería que reemplaza `{{APP_NAME}}` por el nombre del `package.json`.

### Error: Pipeline no detecta el repositorio de configuración externa
*   **Síntoma:** El pipeline falla con error "No such directory: config/nombre-servicio".
*   **Causa:** El checkout del repositorio de configuración no se ejecutó o la estructura de carpetas no coincide con el nombre del servicio.
*   **Solución:** Verificar que en `Main.groovy` el `checkout` use `RelativeTargetDirectory: 'config'` y que la carpeta en el repo de configuración se llame exactamente igual que `package.json -> name`.

### Error: `dockerPush` o `deployK8s` no se ejecuta en ramas específicas
*   **Síntoma:** El pipeline termina correctamente pero no hace push de la imagen o no despliega en K8s.
*   **Causa:** La rama no coincide con las estrategias definidas (ej: `bugfix-*` tiene `dockerPush=false`).
*   **Solución:** Revisar la sección de branching en `AngularPipeline.groovy`. Si necesitas que una rama custom haga deploy, añádela al switch statement o renómbrala a un patrón reconocido (ej: `feature-*`).

### Error: Credenciales de Docker/Kubernetes no encontradas
*   **Síntoma:** `Credentials 'DockerHub' could not be found` o error similar para K8s.
*   **Causa:** Las credenciales no están configuradas en Jenkins con el ID exacto que espera la librería.
*   **Solución:** 
    1.  En Jenkins, ir a "Manage Jenkins" → "Credentials".
    2.  Crear credencial tipo "Username with password" con ID `DockerHub`.
    3.  Crear credencial tipo "Secret file" con ID `kube-config` (o token/certificado según tu cluster).

## 4. Podman y Contenedores

### Error: `permission denied while trying to connect to Podman socket`
*   **Síntoma:** El pipeline falla al intentar ejecutar comandos `podman`.
*   **Causa:** El usuario `jenkins` no tiene permisos para usar el socket de Podman.
*   **Solución:**
    ```bash
    sudo usermod -aG podman jenkins
    sudo systemctl restart jenkins
    ```

### Error: Imagen base no existe (`localhost/base-angular-nombre`)
*   **Síntoma:** El stage de Test o Build falla con "Error: image not found".
*   **Causa:** El stage de Config falló silenciosamente o no se ejecutó el `podman build` de `Dockerfile.base`.
*   **Solución:** Revisar logs del stage "Config". Asegurarse de que `Dockerfile.base` existe en la librería y se puede construir sin errores.

### Error: Volúmenes no se montan correctamente (`npm: command not found` dentro del contenedor)
*   **Síntoma:** Al ejecutar tests/build, el contenedor no encuentra `node_modules` o falla con comandos npm.
*   **Causa:** Los volúmenes montados (`-v $(pwd)/src:/app/src`) no apuntan a las rutas correctas.
*   **Solución:** Verificar que la estructura del proyecto coincida con las rutas esperadas (ej: código en `/src`, no en `/app` o raíz directa).

## 5. Helm y Despliegue en Kubernetes

### Error: `Error: UPGRADE FAILED: timed out waiting for the condition`
*   **Síntoma:** Helm reporta timeout después de 5-10 minutos.
*   **Causa:** Los Pods no llegan a estado `Ready` (puede ser por probes fallidos, imágenes no encontradas, etc.).
*   **Solución:** 
    1.  Ejecutar `kubectl get pods -n dev-labs` para ver el estado real.
    2.  Describir el Pod problemático con `kubectl describe pod <nombre>`.
    3.  Corregir la causa raíz (usualmente probes, imágenes o recursos insuficientes).

### Error: `Error: release name already exists`
*   **Síntoma:** Helm falla al intentar instalar por segunda vez.
*   **Causa:** El release ya existe pero está en estado fallido.
*   **Solución:** Usar `helm upgrade --install` (que ya hace la librería) o eliminar el release fallido con `helm uninstall nombre-servicio -n dev-labs`.

### Error: Valores de Helm no se aplican (imagen incorrecta desplegada)
*   **Síntoma:** El deployment usa una imagen vieja en lugar de la recién construida.
*   **Causa:** El flag `--set container.image=...` no está sobrescribiendo el valor.
*   **Solución:** Verificar el orden de los argumentos de Helm. Los `--set` deben ir **después** del `-f values.yaml` para que tengan prioridad.

## 6. Gestión de Red (Netplan/Cloud-Init)

### Error: IP se pierde al reiniciar la VM
*   **Síntoma:** Después de reiniciar el Worker, pierde su IP estática y el nodo aparece como `NotReady`.
*   **Causa:** Cloud-init sobrescribe el archivo de Netplan en cada boot.
*   **Solución:**
    ```bash
    echo "network: {config: disabled}" | sudo tee /etc/cloud/cloud.cfg.d/99-disable-network-config.cfg
    ```

### Error: `NetworkUnavailable` en el nodo de K8s
*   **Síntoma:** El nodo aparece con taint `node.kubernetes.io/network-unavailable`.
*   **Causa:** El CNI (Calico/Flannel) no se instaló correctamente o no detecta la interfaz de red.
*   **Solución:** Verificar que el pod de Calico/Flannel esté corriendo (`kubectl get pods -n kube-system`) y reiniciar los servicios de red del nodo.
