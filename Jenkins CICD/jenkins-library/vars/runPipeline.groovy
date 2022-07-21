#!groovy

def call(String type,Map map) {
    if (type == "python") {
        pythonPipeline(map)
    }
    if (type == "argocdPython") {
        argocdPythonPipeline(map)
    }
}