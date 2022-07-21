@Library('jenkins-library') _

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
            172.16.10.45,
            all
            """,
            defaultValue: "all",
            description: "选择部署【生产环境】的主机IP"
        )

        file(
            name:'file',
            description: '注：选择要上传的文件'
        )

        string(
            name: 'dest_dir',
            defaultValue: '',
            description: '注：输入要上传的服务器目录'
        )
    }

    environment {
        deploy_hosts = "${params.target_host}"
        desc_dir = "${params.dest_dir}"
        ansible_server = "172.16.10.11"
    }


    stages {
        stage('Upload') {
            steps {
                script {
                    file_in_workspace = unstashParam "file"
                    src_file = "${WORKSPACE}/${file_in_workspace}"
                    echo "Remote Server: ${deploy_hosts}  File Name: ${src_file}  Remote Directory: ${desc_dir}"
                    sshagent(credentials : ['ee87cc61-6780-4ee7-ac85-c69f0f523a15']) {
                        if ("${deploy_hosts}" == "all") {
                            sh "ssh -o StrictHostKeyChecking=no root@${ansible_server} \"ansible ecology -m copy -a 'src=${src_file} dest=${desc_dir} force=yes backup=yes'\""
                        } else {
                            sh "ssh -o StrictHostKeyChecking=no root@${ansible_server} \"ansible ${deploy_hosts} -m copy -a 'src=${src_file} dest=${desc_dir} force=yes backup=yes'\""
                        }
                    }
                }
            }
        }

        stage('Verify') {
            steps {
                script {
                    desc_file = "${desc_dir}/${file_in_workspace}"
                    if ("${deploy_hosts}" == "all") {
                        List all_hosts = ['172.16.10.14', '172.16.10.15', '172.16.10.44', '172.16.10.45']
                        for (i in all_hosts) {
                            sshagent(credentials : ['ee87cc61-6780-4ee7-ac85-c69f0f523a15']) {
                                echo "Remote Server：${i}"
                                sh "ssh -o StrictHostKeyChecking=no root@${ansible_server} \"ansible ${i} -m shell -a 'ls -ltr ${desc_file}'\""
                            }
                        }
                    } else {
                        sshagent(credentials : ['ee87cc61-6780-4ee7-ac85-c69f0f523a15']) {
                            sh "ssh -o StrictHostKeyChecking=no root@${ansible_server} \"ansible ${deploy_hosts} -m shell -a 'ls -ltr ${desc_file}'\""
                        }
                    }
                }
            }
        }

        stage('Clean Up') {
            steps {
                echo "Workspace: ${WORKSPACE}"
                deleteDir()
	         }
        }
    }
}