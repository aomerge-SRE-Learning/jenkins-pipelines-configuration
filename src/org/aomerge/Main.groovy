package org.aomerge

import org.aomerge.angular.Angular

class Main implements Serializable {
    Map config 

    public Main(Map config){
        this.config = config        
    }

    public run(script) {
        this.navLanguage(script)
    }   

    private navLanguage(script){
        def language = this.config.language;

        switch(language) {
            case "Java":
            // Acción para Java
            script.echo "JAVA"
            break
            case "Angular":
            // Acción para Angular
            script.echo "Angular"
            break
            default:
            script.echo "Error: Lenguaje '${language}' no soportado."
        }
    }    
}