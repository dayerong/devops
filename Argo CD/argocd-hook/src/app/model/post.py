#!/usr/bin/env python
# -*-coding:utf-8 -*-

from pydantic import BaseModel


class NotificationsItems(BaseModel):
    app_name: str
    app_status: str
    app_health: str
    start_time: str
    url: str
