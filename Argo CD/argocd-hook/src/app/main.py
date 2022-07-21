#!/usr/bin/env python
# -*-coding:utf-8 -*-

from fastapi import FastAPI
from model.post import NotificationsItems
from config.conf import api_auth
from fastapi.encoders import jsonable_encoder
import uvicorn

app = FastAPI()


# 发送构建状态消息
@app.post("/webhook/send")
def argocd_send(access_token: str,
                items: NotificationsItems
                ):
    _token = api_auth['access_token']
    if _token != access_token:
        raise Exception(f'非法的access_token: {access_token}')
    json_data = jsonable_encoder(items)
    print(json_data)
    return json_data


if __name__ == '__main__':
    log_config = uvicorn.config.LOGGING_CONFIG
    log_config["formatters"]["access"]["fmt"] = "%(asctime)s - %(levelname)s - %(message)s"
    log_config["formatters"]["default"]["fmt"] = "%(asctime)s - %(levelname)s - %(message)s"
    uvicorn.run(app='main:app',
                host="0.0.0.0",
                port=8000,
                reload=True,
                log_config=log_config,
                proxy_headers=True)
