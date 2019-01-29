/*
 * Copyright © 2017 Logistimo.
 *
 * This file is part of Logistimo.
 *
 * Logistimo software is a mobile & web platform for supply chain management and remote temperature monitoring in
 * low-resource settings, made available under the terms of the GNU Affero General Public License (AGPL).
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Affero General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this program.  If not, see
 * <http://www.gnu.org/licenses/>.
 *
 * You can be released from the requirements of the license by purchasing a commercial license. To know more about
 * the commercial license, please contact us at opensource@logistimo.com
 */

package com.logistimo.services.taskqueue.util;

import com.logistimo.entity.ITask;
import com.logistimo.entity.Task;
import com.logistimo.logger.XLog;
import com.logistimo.services.taskqueue.TaskOptions;

import redis.clients.util.MurmurHash;

/**
 * Created by charan on 15/09/16.
 */
public class LogTaskLogger implements ITaskLogger {

  private static final XLog _logger = XLog.getLog(LogTaskLogger.class);

  @Override
  public ITask createTask(String queue, String url, long scheduledTime, long domainId,
                          String userName, String taskName) {
    Task taskEntity = new Task();
    taskEntity.setTaskId(MurmurHash
        .hash64A((queue + url + scheduledTime + domainId + userName + taskName).getBytes(), -1));
    taskEntity.setName(taskName);
    _logger.fine("Task queued: " + taskEntity.getTaskId());
    return taskEntity;
  }

  @Override
  public void moveToInProgress(Long taskId) {
    _logger.fine("Starting task: " + taskId);
  }

  @Override
  public void complete(Long taskId, long duration) {
    _logger.fine("Completed task: " + taskId);
  }

  @Override
  public void fail(TaskOptions taskOptions, long duration, String reason) {
    _logger.info("Task failed: " + taskOptions.getTaskId());
  }
}
