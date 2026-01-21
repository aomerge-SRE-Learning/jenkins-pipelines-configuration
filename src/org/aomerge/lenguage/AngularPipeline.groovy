package org.aomerge.lenguage
import groovy.json.JsonSlurper
import com.cloudbees.groovy.cps.NonCPS
import org.aomerge.config.ClusterPipeline
import org.aomerge.config.Trash
import org.aomerge.config.BranchConfig

class AngularPipeline implements Serializable {
    Map config
    String environment
    String serviceName
    String version
    boolean dockerPush = true
    boolean deployK8s = true
    boolean requireApproval = true
    BranchConfig branchConfig  // Nueva propiedad para manejar configuración por rama

    AngularPipeline(Map config) {
        this.config = config
    }
    
    void test(script) {        
        script.echo "🧪 Ejecutando tests de Angular..."        
        def language = config?.language ?: 'angular'
        def serviceName = this.serviceName ?: 'app'
        def volumeName = "node-modules-${serviceName.toLowerCase()}"
        
        // Crear volumen si no existe
        script.sh "podman volume inspect ${volumeName} > /dev/null 2>&1 || podman volume create ${volumeName}"
        
        script.sh """
            podman run --rm \\
                -v \$(pwd)/src:/app/src \\
                -v \$(pwd)/public:/app/public \\
                -v \$(pwd)/test-results:/app/test-results \\
                -v \$(pwd)/package-lock.json:/app/package-lock.json:ro \\
                -v ${volumeName}:/app/node_modules \\
                -w /app \\
                localhost/base-${language.toLowerCase()}-${serviceName.toLowerCase()} sh -c '
                    if [ ! -f /app/node_modules/.package-lock.json ] || ! cmp -s /app/package-lock.json /app/node_modules/.package-lock.json; then
                        echo "📦 Cambios en dependencias detectados. Ejecutando npm ci..."
                        npm ci
                        cp /app/package-lock.json /app/node_modules/.package-lock.json
                    else
                        echo "✅ Dependencias actualizadas. Saltando npm ci."
                    fi
                    npm run test:ci
                '
        """
    }
    
    void build(script) {
        def dockerfileContent = script.libraryResource('org/aomerge/docker/angular/Dockerfile')
        script.writeFile file: 'Dockerfile', text: dockerfileContent
        
        def nginxConfContent = script.libraryResource('org/aomerge/nginx/nginx.conf')
        // Reemplazamos el placeholder dinámicamente
        nginxConfContent = nginxConfContent.replace('{{APP_NAME}}', this.serviceName)
        script.writeFile file: 'nginx.conf', text: nginxConfContent
        
        script.echo "🔨 Building Angular application..."
        def language = config?.language ?: 'angular'
        def serviceName = this.serviceName ?: 'app'
        def environment = this.environment ?: 'development'
        def dockerRegistry = config?.dockerRegistry ?: 'docker.io'
        def volumeName = "node-modules-${serviceName.toLowerCase()}"
        
        // Crear volumen si no existe
        script.sh "podman volume inspect ${volumeName} > /dev/null 2>&1 || podman volume create ${volumeName}"
        
        script.sh """
            podman run --rm \\
                -v \$(pwd)/src:/app/src \\
                -v \$(pwd)/public:/app/public \\
                -v \$(pwd)/dist:/app/dist \\
                -v \$(pwd)/package-lock.json:/app/package-lock.json:ro \\
                -v ${volumeName}:/app/node_modules \\
                -w /app \\
                localhost/base-${language.toLowerCase()}-${serviceName.toLowerCase()} sh -c '
                    if [ ! -f /app/node_modules/.package-lock.json ] || ! cmp -s /app/package-lock.json /app/node_modules/.package-lock.json; then
                        echo "📦 Cambios en dependencias detectados. Ejecutando npm ci..."
                        npm ci
                        cp /app/package-lock.json /app/node_modules/.package-lock.json
                    else
                        echo "✅ Dependencias actualizadas. Saltando npm ci."
                    fi
                    npm run build --configuration=${environment}
                '
        """
        script.sh "podman build -t ${dockerRegistry}/${serviceName.toLowerCase()}:${this.version ?: 'latest'} ."
                
        if (this.dockerPush) {
            script.echo "🐳 Pushing Docker image to registry..."            
            def version = this.version
            script.echo "Pushing image: ${dockerRegistry}/${serviceName}:${version}"
            
            script.withCredentials([script.usernamePassword(credentialsId: 'DockerHub', usernameVariable: 'DOCKER_USER', passwordVariable: 'DOCKER_PASS')]) {
                script.withEnv([
                    "DOCKER_REGISTRY=${dockerRegistry}",
                    "SERVICE_NAME=${serviceName}",
                    "VERSION=${version}"
                ]) {
                    script.sh('''
                        echo "$DOCKER_PASS" | podman login --username "$DOCKER_USER" --password-stdin docker.io

                        # Determinar el path de la imagen
                        if [ "$DOCKER_REGISTRY" != "docker.io" ] && [ "$DOCKER_REGISTRY" != "localhost" ]; then
                            IMAGE_PATH="$DOCKER_REGISTRY/$SERVICE_NAME:$VERSION"
                        else
                            IMAGE_PATH="docker.io/$DOCKER_USER/$SERVICE_NAME:$VERSION"
                        fi

                        # Validar si la imagen ya existe en el registry (sin descargar)
                        if skopeo inspect "docker://$IMAGE_PATH" > /dev/null 2>&1; then
                            echo "❌ ERROR: La imagen $IMAGE_PATH ya existe en el registry."
                            echo "Por favor, actualiza la versión en package.json antes de hacer push."
                            podman logout docker.io 2>/dev/null || true
                            exit 1
                        fi

                        echo "✅ La versión $VERSION está disponible. Procediendo con el push..."

                        # Push de la imagen
                        podman push "$IMAGE_PATH"
                        
                        podman logout docker.io 2>/dev/null || true
                    ''')
                    script.echo "Imagen Docker subida correctamente: ${dockerRegistry}/${serviceName}:${version}"
                }
            }
        }

    }
    
    void loadExternalConfig(script) {
        def settingPath = "config/${this.serviceName}/setting.json"
        if (script.fileExists(settingPath)) {
            script.echo "🔍 Cargando metadata externa desde: ${settingPath}"
            try {
                def content = script.readFile(settingPath)
                def json = new JsonSlurper().parseText(content)
                this.branchConfig.updateFromExternal(json)
                
                // Actualizar propiedades locales para sincronía
                this.environment = this.branchConfig.environment
                script.echo "✅ Configuración de Cluster actualizada para ambiente: ${this.environment}"
                script.echo "📍 Namespace: ${this.branchConfig.k8sDetails.namespace}"
            } catch (Exception e) {
                script.echo "⚠️ Error al parsear ${settingPath}: ${e.message}"
            }
        } else {
            script.echo "ℹ️ No se encontró setting.json en ${settingPath}. Usando valores por defecto o Jenkinsfile."
        }
    }

    void deploy(script) {
        script.echo "🚀 Desplegando Angular a ${this.environment}..."
        def chartPath = "./helm"
        def valuesPath = "config/${this.serviceName}/deploy-helm.yaml"
        def ingressValuesPath = "config/${this.serviceName}/ingress-helm.yaml"
        def imageFull = "${config.dockerRegistry}/${this.serviceName.toLowerCase()}:${this.version}" 
        script.echo "Contenido de ${valuesPath}:"
        script.sh "cat ${valuesPath}"

        script.echo "Contenido de ${ingressValuesPath}:"
        script.sh "cat ${ingressValuesPath}"        

        if (this.deployK8s) {
            def namespace = this.branchConfig.k8sDetails?.namespace ?: 'dev-labs'
            def credentials = this.branchConfig.k8sDetails?.credentials ?: [:]
            def k8s = new ClusterPipeline(namespace, credentials)
            
            k8s.connect(script) {                            
                def helmCommand = "upgrade --install ${this.serviceName} ${chartPath} " +
                                  "-f ${valuesPath} " +
                                  "-f ${ingressValuesPath} " +
                                  "--set container.image=${imageFull} " +
                                  "--set app.name=${this.serviceName} " +
                                  "--set deployment.name=${this.serviceName} " +
                                  "--set service.name=${this.serviceName} " +
                                  "--set probe.path=/${this.serviceName}/"
                
                // Ejecutamos
                k8s.sh(script, helmCommand, this.config.typeDeployd)            
            }
        } else {
            script.echo "⚠️ Deploy no configurado (deployK8s=false)"
        }
    }

    void trash(script, int keepCount = 3) {
        script.echo "🧹 Ejecutando limpieza de recursos..."
        def trash = new Trash(script)
        
        // 1. Limpieza de artefactos de build (dist, coverage, etc)
        trash.cleanBuildArtifacts()
        
        // 2. Limpieza de imágenes antiguas (Garbage Collection)
        // Construimos el nombre de la imagen igual que en el método build()
        def imageFull = "${config.dockerRegistry}/${this.serviceName.toLowerCase()}"
        trash.cleanImages(imageFull, keepCount)
    }

    @NonCPS
    private Map parsePackageJson(String packageJson) {
        def pkg = new JsonSlurper().parseText(packageJson)
        return [
            name: pkg.name.toString(),
            version: pkg.version.toString()
        ]
    }

    void config(script, branch){
        // Inicializar configuración de rama passing global config
        this.branchConfig = new BranchConfig(branch, this.config)                
        
        def packageJson = script.readFile(file: 'package.json')
        def pkgInfo = parsePackageJson(packageJson)

        def timestamp = new Date().format("yyyyMMdd")
        script.echo "Timestamp: ${timestamp}"       
        this.serviceName = pkgInfo.name
        
        // Configurar propiedades según la rama usando BranchConfig
        this.environment = this.branchConfig.environment
        this.dockerPush = this.branchConfig.dockerPush
        this.deployK8s = this.branchConfig.deployK8s
        this.requireApproval = this.branchConfig.requireApproval
        
        // Usar siempre la versión del package.json
        if (branch == "main" || branch == "master") {
            this.version = pkgInfo.version
        } else {
            this.version = "${branch}-${pkgInfo.version}"
        }        
        
        script.echo "📦 Nombre del servicio: ${this.serviceName}"
        script.echo "🏷️ Versión: ${this.version}"        
        script.echo "🌍 Environment: ${this.environment}"
        script.echo "🐳 Docker Push: ${this.dockerPush}"
        script.echo "🚀 Deploy K8s: ${this.deployK8s}"
        script.echo "✅ Require Approval: ${this.requireApproval}"
        script.echo "🌿 Rama: ${branch}"

        def dockerfileContent = script.libraryResource('org/aomerge/docker/angular/Dockerfile.base')
        script.writeFile file: 'Dockerfile.base', text: dockerfileContent

        script.sh "mkdir -p test-results && chmod 777 test-results"
        script.sh "mkdir -p dist && chmod 777 dist"
        def language = config?.language ?: 'angular'
        def serviceName = this.serviceName ?: 'app'
        script.sh "podman build -f Dockerfile.base -t localhost/base-${language.toLowerCase()}-${serviceName.toLowerCase()} ."

        if (!this.branchConfig.shouldExecute(script, environment)) {
            script.currentBuild.result = 'NOT_BUILT'
            script.echo "🚫 Pipeline cancelado - Rama '${environment}' no válida o duplicada"
            return
        }
    }
    
    // Método auxiliar para verificar si el pipeline debe continuar
    boolean isValidExecution() {
        return this.branchConfig?.isValidForExecution ?: false
    }

}
