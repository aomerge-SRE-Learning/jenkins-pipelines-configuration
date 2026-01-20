package org.aomerge.config
import com.cloudbees.groovy.cps.NonCPS

/**
 * Trash: Componente centralizado de limpieza para Jenkins Shared Library.
 *
 * Funcionalidades:
 * 1. Gestión de Workspace (Filesystem):
 *    - clean(): Borrado granular o total.
 *    - cleanBuildArtifacts(): Borrado inteligente de residuos de build (rápido).
 * 
 * 2. Gestión de Imágenes (Container Registry):
 *    - cleanImages(): Política de retención de imágenes (Garbage Collection).
 *
 * Uso:
 *   def trash = new org.aomerge.config.Trash(this)
 *   trash.cleanBuildArtifacts() // Limpieza rápida post-build
 *   trash.cleanImages("my-registry/my-app", 3) // Mantener últimas 3 imágenes
 **/
class Trash implements Serializable {

    private final def steps

    Trash(def steps) {
        this.steps = steps
    }

    /**
     * Limpia archivos o directorios del workspace.
     * @param cfg Mapa de configuración:
     *   - deleteWorkspace (boolean): Si es true, borra TODO (lento en el próximo build).
     *   - paths (List<String>): Rutas específicas a borrar.
     *   - globs (List<String>): Patrones (ej: *.log) a borrar.
     *   - dryRun (boolean): Solo simula el borrado.
     */
    void clean(Map cfg = [:]) {
        boolean dryRun = (cfg.dryRun as boolean) ?: false
        boolean deleteWorkspace = (cfg.deleteWorkspace as boolean) ?: false

        List<String> paths = (cfg.paths ?: []) as List<String>
        List<String> globs = (cfg.globs ?: []) as List<String>
        List<String> excludes = (cfg.excludes ?: []) as List<String>

        if (deleteWorkspace) {
            steps.echo("[trash] 🚨 deleteWorkspace=true (dryRun=${dryRun}) - Esto forzará un checkout completo en el próximo build.")
            if (!dryRun) {
                steps.deleteDir()
            }
            return
        }

        if (!paths.isEmpty()) {
            steps.echo("[trash] Removing paths: ${paths} (dryRun=${dryRun})")
            removePaths(paths, dryRun)
        }

        if (!globs.isEmpty()) {
            steps.echo("[trash] Removing globs: ${globs} excludes: ${excludes} (dryRun=${dryRun})")
            removeByGlobs(globs, excludes, dryRun)
        }

        if (paths.isEmpty() && globs.isEmpty()) {
            steps.echo("[trash] Nothing to do.")
        }
    }

    /**
     * Limpieza inteligente: Borra solo los artefactos generados por el pipeline.
     * Mantiene 'node_modules' y '.git' para acelerar el siguiente build.
     */
    void cleanBuildArtifacts() {
        steps.echo("[trash] 🧹 Ejecutando limpieza inteligente de artefactos...")
        this.clean(
            paths: [
                'dist', 
                'build', 
                'test-results', 
                'coverage',
                'Dockerfile', 
                'Dockerfile.base',
                'nginx.conf',
                'helm',
                'config'
            ],
            globs: ['*.log', '*.tmp', '*.tar.gz']
        )
    }

    /**
     * Limpia imágenes antiguas del registro local (Podman/Docker).
     * @param imageName Nombre completo de la imagen (ej: localhost/mi-app).
     * @param keepCount Cuántas versiones recientes mantener (default: 3).
     */
    void cleanImages(String imageName, int keepCount = 3) {
        steps.echo("[trash] 🐳 Limpiando imágenes antiguas para: ${imageName} (Mantener: ${keepCount})")
        
        // Validación básica para evitar inyección de comandos
        if (imageName.contains(";") || imageName.contains("|") || imageName.contains("&")) {
             steps.error("[trash] ❌ Nombre de imagen inválido: ${imageName}")
             return
        }

        // 1. Listar tags
        // 2. Ordenar descendente (asumiendo timestamps o semver)
        // 3. Saltar los N primeros (keepCount)
        // 4. Borrar el resto
        String cmd = """
            podman images ${imageName} --format "{{.Tag}}" | \\
            sort -r | \\
            tail -n +${keepCount + 1} | \\
            xargs -r -I {} podman rmi ${imageName}:{} || true
        """
        // Elimina imágenes huérfanas (dangling) que no tienen tag asociado
        steps.echo("[trash] 🧹 Limpiando imágenes huérfanas (dangling)...")
        String orphanCmd = """
            podman images --filter "dangling=true" -q | \\
            xargs -r podman rmi || true
        """
        steps.sh(script: orphanCmd, label: "trash: garbage collection de imagenes huerfanas")
        
        steps.sh(script: cmd, label: "trash: garbage collection de imagenes")
    }

    private void removePaths(List<String> paths, boolean dryRun) {
        paths.each { String p ->
            if (!isSafeRelative(p)) {
                steps.echo("[trash] SKIP unsafe path: '${p}'")
                return
            }

            // Borrado tolerante: si no existe no falla.
            String cmd = "rm -rf -- '${escapeSingleQuotes(p)}' || true"
            if (dryRun) {
                steps.echo("[trash] DRY RUN: ${cmd}")
            } else {
                steps.sh(label: "trash: rm -rf ${p}", script: cmd)
            }
        }
    }

    private void removeByGlobs(List<String> globs, List<String> excludes, boolean dryRun) {
        // findFiles soporta glob. Si no está disponible en tu Jenkins, sustituir por `sh "find ..."`
        List files = []
        globs.each { String g ->
            files.addAll(steps.findFiles(glob: g) ?: [])
        }

        def excluded = { String path ->
            if (!excludes || excludes.isEmpty()) return false
            // Exclusión simple por glob-like (contiene), suficiente para v1; ajustar si hace falta.
            excludes.any { ex -> path.contains(ex.replace("**/", "").replace("/**", "").replace("**", "")) }
        }

        files.collect { it?.path as String }
                .findAll { it }
                .unique()
                .findAll { !excluded(it) }
                .each { String p ->
                    if (!isSafeRelative(p)) {
                        steps.echo("[trash] SKIP unsafe match: '${p}'")
                        return
                    }
                    String cmd = "rm -rf -- '${escapeSingleQuotes(p)}' || true"
                    if (dryRun) {
                        steps.echo("[trash] DRY RUN: ${cmd}")
                    } else {
                        steps.sh(label: "trash: rm -rf ${p}", script: cmd)
                    }
                }
    }

    /**
     * Seguridad básica:
     * - No permite rutas absolutas
     * - No permite '..'
     * - No permite null/empty
     */
    @NonCPS
    private boolean isSafeRelative(String p) {
        if (p == null) return false
        String s = p.trim()
        if (s.isEmpty()) return false
        if (s.startsWith("/") || s.startsWith("\\") || s.matches(/^[A-Za-z]:.*/)) return false
        if (s.contains("..")) return false
        return true
    }

    @NonCPS
    private String escapeSingleQuotes(String s) {
        // Para envolver en comillas simples en sh: ' -> '"'"'
        return s.replace("'", "'\"'\"'")
    }
}