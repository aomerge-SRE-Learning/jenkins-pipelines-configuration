import org.aomerge.Main

def call(Map config = [:]) {
    node {
        def main = new Main(config)
        def currentStageName = ''
        
        try {
            currentStageName = 'Checkout'
            stage('Checkout') {
                checkout scm
                
            }
            
            currentStageName = 'Init'
            stage('Init') {                
                echo "🚀 Pipeline para: ${config.language}"
                echo "📦 Servicio: ${config.serviceName ?: 'app'}"
                echo "Rama actual: ${env.BRANCH_NAME}"
                echo "Git branch: ${env.GIT_BRANCH}"
                
            }
            
            // Stages dinámicos según el lenguaje
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
            // Aquí puedes agregar lógica de limpieza si es necesario
        }
    }
}
