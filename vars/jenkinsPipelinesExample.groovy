import org.aomerge.Main
import org.aomerge.config.Trash

def call(Map config = [:]) {
    properties([
        pipelineTriggers([
            githubPush()
        ])
    ])
    
    node {
        def main = new Main(config, env.BRANCH_NAME)
        def currentStageName = ''
        
        try {
            currentStageName = 'Checkout'
            stage('Checkout') {
                checkout scm
                echo "🚀 Pipeline para: ${config.language}"
                echo "📦 Servicio: ${config.serviceName ?: 'app'}"
                echo "Rama actual: ${env.BRANCH_NAME}"
            }
            
            currentStageName = 'config'                                
            main.executePipeline(this)
            
            echo "✅ Pipeline completado exitosamente!"
            
        } catch (Exception e) {
            echo "❌ Pipeline falló en stage: ${currentStageName}"
            echo "❌ Error: ${e.getMessage()}"
            
            if (config.notifyOnFailure) {
                echo "📧 Enviando notificación de fallo..."
                // Aquí puedes agregar notificaciones (email, slack, etc)
            }

            throw e
            
        } finally {
            script.echo "🧹 Ejecutando limpieza de recursos..."
            def keepCount = 3
            def trash = new Trash(script)
            
            // 1. Limpieza de artefactos de build (dist, coverage, etc)
            trash.cleanBuildArtifacts()
            
            // 2. Limpieza de imágenes antiguas (Garbage Collection)
            // Construimos el nombre de la imagen igual que en el método build()
            def imageFull = "${config.dockerRegistry}/${this.serviceName.toLowerCase()}"
            trash.cleanImages(imageFull, keepCount)
            
        }
    }
}
