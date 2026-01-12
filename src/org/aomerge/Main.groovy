package org.aomerge

import org.aomerge.lenguage.AngularPipeline
import org.aomerge.lenguage.JavaPipeline
import org.aomerge.config.HelmPipeline

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
    
    private def switchLenguage(lenguage){
            switch(lenguage?.toLowerCase()) {
                case 'angular':
                    return new AngularPipeline(config)
                case 'java':
                    return new JavaPipeline(config)
                default:
                    throw new RuntimeException("❌ Lenguaje no soportado: ${config.language}")
            }
    }

    private void switchCICD(branchName, pipeline, script){
        if (branchName?.toLowerCase()?.startsWith('pr')) {
            this.CIPipeline(pipeline, script)
        } else if (branchName) {
            this.CDPipeline(pipeline, script)            
        }
    }

    private void CIPipeline(pipeline, script){

        script.stage('Copy values helm') {                    
                if (config.configRepoUrl) {
                    // Opción A: Clonar desde un repositorio externo
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
        }
        
        script.stage('Copy helm') {            
            def helmPipeline = new HelmPipeline()
            helmPipeline.copyHelm(script)
            script.sh """
                cd helm  
                ls
                cd ..
            """
        }

        // Ejecutar stages comunes
        script.stage('Test') {
            pipeline.test(script)
        }
        
        script.stage('Build') {
            pipeline.build(script)
        }
    }

    private void CDPipeline(pipeline, script){
        if (pipeline.requireApproval) {
            script.stage('Approval') {
                script.timeout(time: 30, unit: 'DAYS') {
                    script.input(
                        message: "¿Desplegar ${pipeline.serviceName}?",
                        submitter: config.approvers ?: 'admin',
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
                
                // Verificar si el pipeline debe continuar después de la configuración
                if (pipeline.metaClass.respondsTo(pipeline, 'isValidExecution') && 
                    !pipeline.isValidExecution()) {
                    script.echo "🛑 Pipeline detenido - Configuración de rama no válida"
                    return  // Salir del pipeline sin ejecutar más stages
                }
            }
        }        

        this.switchCICD(env.BRANCH_NAME, pipeline, script)                                

    }    
}