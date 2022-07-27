#!/usr/bin/python2.7
# coding:utf-8

import os
import json
import urllib3
import json
import requests
import sys

urllib3.disable_warnings()

reload(sys)
sys.setdefaultencoding('utf-8')

# 取消HTTPS网页时的错误
requests.packages.urllib3.disable_warnings()


def GetToken(Corpid, Secret):
    Url = "https://qyapi.weixin.qq.com/cgi-bin/gettoken"
    Data = {
        "corpid": Corpid,
        "corpsecret": Secret
    }
    r = requests.get(url=Url, params=Data, verify=False)
    Token = r.json()['access_token']
    return Token


def SendMessage(Token, User, Agentid, Subject, Content):
    Url = "https://qyapi.weixin.qq.com/cgi-bin/message/send?access_token=%s" % Token
    Data = {
        "touser": User,
        # "touser": '@all',
        # "totag": Tagid,
        "msgtype": "markdown",
        "agentid": Agentid,
        "markdown": {
            "content": Subject + '\n' + Content
        },
        "safe": "0"
    }
    data = json.dumps(Data, ensure_ascii=False)
    r = requests.post(url=Url, data=data.encode('utf-8'))
    return r.json()


def GetBuildUser(build_id, project_name):
    url = "http://infra-api.test.com/CICD/v1/build"
    para = {
        "build_id": build_id,
        "project_name": project_name
    }
    r = requests.get(url=url, params=para, verify=False)
    userid = r.json()['build_user']
    return userid


def GetUserInfo(Token, UserID):
    '''
        获取执行用户信息
        :param token_id:  用于认证的access_token
        :param userid:   用户账号id
    '''
    url = 'https://qyapi.weixin.qq.com/cgi-bin/user/get?'
    para = {
        'access_token': Token,
        'userid': UserID
    }
    r = requests.get(url=url, params=para, verify=False)
    info = r.json()
    return info


def arg_error(args):
    print(u"""Warnning: {0} 参数错误
Usage: python {1}.py -user Build_id Project_name      #获取企业微信用户名
       python {1}.py -send User Subject Content       #发送消息
            """.format(args, basename))


def arg_count_error():
    print(u"""Warnning: 缺少参数
Usage: python {0}.py -user Build_id Project_name      #获取企业微信用户名
       python {0}.py -send User Subject Content       #发送消息
            """.format(basename))


if __name__ == '__main__':
    Corpid = ""
    Secret = ""
    Agentid = ""
    basename = os.path.basename(sys.argv[0]).split(".")[0]
    try:
        args = sys.argv[1]
        if len(sys.argv) > 2:
            if args == "-user" and len(sys.argv) == 4:
                try:
                    build_id = sys.argv[2]
                    project_name = sys.argv[3]
                    UserID = GetBuildUser(build_id, project_name)
                    Token = GetToken(Corpid, Secret)
                    Userinfo = GetUserInfo(Token, UserID)
                    print(Userinfo['name'])
                except Exception as err:
                    print('admin')
            elif args == "-send" and len(sys.argv) == 5:
                User = sys.argv[2]
                Subject = sys.argv[3]
                Content = sys.argv[4]
                Token = GetToken(Corpid, Secret)
                Status = SendMessage(Token, User, Agentid, Subject, Content)
                print(Status)
            else:
                args = sys.argv[2:]
                arg_error(' '.join(args))
        else:
            args = sys.argv[2:]
            arg_error(' '.join(args))
    except IndexError:
        arg_count_error()
