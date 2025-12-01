// CD Pipeline para Angular con Docker
// Continuous Deployment - Deploy y Rollback

def deployToDocker(String environment, String imageName, String tag, String port) {
    echo "🚀 Desplegando a ${environment} en puerto ${port}"
    
    // Detener contenedor anterior si existe
    sh """
        docker stop angular-${environment} 2>/dev/null || true
        docker rm angular-${environment} 2>/dev/null || true
    """
    
    // Desplegar nuevo contenedor
    sh """
        docker run -d \
            --name angular-${environment} \
            --restart unless-stopped \
            -p ${port}:80 \
            -e ENVIRONMENT=${environment} \
            ${imageName}:${tag}
    """
    
    echo "✅ Aplicación desplegada en http://localhost:${port}"
}

def deployToDockerCompose(String environment) {
    echo "🚀 Desplegando con Docker Compose en ${environment}"
    sh """
        docker-compose -f docker-compose.${environment}.yml down
        docker-compose -f docker-compose.${environment}.yml up -d
    """
}

def deployToKubernetes(String namespace, String deployment) {
    echo "☸️ Desplegando a Kubernetes namespace: ${namespace}"
    sh """
        kubectl set image deployment/${deployment} \
            app=\${IMAGE_NAME}:\${IMAGE_TAG} \
            -n ${namespace}
        
        kubectl rollout status deployment/${deployment} -n ${namespace}
    """
}

def rollback(String environment) {
    echo "⏪ Ejecutando rollback en ${environment}"
    sh """
        docker stop angular-${environment}
        docker rm angular-${environment}
        
        # Restaurar última versión estable
        docker run -d \
            --name angular-${environment} \
            --restart unless-stopped \
            -p \${PORT}:80 \
            \${IMAGE_NAME}:stable
    """
}

def rollbackKubernetes(String namespace, String deployment) {
    echo "⏪ Rollback en Kubernetes"
    sh """
        kubectl rollout undo deployment/${deployment} -n ${namespace}
        kubectl rollout status deployment/${deployment} -n ${namespace}
    """
}

def healthCheck(String url, int maxRetries = 5) {
    echo "🏥 Verificando health check: ${url}"
    
    for (int i = 1; i <= maxRetries; i++) {
        try {
            def response = sh(
                script: "curl -s -o /dev/null -w '%{http_code}' ${url}",
                returnStdout: true
            ).trim()
            
            if (response == '200') {
                echo "✅ Health check exitoso (${response})"
                return true
            }
            echo "⚠️ Intento ${i}/${maxRetries}: ${response}"
            sleep(10)
        } catch (Exception e) {
            echo "❌ Error en intento ${i}: ${e.message}"
            if (i == maxRetries) {
                error "Health check falló después de ${maxRetries} intentos"
            }
            sleep(10)
        }
    }
}

def cleanOldImages(String imageName, int keepLast = 3) {
    echo "🧹 Limpiando imágenes antiguas (manteniendo últimas ${keepLast})"
    sh """
        docker images ${imageName} --format '{{.Tag}}' | \
        grep -v latest | \
        tail -n +\$((${keepLast} + 1)) | \
        xargs -I {} docker rmi ${imageName}:{} 2>/dev/null || true
    """
}

def backupVolume(String containerName, String backupPath) {
    echo "💾 Creando backup del volumen"
    sh """
        docker run --rm \
            --volumes-from ${containerName} \
            -v ${backupPath}:/backup \
            alpine \
            tar czf /backup/backup-\$(date +%Y%m%d-%H%M%S).tar.gz /app/data
    """
}

// Retornar el script para que Jenkins pueda cargarlo
return this
