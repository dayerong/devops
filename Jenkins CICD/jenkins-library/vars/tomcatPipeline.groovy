#!groovy

def createTime() {
    return new Date().format('yyyy-MM-dd HH:mm:ss')
}

def podCreateTime() {
    return new Date().format('yyyyMMddHHmmss', TimeZone.getTimeZone('Asia/Shanghai'))
}

def call(Map map) {
    pipeline {
        agent {
            node {
                label 'maven'
            }
        }

        environment {
            start_time = createTime()
            // 通知人员
            touser = "${map.touser}"

            // harbor仓库配置
            registry_name = "${map.registry_name}"
            project_name = "${map.project_name}"
            registryUrl= "${map.registryUrl}"
            branch_name = "${env.BRANCH_NAME}"
            docker_name = "${project_name}"

            // 应用代码仓库地址
            app_git_url = "${map.app_git_url}"

            // 配置文件仓库地址
            config_prd_url = "${map.config_prd_url}"
            config_dev_url = "${map.config_dev_url}"


            // 代码本地目录
            config_workspace = "/home/jenkins/agent/workspace/${project_name}/config/${env.BRANCH_NAME}"
            app_workspace = "/home/jenkins/agent/workspace/${project_name}/code/${env.BRANCH_NAME}"

            dockerfile_path = "${WORKSPACE}/${project_name}/Dockerfile"
            config_file = "${config_workspace}/cas-server-webapp/src/main/webapp/WEB-INF/deployerConfigContext.xml"
            base_config_file = "${app_workspace}/cas-server-webapp/src/main/webapp/WEB-INF/deployerConfigContext.xml"

            // Kubesphere变量
            DOCKER_CREDENTIAL_ID = "${map.DOCKER_CREDENTIAL_ID}"
            KUBECONFIG_CREDENTIAL_ID = "${map.KUBECONFIG_CREDENTIAL_ID}"
            REGISTRY =  "${map.REGISTRY}"
            DOCKERHUB_NAMESPACE = "${map.DOCKERHUB_NAMESPACE}"
            GITHUB_ACCOUNT = "${map.GITHUB_ACCOUNT}"
            SONAR_CREDENTIAL_ID = "${map.SONAR_CREDENTIAL_ID}"

            // k8s部署配置文件
            deploy_yaml_file = "${project_name}/deploy/${env.BRANCH_NAME}/${project_name}.yaml"
        }

        stages {
            stage('Prepare') {
                steps {
                    echo "1. Prepare Stage"
                    script {
                        // 检出应用代码
                        dir("${app_workspace}") {
                            echo "Checkout App Code"
                            checkout([
                                $class: 'GitSCM',
                                branches: [[name: '*/master']],     // 只用到master分支
                                doGenerateSubmoduleConfigurations: false,
                                extensions: [[$class: 'CleanBeforeCheckout'], [$class: 'ScmName', name: 'config-scm']],
                                submoduleCfg: [],
                                userRemoteConfigs: [[credentialsId: 'jenkins2gitlab-ssh', url: "${app_git_url}"]]
                            ])

                            build_tag = sh(returnStdout: true, script: 'git rev-parse --short HEAD').trim()
                            if (env.BRANCH_NAME == 'master') {
                                image_name = "${registryUrl}/${registry_name}/${project_name}-prod:${build_tag}"
                                config_git_url = "${config_prd_url}"
                            } else if (env.BRANCH_NAME == 'develop') {
                                image_name = "${registryUrl}/${registry_name}/${project_name}-dev:${build_tag}"
                                config_git_url = "${config_dev_url}"
                                config_workspace = "/home/jenkins/agent/workspace/${project_name}/config_test/${env.BRANCH_NAME}"
                                config_file = "${config_workspace}/cas-server-webapp/src/main/webapp/WEB-INF/deployerConfigContext.xml"
                            }
                         }

                        // 检出应用配置文件代码
                        dir("${config_workspace}") {
                            echo "1.3 Checkout App Configuration Code"
                            checkout([
                                $class: 'GitSCM',
                                branches: [[name: '*/master']],      // 只用到master分支
                                doGenerateSubmoduleConfigurations: false,
                                extensions: [[$class: 'CleanBeforeCheckout'], [$class: 'ScmName', name: 'config-scm']],
                                submoduleCfg: [],
                                userRemoteConfigs: [[credentialsId: 'jenkins2gitlab-ssh', url: "${config_git_url}"]]
                            ])
                        }
                    }
                }
            }

            stage('Summary') {
                steps {
                    echo "2. Print Summary Stage"
                    echo "Workspace: ${WORKSPACE}  App Workspace: ${app_workspace}  Build_id: ${BUILD_ID}  Branch_name: ${env.BRANCH_NAME}  Commit_id: ${build_tag}  "
                    echo "registryUrl: ${registryUrl}"
                    echo "Project Name: ${project_name}"
                    echo "Docker Image Name: ${image_name}"
                    echo "Dockerfile Path: ${dockerfile_path}"
                }
            }

            stage('Sonarqube Analysis') {
                steps {
                    echo "Code Analysis"
                    dir("${app_workspace}") {
                        container ('maven') {
                            withCredentials([string(credentialsId: "$SONAR_CREDENTIAL_ID", variable: 'SONAR_TOKEN')]) {
                                withSonarQubeEnv('sonar') {
                                 sh "sonar-scanner \
                                  -Dsonar.projectKey=${project_name} \
                                  -Dsonar.sources=. \
                                  -Dsonar.host.url=http://10.0.3.60:31073 \
                                  -Dsonar.login=${SONAR_TOKEN}"
                                }
                          }
                          timeout(time: 1, unit: 'HOURS') {
                            waitForQualityGate abortPipeline: true
                          }
                        }
                    }
                }
            }

            stage('Maven Build') {
                steps {
                    echo "3. Maven Build Stage"
                    script {
                        dir("${app_workspace}") {
                            container ('maven') {
                                if (env.BRANCH_NAME == 'master') {
                                    echo "Copy Production Config File: ${config_file} to ${base_config_file}"
                                    sh "cp ${config_file} ${base_config_file}"
                                    sh "mvn clean install -Dmaven.test.skip=true -P nocheck"
                                } else if (env.BRANCH_NAME == 'develop') {
                                    echo "Copy Develop Config File: ${config_file} to ${base_config_file}"
                                    sh "cp ${config_file} ${base_config_file}"
                                    sh "mvn clean install -Dmaven.test.skip=true -P nocheck"
                                }
                            }
                        }
                    }
                }
            }

            stage('Build Image') {
                steps {
                    dir("${app_workspace}") {
                        echo "4. Build Docker Image Stage"
                        echo "Copy ${dockerfile_path}/Dockerfile to ${app_workspace}"
                        script {
                            container ('maven') {
                                sh "cp ${dockerfile_path}/Dockerfile ./"
                                customImage = docker.build("${image_name}","./",)
                            }
                        }
                    }
                }
            }

            stage('Push Image') {
                steps {
                    echo '5. Push Docker Image Stage'
                    script {
                        container ('maven') {
                            withCredentials([usernamePassword(passwordVariable : 'DOCKER_PASSWORD' ,usernameVariable : 'DOCKER_USERNAME' ,credentialsId : "$DOCKER_CREDENTIAL_ID" ,)]) {
                                sh 'echo "$DOCKER_PASSWORD" | docker login $REGISTRY -u "$DOCKER_USERNAME" --password-stdin'
                                customImage.push()
                            }
                        }
                    }
                }
            }

            stage('Deploy To k8s') {
                steps {
                    echo "6. Deploy ${project_name} to kubernetes"
                    script {
                        pod_create_time = podCreateTime()
                        container ('maven') {
                            withCredentials([usernamePassword(passwordVariable : 'DOCKER_PASSWORD' ,usernameVariable : 'DOCKER_USERNAME' ,credentialsId : "$DOCKER_CREDENTIAL_ID" ,)]) {
                                dir("${WORKSPACE}") {
                                    sh "sed -i 's/<BUILD_TAG>/${build_tag}/' ${deploy_yaml_file}"
                                    sh "sed -i 's/<BRANCH_NAME>/${env.BRANCH_NAME}/' ${deploy_yaml_file}"
                                    sh "sed -i 's/<CREATE_TIME>/${pod_create_time}/' ${deploy_yaml_file}"
                                    kubernetesDeploy(
                                        configs: "${deploy_yaml_file}",
                                        enableConfigSubstitution: true,
                                        kubeconfigId: "$KUBECONFIG_CREDENTIAL_ID"
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        post {
            success {
                script {
                    def full_job_name = env.JOB_NAME.tokenize('/') as String[]
                    def job_name = full_job_name[1] + "/" + full_job_name[2]
                    def alert = new org.devops.wechat()

                    dir("${app_workspace}") {
                        def config = [
                            subject: "【${job_name}】发布信息 - 成功",
                            touser: "${touser}",
                            start_time: "${start_time}"
                        ]
                        alert.SendMessage(config)
                    }
                }
            }

            failure {
                script {
                    def full_job_name = env.JOB_NAME.tokenize('/') as String[]
                    def job_name = full_job_name[1] + "/" + full_job_name[2]
                    def alert = new org.devops.wechat()

                    dir("${app_workspace}") {
                        def config = [
                            subject: "【${job_name}】发布信息 - 失败",
                            touser: "${touser}",
                            start_time: "${start_time}"
                        ]
                        alert.SendMessage(config)
                    }
                }
            }
        }
    }
}