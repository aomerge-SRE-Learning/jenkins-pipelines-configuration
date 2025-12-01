/** Construccion de imagen docker, CI
*
*/


def runDocker(){
    echo " Ejecutando tests Docker"
    sh '''        
    '''   
}

def test() {
    echo "🧪 Ejecutando tests unitarios"
    sh '''
        docker run --rm \
            -v ${WORKSPACE}:/app \
            -w /app \
            node:18-alpine \
            npm run test -- --watch=false --browsers=ChromeHeadless
    '''
}



def build(String environment = 'production') {
    echo "🏗️ Construyendo aplicación Angular para ${environment}"
    sh """
        docker run --rm \
            -v ${WORKSPACE}:/app \
            -w /app \
            node:18-alpine \
            npm run build -- --configuration=${environment}
    """
}

def buildDockerImage(String imageName, String tag) {
    echo "🐳 Construyendo imagen Docker: ${imageName}:${tag}"
    sh """
        docker build \
            -t ${imageName}:${tag} \
            -t ${imageName}:latest \
            -f Dockerfile \
            .
    """
}

def runSecurityScan(String imageName, String tag) {
    echo "🔒 Escaneando vulnerabilidades con Trivy"
    sh """
        docker run --rm \
            -v /var/run/docker.sock:/var/run/docker.sock \
            aquasec/trivy:latest \
            image ${imageName}:${tag}
    """
}

def pushDockerImage(String imageName, String tag) {
    echo "📤 Subiendo imagen a registry"
    sh """
        docker push ${imageName}:${tag}
        docker push ${imageName}:latest
    """
}


return this
