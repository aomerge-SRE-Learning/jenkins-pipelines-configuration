package org.aomerge.angular

class AngularPipeline implements Serializable {
    Map config
    
    AngularPipeline(Map config) {
        this.config = config
    }
    
    void test(script) {
        def dockerfileContent = script.libraryResource('org/aomerge/docker/angular/Dockerfile.base')
        script.writeFile file: 'Dockerfile.base', text: dockerfileContent
        script.echo "🧪 Ejecutando tests de Angular..."
        script.sh "mkdir -p test-results"
        script.sh "podman build -f Dockerfile.base -t base-angular-${config.serviceName} ."
        script.sh """
            podman run --rm \\
                -v \$(pwd)/src:/app/src \\
                -v \$(pwd)/public:/app/public \\
                -v \$(pwd)/test-results:/test/test-results \\
                -w /app \\
                base-angular-${config.serviceName} npm run test:ci
        """
    }
    
    void build(script) {
        def dockerfileContent = script.libraryResource('org/aomerge/docker/angular/Dockerfile')
        script.writeFile file: 'Dockerfile', text: dockerfileContent
        script.echo "🔨 Building Angular application..."
        script.sh "mkdir -p dist"
        script.sh """
            podman run --rm \\
                -v \$(pwd)/src:/app/src \\
                -v \$(pwd)/public:/app/public \\
                -v \$(pwd)/dist:/app/dist \\
                -w /app \\
                base-angular-${config.serviceName} npm run build --configuration=${config.environment}
        """
        script.sh "podman build -t ${config.dockerRegistry}/${config.serviceName}:${config.version} ."
        
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

    void trash(script){

    }
}
