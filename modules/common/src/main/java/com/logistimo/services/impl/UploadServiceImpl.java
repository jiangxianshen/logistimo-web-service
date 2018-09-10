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

package com.logistimo.services.impl;

import com.logistimo.dao.JDOUtils;
import com.logistimo.entity.IDownloaded;
import com.logistimo.entity.IUploaded;
import com.logistimo.logger.XLog;
import com.logistimo.services.ObjectNotFoundException;
import com.logistimo.services.ServiceException;
import com.logistimo.services.UploadService;

import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

import javax.jdo.JDOObjectNotFoundException;
import javax.jdo.PersistenceManager;
import javax.jdo.Query;

@Service
public class UploadServiceImpl implements UploadService {

  private static final XLog xLogger = XLog.getLog(UploadServiceImpl.class);

  public IUploaded getUploaded(String filename, String version, String locale)
      throws ServiceException, ObjectNotFoundException {
    String key = JDOUtils.createUploadedKey(filename, version, locale);
    return getUploaded(key);
  }

  @SuppressWarnings("unchecked")
  public IUploaded getLatestUpload(String filename, String locale) throws ServiceException {
    if (filename == null || filename.isEmpty() || locale == null || locale.isEmpty()) {
      throw new ServiceException("Invalid arguments - filename or locale not specified");
    }
    IUploaded u = null;
    PersistenceManager pm = PMF.get().getPersistenceManager();
    Query q = pm.newQuery(JDOUtils.getImplClass(IUploaded.class));
    q.setFilter("fn == fnParam && l == lParam");
    q.declareParameters("String fnParam, String lParam");
    q.setOrdering("t desc");
    q.setRange(0, 1); // get the latest single entry
    try {
      List<IUploaded> results = (List<IUploaded>) q.execute(filename, locale);
      if (results != null && results.size() > 0) {
        u = results.get(0);
        u = pm.detachCopy(u);
      }
    } finally {
      try {
        q.closeAll();
      } catch (Exception ignored) {
        xLogger.warn("Exception while closing query", ignored);
      }
      pm.close();
    }

    return u;
  }

  public IUploaded getUploaded(String key) throws ServiceException, ObjectNotFoundException {
    String errMsg = null;
    IUploaded uploaded = null;
    PersistenceManager pm = PMF.get().getPersistenceManager();
    try {
      uploaded = JDOUtils.getObjectById(IUploaded.class, key, pm);
      uploaded = pm.detachCopy(uploaded);
    } catch (JDOObjectNotFoundException e) {
      // pm.close();
      throw new ObjectNotFoundException(e.getMessage());
    } catch (Exception e) {
      errMsg = e.getMessage();
    } finally {
      pm.close();
    }
    if (errMsg != null) {
      throw new ServiceException(errMsg);
    }

    return uploaded;
  }

  // Get an Uploaded object based on a MapReduce job Id (e.g. as used by bulk import of CSV files)
  @SuppressWarnings("unchecked")
  public IUploaded getUploadedByJobId(String mrJobId) throws ServiceException {
    xLogger.fine("Entered getUploadedByJobId");
    PersistenceManager pm = PMF.get().getPersistenceManager();
    // Form and execute query
    Query
        q =
        pm.newQuery("SELECT FROM " + JDOUtils.getImplClass(IUploaded.class).getName()
            + " WHERE jid == jidParam PARAMETERS String jidParam");
    IUploaded u = null;
    try {
      List<IUploaded> list = (List<IUploaded>) q.execute(mrJobId);
      if (list != null && !list.isEmpty()) {
        u = list.get(0);
        u = pm.detachCopy(u);
      }
    } finally {
      try {
        q.closeAll();
      } catch (Exception ignored) {
        xLogger.warn("Exception while closing query", ignored);
      }
      pm.close();
    }
    xLogger.fine("Exiting getUploadedByJobId");
    return u;
  }


  public void addNewUpload(IUploaded upload) throws ServiceException {
    PersistenceManager pm = PMF.get().getPersistenceManager();
    try {
      if (upload.getTimeStamp() == null) {
        upload.setTimeStamp(new Date());
      }
      upload = pm.makePersistent(upload);
      pm.detachCopy(upload);
    } finally {
      pm.close();
    }
  }

  public void addNewUpload(List<IUploaded> uploads) throws ServiceException {
    xLogger.fine("Entered addNewUpload");
    String errMsg = null;
    PersistenceManager pm = PMF.get().getPersistenceManager();

    if (uploads == null) {
      throw new ServiceException("Nothing to upload");
    }

    // Iterate through the list of uploads.
    uploads.stream().filter(u -> u.getId() == null).forEach(u ->
        u.setId(JDOUtils.createUploadedKey(u.getFileName(), u.getVersion(), u.getLocale())));

    // Write the list of Uploaded objects to the datastore.
    try {
      pm.makePersistentAll(uploads);
    } catch (Exception e) {
      errMsg = e.getMessage();
      xLogger.severe("Failed to store upload object", e);
    } finally {
      pm.close();
    }
    if (errMsg != null) {
      throw new ServiceException(errMsg);
    }
  }

  public void removeUploaded(String id) throws ServiceException {
    if (id == null) {
      throw new ServiceException("Nothing to delete");
    }
    PersistenceManager pm = PMF.get().getPersistenceManager();
    try {
      IUploaded objToBeDeleted = JDOUtils.getObjectById(IUploaded.class, id, pm);
      pm.deletePersistent(objToBeDeleted);
    } finally {
      pm.close();
    }
  }

  public void updateUploaded(IUploaded u) throws ServiceException, ObjectNotFoundException {
    if (u == null) {
      throw new ServiceException("Nothing to update");
    }
    PersistenceManager pm = PMF.get().getPersistenceManager();
    try {
      IUploaded uploaded = JDOUtils.getObjectById(IUploaded.class, u.getId(), pm);
      uploaded.setBlobKey(u.getBlobKey());
      uploaded.setDescription(u.getDescription());
      uploaded.setDomainId(u.getDomainId());
      uploaded.setFileName(u.getFileName());
      uploaded.setLocale(u.getLocale());
      uploaded.setTimeStamp(u.getTimeStamp());
      uploaded.setType(u.getType());
      uploaded.setUserId(u.getUserId());
      uploaded.setVersion(u.getVersion());
      uploaded.setJobId(u.getJobId());
      uploaded.setJobStatus(u.getJobStatus());
    } catch (JDOObjectNotFoundException e) {
      throw new ObjectNotFoundException(e.getMessage());
    } finally {
      pm.close();
    }
  }

  public void storeDownloaded(IDownloaded downloaded) throws ServiceException {
    String errMsg = null;
    PersistenceManager pm = PMF.get().getPersistenceManager();

    if (downloaded == null) {
      throw new ServiceException("Nothing has been downloaded");
    }

    // Write the Downloaded object to the datastore.
    try {
      pm.makePersistent(downloaded);
    } catch (Exception e) {
      errMsg = e.getMessage();
    } finally {
      pm.close();
    }
    if (errMsg != null) {
      throw new ServiceException(errMsg);
    }

  }
}

