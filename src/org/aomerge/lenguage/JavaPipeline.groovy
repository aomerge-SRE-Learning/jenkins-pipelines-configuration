package org.aomerge.lenguage
import groovy.json.JsonSlurper
import com.cloudbees.groovy.cps.NonCPS
import org.aomerge.config.ClusterPipeline
import org.aomerge.config.BranchConfig
import org.aomerge.config.Trash

class JavaPipeline implements Serializable {
    Map config
    
    String environment
    String serviceName
    String version
    boolean dockerPush = true
    boolean deployK8s = true
    boolean requireApproval = true
    BranchConfig branchConfig

    JavaPipeline(Map config) {
        this.config = config
    }
    
    void test(script) {
        script.echo "🧪 Ejecutando tests de Java..."
        script.sh './gradlew test || mvn test'
    }
    
    void build(script) {
        script.echo "🔨 Building Java application..."
        script.sh './gradlew build || mvn clean package'
        
        // Resolver Registro Docker y Credenciales
        def dockerRegistry = this.branchConfig.dockerDetails?.registry ?: config?.dockerRegistry ?: 'docker.io'
        def dockerCredId = this.branchConfig.dockerDetails?.credentialId ?: 'DockerHub'
        def dockerType = this.branchConfig.dockerDetails?.type ?: 'dockerhub'
        
        def imageTag = "${dockerRegistry}/${this.serviceName.toLowerCase()}:${this.version}"

        if (this.dockerPush) {
            script.echo "🐳 Building Docker image: ${imageTag}"
            script.sh "podman build -t ${imageTag} ."
            
            def registryHost = dockerRegistry.split('/')[0]
            if (registryHost == 'docker.io' || !registryHost.contains('.')) {
                registryHost = 'docker.io'
            }

            def checkAndPush = { loginCmd, logoutCmd ->
                script.sh """
                    ${loginCmd}
                    echo "🔍 Validando si la versión ${this.version} ya existe..."
                    if skopeo inspect "docker://${imageTag}" > /dev/null 2>&1; then
                        echo "❌ ERROR: La imagen ${imageTag} ya existe."
                        ${logoutCmd}
                        exit 1
                    fi
                    podman push ${imageTag}
                    ${logoutCmd}
                """
            }

            if (dockerType == 'artifact-registry') {
                script.withCredentials([script.file(credentialsId: dockerCredId, variable: 'GCP_SA_KEY')]) {
                    def login = "cat \"\$GCP_SA_KEY\" | podman login -u _json_key --password-stdin ${registryHost}"
                    def logout = "podman logout ${registryHost}"
                    checkAndPush(login, logout)
                }
            } else {
                script.withCredentials([script.usernamePassword(credentialsId: dockerCredId, usernameVariable: 'DOCKER_USER', passwordVariable: 'DOCKER_PASS')]) {
                    def login = "echo \"\$DOCKER_PASS\" | podman login --username \"\$DOCKER_USER\" --password-stdin ${registryHost}"
                    def logout = "podman logout ${registryHost}"
                    checkAndPush(login, logout)
                }
            }
        }
    }
    
    void loadExternalConfig(script) {
        def settingPath = "config/${this.serviceName}/setting.json"
        if (script.fileExists(settingPath)) {
            script.echo "🔍 Cargando metadata externa para Java desde: ${settingPath}"
            try {
                def content = script.readFile(settingPath)?.trim()
                if (!content) {
                    script.echo "⚠️ El archivo ${settingPath} está VACÍO."
                    return
                }
                def json = new JsonSlurper().parseText(content)
                this.branchConfig.updateFromExternal(json)
                
                // Sincronizar propiedades
                this.environment = this.branchConfig.environment
                this.dockerPush = this.branchConfig.dockerPush
                this.deployK8s = this.branchConfig.deployK8s
                this.requireApproval = this.branchConfig.requireApproval

                script.echo "✅ Configuración externa cargada (Ambiente: ${this.environment})"
            } catch (Exception e) {
                script.echo "❌ Error al parsear metadata externa (${settingPath}): ${e.message}"
            }
        } else {
            script.echo "ℹ️ No se detectó setting.json para el servicio ${this.serviceName}"
        }
    }

    void deploy(script) {
        script.echo "🚀 Desplegando Java a ${this.environment}..."
        def dockerRegistry = this.branchConfig.dockerDetails?.registry ?: config?.dockerRegistry ?: 'docker.io'
        def imageFull = "${dockerRegistry}/${this.serviceName.toLowerCase()}:${this.version}"

        if (this.deployK8s) {
            def namespace = this.branchConfig.k8sDetails?.namespace ?: 'dev-labs'
            def credentials = this.branchConfig.k8sDetails?.credentials ?: [:]
            def k8s = new ClusterPipeline(namespace, credentials)
            
            k8s.connect(script) {
                if (script.fileExists('k8s/')) {
                    k8s.sh(script, "apply -f k8s/")
                } else {
                    k8s.sh(script, "set image deployment/${this.serviceName.toLowerCase()} app=${imageFull}")
                }
                k8s.healthcheck(script, this.serviceName.toLowerCase())
            }
        } else {
            script.echo "⚠️ Deploy no configurado (deployK8s=false)"
        }
    }

    void trash(script, int keepCount = 3) {
        def trash = new Trash(script)
        trash.cleanBuildArtifacts()
        def dockerRegistry = this.branchConfig.dockerDetails?.registry ?: config?.dockerRegistry ?: 'docker.io'
        trash.cleanImages("${dockerRegistry}/${this.serviceName.toLowerCase()}", keepCount)
    }

    void config(script, branch){
        this.branchConfig = new BranchConfig(branch, this.config)
        
        def info = parseProjectInfo(script)
        this.serviceName = info.name
        
        // Configurar propiedades según la rama
        this.environment = this.branchConfig.environment
        this.dockerPush = this.branchConfig.dockerPush
        this.deployK8s = this.branchConfig.deployK8s
        this.requireApproval = this.branchConfig.requireApproval
        
        def timestamp = new Date().format("yyyyMMdd")
        if (branch == "main" || branch == "master") {
            this.version = info.version
        } else {
            this.version = "${branch}-${info.version}-${timestamp}.${script.env.BUILD_NUMBER}"
        }
        
        script.echo "📦 Nombre del servicio (Java): ${this.serviceName}"
        script.echo "🏷️ Versión: ${this.version}"        
        script.echo "🌍 Environment: ${this.environment}"
        script.echo "🌿 Rama: ${branch}"
    }

    @NonCPS
    private Map parseProjectInfo(script) {
        // Intentar leer de archivos comunes de Java
        if (script.fileExists('pom.xml')) {
            def pom = script.readFile('pom.xml')
            // Parseo básico de POM (mejorable con XmlSlurper si no hay CPS issues)
            def name = pom.find(/<artifactId>(.*?)<\/artifactId>/) { it[1] } ?: 'java-app'
            def version = pom.find(/<version>(.*?)<\/version>/) { it[1] } ?: '1.0.0'
            return [name: name, version: version]
        } else if (script.fileExists('build.gradle')) {
            def gradle = script.readFile('build.gradle')
            def name = gradle.find(/rootProject.name\s*=\s*['"](.*?)['"]/) { it[1] } ?: 'java-app'
            def version = gradle.find(/version\s*=\s*['"](.*?)['"]/) { it[1] } ?: '1.0.0'
            return [name: name, version: version]
        }
        return [name: 'java-app', version: '1.0.0']
    }

    // Método auxiliar para verificar si el pipeline deba continuar
    boolean isValidExecution() {
        return this.branchConfig?.isValidForExecution ?: false
    }

}
