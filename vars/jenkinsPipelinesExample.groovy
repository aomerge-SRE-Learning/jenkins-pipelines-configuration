import org.aomerge.Main

def call(Map config = [:]) {
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
            echo "🧹 Limpieza final del workspace..."
            
        }
    }
}
