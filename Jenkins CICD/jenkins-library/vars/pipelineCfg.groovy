#!groovy


def call() {
    def full_job_name = env.JOB_NAME.tokenize('/') as String[]
    def job_name = full_job_name[1]

    // 需安装插件 Pipeline Utility Steps
    Map pipelineCfg = readYaml file: "${job_name}/pipelineCfg.yaml"
    return pipelineCfg
}