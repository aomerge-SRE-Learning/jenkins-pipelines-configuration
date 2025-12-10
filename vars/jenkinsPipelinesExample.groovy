import org.aomerge.Main

def call(Map config = [:]) {
    node {
        def main = new Main(config)
        
        stage('Checkout') {
            checkout scm
        }
        
        stage('Init') {
            echo "🚀 Pipeline para: ${config.language}"
            echo "📦 Servicio: ${config.serviceName ?: 'app'}"
        }
        
        // Stages dinámicos según el lenguaje
        main.executePipeline(this)
        
        echo "✅ Pipeline completado exitosamente!"
    }
}
