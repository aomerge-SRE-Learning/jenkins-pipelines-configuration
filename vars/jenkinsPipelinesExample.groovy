import org.aomerge.Main
import org.aomerge.config.Trash
import com.cloudbees.groovy.cps.NonCPS

def call(Map config = [:]) {    
    
    // Configurar triggers con validación mejorada
    def triggers = []
    if (config.enableWebhook != false) {  // Por defecto habilitado
        triggers.add(githubPush())
    }
    
    properties([
        pipelineTriggers(triggers)
    ])

    node {
        def main = new Main(config, env)
        def currentStageName = ''
        
        try {
            currentStageName = 'Checkout'
            stage('Checkout') {
                checkout scm
                echo "🚀 Pipeline para: ${config?.language ?: 'unknown'}"
                echo "📦 Servicio: ${config?.serviceName ?: 'app'}"
                echo "🌿 Rama actual: ${env.BRANCH_NAME}"
                
                // Información adicional para PRs
                if (env.CHANGE_ID) {
                    echo "🔀 Pull Request #${env.CHANGE_ID}"
                    echo "📌 Rama origen: ${env.CHANGE_BRANCH}"
                    echo "🎯 Rama destino: ${env.CHANGE_TARGET}"
                }
                
                // Validación temprana para evitar ejecuciones innecesarias
                def targetBranch = env.CHANGE_TARGET ?: env.BRANCH_NAME
                echo "🎯 Rama objetivo determinada: ${targetBranch}"
                
                // Lista de ramas válidas (configurable)
                def validBranches = config.validBranches ?: ['main', 'master', 'dev', 'develop', 'qa']
                def isFeatureBranch = targetBranch?.toLowerCase() ==~ /^(feature|bugfix|hotfix)-.*$/
                
                if (!validBranches.contains(targetBranch?.toLowerCase()) && !isFeatureBranch) {
                    echo "⚠️ Rama '${targetBranch}' no está en la lista de ramas válidas: ${validBranches}"
                    if (config.skipInvalidBranches != false) {  // Por defecto skip
                        currentBuild.result = 'NOT_BUILT'
                        echo "🛑 Pipeline cancelado para rama no válida"
                        return
                    }
                }
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
            echo "🧹 Ejecutando limpieza de recursos..."
            def keepCount = 3
            def trash = new Trash(this)
            
            // 1. Limpieza de artefactos de build (dist, coverage, etc)
            trash.cleanBuildArtifacts()
            
            // 2. Limpieza de imágenes antiguas (Garbage Collection)
            // Construimos el nombre de la imagen igual que en el método build()
            def imageFull = "${config.dockerRegistry}/${config.serviceName?.toLowerCase() ?: 'app'}"
            trash.cleanImages(imageFull, keepCount)
            
        }
    }
}

// Función para construir triggers basado en configuración
def buildTriggers(Map triggerConfig) {

    def triggers = []
    
    switch(triggerConfig.type) {
        case 'polling':
            echo "🔄 Configurando polling SCM: ${triggerConfig.schedule}"
            triggers.add(pollSCM(triggerConfig.schedule))
            break
            
        case 'webhook':
            echo "🎯 Configurando webhook GitHub"
            triggers.add(githubPush())
            break
            
        case 'hybrid':
            echo "🔄🎯 Configurando trigger híbrido (webhook + polling de respaldo)"
            triggers.add(githubPush())
            triggers.add(pollSCM(triggerConfig.backupSchedule ?: 'H/30 * * * *'))
            break
            
        case 'generic':
            echo "⚡ Configurando trigger genérico con token"
            triggers.add(genericTrigger(
                genericVariables: [
                    [key: 'ref', value: '$.ref'],
                    [key: 'repository', value: '$.repository.full_name']
                ],
                causeString: 'Triggered on $ref',
                token: triggerConfig.token ?: 'default-token-123',
                regexpFilterText: '$ref',
                regexpFilterExpression: "refs/heads/(${triggerConfig.branches.join('|')})"
            ))
            break
            
        case 'manual':
            echo "✋ Trigger manual - Solo se ejecuta manualmente"
            break
            
        default:
            echo "🔄 Trigger por defecto: polling cada 5 minutos"
            triggers.add(pollSCM('H/5 * * * *'))
    }
    
    return triggers
}
