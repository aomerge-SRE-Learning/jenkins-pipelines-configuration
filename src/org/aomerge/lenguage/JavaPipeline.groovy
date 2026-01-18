package org.aomerge.lenguage
import groovy.json.JsonSlurper
import com.cloudbees.groovy.cps.NonCPS
import org.aomerge.config.ClusterPipeline

class JavaPipeline implements Serializable {
    Map config
    
    String environment
    String serviceName
    String version
    boolean dockerPush = true
    boolean deployK8s = true
    boolean requireApproval = true

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
        
        if (this.dockerPush) {
            script.echo "🐳 Building Docker image..."
            script.sh "podman build -t ${config.dockerRegistry}/${this.serviceName.toLowerCase()}:${this.version} ."
            
            script.withCredentials([script.usernamePassword(credentialsId: 'DockerHub', usernameVariable: 'DOCKER_USER', passwordVariable: 'DOCKER_PASS')]) {
                script.sh """
                    echo \$DOCKER_PASS | podman login --username \$DOCKER_USER --password-stdin docker.io 
                    podman push ${config.dockerRegistry}/${this.serviceName.toLowerCase()}:${this.version}
                    podman logout docker.io
                """
            }
        }
    }
    
    void deploy(script) {
        script.echo "🚀 Desplegando Java a ${this.environment}..."
        if (this.deployK8s) {
            def k8s = new ClusterPipeline(this.environment)
            k8s.connect(script) {
                if (script.fileExists('k8s/')) {
                    k8s.sh(script, "apply -f k8s/")
                } else {
                    k8s.sh(script, "set image deployment/${this.serviceName.toLowerCase()} app=${config.dockerRegistry}/${this.serviceName.toLowerCase()}:${this.version}")
                }
                k8s.healthcheck(script, this.serviceName.toLowerCase())
            }
        } else {
            script.echo "⚠️ Deploy no configurado (deployK8s=false)"
        }
    }

    @NonCPS
    private Map parseProjectInfo(script) {
        // Para Java, podríamos leer pom.xml o build.gradle, pero por ahora simplificamos
        // o usamos valores de config si están presentes
        return [
            name: config.serviceName ?: 'java-app',
            version: config.version ?: '1.0.0'
        ]
    }

    void config(script, branch){
        def info = parseProjectInfo(script)
        
        def timestamp = new Date().format("yyyyMMdd")
        this.serviceName = info.name
        this.version = "${info.version}-${timestamp}.${script.env.BUILD_NUMBER}"
        
        script.echo "Nombre del servicio: ${this.serviceName}"
        script.echo "Versión: ${this.version}"
        script.echo "Branch original: ${branch}"

        switch(branch){
            case { it ==~ /^PR-\d+$/ } :
                def target = script.env.CHANGE_TARGET
                script.echo "PR detectado (${branch}). Rama destino real: ${target}"
                branch = target ?: branch
                // volver a evaluar con la rama real
                return config(script, branch)
            case "master":
            case "main":
                this.environment = "production"
                break
            case "QA":
                this.environment = "qa"
                break
            case "dev":
                this.environment = "development"
                break
            default:
                if (branch ==~ /^feature-.*$/) {
                    this.environment = "feature"
                    this.requireApproval = false
                } else {
                    this.environment = branch
                    this.dockerPush = false
                    this.deployK8s = false
                    this.requireApproval = false
                }
                break
        }
    }    

}
