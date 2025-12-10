package org.aomerge.java

class JavaPipeline implements Serializable {
    Map config
    
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
        
        if (config.dockerPush) {
            script.echo "🐳 Building Docker image..."
            script.sh "podman build -t ${config.dockerRegistry}/${config.serviceName}:latest ."
            script.sh "podman push ${config.dockerRegistry}/${config.serviceName}:latest"
        }
    }
    
    void deploy(script) {
        script.echo "🚀 Desplegando Java a ${config.environment ?: 'production'}..."
        if (config.deployK8s) {
            script.sh "kubectl set image deployment/${config.serviceName} app=${config.dockerRegistry}/${config.serviceName}:latest"
        } else {
            script.echo "⚠️ Deploy no configurado (deployK8s=false)"
        }
    }
}
