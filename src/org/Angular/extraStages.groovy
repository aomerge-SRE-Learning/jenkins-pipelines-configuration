def call() {
    stage('Stage Extra 1') {
        steps {
            script {
                echo "🔧 Este es el Stage Extra 1"
            }
        }
    }
    stage('Stage Extra 2') {
        steps {
            script {
                echo "🔧 Este es el Stage Extra 2"
            }
        }
    }
}