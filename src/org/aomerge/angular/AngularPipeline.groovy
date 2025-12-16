package org.aomerge.angular
import groovy.json.JsonSlurper
import com.cloudbees.groovy.cps.NonCPS

class AngularPipeline implements Serializable {
    Map config
    
    String environment
    String serviceName
    String version
    boolean dockerPush = true
    boolean deployK8s = true
    boolean requireApproval = true

    AngularPipeline(Map config) {
        this.config = config
    }
    
    void test(script) {
        def dockerfileContent = script.libraryResource('org/aomerge/docker/angular/Dockerfile.base')
        script.writeFile file: 'Dockerfile.base', text: dockerfileContent
        script.echo "🧪 Ejecutando tests de Angular..."        
        script.sh """
            podman run --rm \\
                -v \$(pwd)/src:/app/src \\
                -v \$(pwd)/public:/app/public \\
                -v \$(pwd)/test-results:/app/test-results \\
                -w /app \\
                localhost/base-${config.language}-${this.serviceName} npm run test:ci 
        """
    }
    
    void build(script) {
        def dockerfileContent = script.libraryResource('org/aomerge/docker/angular/Dockerfile')
        script.writeFile file: 'Dockerfile', text: dockerfileContent
        script.echo "🔨 Building Angular application..."        
        script.sh """
            podman run --rm \\
                -v \$(pwd)/src:/app/src \\
                -v \$(pwd)/public:/app/public \\
                -v \$(pwd)/dist:/app/dist \\
                -w /app \\
                localhost/base-${config.language}-${this.serviceName} npm run build --configuration=${this.environment}
        """
        script.sh "podman build -t ${config.dockerRegistry}/${this.serviceName}:${this.version} ."
        
        if (this.dockerPush) {
            script.echo "🐳 Building Docker image..."            
            script.withCredentials([usernamePassword(credentialsId: 'DockerHub', usernameVariable: 'DOCKER_USER', passwordVariable: 'DOCKER_PASS')]) {
                sh """
                    podman login --username \$DOCKER_USER --password-stdin docker.io 
                    podman push ${config.dockerRegistry}/${this.serviceName}:${this.version}
                    podman logout docker.io
                """
            }            
        }
    }
    
    void deploy(script) {
        script.echo "🚀 Desplegando Angular a ${config.environment ?: 'production'}..."
        script.sh "exit 1"
        if (config.deployK8s) {
            script.sh "kubectl set image deployment/${config.serviceName} app=${config.dockerRegistry}/${config.serviceName}:latest"
        } else {
            script.echo "⚠️ Deploy no configurado (deployK8s=false)"
        }
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
        def packageJson = script.readFile(file: 'package.json')
        def pkgInfo = parsePackageJson(packageJson)
        
        script.echo "Nombre del servicio: ${pkgInfo.name}"
        script.echo "Versión: ${pkgInfo.version}"        
        this.serviceName = pkgInfo.name
        this.version = pkgInfo.version

        switch(branch){
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
                } else if (branch ==~ /^bugfix-.*$/) {
                    this.environment = "bugfix"
                    this.dockerPush = false
                    this.deployK8s = false
                    this.requireApproval = false
                } else if (branch ==~ /^hotfix-.*$/) {
                    this.environment = "hotfix"
                } else {
                    this.environment = branch
                    this.dockerPush = false
                    this.deployK8s = false
                    this.requireApproval = false
                }
                break
        }

        script.sh "mkdir -p test-results && chmod 777 test-results"
        script.sh "mkdir -p dist && chmod 777 dist"
        script.sh "podman build -f Dockerfile.base -t localhost/base-${config.language}-${this.serviceName} ."

    }

    void trash(script){
        script.echo "🧹 Limpiando imágenes Docker colgantes..."
        script.sh 'docker rmi $(docker images -f "dangling=true" -q)'

    }
}
