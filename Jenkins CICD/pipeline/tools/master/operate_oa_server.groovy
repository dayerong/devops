def createTime() {
    return new Date().format('yyyy-MM-dd HH:mm:ss')
}

pipeline {
    agent any
    parameters{
        extendedChoice(
            name: "target_host",
            type: "PT_CHECKBOX",
            multiSelectDelimiter: ",",
            value: """
            172.16.10.14,
            172.16.10.15,
            172.16.10.44,
            172.16.10.45
            """,
            defaultValue: "",
            description: "选择【OA生产环境】的主机IP"
        )

        booleanParam(
            name: 'confirm',
            defaultValue: false,
            description: '''\
是否确认重启所选服务器的OA应用程序？
注：默认重启应用前会从负载均衡配置中移除，重启完成后再加入
            '''
        )
    }

    environment {
        deploy_hosts = "${params.target_host}"
        ansible_server = "172.16.10.11"
        config_file = "/etc/nginx/conf.d/oa.test.com.conf"
        nginx_ip = "172.16.10.5"
        start_time = createTime()
    }


    stages {
        stage('修改Nginx配置') {
            steps {
                echo "主机IP: ${deploy_hosts}  Nginx配置文件: ${config_file}"
                script {
                    if (params.confirm.toBoolean() == false) {
                        currentBuild.result = 'ABORTED'
                        error('未确认,任务取消!')
                    }

                    sshagent(credentials : ['ee87cc61-6780-4ee7-ac85-c69f0f523a15']) {
                        sh "ssh -o StrictHostKeyChecking=no root@${ansible_server} \"ansible ${nginx_ip} -m shell -a \'sed -i \"/${deploy_hosts}/s/^/#/\" ${config_file}\'\""
                        sh "ssh -o StrictHostKeyChecking=no root@${ansible_server} \"ansible ${nginx_ip} -m shell -a \'grep ${deploy_hosts} ${config_file}\'\""
                        exitValue = sh(returnStatus: true, script: "ssh -o StrictHostKeyChecking=no root@${ansible_server} \"ansible ${nginx_ip} -m shell -a \'nginx -t\'\"")
                        if (exitValue != 0) {
                            currentBuild.result = 'ABORTED'
                            error('nginx -t 测试失败!')
                        }
                    }
                }
                ansiColor("xterm") {
                    echo "\033[42m nginx -t 测试成功! \033[0m"
                }
            }
        }

        stage('重新加载Nginx') {
            steps {
                script {
                    sshagent(credentials : ['ee87cc61-6780-4ee7-ac85-c69f0f523a15']) {
                        exitValue = sh(returnStatus: true, script: "ssh -o StrictHostKeyChecking=no root@${ansible_server} \"ansible ${nginx_ip} -m shell -a \'nginx -s reload\'\"")
                        sh "ssh -o StrictHostKeyChecking=no root@${ansible_server} \"ansible ${nginx_ip} -m shell -a \'ps -ef | grep nginx\'\""
                        if (exitValue != 0) {
                            currentBuild.result = 'ABORTED'
                            error('Nginx reload失败!')
                        }
                    }
                }
                ansiColor("xterm") {
                    echo "\033[42m Nginx reload成功! \033[0m"
                }
            }
        }

        stage('重启ecology') {
            steps {
                script {
                    sshagent(credentials : ['ee87cc61-6780-4ee7-ac85-c69f0f523a15']) {
                        sh(returnStatus: true, script: "ssh -o StrictHostKeyChecking=no root@${ansible_server} \"ansible ${deploy_hosts} -m shell -a \'nohup sh /root/resin.sh stop\'\"")
                        sleep(time: 3, unit: "SECONDS")
                        exitValue = sh(returnStatus: true, script: "ssh -o StrictHostKeyChecking=no root@${ansible_server} \"ansible ${deploy_hosts} -m shell -a \'nohup sh /root/resin.sh start\'\"")
                        if (exitValue != 0) {
                            currentBuild.result = 'ABORTED'
                            error('重启ecology失败!')
                        }
                    }

                    count=1
                    while(true) {
                        def statusCode = sh(returnStatus: true, script: "curl -sIL -w '%{http_code}' -o /dev/null http://${deploy_hosts}")
                        if (statusCode != 0) {
                            ansiColor("xterm") {
                                echo "\033[43m [${count}]正在检测主机${deploy_hosts}的ecology应用启动... \033[0m"
                            }
                        } else {
                            def int responseCode = sh(returnStdout: true, script: "curl -sIL -w '%{http_code}' -o /dev/null http://${deploy_hosts}").trim()
                            if (responseCode==200) {
                                break
                            }
                        }
                        sleep(time: 3, unit: "SECONDS")
                        count++
                    }
                }
                ansiColor("xterm") {
                    echo "\033[42m ecology启动完成,${deploy_hosts}开始重新加入Nginx配置 \033[0m"
                }
            }
        }

        stage('恢复Nginx配置') {
            steps {
                script {
                    sshagent(credentials : ['ee87cc61-6780-4ee7-ac85-c69f0f523a15']) {
                        sh "ssh -o StrictHostKeyChecking=no root@${ansible_server} \"ansible ${nginx_ip} -m shell -a \'sed -i \"/${deploy_hosts}/s/^#//\" ${config_file}\'\""
                        sh "ssh -o StrictHostKeyChecking=no root@${ansible_server} \"ansible ${nginx_ip} -m shell -a \'grep ${deploy_hosts} ${config_file}\'\""
                        exitValue = sh(returnStatus: true, script: "ssh -o StrictHostKeyChecking=no root@${ansible_server} \"ansible ${nginx_ip} -m shell -a \'nginx -t\'\"")
                        if (exitValue != 0) {
                            currentBuild.result = 'ABORTED'
                            error('恢复Nginx配置,nginx -t 测试失败!')
                        }
                    }
                    ansiColor("xterm") {
                        echo "\033[42m 恢复Nginx配置成功! \033[0m"
                    }
                }
            }
        }

        stage('再次加载Nginx') {
            steps {
                script {
                    sshagent(credentials : ['ee87cc61-6780-4ee7-ac85-c69f0f523a15']) {
                        exitValue = sh(returnStatus: true, script: "ssh -o StrictHostKeyChecking=no root@${ansible_server} \"ansible ${nginx_ip} -m shell -a \'nginx -s reload\'\"")
                        sh "ssh -o StrictHostKeyChecking=no root@${ansible_server} \"ansible ${nginx_ip} -m shell -a \'ps -ef | grep nginx\'\""
                        if (exitValue != 0) {
                            currentBuild.result = 'ABORTED'
                            error('Nginx reload失败!')
                        }
                    }
                }
                ansiColor("xterm") {
                    echo "\033[42m Nginx reload成功! \033[0m"
                }
            }
        }
    }

    post {
        success {
            script {
                dir("${WORKSPACE}"){
                    sh '''
                        #!/bin/bash
                        BUILDER_USER=$(python /var/jenkins_home/tools/wechat.py -user "${BUILD_DISPLAY_NAME:1}" "${JOB_NAME}")
                        SUBJECT="【"${deploy_hosts}"】的ecology服务重启成功"
                        TOUSER="1234"
                        END_TIME=$(date "+%Y-%m-%d %H:%M:%S")
                        CONTENT=">**任务详情**\n  \
发布人员：<font color=\"info\">${BUILDER_USER}</font>\n \
任务   ID：<font color=\"info\">${BUILD_DISPLAY_NAME}</font>\n \
主机   IP：<font color=\"info\">${deploy_hosts}</font>\n \
开始时间：<font color=\"info\">${start_time}</font>\n \
结束时间：<font color=\"info\">${END_TIME}</font>
"

                        #微信通知
                        python /var/jenkins_home/tools/wechat_monitor.py -send "${TOUSER}" "${SUBJECT}" "${CONTENT}"
                    '''
                    deleteDir()
                }
            }
        }

        failure {
            script {
                dir("${WORKSPACE}"){
                    sh '''
                        #!/bin/bash
                        BUILDER_USER=$(python /var/jenkins_home/tools/wechat.py -user "${BUILD_DISPLAY_NAME:1}" "${JOB_NAME}")
                        SUBJECT="【"${deploy_hosts}"】的ecology服务重启失败"
                        TOUSER="1234"
                        END_TIME=$(date "+%Y-%m-%d %H:%M:%S")
                        CONTENT=">**任务详情**\n  \
发布人员：<font color=\"info\">${BUILDER_USER}</font>\n \
任务   ID：<font color=\"info\">${BUILD_DISPLAY_NAME}</font>\n \
主机   IP：<font color=\"info\">${deploy_hosts}</font>\n \
开始时间：<font color=\"info\">${start_time}</font>\n \
结束时间：<font color=\"info\">${END_TIME}</font>
"

                        #微信通知
                        python /var/jenkins_home/tools/wechat_monitor.py -send "${TOUSER}" "${SUBJECT}" "${CONTENT}"
                    '''
                    deleteDir()
                }
            }
        }
    }
}