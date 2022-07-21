#!groovy

def createTime() {
    return new Date().format('yyyy-MM-dd HH:mm:ss', TimeZone.getTimeZone('Asia/Shanghai'))
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

            // pipeline仓库地址
            pipeline_git = "${map.pipeline_git_url}"

            // 代码本地目录
            app_workspace = "${WORKSPACE}/${project_name}/code/${env.BRANCH_NAME}"
            dockerfile_path = "${WORKSPACE}/CI/${project_name}"

            // argocd路径
            argo_cd_path = "${WORKSPACE}/CD/${project_name}"
        }

        // trigger配置
        triggers {
            // 由 Generic Webhook Trigger 提供的触发器
            GenericTrigger(
                // 打印通过 genericVariables 配置的变量
                // printContributedVariables: true,

                // 打印 request body 内容
                // printPostContent: true,

                // 项目运行消息, 会显示在 Jenkins Blue 中的项目活动列表中
                causeString: 'Triggered by $WEBHOOK_REF',

                genericVariables: [
                    [key: 'WEBHOOK_REF', value: '$.ref'],
                    // [key: 'WEBHOOK_USER_NAME', value: '$.user_name'],
                    // [key: 'WEBHOOK_RECENT_COMMIT_ID', value: '$.commits[-1].id'],
                    // [key: 'WEBHOOK_RECENT_COMMIT_MESSAGE', value: '$.commits[-1].message'],
                ],

                // http://jenkins_url/generic-webhook-trigger/invoke?token=VXnNT5X/GH8Rs
                token: 'Yth6taahsf12=ijYU',

                // 避免使用已触发工作的信息作出响应
                silentResponse: false,

                // 可选的正则表达式过滤, 比如希望仅在 develop 分支上触发, 你可以进行如下配置
                regexpFilterText: '$WEBHOOK_REF',
                regexpFilterExpression: 'refs/heads/' + BRANCH_NAME
                // regexpFilterExpression: 'refs/heads/develop'
            )
        }

        stages {
            stage('Prepare') {
                steps {
                    echo "1. Prepare Stage"
                    script {
                        echo "App Workspace Path: ${app_workspace}"
                        dir("${app_workspace}"){
                            echo "Checkout App Code"
                            checkout([
                                $class: 'GitSCM',
                                branches: [[name: "${branch_name}"]],
                                doGenerateSubmoduleConfigurations: false,
                                extensions: [[$class: 'CleanBeforeCheckout'], [$class: 'ScmName', name: 'config-scm']],
                                submoduleCfg: [],
                                userRemoteConfigs: [[credentialsId: 'jenkins2gitlab', url: "${app_git_url}"]]
                            ])

                            build_tag = sh(returnStdout: true, script: 'git rev-parse --short HEAD').trim()

                            if (env.BRANCH_NAME == 'main') {
                                image_name = "${registryUrl}/${registry_name}/${project_name}-prod:${build_tag}"
                                argocd_overlays = "prod"
                                argocd_application = "webapi-prod"
                            } else if (env.BRANCH_NAME == 'develop'){
                                image_name = "${registryUrl}/${registry_name}/${project_name}-dev:${build_tag}"
                                argocd_overlays = "dev"
                                argocd_application = "webapi-dev"
                            }
                        }
                    }
                }
            }

            stage('Summary') {
                steps {
                    echo "2. Print Summary Stage"
                    echo "Workspace: ${WORKSPACE}  App Workspace: ${app_workspace}  Build_id: ${BUILD_ID}  Branch_name: ${env.BRANCH_NAME}  Commit_id: ${build_tag}  "
                    echo "RegistryUrl: ${registryUrl}"
                    echo "Project Name: ${project_name}"
                    echo "Docker Image Name: ${image_name}"
                    echo "Dockerfile Path: ${dockerfile_path}"
                }
            }

            stage('Build Image') {
                steps {
                    dir("${app_workspace}") {
                        echo "3. Build Docker Image Stage"
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
                    echo '4. Push Docker Image Stage'
                    script {
                        container ('maven') {
                            withCredentials([usernamePassword(passwordVariable : 'DOCKER_PASSWORD' ,usernameVariable : 'DOCKER_USERNAME' ,credentialsId : "harbor-id" ,)]) {
                                sh 'echo "$DOCKER_PASSWORD" | docker login $registryUrl -u "$DOCKER_USERNAME" --password-stdin'
                                customImage.push()
                            }
                        }
                    }
                }
            }

            stage('Update Yaml') {
                steps {
                    echo "5. Edit Yaml and Update to Gitlab"
                    script {
                        pod_create_time = podCreateTime()
                        container ('maven') {
                            withCredentials([sshUserPrivateKey(credentialsId: 'jenkins2gitlab', keyFileVariable: 'SSH_KEY')]) {
                                dir("${WORKSPACE}") {
                                    withEnv(["GIT_SSH_COMMAND=ssh -i ${SSH_KEY} -o StrictHostKeyChecking=no"]) {
                                        sh """
                                            git remote set-url origin ${pipeline_git}
                                            git config --global user.email "jenkins@example.com"
                                            git config --global user.name "Jenkins"
                                            cd ${argo_cd_path}/overlays/${argocd_overlays}
                                            cat <<EOF > deployment_patch.yaml
- op: replace
  path: /metadata/annotations/kubernetes.io~1change-cause
  value: image updated to ${build_tag}
- op: replace
  path: /metadata/labels/create_time
  value: "${pod_create_time}"
- op: replace
  path: /spec/template/metadata/labels/create_time
  value: "${pod_create_time}"
- op: add
  path: /spec/template/spec/containers/0/env/-
  value:
    name: branch
    value: ${branch_name}
EOF
                                            kustomize edit set image ${project_name}=${image_name}
                                            git commit -am "ArgoCD应用名称:${project_name} 提交ID:${build_tag}"
                                            git push origin HEAD:${branch_name}
                                            kustomize build .
                                        """
                                    }
                                }
                            }
                        }
                    }
                }
            }

            stage('Check App Status') {
                steps {
                    echo "6. Check ArgoCD Deploye Result"
                    script {
                        container ('maven') {
                            withCredentials([string(credentialsId: 'argocd-token', variable: 'ARGO_TOKEN')]) {
                                sh "argocd app wait ${argocd_application} --timeout 60 --auth-token $ARGO_TOKEN --server argocd-server.argocd.svc.cluster.local --insecure"
                            }
                        }
                    }
                }
            }
        }

        post {
            success {
                script {
                    def job_name = env.JOB_NAME
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
                    def job_name = env.JOB_NAME
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