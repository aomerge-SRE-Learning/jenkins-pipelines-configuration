package org.aomerge.config
import com.cloudbees.groovy.cps.NonCPS

class BranchConfig implements Serializable {
    boolean dockerPush = false
    boolean deployK8s = false
    boolean requireApproval = false
    String environment
    boolean isValidForExecution = true  // Nueva propiedad para controlar ejecución

    BranchConfig(branch){
        this.switchBranch(branch)
    }

    @NonCPS
    public void switchBranch (branch){
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
                break
            case "qa":
                this.environment = "qa"
                this.requireApproval = false
                this.deployK8s = true
                this.dockerPush = true
                this.isValidForExecution = true
                break
            case "dev":
            case "develop":
                this.environment = "development"
                this.requireApproval = false
                this.deployK8s = true
                this.dockerPush = false  // En dev no push a registry
                this.isValidForExecution = true
                break
            default:
                if (currentBranch ==~ /^feature-.*$/) {
                    this.environment = "feature"
                    this.requireApproval = false
                    this.deployK8s = false
                    this.dockerPush = false
                    this.isValidForExecution = true
                } else if (currentBranch ==~ /^bugfix-.*$/) {
                    this.environment = "bugfix"
                    this.requireApproval = false
                    this.deployK8s = false
                    this.dockerPush = false
                    this.isValidForExecution = true
                } else if (currentBranch ==~ /^hotfix-.*$/) {
                    this.environment = "hotfix"
                    this.requireApproval = true
                    this.deployK8s = true
                    this.dockerPush = true
                    this.isValidForExecution = true
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

    // Método para validar si esta rama debe ejecutarse (solución al problema del webhook)
    @NonCPS
    public boolean shouldExecute(script, currentBranch = null) {
        if (!this.isValidForExecution) {
            script.echo "⚠️ Rama '${this.environment}' no configurada para ejecución automática"
            return false
        }
        
        // Validación específica para webhooks que evita ejecución duplicada
        def realBranch = currentBranch ?: script.env.BRANCH_NAME
        if (realBranch?.toLowerCase() != this.environment?.toLowerCase() && 
            !realBranch?.toLowerCase()?.contains(this.environment?.toLowerCase())) {
            script.echo "⚠️ Rama actual '${realBranch}' no coincide con configuración '${this.environment}' - Saltando ejecución"
            return false
        }
        
        return true
    }

    public boolean getDockerPush(){
        return this.dockerPush
    }

    public boolean getDeployK8s(){
        return this.deployK8s
    }

    public boolean getRequireApproval(){
        return this.requireApproval
    }

    public String getEnvironment(){
        return this.environment
    }
}