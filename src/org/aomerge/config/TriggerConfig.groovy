package org.aomerge.config

class TriggerConfig implements Serializable {
    
    /**
     * Configuraciones predefinidas para diferentes tipos de proyectos
     */
    static Map getDefaultConfig(String projectType, String environment = 'development') {
        def configs = [
            'angular': [
                development: [
                    type: 'polling',
                    schedule: 'H/5 * * * *',  // Cada 5 minutos
                    branches: ['main', 'develop', 'feature/*']
                ],
                staging: [
                    type: 'hybrid',
                    schedule: 'H/10 * * * *',
                    backupSchedule: 'H/30 * * * *',
                    branches: ['main', 'develop']
                ],
                production: [
                    type: 'webhook',
                    branches: ['main']
                ]
            ],
            'java': [
                development: [
                    type: 'polling',
                    schedule: 'H/10 * * * *',
                    branches: ['main', 'develop', 'feature/*', 'hotfix/*']
                ],
                staging: [
                    type: 'hybrid',
                    schedule: 'H/15 * * * *',
                    backupSchedule: 'H/60 * * * *',
                    branches: ['main', 'develop']
                ],
                production: [
                    type: 'manual',
                    branches: ['main']
                ]
            ]
        ]
        
        return configs[projectType]?[environment] ?: [
            type: 'polling',
            schedule: 'H/5 * * * *',
            branches: ['main', 'develop']
        ]
    }
    
    /**
     * Configuración para webhook programático
     */
    static Map getWebhookConfig(String jenkinsUrl) {
        return [
            type: 'generic',
            token: generateSecureToken(),
            url: "${jenkinsUrl}/generic-webhook-trigger/invoke",
            branches: ['main', 'develop', 'feature/*']
        ]
    }
    
    private static String generateSecureToken() {
        def random = new Random()
        def chars = 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789'
        return (1..32).collect { chars[random.nextInt(chars.length())] }.join('')
    }
}