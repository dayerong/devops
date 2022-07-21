# pull official base image
FROM hub.test.com/infra-app/python:3.8.5-slim-buster

# set work directory
WORKDIR /infra-job

#TZ
ENV TZ Asia/Shanghai

# set environment variables
ENV PYTHONDONTWRITEBYTECODE 1
ENV PYTHONUNBUFFERED 1

# copy requirements file
COPY ./src/requirements.txt /infra-job/requirements.txt

# install dependencies
RUN set -eux \
    && pip install -r /infra-job/requirements.txt -i https://mirrors.aliyun.com/pypi/simple/\
    && rm -rf /root/.cache/pip

# copy project
COPY ./src /infra-job

# RUN APP
CMD ["python", "./app/main.py"]