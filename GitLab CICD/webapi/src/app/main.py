#!/usr/bin/env python
# -*-coding:utf-8 -*-

from fastapi import FastAPI
import uvicorn
import time

app = FastAPI()

c_time = time.strftime("%Y-%m-%d %H:%M:%S")


@app.get("/")
def read_root():
    response = {"code": 200,
                "msg": "sucess",
                "data": {"version": "gitlab-cicd-test-v1",
                         "create_time": c_time,
                         "current_time": time.strftime("%Y-%m-%d %H:%M:%S"),
                         }
                }

    return response


@app.get("/healthz")
def read_root():
    response = {"status": "up"}

    return response


@app.get("/healthz/readiness")
def read_readiness():
    response = "I\'m ready!"

    return response


@app.get("/healthz/liveness")
def read_liveness():
    response = "I\'m alive!"

    return response


if __name__ == '__main__':
    log_config = uvicorn.config.LOGGING_CONFIG
    log_config["formatters"]["access"]["fmt"] = "%(asctime)s - %(levelname)s - %(message)s"
    log_config["formatters"]["default"]["fmt"] = "%(asctime)s - %(levelname)s - %(message)s"
    time.sleep(30)
    uvicorn.run(app='main:app',
                host="0.0.0.0",
                port=8000,
                log_config=log_config,
                proxy_headers=True)
