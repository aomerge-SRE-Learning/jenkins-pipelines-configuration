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
     * Usa withEnv para que el KUBECONFIG tenga el alcance correcto en todo el bloque.
     */
    void connect(script, Closure callback) {
        script.withCredentials([
            script.string(credentialsId: 'k8s_token_ci', variable: 'K8S_TOKEN'),
            script.string(credentialsId: 'k8s_server_ci', variable: 'K8S_SERVER'),
            script.string(credentialsId: 'k8s_ca_data_ci', variable: 'K8S_CA_DATA')
        ]) {
            def workDir = script.sh(script: "mktemp -d", returnStdout: true).trim()
            this.kubeconfigPath = "${workDir}/config"

            script.sh """#!/bin/bash
                # Configuración inicial del cluster
                kubectl config set-cluster ci-cluster --server="\$K8S_SERVER" --kubeconfig=${this.kubeconfigPath}
                
                # Decodificar CA
                echo "\$K8S_CA_DATA" | base64 -d > "${workDir}/ca.crt" || echo "\$K8S_CA_DATA" | base64 --decode > "${workDir}/ca.crt"
                
                kubectl config set-credentials ci-user --token="$K8S_TOKEN" --kubeconfig=${this.kubeconfigPath}

                kubectl config set-cluster ci-cluster \\
                    --certificate-authority="${workDir}/ca.crt" \\
                    --embed-certs=true \\
                    --kubeconfig=${this.kubeconfigPath}
                
                kubectl config set-context ci-context \\
                    --cluster=ci-cluster \\
                    --namespace="${this.namespace}" \\
                    --user=ci-user \\
                    --kubeconfig=${this.kubeconfigPath}
                
                kubectl config use-context ci-context --kubeconfig=${this.kubeconfigPath}
                rm "${workDir}/ca.crt"
            """

            // El secreto del alcance: withEnv inyecta la variable en todos los procesos hijos (sh)
            script.withEnv(["KUBECONFIG=${this.kubeconfigPath}"]) {
                try {
                    script.echo "✅ Alcance validado: KUBECONFIG configurado en el entorno."
                    // Validación solicitada por el usuario dentro del pipeline
                    script.sh "kubectl config view --minify -o jsonpath='{.clusters[0].cluster.server}'; echo"
                    
                    callback.call()
                } finally {
                    script.sh "rm -rf ${workDir}"
                    this.kubeconfigPath = null
                }
            }
        }
    }

    /**
     * Ejecuta un comando kubectl inyectando el TOKEN directamente.
     * El KUBECONFIG ya está en el alcance gracias al withEnv del connect.
     */
    void sh(script, String command) {
        script.withCredentials([script.string(credentialsId: 'k8s_token_ci', variable: 'K8S_TOKEN')]) {
            script.sh "helm ${command} --token=\$K8S_TOKEN"
        }
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