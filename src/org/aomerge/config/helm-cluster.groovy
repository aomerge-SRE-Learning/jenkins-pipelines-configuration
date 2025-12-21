package org.aomerge.config


class HelmPipeline implements Serializable {
    String templateName

    HelmPipeline(String templateName = 'deploy-basic') {
        this.templateName = templateName
    }

    void copyHelm(script, String targetDir = "helm") {
        script.echo "📦 Copiando recursos de Helm desde plantilla: ${this.templateName}"
        
        def resourceBase = "org/aomerge/helm/${this.templateName}"
        def helmFiles = [
            'Chart.yaml',
            'values.yaml',
            'templates/deployment.yaml',
            'templates/service.yaml'
        ]

        helmFiles.each { relativePath ->
            def resourcePath = "${resourceBase}/${relativePath}"
            def targetPath = "${targetDir}/${relativePath}"
            
            // Asegurar que el directorio destino existe (especialmente para templates/)
            def parentDir = targetPath.contains('/') ? targetPath.substring(0, targetPath.lastIndexOf('/')) : '.'
            script.sh "mkdir -p ${parentDir}"

            try {
                def fileContent = script.libraryResource(resourcePath)
                script.writeFile file: targetPath, text: fileContent
            } catch (Exception e) {
                script.echo "⚠️ No se pudo cargar el recurso: ${resourcePath}. Saltando..."
            }
        }
    }
}