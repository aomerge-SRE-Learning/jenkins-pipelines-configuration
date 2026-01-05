# Configuraciones de ejemplo para diferentes proyectos

## Proyecto Angular (Desarrollo rápido)
```groovy
jenkinsPipelinesExample([
    language: 'Angular',    
    dockerRegistry: 'docker.io/mycompany',        
    approvers: 'admin',    
    typeDeployd: "helm",
    configRepoUrl: "",
    
    // 🔄 Trigger: Polling cada 5 minutos (CONFIABLE)
    triggers: [
        type: 'polling',
        schedule: 'H/5 * * * *',
        branches: ['main', 'develop', 'feature/*']
    ]
])
```

## Proyecto Java (Producción)
```groovy
jenkinsPipelinesExample([
    language: 'Java',    
    dockerRegistry: 'docker.io/mycompany',        
    approvers: 'admin,tech-lead',    
    typeDeployd: "kubectl",
    configRepoUrl: "",
    
    // ✋ Trigger: Manual (CONTROL TOTAL)
    triggers: [
        type: 'manual',
        branches: ['main']
    ]
])
```

## Proyecto Híbrido (Webhook + Respaldo)
```groovy
jenkinsPipelinesExample([
    language: 'Angular',    
    dockerRegistry: 'docker.io/mycompany',        
    approvers: 'admin',    
    typeDeployd: "helm",
    configRepoUrl: "",
    
    // 🔄🎯 Trigger: Webhook con respaldo de polling
    triggers: [
        type: 'hybrid',
        schedule: 'H/10 * * * *',        // Trigger principal cada 10 min
        backupSchedule: 'H/60 * * * *',  // Respaldo cada hora
        branches: ['main', 'develop']
    ]
])
```

## Proyecto con Token Personalizado
```groovy
jenkinsPipelinesExample([
    language: 'Angular',    
    dockerRegistry: 'docker.io/mycompany',        
    approvers: 'admin',    
    typeDeployd: "helm",
    configRepoUrl: "",
    
    // ⚡ Trigger: Genérico con token personalizado
    triggers: [
        type: 'generic',
        token: 'mi-token-secreto-angular-123',
        branches: ['main', 'develop', 'feature/*']
    ]
])
```