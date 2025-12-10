package org.aomerge.angular

class AngularPipeline implements Serializable {
    Map config
    
    AngularPipeline(Map config) {
        this.config = config
    }
    
    void test(script) {
        script.echo "🧪 Ejecutando tests de Angular..."
        script.sh 'npm install'
        script.sh 'npm run test'
    }
    
    void build(script) {
        script.echo "🔨 Building Angular application..."
        script.sh 'npm run build --prod'
        
        if (config.dockerPush) {
            script.echo "🐳 Building Docker image..."
            script.sh "podman build -t ${config.dockerRegistry}/${config.serviceName}:latest ."
            script.sh "podman push ${config.dockerRegistry}/${config.serviceName}:latest"
        }
    }
    
    void deploy(script) {
        script.echo "🚀 Desplegando Angular a ${config.environment ?: 'production'}..."
        if (config.deployK8s) {
            script.sh "kubectl set image deployment/${config.serviceName} app=${config.dockerRegistry}/${config.serviceName}:latest"
        } else {
            script.echo "⚠️ Deploy no configurado (deployK8s=false)"
        }
    }
}
