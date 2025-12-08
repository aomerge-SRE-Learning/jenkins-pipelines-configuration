import org.Angular.ExtraStages

def call(Map config = [:]) {
    pipeline {
        agent any
        
        stages {
            stage('Hello World') {
                steps {
                    script {
                        echo "👋 ¡Hola Mundo desde Jenkins!"
                        echo "🚀 Pipeline: ${config.name ?: 'jenkins-pipeline-example'}"
                        echo "📅 Fecha: ${new Date()}"
                    }
                }
            }
            
            stage('Información del Sistema') {
                steps {
                    script {
                        echo "💻 Test Podman"
                        sh 'podman ps -la'                        
                    }
                }
            }
            stage('Extra') {
                steps {
                    script {
                        org.Angular.ExtraStages.runExtraSteps(this)
                    }
                }
            }
        }
        
        post {
            success {
                echo "✅ Pipeline completado exitosamente!"
            }
            failure {
                echo "❌ Pipeline falló"
            }
        }
    }
}
