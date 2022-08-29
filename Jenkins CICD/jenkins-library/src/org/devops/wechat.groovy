#!/usr/bin/groovy
package org.devops;

def SendMessage(Map config = [:]) {
    def wechat = libraryResource 'org/devops/wechat.py'
    writeFile file: '/tmp/wechat.py', text: wechat

    withEnv([
        'subject=' + config.subject,
        'touser=' + config.touser,
        'start_time=' + config.start_time
    ]) {
        sh '''
            #!/bin/bash
            GIT_COMMITTER_NAME=$(git log --name-only -n 1 |awk '/Author/{print $2}')
            GIT_COMMITTER_EMAIL=$(git log --name-only -n 1 |awk -F "<" '/Author/{print $2}' |awk -F ">" '{print $1}')
            COMMITTER_ID=$(git log --pretty=format:%h -n 1)
            COMMITTER_DATE=$(date -d "`git log --name-only -n 1 |awk '/Date:/{print $2,$3,$4,$5,$6}'`" +'%Y-%m-%d %H:%M:%S')
            COMMITTER_FILE=$(git diff --name-only HEAD~ HEAD)
            COMMITTER_COMMENT=$(git log -1 --pretty=%B)
            END_TIME=$(date "+%Y-%m-%d %H:%M:%S")
            BUILDER_USER=$(python /tmp/wechat.py -user "${BUILD_NUMBER}" "${JOB_NAME}")
            CONTENT=">**发布详情**\n \
    发布人员：<font color=\"info\">${BUILDER_USER}</font>\n \
    构建   ID：<font color=\"info\">${BUILD_DISPLAY_NAME}</font>\n \
    开始时间：<font color=\"info\">${start_time}</font>\n \
    结束时间：<font color=\"info\">${END_TIME}</font>\n \
    邮      箱：<font color=\"info\">${GIT_COMMITTER_EMAIL}</font>\n \
    提交描述：<font color=\"info\">${COMMITTER_COMMENT}</font>\n \
    提交   ID：<font color=\"info\">${COMMITTER_ID}</font>\n \
    提交时间：<font color=\"info\">${COMMITTER_DATE}</font>\n \
    修改文件：\n \
    <font color=\"info\">${COMMITTER_FILE}</font>
    "

            #微信通知
            python /tmp/wechat.py -send "${touser}" "${subject}" "${CONTENT}"
        '''
    }
}

def SendFailMessage(Map config = [:]) {
    def wechat = libraryResource 'org/devops/wechat.py'
    writeFile file: '/tmp/wechat.py', text: wechat

    withEnv([
        'subject=' + config.subject,
        'touser=' + config.touser,
        'start_time=' + config.start_time
    ]) {
        sh '''
            #!/bin/bash
            if [ "${BRANCH_NAME}" = "master" ];then
                url="http://10.0.1.74:30698"
            elif [ "${BRANCH_NAME}" = "develop" ];then
                url="http://10.0.3.67:32662"
            fi
            build_path=$(echo ${BUILD_URL} | grep / | cut -d/ -f4-)
            log_url=${url}/${build_path}

            GIT_COMMITTER_NAME=$(git log --name-only -n 1 |awk '/Author/{print $2}')
            GIT_COMMITTER_EMAIL=$(git log --name-only -n 1 |awk -F "<" '/Author/{print $2}' |awk -F ">" '{print $1}')
            COMMITTER_ID=$(git log --pretty=format:%h -n 1)
            COMMITTER_DATE=$(date -d "`git log --name-only -n 1 |awk '/Date:/{print $2,$3,$4,$5,$6}'`" +'%Y-%m-%d %H:%M:%S')
            COMMITTER_FILE=$(git diff --name-only HEAD~ HEAD)
            COMMITTER_COMMENT=$(git log -1 --pretty=%B)
            END_TIME=$(date "+%Y-%m-%d %H:%M:%S")
            BUILDER_USER=$(python /tmp/wechat.py -user "${BUILD_NUMBER}" "${JOB_NAME}")
            CONTENT=">**发布详情**\n \
    发布人员：<font color=\"info\">${BUILDER_USER}</font>\n \
    构建   ID：<font color=\"info\">${BUILD_DISPLAY_NAME}</font>\n \
    开始时间：<font color=\"info\">${start_time}</font>\n \
    结束时间：<font color=\"info\">${END_TIME}</font>\n \
    邮      箱：<font color=\"info\">${GIT_COMMITTER_EMAIL}</font>\n \
    提交描述：<font color=\"info\">${COMMITTER_COMMENT}</font>\n \
    提交   ID：<font color=\"info\">${COMMITTER_ID}</font>\n \
    提交时间：<font color=\"info\">${COMMITTER_DATE}</font>\n \
    错误日志：[内网点击](${log_url}consoleText) \n \
    修改文件：\n \
    <font color=\"info\">${COMMITTER_FILE}</font>
    "

            #微信通知
            python /tmp/wechat.py -send "${touser}" "${subject}" "${CONTENT}"
        '''
    }
}
