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
                        
                        # Si el registry no es docker.io, construir URL completa
                        if [ "$DOCKER_REGISTRY" != "docker.io" ] && [ "$DOCKER_REGISTRY" != "localhost" ]; then
                            IMAGE_PATH="$DOCKER_REGISTRY/$SERVICE_NAME:$VERSION"
                        else
                            IMAGE_PATH="docker.io/$DOCKER_USER/$SERVICE_NAME:$VERSION"
                        fi
                        
                        # Re-tag si es necesario
                        podman tag "$DOCKER_REGISTRY/$SERVICE_NAME:$VERSION" "$IMAGE_PATH" || true
                        
                        # Push
                        podman push "$IMAGE_PATH"
                        
                        podman logout docker.io 2>/dev/null || true
                    ''')
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
                
                if (!script.fileExists(valuesPath) && !script.fileExists(ingressValuesPath)) {
                    script.error("❌ No se encontraron los archivos de configuración: '${valuesPath}' y '${ingressValuesPath}'. No se puede continuar con el despliegue.")
                } else if (!script.fileExists(valuesPath)) {
                    script.error("❌ No se encontró el archivo de configuración de valores: '${valuesPath}'. No se puede continuar con el despliegue.")
                } else if (!script.fileExists(ingressValuesPath)) {
                    script.error("❌ No se encontró el archivo de configuración de ingress: '${ingressValuesPath}'. No se puede continuar con el despliegue.")
                }

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
        
        this.branchConfig.switchBranch(branch)        

        // Validar si debe ejecutarse (solución al problema del webhook)
        if (!this.branchConfig.shouldExecute(script, branch)) {
            script.currentBuild.result = 'NOT_BUILT'
            script.echo "🚫 Pipeline cancelado - Rama '${branch}' no válida o duplicada"
            return
        }
        
        def packageJson = script.readFile(file: 'package.json')
        def pkgInfo = parsePackageJson(packageJson)

        def timestamp = new Date().format("yyyyMMdd")
        script.echo "Timestamp: ${timestamp}"       
        this.serviceName = pkgInfo.name
        this.version = "${pkgInfo.version}-${timestamp}.${script.env.BUILD_NUMBER}"
        
        // Configurar propiedades según la rama usando BranchConfig
        this.environment = this.branchConfig.getEnvironment()
        this.dockerPush = this.branchConfig.getDockerPush()
        this.deployK8s = this.branchConfig.getDeployK8s()
        this.requireApproval = this.branchConfig.getRequireApproval()
        
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
    }
    
    // Método auxiliar para verificar si el pipeline debe continuar
    boolean isValidExecution() {
        return this.branchConfig?.isValidForExecution ?: false
    }

}
