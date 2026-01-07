package org.aomerge

import org.aomerge.angular.AngularPipeline
import org.aomerge.java.JavaPipeline
import org.aomerge.config.HelmPipeline

class Main implements Serializable {
    Map config
    String branch
    def env  // Objeto env completo de Jenkins
    
    Main(Map config, def env) {
        this.config = config
        this.env = env
        // Determinar la rama real: Si es PR, usar CHANGE_TARGET, sino usar BRANCH_NAME
        this.branch = env.CHANGE_TARGET ?: env.BRANCH_NAME
    }
    
    private void switchLenguage(pipeline, lenguage){
        switch(lenguage?.toLowerCase()) {
            case 'angular':
                pipeline = new AngularPipeline(config)
                break
            case 'java':
                pipeline = new JavaPipeline(config)
                break
            default:
                script.error "❌ Lenguaje no soportado: ${config.language}"
        }
    }

    private void switchCICD(branchName, pipeline){
        if (branchName?.toLowerCase()?.startsWith('pr')) {
            this.CIPipeline(pipeline)
        } else if (branchName) {
            this.CDPipeline(pipeline)            
        }
    }

    private void CIPipeline(pipeline){
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

    private void CDPipeline(pipeline){
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
        def pipeline = this.switchLenguage(pipeline, config.language)

        script.stage('Info') {
            script.echo "Rama actual (BRANCH_NAME): ${env.BRANCH_NAME}"
            script.echo "Rama fuente del PR (CHANGE_BRANCH): ${env.CHANGE_BRANCH}"
            script.echo "Rama destino del PR (CHANGE_TARGET): ${env.CHANGE_TARGET}"
        }
        
        script.stage('Config') {
            pipeline.config(script, this.branch)
        }        

        this.switchCICD(env.BRANCH_NAME, pipeline)                                

    }    
}