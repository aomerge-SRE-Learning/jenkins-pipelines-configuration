package org.aomerge

import org.aomerge.lenguage.AngularPipeline
import org.aomerge.lenguage.JavaPipeline
import org.aomerge.config.HelmPipeline
import com.cloudbees.groovy.cps.NonCPS

class Main implements Serializable {
    Map config
    String branch
    def env  // Objeto env completo de Jenkins
    def script  // Objeto script de Jenkins
    
    Main(Map config, def env) {
        this.config = config
        this.env = env
        // Determinar la rama real: Si es PR, usar CHANGE_TARGET, sino usar BRANCH_NAME
        this.branch = env.CHANGE_TARGET ?: env.BRANCH_NAME
    }
        
    private boolean isManualTrigger(script) {
        def buildCauses = script.currentBuild.getBuildCauses()
        for (cause in buildCauses) {
            // Detectar UserIdCause (ejecución manual desde UI)
            if (cause._class?.contains('UserIdCause')) {
                script.echo "🖱️ Ejecución MANUAL detectada por: ${cause.userName ?: 'usuario'}"
                return true
            }
        }
        return false
    }
    
    private def switchLenguage(lenguage){
            switch(lenguage?.toLowerCase()) {
                case 'angular':
                    return new AngularPipeline(config)
                case 'java':
                    return new JavaPipeline(config)
                default:
                    throw new RuntimeException("❌ Lenguaje no soportado: ${lenguage}")
            }
    }

    private void switchCICD(branchName, pipeline, script){        
        boolean isManual = isManualTrigger(script)
        if (isManual) {
            script.echo "✅ Ejecución manual - Ejecutando proceso completo (CI/CD)"
            // En ejecución manual, ejecutar tanto CI como CD
            this.CIPipeline(pipeline, script)
            this.CDPipeline(pipeline, script)
        }else if (script.env.CHANGE_ID) {
            script.echo "🔀 PR detectado - Solo CI"
            this.CIPipeline(pipeline, script)
        } else if (branchName) {
            script.echo "🚀 Push detectado - Ejecutando CD completo"
            this.CDPipeline(pipeline, script)            
        }
    }

    private void CIPipeline(pipeline, script){

        // Ejecutar stages comunes
        script.stage('Test') {
            pipeline.test(script)
        }
        
        script.stage('Build') {
            pipeline.build(script)
        }

        script.stage("Remove files"){
            pipeline.trash(script)
        }
    }

    private void CDPipeline(pipeline, script){
        script.stage('Copy values helm') {
            def valuesPath = "config/${this.serviceName}/deploy-helm.yaml"
            def ingressValuesPath = "config/${this.serviceName}/ingress-helm.yaml"
            if (config.configRepoUrl) {                
                script.echo "Source: External Repository ${config.configRepoUrl}"
                script.checkout([
                    $class: 'GitSCM',
                    branches: [[name: this.branch ?: 'dev']],
                    extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'config']],
                    userRemoteConfigs: [[
                        url: config.configRepoUrl, 
                        credentialsId: "aomerge"
                    ]]
                ])
            } else {
                // Opción B: Usar la lógica actual de la librería (libraryResource)
                def helmPipeline = new HelmPipeline()
                helmPipeline.copyHelm(script)
            }
            
            script.sh """
                echo "📂 Contenido de la carpeta helm:"
                ls -R config                
            """       
            if (!script.fileExists(valuesPath) && !script.fileExists(ingressValuesPath)) {
                script.error("❌ No se encontraron los archivos de configuración: '${valuesPath}' y '${ingressValuesPath}'. No se puede continuar con el despliegue.")
            } else if (!script.fileExists(valuesPath)) {
                script.error("❌ No se encontró el archivo de configuración de valores: '${valuesPath}'. No se puede continuar con el despliegue.")
            } else if (!script.fileExists(ingressValuesPath)) {
                script.error("❌ No se encontró el archivo de configuración de ingress: '${ingressValuesPath}'. No se puede continuar con el despliegue.")
            }             
        }
        
        script.stage('Copy helm') {            
            def helmPipeline = new HelmPipeline()
            helmPipeline.copyHelm(script)
            script.sh """
                cd helm  
                ls
                cd templates
                ls
                cd ../..
            """
        }

        script.echo "pipeline.requireApproval: ${pipeline.requireApproval}"
        if (pipeline.requireApproval) {
            script.stage('Approval') {
                def serviceName = pipeline.serviceName ?: config?.serviceName ?: 'aplicación'
                script.timeout(time: 30, unit: 'DAYS') {
                    script.input(
                        message: "¿Desplegar ${serviceName}?",
                        submitter: config?.approvers ?: 'admin',
                        ok: 'Aprobar'
                    )
                }
            }
        }

        script.stage('Deploy') {
            pipeline.deploy(script)
        }

        script.stage('healcheck'){
        }

        script.stage("Remove files"){
            pipeline.trash(script)
        }

    }

    void executePipeline(script) {
        this.script = script  // Guardamos script como atributo de la clase
        def pipeline = this.switchLenguage(config.language)

        script.stage('Info') {
            script.echo "🌿 Rama actual (BRANCH_NAME): ${env.BRANCH_NAME}"
            script.echo "🔀 Rama fuente del PR (CHANGE_BRANCH): ${env.CHANGE_BRANCH}"
            script.echo "🎯 Rama destino del PR (CHANGE_TARGET): ${env.CHANGE_TARGET}"
            script.echo "🔧 Rama procesada: ${this.branch}"
        }
        
        script.stage('Config') {
            if (pipeline.metaClass.respondsTo(pipeline, 'config')) {
                pipeline.config(script, this.branch)                                
            }            
        }    
                
        if (pipeline.metaClass.respondsTo(pipeline, 'isValidExecution') && 
            !pipeline.isValidExecution()) {
            script.echo "🛑 Pipeline detenido - Configuración de rama no válida"
            return  
        } else{
            this.switchCICD(env.BRANCH_NAME, pipeline, script)                                            
        }



    }    
}