package org.aomerge.config
import com.cloudbees.groovy.cps.NonCPS

class BranchConfig implements Serializable {
    boolean dockerPush = false
    boolean deployK8s = false
    boolean requireApproval = false
    String environment
    boolean isValidForExecution = true
    Map k8sDetails = [:]
    Map dockerDetails = [:]
    Map pipelineConfig = [:]

    BranchConfig(branch, config = [:]){
        this.pipelineConfig = config
        this.switchBranch(branch)
        this.applyOverrides()
    }

    private void applyOverrides() {
        // 1. Soporte para configuración estructurada por ambientes (environments)
        if (this.pipelineConfig?.environments && this.pipelineConfig.environments[this.environment]) {
             def envConfig = this.pipelineConfig.environments[this.environment]
             if (envConfig.namespace) {
                this.k8sDetails.namespace = envConfig.namespace
             }
             if (envConfig.credentials) {
                this.k8sDetails.credentials = envConfig.credentials
             }
             if (envConfig.docker) {
                this.dockerDetails = envConfig.docker
             }
        }

        // 2. Soporte para configuración simplificada en raíz
        
        // Docker Registry override
        if (this.pipelineConfig?.dockerRegistry) {
            this.dockerDetails.registry = this.pipelineConfig.dockerRegistry
        }
        if (this.pipelineConfig?.dockerCredentialId) {
            this.dockerDetails.credentialId = this.pipelineConfig.dockerCredentialId
        }

        // Namespace override: Map (por ambiente) o String (global)
        if (this.pipelineConfig?.namespace) {
            def ns = this.pipelineConfig.namespace
            if (ns instanceof Map && ns[this.environment]) {
                this.k8sDetails.namespace = ns[this.environment]
            } else if (ns instanceof String && !ns.isEmpty()) {
                this.k8sDetails.namespace = ns
            }
        }

        // Credentials override: Map (por ambiente) o List (global)
        // Se espera lista ordenada: [token_id, server_url_id, ca_cert_id]
        if (this.pipelineConfig?.credentialID) {
            def creds = this.pipelineConfig.credentialID
            if (creds instanceof Map && creds[this.environment]) {
                this.k8sDetails.credentials = parseCredsList(creds[this.environment])
            } else if (creds instanceof List && !creds.isEmpty()) {
                this.k8sDetails.credentials = parseCredsList(creds)
            }
        }
    }

    @NonCPS
    private Map parseCredsList(List list) {
        if (list.size() >= 3) {
            return [
                tokenRef: list[0],
                serverRef: list[1],
                caRef: list[2]
            ]
        }
        return [:] // Retornar vacío si la lista no es válida
    }

    @NonCPS
    public void updateFromExternal(Map externalConfig) {
        if (!externalConfig) return

        // Prioridad 1: Estructura plana (Branch-specific)
        // Si el JSON viene directo para la rama actual
        if (externalConfig.namespace) {
            this.k8sDetails.namespace = externalConfig.namespace
        }
        if (externalConfig.credentials) {
            if (externalConfig.credentials instanceof Map) {
                this.k8sDetails.credentials = externalConfig.credentials
            } else if (externalConfig.credentials instanceof List) {
                this.k8sDetails.credentials = parseCredsList(externalConfig.credentials)
            }
        }
        if (externalConfig.docker) {
            this.dockerDetails = externalConfig.docker
        }

        // Prioridad 2: Retrocompatibilidad con bloque 'environments'
        if (externalConfig.environments && externalConfig.environments[this.environment]) {
            def envConfig = externalConfig.environments[this.environment]
            if (envConfig.namespace) {
                this.k8sDetails.namespace = envConfig.namespace
            }
            if (envConfig.credentials) {
                if (envConfig.credentials instanceof Map) {
                    this.k8sDetails.credentials = envConfig.credentials
                } else if (envConfig.credentials instanceof List) {
                    this.k8sDetails.credentials = parseCredsList(envConfig.credentials)
                }
            }
            if (envConfig.docker) {
                this.dockerDetails = envConfig.docker
            }
        }
    }

    @NonCPS
    public String switchBranch (branch){
        // Validación inicial para evitar ejecución duplicada en webhook
        def currentBranch = branch?.toLowerCase()?.trim()
        
        switch(currentBranch){
            case "master":
            case "main":
                this.environment = "production"    
                this.requireApproval = true     
                this.deployK8s = true
                this.dockerPush = true
                this.isValidForExecution = true
                this.k8sDetails = [:] // Limpio, esperando override
                break
            case "qa":
                this.environment = "qa"
                this.requireApproval = true
                this.deployK8s = true
                this.dockerPush = true
                this.isValidForExecution = true
                this.k8sDetails = [:] // Limpio, esperando override
                break
            case "dev":
            case "develop":
                this.environment = "development"
                this.requireApproval = false
                this.deployK8s = true
                this.dockerPush = true  // En dev no push a registry
                this.isValidForExecution = true
                this.k8sDetails = [:] // Limpio, esperando override
                break
            default:
                if (currentBranch ==~ /^feature-.*$/) {
                    this.environment = "feature"
                    this.requireApproval = false
                    this.deployK8s = false
                    this.dockerPush = false
                    this.isValidForExecution = true
                    this.k8sDetails = [:] // Limpio, esperando override
                } else if (currentBranch ==~ /^bugfix-.*$/) {
                    this.environment = "bugfix"
                    this.requireApproval = false
                    this.deployK8s = false
                    this.dockerPush = false
                    this.isValidForExecution = true
                    this.k8sDetails = [:] // Limpio, esperando override
                } else if (currentBranch ==~ /^hotfix-.*$/) {
                    this.environment = "hotfix"
                    this.requireApproval = true
                    this.deployK8s = true
                    this.dockerPush = true
                    this.isValidForExecution = true
                    this.k8sDetails = [:] // Limpio, esperando override
                } else if (currentBranch?.startsWith('pr')) {
                    // Para Pull Requests - solo CI, no CD
                    this.environment = "pr"
                    this.requireApproval = false
                    this.deployK8s = false
                    this.dockerPush = false
                    this.isValidForExecution = true
                } else {
                    // Ramas no reconocidas - marcar como no válidas para evitar ejecución
                    this.environment = currentBranch ?: "unknown"
                    this.requireApproval = false
                    this.deployK8s = false
                    this.dockerPush = false
                    this.isValidForExecution = false
                }
                break
        }        
    }
    
    @NonCPS
    public boolean shouldExecute(script, currentBranch = null) {        

        if (!this.isValidForExecution) {
            script.echo "⚠️ Rama '${this.environment}' no configurada para ejecución automática"
            return false
        }
        return true
    }    
}