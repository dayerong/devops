# pull official base image
FROM hub.test.com/infra-app/python:3.8.5-slim-buster

# set work directory
WORKDIR /wecom

#TZ
ENV TZ Asia/Shanghai

# set environment variables
ENV PYTHONDONTWRITEBYTECODE 1
ENV PYTHONUNBUFFERED 1

# install ping
RUN apt-get update \
    && apt-get -y install iputils-ping

# copy requirements file
COPY ./src/requirements.txt /wecom/requirements.txt

# install dependencies
RUN set -eux \
    && pip install --no-cache-dir -r /wecom/requirements.txt -i https://mirrors.aliyun.com/pypi/simple/\
    && rm -rf /root/.cache/pip

# copy project
COPY ./src /wecom

# start app
CMD ["python", "./app/main.py"]