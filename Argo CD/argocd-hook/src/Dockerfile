# pull official base image
FROM hub.test.com/infra-app/python:3.8.5-slim-buster

# set work directory
WORKDIR /argocd-hook

#TZ
ENV TZ Asia/Shanghai

# set environment variables
ENV PYTHONDONTWRITEBYTECODE 1
ENV PYTHONUNBUFFERED 1

# copy requirements file
COPY ./src/requirements.txt /argocd-hook/requirements.txt

# install dependencies
RUN set -eux \
    && pip install -r /infra-tools/requirements.txt -i https://mirrors.aliyun.com/pypi/simple/\
    && rm -rf /root/.cache/pip

# copy project
COPY ./src /argocd-hook

# RUN APP
CMD ["python", "./app/main.py"]