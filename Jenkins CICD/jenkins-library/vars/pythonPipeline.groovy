#!groovy

def createTime() {
    return new Date().format('yyyy-MM-dd HH:mm:ss', TimeZone.getTimeZone('Asia/Shanghai'))
    // return new Date().format('yyyy-MM-dd HH:mm:ss')
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

            // 代码本地目录
            app_workspace = "/home/jenkins/agent/workspace/${project_name}/code/${env.BRANCH_NAME}"
            dockerfile_path = "${WORKSPACE}/${project_name}/Dockerfile"

            // Kubesphere变量
            DOCKER_CREDENTIAL_ID = "${map.DOCKER_CREDENTIAL_ID}"
            KUBECONFIG_CREDENTIAL_ID = "${map.KUBECONFIG_CREDENTIAL_ID}"
            REGISTRY =  "${map.REGISTRY}"
            DOCKERHUB_NAMESPACE = "${map.DOCKERHUB_NAMESPACE}"
            GITHUB_ACCOUNT = "${map.GITHUB_ACCOUNT}"
            SONAR_CREDENTIAL_ID = "${map.SONAR_CREDENTIAL_ID}"
            SONAR_SERVER_IP = "${map.SONAR_SERVER_IP}"

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
                                branches: [[name: "${branch_name}"]],     // 只用到master分支
                                doGenerateSubmoduleConfigurations: false,
                                extensions: [[$class: 'CleanBeforeCheckout'], [$class: 'ScmName', name: 'config-scm']],
                                submoduleCfg: [],
                                userRemoteConfigs: [[credentialsId: 'jenkins2gitlab-ssh', url: "${app_git_url}"]]
                            ])

                            build_tag = sh(returnStdout: true, script: 'git rev-parse --short HEAD').trim()
                            if (env.BRANCH_NAME == 'master') {
                                image_name = "${registryUrl}/${registry_name}/${project_name}-prod:${build_tag}"
                            } else if (env.BRANCH_NAME == 'develop') {
                                image_name = "${registryUrl}/${registry_name}/${project_name}-dev:${build_tag}"
                            }
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

            // stage('Sonarqube Analysis') {
            //     steps {
            //         dir("${app_workspace}") {
            //             echo "3. Code Analysis"
            //             container ('maven') {
            //                 withCredentials([string(credentialsId: "$SONAR_CREDENTIAL_ID", variable: 'SONAR_TOKEN')]) {
            //                     withSonarQubeEnv('sonar') {
            //                      sh "sonar-scanner \
            //                       -Dsonar.projectKey=${project_name} \
            //                       -Dsonar.sources=. \
            //                       -Dsonar.host.url=${SONAR_SERVER_IP} \
            //                       -Dsonar.login=${SONAR_TOKEN}"
            //                     }
            //                 }
            //                 timeout(time: 1, unit: 'HOURS') {
            //                     waitForQualityGate abortPipeline: true
            //                 }
            //             }
            //         }
            //     }
            // }

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

            stage('Deploy To K8S') {
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

            stage('Check Deployments') {
                steps {
                    echo "7.Check Deployments Status"
                    script {
                        container ('maven') {
                            withCredentials([file(credentialsId: 'kubeconfig-k8s', variable: 'KUBECONFIG')]) {
                                sh "mkdir -p ~/.kube && cp ${KUBECONFIG} ~/.kube/config"
                                sh """
                                    if ! kubectl rollout status deployment ${project_name} -n infra --watch --timeout=5m; then
                                        echo "发布失败，开始回滚！"
                                        kubectl rollout undo deployment ${project_name} -n infra
                                        // kubectl rollout status deployment ${project_name} -n infra
                                        exit 1
                                    fi
                                    """
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
                    withCredentials([usernamePassword(passwordVariable : 'JENKINS_PASSWORD' ,usernameVariable : 'JENKINS_USER' ,credentialsId : "jenkins-id" ,)]) {
                        def full_job_name = env.JOB_NAME.tokenize('/') as String[]
                        def job_name = full_job_name[1] + "/" + full_job_name[2]
                        def alert = new org.devops.wechat()

                        // 生成错误日志
                        sh '''
                            #!/bin/bash
                            jenkins_url="http://ks-jenkins.kubesphere-devops-system.svc.cluster.local"
                            build_path=$(echo ${BUILD_URL} | grep / | cut -d/ -f4-)
                            build_url=${jenkins_url}/${build_path}
                            mkdir -p /tmp/build_html/${build_path}
                            curl -u ${JENKINS_USER}:${JENKINS_PASSWORD} ${build_url}consoleText -o /tmp/build_html/${build_path}consoleText -s
                        '''
                        dir("${app_workspace}") {
                            def config = [
                                subject: "【${job_name}】发布信息 - 失败",
                                touser: "${touser}",
                                start_time: "${start_time}"
                            ]
                            alert.SendFailMessage(config)
                        }
                    }
                }
            }
        }
    }
}