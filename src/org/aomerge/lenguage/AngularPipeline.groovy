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
        script.sh """
            podman run --rm \\
                -v \$(pwd)/src:/app/src \\
                -v \$(pwd)/public:/app/public \\
                -v \$(pwd)/test-results:/app/test-results \\
                -w /app \\
                localhost/base-${language.toLowerCase()}-${serviceName.toLowerCase()} npm run test:ci 
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
        script.sh """
            podman run --rm \\
                -v \$(pwd)/src:/app/src \\
                -v \$(pwd)/public:/app/public \\
                -v \$(pwd)/dist:/app/dist \\
                -w /app \\
                localhost/base-${language.toLowerCase()}-${serviceName.toLowerCase()} npm run build --configuration=${environment}
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

                        # Intentar hacer pull de la imagen para ver si ya existe
                        if podman pull "$IMAGE_PATH" > /dev/null 2>&1; then
                            echo "La imagen $IMAGE_PATH ya existe. Buscando siguiente versión disponible..."
                            BASE_VERSION="$VERSION"
                            SUFFIX=1
                            while true; do
                                NEW_VERSION="${BASE_VERSION}.a${SUFFIX}"
                                if [ "$DOCKER_REGISTRY" != "docker.io" ] && [ "$DOCKER_REGISTRY" != "localhost" ]; then
                                    NEW_IMAGE_PATH="$DOCKER_REGISTRY/$SERVICE_NAME:$NEW_VERSION"
                                else
                                    NEW_IMAGE_PATH="docker.io/$DOCKER_USER/$SERVICE_NAME:$NEW_VERSION"
                                fi
                                if ! podman pull "$NEW_IMAGE_PATH" > /dev/null 2>&1; then
                                    echo "Usando nueva versión: $NEW_VERSION"
                                    IMAGE_PATH="$NEW_IMAGE_PATH"
                                    VERSION="$NEW_VERSION"
                                    break
                                fi
                                SUFFIX=$((SUFFIX+1))
                            done
                        else
                            echo "La imagen $IMAGE_PATH no existe. Usando versión original."
                        fi

                        # Re-tag si es necesario
                        podman tag "$DOCKER_REGISTRY/$SERVICE_NAME:${version}" "$IMAGE_PATH" || true

                        # Push
                        podman push "$IMAGE_PATH"
                        # Guardar la versión final en archivo temporal para Groovy
                        echo "$VERSION" > .version.tmp
                        
                        podman logout docker.io 2>/dev/null || true
                    ''')
                    // Guardar la versión en un archivo para futuras ejecuciones
                    this.version = script.readFile('.version.tmp').trim()
                    script.sh('rm -f .version.tmp')
                    // Persistir la versión en archivo VERSION para siguientes ejecuciones
                    script.writeFile file: 'VERSION', text: this.version                                                            
                    script.echo "Imagen Docker subida correctamente: $IMAGE_PATH"
                    script.echo "Nueva versión desplegada: $VERSION"
                }
            }
        }

    }
    
    void deploy(script) {
        script.echo "🚀 Desplegando Angular a ${this.environment}..."

        if (this.deployK8s) {
            def k8s = new ClusterPipeline("dev-labs")
            k8s.connect(script) {                
                def chartPath = "./helm"
                def valuesPath = "config/${this.serviceName}/deploy-helm.yaml"
                def ingressValuesPath = "config/${this.serviceName}/ingress-helm.yaml"
                def imageFull = "${config.dockerRegistry}/${this.serviceName.toLowerCase()}:${this.version}"                            

                def helmCommand = "upgrade --install ${this.serviceName} ${chartPath} " +
                                  "-f ${valuesPath} " +
                                  "-f ${ingressValuesPath} " +
                                  "--set container.image=${imageFull} " +
                                  "--set app.name=${this.serviceName} " +
                                  "--set deployment.name=${this.serviceName} " +
                                  "--set service.name=${this.serviceName}" +
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
        // Inicializar configuración de rama
        this.branchConfig = new BranchConfig(branch)                
        
        def packageJson = script.readFile(file: 'package.json')
        def pkgInfo = parsePackageJson(packageJson)

        def timestamp = new Date().format("yyyyMMdd")
        script.echo "Timestamp: ${timestamp}"       
        this.serviceName = pkgInfo.name
        // Leer versión persistida si existe, si no usar la de package.json
        if (script.fileExists('VERSION')) {
            this.version = script.readFile('VERSION').trim()
            script.echo "🔄 Usando versión persistida: ${this.version}"
        } else {
            this.version = "${pkgInfo.version}"
        }        
        
        // Configurar propiedades según la rama usando BranchConfig
        this.environment = this.branchConfig.environment
        this.dockerPush = this.branchConfig.dockerPush
        this.deployK8s = this.branchConfig.deployK8s
        this.requireApproval = this.branchConfig.requireApproval        
        
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
