package org.aomerge

import org.aomerge.angular.Angular

class ExtraStages implements Serializable {
    static void runExtraSteps(script) {
        script.echo "🔧 Ejecutando Stage Extra 1 desde src"
        script.echo "📦 Preparando entorno desde src..."
        script.echo "🔧 Ejecutando Stage Extra 2 desde src"
        script.echo "✨ Finalizando tareas adicionales desde src"
        Angular.runAngular(script)
    }
}