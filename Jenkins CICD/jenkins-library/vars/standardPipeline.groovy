#!groovy

def call() {
    node {
        stage('Checkout') {
            checkout scm
        }
        def cfg = pipelineCfg()
        println cfg

        switch(cfg.type) {
            case "python":
                pythonPipeline(cfg)
                break
            case "nodejs":
                nodejsPipeline(cfg)
                break
        }
    }
}