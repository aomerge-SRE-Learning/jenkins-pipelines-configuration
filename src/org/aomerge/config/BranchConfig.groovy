package org.aomerge.config
import com.cloudbees.groovy.cps.NonCPS

class BranchConfig implements Serializable {
    boolean dockerPush = false
    boolean deployK8s = false
    boolean requireApproval = false
    String environment
    boolean isValidForExecution = true  

    BranchConfig(branch){
        this.switchBranch(branch)
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
    
    @NonCPS
    public boolean shouldExecute(script, currentBranch = null) {        

        if (!this.isValidForExecution) {
            script.echo "⚠️ Rama '${this.environment}' no configurada para ejecución automática"
            return false
        }
                
        def realBranch = currentBranch ?: script.env.BRANCH_NAME
        def changeTarget = script.env.CHANGE_TARGET
        def changeBranch = script.env.CHANGE_BRANCH

        // Si CHANGE_TARGET es null, consideramos ejecución manual
        if (changeTarget == null && changeBranch == null) {
            script.echo "ℹ️ Ejecución manual detectada (CHANGE_TARGET es null)"
            return true
        }

        // Validar que solo se ejecute en la rama destino (target), no en la fuente (source)
        if (realBranch?.toLowerCase() == changeBranch?.toLowerCase()) {
            script.echo "⛔ Ejecución en rama fuente '${realBranch}' detectada (source branch) - Cancelando ejecución"
            return false
        }
        // if (realBranch?.toLowerCase() != changeTarget?.toLowerCase()) {
        //     script.echo "⚠️ Rama actual '${realBranch}' no coincide con rama destino '${changeTarget}' - Saltando ejecución"
        //     return false
        // }
        
        return true
    }    
}