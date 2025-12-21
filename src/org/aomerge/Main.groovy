package org.aomerge

import org.aomerge.angular.AngularPipeline
import org.aomerge.java.JavaPipeline

class Main implements Serializable {
    Map config
    String branch
    
    Main(Map config, String branch) {
        this.config = config
        this.branch = branch
    }
    
    void executePipeline(script) {
        def pipeline
        
        switch(config.language?.toLowerCase()) {
            case 'angular':
                pipeline = new AngularPipeline(config)
                break
            case 'java':
                pipeline = new JavaPipeline(config)
                break
            default:
                script.error "❌ Lenguaje no soportado: ${config.language}"
        }
        
        script.stage('Config') {
            pipeline.config(script, this.branch)
        }

        // Ejecutar stages comunes
        script.stage('Test') {
            pipeline.test(script)
        }
        
        script.stage('Build') {
            pipeline.build(script)
        }
        
        // Aprobación opcional
        if (pipeline.requireApproval) {
            script.stage('Approval') {
                script.timeout(time: 30, unit: 'MINUTES') {
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
            
        }


    }
}