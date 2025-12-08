package org.aomerge

import org.aomerge.angular.Angular

class ExtraStages implements Serializable {
    static void runExtraSteps(script, String name) {
        script.echo "🔧 Ejecutando Stage Extra 1 desde src"
        script.echo "📦 Preparando entorno desde src..."
        script.echo "🔧 Ejecutando Stage Extra 2 desde src"
        script.echo "Nombre recibido: ${name}"
        Angular.runAngular(script)
    }
}