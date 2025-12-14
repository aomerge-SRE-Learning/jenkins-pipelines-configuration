package org.aomerge.angular

class AngularPipeline implements Serializable {
    Map config
    
    AngularPipeline(Map config) {
        this.config = config
    }
    
    void test(script) {
        script.readFile('resources/org/aomerge/docker/angular/Dockerfile.test')
        script.echo "🧪 Ejecutando tests de Angular..."
        script.sh 'podman build -f Dockerfile.test -t angular-test .'
        script.sh 'podman run --rm angular-test'
    }
    
    void build(script) {
        script.readFile('resources/org/aomerge/docker/angular/Dockerfile')
        script.echo "🔨 Building Angular application..."
        script.sh "podman build -t ${config.dockerRegistry}/${config.serviceName}:${config.version} ."
        script.sh 'podman run --rm angular-build'
        
        if (config.dockerPush) {
            script.echo "🐳 Building Docker image..."            
            script.withCredentials([usernamePassword(credentialsId: 'DockerHub', usernameVariable: 'DOCKER_USER', passwordVariable: 'DOCKER_PASS')]) {
                sh """
                    podman login --username \$DOCKER_USER --password-stdin docker.io 
                    podman push ${config.dockerRegistry}/${config.serviceName}:${config.version}
                    podman logout docker.io
                """
            }            
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
