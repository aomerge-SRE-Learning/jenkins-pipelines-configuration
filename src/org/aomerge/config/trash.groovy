package org.aomerge.config

/**
 * Trash: componente de limpieza para Jenkins Shared Library.
 *
 * Primera versión (base):
 * - Permite limpiar todo el workspace o rutas/patrones específicos.
 * - Incluye protecciones básicas para evitar borrar fuera del workspace.
 *
 * Uso típico (desde pipeline):
 *   def trash = new org.aomerge.config.Trash(this)
 *   trash.clean(deleteWorkspace: true)
 *   trash.clean(paths: ['dist', 'build'], dryRun: false)
 *   trash.clean(globs: ['/*.tmp', '/.cache/'], excludes: ['/node_modules/'])
 **/
class Trash implements Serializable {

    private final def steps

    Trash(def steps) {
        this.steps = steps
    }

    void clean(Map cfg = [:]) {
        boolean dryRun = (cfg.dryRun as boolean) ?: false
        boolean deleteWorkspace = (cfg.deleteWorkspace as boolean) ?: false

        List<String> paths = (cfg.paths ?: []) as List<String>
        List<String> globs = (cfg.globs ?: []) as List<String>
        List<String> excludes = (cfg.excludes ?: []) as List<String>

        if (deleteWorkspace) {
            steps.echo("[trash] deleteWorkspace=true (dryRun=${dryRun})")
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

        if (paths.isEmpty() && globs.isEmpty() && !deleteWorkspace) {
            steps.echo("[trash] Nothing to do (no deleteWorkspace, no paths, no globs).")
        }
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
    private boolean isSafeRelative(String p) {
        if (p == null) return false
        String s = p.trim()
        if (s.isEmpty()) return false
        if (s.startsWith("/") || s.startsWith("\\") || s.matches(/^[A-Za-z]:.*/)) return false
        if (s.contains("..")) return false
        return true
    }

    private String escapeSingleQuotes(String s) {
        // Para envolver en comillas simples en sh: ' -> '"'"'
        return s.replace("'", "'\"'\"'")
    }
}