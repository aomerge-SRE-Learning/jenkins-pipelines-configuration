package org.aomerge.config

/**
 * Componente para gestionar operaciones en el Cluster de Kubernetes.
 * Abstrae la configuración de kubectl y asegura la limpieza de credenciales.
 */
class ClusterPipeline implements Serializable {
    
    private String namespace
    private String kubeconfigPath
    private String contextName = "ci-context"

    ClusterPipeline(String namespace) {
        this.namespace = namespace
    }

    /**
     * Establece la conexión y ejecuta un bloque de código (Closure).
     * Garantiza que el archivo KUBECONFIG temporal se elimine al finalizar.
     */
    void connect(script, Closure callback) {
        script.withCredentials([
            script.string(credentialsId: 'k8s_token_ci', variable: 'K8S_TOKEN'),
            script.string(credentialsId: 'k8s_server_ci', variable: 'K8S_SERVER'),
            script.string(credentialsId: 'k8s_ca_data_ci', variable: 'K8S_CA_DATA')
        ]) {
            try {
                // Crear un directorio temporal único para el KUBECONFIG
                def workDir = script.sh(script: "mktemp -d", returnStdout: true).trim()
                this.kubeconfigPath = "${workDir}/config"

                script.sh """#!/bin/bash
                    export KUBECONFIG=${this.kubeconfigPath}
                    kubectl config set-cluster ci-cluster \\
                        --server="\$K8S_SERVER" \\
                        --certificate-authority=<(echo "\$K8S_CA_DATA" | base64 -d) \\
                        --embed-certs=true
                    kubectl config set-credentials jenkins-deployer --token="\$K8S_TOKEN"
                    kubectl config set-context ${this.contextName} --cluster=ci-cluster --user=jenkins-deployer --namespace="${this.namespace}"
                    kubectl config use-context ${this.contextName}
                """
                
                script.echo "✅ Conectado al namespace: ${this.namespace}"
                
                // Ejecutar las acciones del usuario
                callback.call()

            } finally {
                disconnect(script)
            }
        }
    }

    /**
     * Ejecuta un comando kubectl inyectando el KUBECONFIG actual.
     */
    void sh(script, String command) {
        if (!this.kubeconfigPath) script.error "❌ No hay una conexión activa al cluster."
        script.sh "export KUBECONFIG=${this.kubeconfigPath} && kubectl ${command}"
    }

    /**
     * Verifica que un despliegue se haya completado correctamente.
     */
    void healthcheck(script, String deploymentName) {
        script.echo "🔍 Verificando estado de: ${deploymentName}..."
        this.sh(script, "rollout status deployment/${deploymentName} --timeout=2m")
    }

    /**
     * Elimina los archivos temporales de configuración.
     */
    void disconnect(script) {
        if (this.kubeconfigPath) {
            script.echo "🧹 Limpiando configuración de Kubernetes..."
            def dir = this.kubeconfigPath.substring(0, this.kubeconfigPath.lastIndexOf('/'))
            script.sh "rm -rf ${dir}"
            this.kubeconfigPath = null
        }
    }
}