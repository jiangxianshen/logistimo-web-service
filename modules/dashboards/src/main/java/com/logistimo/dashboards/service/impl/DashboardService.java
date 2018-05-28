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

package com.logistimo.dashboards.service.impl;

import com.logistimo.assets.entity.IAsset;
import com.logistimo.config.models.AssetSystemConfig;
import com.logistimo.config.models.ConfigurationException;
import com.logistimo.constants.Constants;
import com.logistimo.dao.JDOUtils;
import com.logistimo.dashboards.entity.IDashboard;
import com.logistimo.dashboards.entity.IWidget;
import com.logistimo.dashboards.querygenerators.AssetsDashboardQueryGenerator;
import com.logistimo.dashboards.querygenerators.EntityActivityQueryGenerator;
import com.logistimo.dashboards.service.IDashboardService;
import com.logistimo.exception.SystemException;
import com.logistimo.logger.XLog;
import com.logistimo.services.ServiceException;
import com.logistimo.services.impl.PMF;
import com.logistimo.services.utils.ConfigUtil;
import com.logistimo.tags.entity.ITag;
import com.sun.rowset.CachedRowSetImpl;

import org.apache.commons.lang.StringUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Map;

import javax.jdo.PersistenceManager;
import javax.jdo.Query;
import javax.jdo.datastore.JDOConnection;
import javax.sql.rowset.CachedRowSet;

/**
 * @author Mohan Raja
 */
@org.springframework.stereotype.Service
public class DashboardService implements IDashboardService {
  private static final XLog xLogger = XLog.getLog(DashboardService.class);
  private static final int PREDICTIVE_PERIOD = ConfigUtil.getInt("predictive.period", 7);

  @Override
  public void createDashboard(IDashboard ds) throws ServiceException {
    try {
      create(ds);
    } catch (Exception e) {
      xLogger.severe("Error in creating Dashboard:", e);
      throw new ServiceException("Error in creating Dashboard:", e);
    }
  }

  @Override
  public void createWidget(IWidget wid) throws ServiceException {
    try {
      create(wid);
    } catch (Exception e) {
      xLogger.severe("Error in creating Widget:", e);
      throw new ServiceException("Error in creating Widget:", e);
    }
  }

  private void create(Object o) throws ServiceException {
    PersistenceManager pm = PMF.get().getPersistenceManager();
    try {
      pm.makePersistent(o);
    } catch (Exception e) {
      throw new ServiceException("");
    } finally {
      pm.close();
    }
  }

  @Override
  public List<IDashboard> getDashBoards(Long domainId) {
    return getAll(domainId, IDashboard.class);
  }

  @Override
  public List<IWidget> getWidgets(Long domainId) {
    return getAll(domainId, IWidget.class);
  }

  @SuppressWarnings("unchecked")
  private <T> List<T> getAll(Long domainId, Class<T> clz) {
    List<T> o = null;
    PersistenceManager pm = PMF.get().getPersistenceManager();
    Query q = pm.newQuery(JDOUtils.getImplClass(clz));
    String declaration = " Long dIdParam";
    q.setFilter("dId == dIdParam");
    q.declareParameters(declaration);
    try {
      o = (List<T>) q.execute(domainId);
      if (o != null) {
        o.size();
        o = (List<T>) pm.detachCopyAll(o);
      }
    } finally {
      try {
        q.closeAll();
      } catch (Exception ignored) {
        xLogger.warn("Exception while closing query", ignored);
      }
      pm.close();
    }
    return o;
  }

  @Override
  public String deleteDashboard(Long id) throws ServiceException {
    PersistenceManager pm = PMF.get().getPersistenceManager();
    try {
      IDashboard db = JDOUtils.getObjectById(IDashboard.class, id, pm);
      String name = db.getName();
      pm.deletePersistent(db);
      return name;
    } catch (Exception e) {
      xLogger.severe("Error in deleting Dashboard:", e);
      throw new ServiceException("Error while deleting Dashboard" + id, e);
    } finally {
      pm.close();
    }
  }

  @Override
  public String deleteWidget(Long id) throws ServiceException {
    PersistenceManager pm = PMF.get().getPersistenceManager();
    try {
      IWidget wid = JDOUtils.getObjectById(IWidget.class, id, pm);
      String name = wid.getName();
      pm.deletePersistent(wid);
      return name;
    } catch (Exception e) {
      xLogger.severe("Error in deleting Widget:", e);
      throw new ServiceException("Error while deleting Widget" + id, e);
    } finally {
      pm.close();
    }
  }

  @Override
  public String setDefault(Long oid, Long id) throws ServiceException {
    PersistenceManager pm = PMF.get().getPersistenceManager();
    try {
      if (oid != 0) {
        IDashboard db = JDOUtils.getObjectById(IDashboard.class, oid, pm);
        //db.setDef(false);
        pm.makePersistent(db);
      }
      IDashboard defDb = JDOUtils.getObjectById(IDashboard.class, id, pm);
      //defDb.setDef(true);
      String name = defDb.getName();
      pm.makePersistent(defDb);
      return name;
    } catch (Exception e) {
      xLogger.severe("Error in setting dashboard {0} as default", id, e);
      throw new ServiceException("Error in setting dashboard " + id + " as default", e);
    } finally {
      pm.close();
    }
  }

  @Override
  public IDashboard getDashBoard(Long dbId) throws ServiceException {
    try {
      return get(dbId, IDashboard.class);
    } catch (Exception e) {
      xLogger.severe("Error in fetching Widget:", dbId, e);
      throw new ServiceException("Error in fetching Widget" + dbId, e);
    }
  }

  @Override
  public IWidget getWidget(Long wId) throws ServiceException {
    try {
      return get(wId, IWidget.class);
    } catch (Exception e) {
      xLogger.severe("Error in fetching Widget:", wId, e);
      throw new ServiceException("Error in fetching Widget" + wId, e);
    }
  }

  private <T> T get(Long id, Class<T> clz) {
    PersistenceManager pm = PMF.get().getPersistenceManager();
    try {
      return JDOUtils.getObjectById(clz, id, pm);
    } finally {
      pm.close();
    }
  }

  @Override
  public String updateDashboard(Long id, String ty, String val) throws ServiceException {
    PersistenceManager pm = PMF.get().getPersistenceManager();
    try {
      IDashboard db = JDOUtils.getObjectById(IDashboard.class, id, pm);
      String name = db.getName();
      if ("nm".equals(ty)) {
        db.setName(val);
      }
      pm.makePersistent(db);
      return name;
    } catch (Exception e) {
      xLogger.severe("Error in updating dashboard {0}", id, e);
      throw new ServiceException("Error in updating dashboard " + id, e);
    } finally {
      pm.close();
    }
  }

  @Override
  public String updateWidget(Long id, String ty, String val) throws ServiceException {
    PersistenceManager pm = PMF.get().getPersistenceManager();
    try {
      IWidget wid = JDOUtils.getObjectById(IWidget.class, id, pm);
      String name = wid.getName();
      if ("nm".equals(ty)) {
        wid.setName(val);
      } else if ("desc".equals(ty)) {
        wid.setDesc(val);
      }
      pm.makePersistent(wid);
      return name;
    } catch (Exception e) {
      xLogger.severe("Error in updating widget {0}", id, e);
      throw new ServiceException("Error in updating widget " + id, e);
    } finally {
      pm.close();
    }
  }

  @Override
  public void updateWidgetConfig(IWidget widget) throws ServiceException {
    PersistenceManager pm = PMF.get().getPersistenceManager();
    try {
      IWidget wid = JDOUtils.getObjectById(IWidget.class, widget.getwId(), pm);
      wid.setTitle(widget.getTitle());
      wid.setSubtitle(widget.getSubtitle());
      wid.setType(widget.getType());
      wid.setFreq(widget.getFreq());
      wid.setNop(widget.getNop());
      wid.setAggrTy(widget.getAggrTy());
      wid.setAggr(widget.getAggr());
      wid.setyLabel(widget.getyLabel());
      wid.setExpEnabled(widget.getExpEnabled());
      wid.setShowLeg(widget.getShowLeg());
      pm.makePersistent(wid);
    } catch (Exception e) {
      xLogger.severe("Error in updating widget config for widget {0}", widget.getwId(), e);
      throw new ServiceException("Error in updating widget config for widget " + widget.getwId(),
          e);
    } finally {
      pm.close();
    }
  }

  @Override
  public ResultSet getMainDashboardResults(Long domainId, Map<String, String> filters,
                                           String type) {
    return getMainDashboardResults(domainId, filters, type, false, null);
  }

  @Override
  public ResultSet getMainDashboardResults(Long domainId, Map<String, String> filters, String type,
                                           boolean isCountOnly, String groupby) {
    PersistenceManager pm = PMF.getReadOnlyPM().getPersistenceManager();
    JDOConnection conn = pm.getDataStoreConnection();
    Statement statement = null;
    try {
      java.sql.Connection sqlConn = (java.sql.Connection) conn;
      statement = sqlConn.createStatement();
      CachedRowSet rowSet = new CachedRowSetImpl();
      String query = null;
      switch (type) {
        case "inv":
          if (isCountOnly) {
            query = getMDInvQuery(domainId, filters, true, null);
          } else if (StringUtils.isNotEmpty(groupby)) {
            query = getMDInvQuery(domainId, filters, false, groupby);
          } else {
            query = getMDInvQuery(domainId, filters);
          }
          break;
        case "all_inv":
          if (isCountOnly) {
            query = getMDAllInvQuery(domainId, filters, true, null);
          } else if (StringUtils.isNotEmpty(groupby)) {
            query = getMDAllInvQuery(domainId, filters, false, groupby);
          } else {
            query = getMDAllInvQuery(domainId, filters);
          }
          break;
        case "activity":
          if (isCountOnly) {
            query = getMDEntQuery(domainId, filters, true);
          } else {
            query = getMDEntQuery(domainId, filters);
          }
          break;
        case "all_activity":
          if (isCountOnly) {
            query = getMDAllEntQuery(domainId, filters, true);
          } else {
            query = getMDAllEntQuery(domainId, filters);
          }
          break;
        case "temperature":
          query = getMDTempQuery(domainId, filters);
          break;
        case "idb_events":
          query = getIDEventsQuery(domainId, filters);
          break;
        case "idb_inv":
          query = getIDInventoryQuery(domainId, filters);
          break;
        case "sdb_session":
          query = getSessionQuery(domainId, filters);
          break;
        case "sdb_all_session":
          query = getAllSessionQuery(domainId, filters);
          break;
        case "pdb_stock_out":
          query = getPredictiveStockOutQuery(domainId, filters);
          break;
        case "all_predictive":
          query = getAllPredictiveStockOutQuery(domainId, filters);
          break;
        case "en_inv":
          query = getEntityInvDataQuery(filters);
          break;
        case "en_temp":
          query = getEntityTempDataQuery(filters);
          break;
        case "asset":
          query = getAssetStatusDashboardQuery(domainId, filters);
          break;
      }
      xLogger.info("Dashboard type: {0} query: {1}", type, query);
      rowSet.populate(statement.executeQuery(query));
      return rowSet;
    } catch (SQLException e) {
      throw new SystemException(e);
    } finally {
      try {
        if (statement != null) {
          statement.close();
        }
      } catch (Exception ignored) {
        xLogger.warn("Exception while closing statement", ignored);
      }

      try {
        conn.close();
      } catch (Exception ignored) {
        xLogger.warn("Exception while closing connection", ignored);
      }

      try {
        pm.close();
      } catch (Exception ignored) {
      }
    }

  }

  private String getPredictiveStockOutQuery(Long domainId, Map<String, String> filters) {
    StringBuilder query = new StringBuilder();
    query.append("SELECT 'so' ty, (SELECT NAME from MATERIAL WHERE MATERIALID = MID) MATERIAL, MID,");
    StringBuilder groupBy = new StringBuilder(" GROUP BY MATERIAL,");
    StringBuilder where = new StringBuilder();
    where.append(" WHERE PDOS <= ").append(PREDICTIVE_PERIOD)
        .append(" and `KEY` IN (SELECT KEY_OID FROM INVNTRY_DOMAINS WHERE DOMAIN_ID = ")
        .append(domainId).append(")");
    if (filters != null) {
      if (filters.get("district") != null) {
        query.append("(SELECT NAME FROM KIOSK WHERE KIOSKID = KID) NAME,KID,");
        groupBy.append("NAME, KID");
        where.append(" AND KID IN(SELECT KIOSKID FROM KIOSK WHERE STATE = '")
            .append(filters.get("state")).append("'")
            .append(" AND COUNTRY = '").append(filters.get("country")).append("'");
        if ("".equals(filters.get("district"))) {
          where.append("AND (DISTRICT = '' OR DISTRICT IS NULL))");
        } else {
          where.append("AND DISTRICT = '").append(filters.get("district")).append("')");
        }
      } else if (filters.get("state") != null) {
        query.append("(SELECT DISTRICT FROM KIOSK WHERE KIOSKID = KID) DISTRICT,");
        groupBy.append("DISTRICT");
        where.append(" AND KID IN(SELECT KIOSKID FROM KIOSK WHERE STATE = '")
            .append(filters.get("state")).append("'")
            .append(" AND COUNTRY = '").append(filters.get("country")).append("')");
      } else {
        query.append("(SELECT STATE FROM KIOSK WHERE KIOSKID = KID) STATE,");
        groupBy.append("STATE");
        where.append(" AND KID IN (SELECT KIOSKID FROM KIOSK WHERE COUNTRY = '")
            .append(filters.get("country")).append("')");
      }
      if (filters.get("mTag") != null) {
        where.append(" AND MID IN(SELECT MATERIALID from MATERIAL_TAGS WHERE ID IN(")
            .append("(SELECT ID FROM TAG WHERE NAME IN(").append(filters.get("mTag"))
            .append(") AND TYPE=").append(ITag.MATERIAL_TAG).append("))")
            .append(")");
      } else if (filters.get("mId") != null) {
        where.append(" AND MID = ").append(filters.get("mId"));
      }
      if (filters.get("eTag") != null) {
        where.append(" AND KID IN(SELECT DISTINCT(KIOSKID) from KIOSK_TAGS WHERE ID IN(")
            .append("(SELECT ID FROM TAG WHERE NAME IN(").append(filters.get("eTag"))
            .append(") AND TYPE=").append(ITag.KIOSK_TAG).append("))")
            .append(")");
      } else if (filters.get("eeTag") != null) {
        where.append(" AND KID NOT IN(SELECT DISTINCT(KIOSKID) from KIOSK_TAGS WHERE ID IN(")
            .append("(SELECT ID FROM TAG WHERE NAME IN(").append(filters.get("eeTag"))
            .append(") AND TYPE=").append(ITag.KIOSK_TAG).append("))")
            .append(")");
      }
    }
    query.append("COUNT(1) COUNT FROM INVNTRY");
    query.append(where);
    query.append(groupBy);
    return query.toString();
  }

  private String getAllPredictiveStockOutQuery(Long domainId, Map<String, String> filters) {
    StringBuilder query = new StringBuilder();
    query.append(
        "select SUM(ALLCOUNT) ALLCOUNT, SUM(COUNT) COUNT FROM (SELECT 1 ALLCOUNT, IF(PDOS <= ")
        .append(PREDICTIVE_PERIOD).append(",1,0) COUNT");
    StringBuilder where = new StringBuilder();
    where.append(" WHERE `KEY` IN (SELECT KEY_OID FROM INVNTRY_DOMAINS WHERE DOMAIN_ID = ")
        .append(domainId).append(")");
    if (filters != null) {
      if (filters.get("district") != null) {
        where.append(" AND KID IN(SELECT KIOSKID FROM KIOSK WHERE STATE = '")
            .append(filters.get("state")).append("'")
            .append(" AND COUNTRY = '").append(filters.get("country")).append("'");
        if ("".equals(filters.get("district"))) {
          where.append("AND (DISTRICT = '' OR DISTRICT IS NULL))");
        } else {
          where.append("AND DISTRICT = '").append(filters.get("district")).append("')");
        }
      } else if (filters.get("state") != null) {
        where.append(" AND KID IN(SELECT KIOSKID FROM KIOSK WHERE STATE = '")
            .append(filters.get("state")).append("'")
            .append(" AND COUNTRY = '").append(filters.get("country")).append("')");
      } else {
        where.append(" AND KID IN (SELECT KIOSKID FROM KIOSK WHERE COUNTRY = '")
            .append(filters.get("country")).append("')");
      }
      if (filters.get("mTag") != null) {
        where.append(" AND MID IN(SELECT MATERIALID from MATERIAL_TAGS WHERE ID IN(")
            .append("(SELECT ID FROM TAG WHERE NAME IN(").append(filters.get("mTag"))
            .append(") AND TYPE=").append(ITag.MATERIAL_TAG).append("))")
            .append(")");
      } else if (filters.get("mId") != null) {
        where.append(" AND MID = ").append(filters.get("mId"));
      }
      if (filters.get("eTag") != null) {
        where.append(" AND KID IN(SELECT DISTINCT(KIOSKID) from KIOSK_TAGS WHERE ID IN(")
            .append("(SELECT ID FROM TAG WHERE NAME IN(").append(filters.get("eTag"))
            .append(") AND TYPE=").append(ITag.KIOSK_TAG).append("))")
            .append(")");
      } else if (filters.get("eeTag") != null) {
        where.append(" AND KID NOT IN(SELECT DISTINCT(KIOSKID) from KIOSK_TAGS WHERE ID IN(")
            .append("(SELECT ID FROM TAG WHERE NAME IN(").append(filters.get("eeTag"))
            .append(") AND TYPE=").append(ITag.KIOSK_TAG).append("))")
            .append(")");
      }
    }
    where.append(")A");
    query.append(" FROM INVNTRY");
    query.append(where);
    return query.toString();
  }

  private String getAllSessionQuery(Long domainId, Map<String, String> filters) {
    StringBuilder query = new StringBuilder();
    query.append("SELECT COUNT(1) COUNT");
    StringBuilder where = new StringBuilder();
    where.append(" WHERE K.KIOSKID = KD.KIOSKID_OID AND KD.DOMAIN_ID = ").append(domainId);

    if (filters.get("eTag") != null) {
      where
          .append(" AND EXISTS( SELECT 1 FROM KIOSK_TAGS KT WHERE KT.KIOSKID = K.KIOSKID AND ID = ")
          .append("(SELECT ID FROM TAG WHERE NAME='").append(filters.get("eTag"))
          .append("' AND TYPE=")
          .append(ITag.KIOSK_TAG).append(" limit 1)").append(")");
    }

    if (filters.get("mTag") != null) {
      where.append(
          " AND EXISTS( SELECT 1 FROM MATERIAL_TAGS WHERE MATERIALID IN (SELECT MID FROM INVNTRY I WHERE I.KID=K.KIOSKID) AND ID IN ")
          .append("(SELECT ID FROM TAG WHERE NAME IN(").append(filters.get("mTag"))
          .append(") AND TYPE=")
          .append(ITag.MATERIAL_TAG).append("))");
    } else if (filters.get("mId") != null) {
      where.append(" AND EXISTS( SELECT 1 FROM INVNTRY I WHERE I.KID=K.KIOSKID AND MID = ")
          .append(filters.get("mId")).append(")");
    }

    if (filters.get("district") != null) {
      where.append(" AND COUNTRY = '").append(filters.get("country"))
          .append("' AND STATE = '").append(filters.get("state"));
      if ("No District".equals(filters.get("district"))) {
        where.append("' AND (DISTRICT IS NULL OR DISTRICT = '')");
      } else {
        where.append("' AND DISTRICT = '").append(filters.get("district")).append("'");
      }
      query.append(", NAME LEVEL");
    } else if (filters.get("state") != null) {
      where.append(" AND COUNTRY = '").append(filters.get("country"))
          .append("' AND STATE = '").append(filters.get("state")).append("'");
      query.append(", DISTRICT LEVEL");
    } else {
      where.append(" AND COUNTRY = '").append(filters.get("country")).append("'");
      query.append(", STATE LEVEL");
    }
    query.append(" FROM KIOSK K, KIOSK_DOMAINS KD")
        .append(where).append(" GROUP BY LEVEL");
    return query.toString();
  }

  private String getSessionQuery(Long domainId, Map<String, String> filters) {
    StringBuilder query = new StringBuilder();
    query.append("SELECT COUNT(1) CNT,DF,ATD,SUM(TCNT) TCNT,GROUP_BY_NAME");
    String groupNameClause;
    StringBuilder where = new StringBuilder();
    where.append(" WHERE ATD BETWEEN DATE_SUB('").append(filters.get("atd"))
        .append("', INTERVAL 6 DAY) AND '")
        .append(filters.get("atd")).append("' AND T.KID = K.KIOSKID AND T.KID = KD.KIOSKID_OID AND KD.DOMAIN_ID = ")
        .append(domainId);

    if (filters.get("type") != null) {
      where.append(" AND T.TYPE = '").append(filters.get("type")).append("'");
    }
    if (filters.get("eTag") != null) {
      where.append(" AND EXISTS( SELECT 1 FROM KIOSK_TAGS WHERE KIOSKID = KID AND ID = ")
          .append("(SELECT ID FROM TAG WHERE NAME='").append(filters.get("eTag"))
          .append("' AND TYPE=")
          .append(ITag.KIOSK_TAG).append(" limit 1)").append(")");
    }

    if (filters.get("mTag") != null) {
      where.append(" AND EXISTS( SELECT 1 FROM MATERIAL_TAGS WHERE MATERIALID = MID AND ID IN ")
          .append("(SELECT ID FROM TAG WHERE NAME IN (").append(filters.get("mTag"))
          .append(") AND TYPE=")
          .append(ITag.MATERIAL_TAG).append("))");
    } else if (filters.get("mId") != null) {
      where.append(" AND MID = ").append(filters.get("mId"));
    }

    if (filters.get("district") != null) {
      where.append(" AND K.COUNTRY = '")
          .append(filters.get("country"))
          .append("' AND K.STATE = '").append(filters.get("state"));
      if ("No District".equals(filters.get("district"))) {
        where.append("' AND (K.DISTRICT IS NULL OR K.DISTRICT = '')");
      } else {
        where.append("' AND K.DISTRICT = '").append(filters.get("district")).append("'");
      }
      groupNameClause = "K.NAME AS GROUP_BY_NAME";
    } else if (filters.get("state") != null) {
      where.append(" AND K.COUNTRY = '")
          .append(filters.get("country"))
          .append("' AND K.STATE = '").append(filters.get("state")).append("'");
      groupNameClause = "K.DISTRICT AS GROUP_BY_NAME";
    } else {
      where.append(" AND K.COUNTRY = '")
          .append(filters.get("country")).append("'");
      groupNameClause = "K.STATE AS GROUP_BY_NAME";
    }
    where.append(" GROUP BY CONCAT(KID, DF, ATD)");
    query.append(" FROM (SELECT KID,").append(groupNameClause)
        .append(",CASE WHEN DATEDIFF(DATE(DATE_ADD(T, INTERVAL '")
        .append(filters.get("diff"))
        .append("' HOUR_SECOND)), ATD) <= 0 THEN '1' WHEN DATEDIFF(DATE(DATE_ADD(T, INTERVAL '")
        .append(filters.get("diff"))
        .append("' HOUR_SECOND)), ATD) >= 3 THEN '3' ELSE DATEDIFF(DATE(DATE_ADD(T, INTERVAL '")
        .append(filters.get("diff"))
        .append(
            "' HOUR_SECOND)), ATD) END AS DF,ATD, COUNT(1) TCNT FROM TRANSACTION T, KIOSK_DOMAINS KD, KIOSK K")
        .append(where).append(") A").append(" GROUP BY ATD, DF, GROUP_BY_NAME");
    return query.toString();
  }

  private String getMDTempQuery(Long domainId, Map<String, String> filters) {
    try {
      String csv = filters.get("type");
      if (csv == null) {
        AssetSystemConfig config = AssetSystemConfig.getInstance();
        Map<Integer, AssetSystemConfig.Asset>
            monitoredAssets =
            config.getAssetsByType(IAsset.MONITORED_ASSET);
        csv = monitoredAssets.keySet().toString();
        csv = csv.substring(1, csv.length() - 1);
      }
      String p = StringUtils.isEmpty(filters.get("tPeriod")) ? "M_0" : filters.get("tPeriod");
      String period;
      if (p.startsWith("M")) {
        period = "INTERVAL " + p.substring(2) + " MINUTE";
      } else if (p.startsWith("H")) {
        period = "INTERVAL " + p.substring(2) + " HOUR";
      } else if (p.startsWith("D")) {
        period = "INTERVAL " + p.substring(2) + " DAY";
      } else { //From Inventory dashboard period filter
        period = "INTERVAL " + p + " DAY";
      }
      StringBuilder query = new StringBuilder();
      boolean isDistrict = filters.get("district") != null;
      boolean isState = filters.get("state") != null;
      String colName = isDistrict ? "NAME" : (isState ? "DISTRICT" : "STATE");
      String idHandle = isDistrict ? null : (isState ? "DISTRICT_ID" : "STATE_ID");
      query.append("SELECT ").append(colName).append(isDistrict ? ",KID" : "")
          .append((null == idHandle) ? "" : "," + idHandle)
          .append(", STAT, COUNT(1) COUNT FROM (SELECT A.")
          .append(colName).append(isDistrict ? ",KID" : "")
          .append((null == idHandle) ? "" : ",A." + idHandle)
          .append(", IF(ASF.STAT = 'tu', 'tu',")
          .append("(SELECT IF(MAX(ABNSTATUS) = 2, 'th', IF(MAX(ABNSTATUS) = 1, 'tl', 'tn'))")
          .append("FROM ASSETSTATUS AST WHERE ")
          .append("AST.ASSETID = A.ID AND AST.TYPE = 1 AND ")
          .append("AST.STATUS = 3 AND ")
          .append("AST.TS <= DATE_SUB(NOW(), ").append(period)
          .append("))) STAT FROM (SELECT A.ID,")
          .append(isDistrict ? "KID," : "")
          .append("(SELECT ").append(colName).append(" FROM KIOSK WHERE KIOSKID = A.KID)")
          .append(colName)
          .append((null == idHandle) ? ""
              : "," + "(SELECT " + idHandle + " FROM KIOSK WHERE KIOSKID = A.KID)" + idHandle)
          .append(" FROM ASSET A, ASSET_DOMAINS AD WHERE A.TYPE IN (").append(csv)
          .append(") AND (SELECT COUNTRY FROM KIOSK WHERE KIOSKID = A.KID) = '")
          .append(filters.get("country")).append("'")
          .append(" AND EXISTS(SELECT 1 FROM ASSETRELATION R WHERE A.ID = R.ASSETID AND R.TYPE=2)")
          .append(
              "AND 1 = (SELECT IFNULL(0 = (SELECT STATUS FROM ASSETSTATUS S WHERE TYPE = 7 and S.ASSETID = A.ID),1))");
      if (isDistrict || isState) {
        query.append(" AND (SELECT STATE FROM KIOSK WHERE KIOSKID = A.KID) = '")
            .append(filters.get("state")).append("'");
      }
      if (isDistrict) {
        query.append(" AND (SELECT DISTRICT FROM KIOSK WHERE KIOSKID = A.KID) = '")
            .append(filters.get("district")).append("'");
      }

      query.append(" AND A.ID = AD.ID_OID AND AD.DOMAIN_ID=").append(domainId);
      if (filters.get("eTag") != null) {
        query.append(" AND KID IN(SELECT DISTINCT(KIOSKID) from KIOSK_TAGS WHERE ID IN(")
            .append("(SELECT ID FROM TAG WHERE NAME IN(").append(filters.get("eTag"))
            .append(") AND TYPE=").append(ITag.KIOSK_TAG).append("))")
            .append(")");
      } else if (filters.get("eeTag") != null) {
        query.append(" AND KID NOT IN(SELECT DISTINCT(KIOSKID) from KIOSK_TAGS WHERE ID IN(")
            .append("(SELECT ID FROM TAG WHERE NAME IN(").append(filters.get("eeTag"))
            .append(") AND TYPE=").append(ITag.KIOSK_TAG).append("))")
            .append(")");
      } else {
        query.append(" AND KID IS NOT NULL");
      }
      query.append(
          ") A LEFT JOIN (SELECT ASSETID, IF(MIN(STATUS) = 0, 'tk', 'tu') STAT FROM ASSETSTATUS ASI, ")
          .append("ASSET_DOMAINS AD WHERE ASI.TYPE = 3 AND ")
          .append("ASI.ASSETID=AD.ID_OID AND AD.DOMAIN_ID=")
          .append(domainId).append(" AND TS <= DATE_SUB(NOW(), ").append(period)
          .append(") GROUP BY ASI.ASSETID) ASF ON A.ID = ASF.ASSETID) T GROUP BY T.STAT, T.")
          .append(colName).append(isDistrict ? ",T.KID" : "");
      return query.toString();
    } catch (ConfigurationException e) {
      xLogger.severe("Error in constructing data for dashboard (temperature)");
    }
    return null;
  }

  private String getMDInvQuery(Long domainId, Map<String, String> filters) {
    return getMDInvQuery(domainId, filters, false, null);
  }

  private String getMDInvQuery(Long domainId, Map<String, String> filters, boolean isCountOnly,
                               String grpby) {

    boolean isGrpByLoc = false;
    boolean isGrpByMat = false;
    if (StringUtils.isEmpty(grpby)) {
      isGrpByLoc = true;
      isGrpByMat = true;
    } else if ("loc".equals(grpby)) {
      isGrpByLoc = true;
    } else if ("mat".equals(grpby)) {
      isGrpByMat = true;
    }
    StringBuilder query = new StringBuilder();
    query.append("SELECT ty TYPE,");
    StringBuilder groupBy = new StringBuilder(" GROUP BY TY");
    if (!isCountOnly && isGrpByMat) {
      query.append("(SELECT NAME from MATERIAL WHERE MATERIALID = MID) MATERIAL, MID,");
      groupBy.append(", MATERIAL");
    }
    StringBuilder where = new StringBuilder();
    where.append(" WHERE `KID` IN (SELECT KIOSKID_OID FROM KIOSK_DOMAINS WHERE DOMAIN_ID = ")
        .append(domainId).append(")");

    if (filters != null) {
      if (filters.get("district") != null) {
        if (!isCountOnly && isGrpByLoc) {
          query.append("(SELECT NAME FROM KIOSK WHERE KIOSKID = KID) NAME, KID,");
          groupBy.append(",NAME, KID");
        }
        where.append(" AND KID IN(SELECT KIOSKID FROM KIOSK WHERE STATE = '")
            .append(filters.get("state")).append("'")
            .append(" AND COUNTRY = '").append(filters.get("country")).append("'");
        if ("".equals(filters.get("district"))) {
          where.append("AND (DISTRICT = '' OR DISTRICT IS NULL))");
        } else {
          where.append("AND DISTRICT = '").append(filters.get("district")).append("')");
        }
      } else if (filters.get("state") != null) {
        if (!isCountOnly && isGrpByLoc) {
          query.append("(SELECT DISTRICT FROM KIOSK WHERE KIOSKID = KID) DISTRICT,");
          query.append("(SELECT DISTRICT_ID FROM KIOSK WHERE KIOSKID = KID) DISTRICT_ID,");
          groupBy.append(",DISTRICT");
          groupBy.append(",DISTRICT_ID");
        }
        where.append(" AND KID IN(SELECT KIOSKID FROM KIOSK WHERE STATE = '")
            .append(filters.get("state")).append("'")
            .append(" AND COUNTRY = '").append(filters.get("country")).append("')");
      } else {
        if (!isCountOnly && isGrpByLoc) {
          query.append("(SELECT STATE FROM KIOSK WHERE KIOSKID = KID) STATE,");
          query.append("(SELECT STATE_ID FROM KIOSK WHERE KIOSKID = KID) STATE_ID,");
          groupBy.append(",STATE");
          groupBy.append(",STATE_ID");
        }
        where.append(" AND KID IN (SELECT KIOSKID FROM KIOSK WHERE COUNTRY = '")
            .append(filters.get("country")).append("')");
      }
      if (filters.get("mTag") != null) {
        where.append(" AND MID IN(SELECT MATERIALID from MATERIAL_TAGS WHERE ID IN(")
            .append("(SELECT ID FROM TAG WHERE NAME IN(").append(filters.get("mTag"))
            .append(") AND TYPE=").append(ITag.MATERIAL_TAG).append("))")
            .append(")");
      } else if (filters.get("mId") != null) {
        where.append(" AND MID = ").append(filters.get("mId"));
      }
      if (filters.get("eTag") != null) {
        where.append(" AND KID IN(SELECT DISTINCT(KIOSKID) from KIOSK_TAGS WHERE ID IN(")
            .append("(SELECT ID FROM TAG WHERE NAME IN(").append(filters.get("eTag"))
            .append(") AND TYPE=").append(ITag.KIOSK_TAG).append("))")
            .append(")");
      } else if (filters.get("eeTag") != null) {
        where.append(" AND KID NOT IN(SELECT DISTINCT(KIOSKID) from KIOSK_TAGS WHERE ID IN(")
            .append("(SELECT ID FROM TAG WHERE NAME IN(").append(filters.get("eeTag"))
            .append(") AND TYPE=").append(ITag.KIOSK_TAG).append("))")
            .append(")");
      }
      String date = null;
      if (filters.get("date") != null) {
        date = filters.get("date");
        where.append(" AND ( ED IS NULL OR ED > '").append(date).append("')");
      } else {
        where.append(" AND ED IS NULL");
      }
      int period = 0;
      if (filters.get("period") != null) {
        period = Integer.parseInt(filters.get("period"));
      }
      if (period > 0 || date != null) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Calendar cal = GregorianCalendar.getInstance();
        if (date != null) {
          try {
            cal.setTime(sdf.parse(date));
          } catch (ParseException e) {
            xLogger
                .warn("This should never happen, unable to parse date {0}", filters.get("date"), e);
          }
        }
        cal.add(Calendar.DAY_OF_MONTH, -period);
        where.append(" AND SD <= '").append(sdf.format(cal.getTime())).append("'");
      }

    } else {
      where.append(" AND ED IS NULL");
    }
    query.append("COUNT(1) COUNT FROM INVNTRYEVNTLOG");
    query.append(where);
    query.append(groupBy);
    return query.toString();
  }

  private String getMDAllInvQuery(Long domainId, Map<String, String> filters) {
    return getMDAllInvQuery(domainId, filters, false, null);
  }

  private String getMDAllInvQuery(Long domainId, Map<String, String> filters, boolean isCountOnly,
                                  String grpby) {
    boolean isGrpByLoc = false;
    boolean isGrpByMat = false;
    if (StringUtils.isEmpty(grpby)) {
      isGrpByLoc = true;
      isGrpByMat = true;
    } else if ("loc".equals(grpby)) {
      isGrpByLoc = true;
    } else if ("mat".equals(grpby)) {
      isGrpByMat = true;
    }
    StringBuilder query = new StringBuilder();
    query.append("SELECT ");
    StringBuilder groupBy = new StringBuilder(" GROUP BY ");
    if (!isCountOnly && isGrpByMat) {
      query.append("(SELECT NAME from MATERIAL WHERE MATERIALID = MID) MATERIAL, MID,");
      groupBy.append(" MATERIAL");
      groupBy.append(",");
    }
    StringBuilder where = new StringBuilder();
    where.append(" WHERE `KEY` IN (SELECT KEY_OID FROM INVNTRY_DOMAINS WHERE DOMAIN_ID = ")
        .append(domainId).append(")");
    if (filters != null) {
      if (filters.get("district") != null) {
        if (!isCountOnly && isGrpByLoc) {
          query.append("(SELECT NAME FROM KIOSK WHERE KIOSKID = KID) NAME,KID,");
          groupBy.append("NAME, KID");
          groupBy.append(",");
        }
        where.append(" AND KID IN(SELECT KIOSKID FROM KIOSK WHERE STATE = '")
            .append(filters.get("state")).append("'")
            .append(" AND COUNTRY = '").append(filters.get("country")).append("'");
        if ("".equals(filters.get("district"))) {
          where.append("AND (DISTRICT = '' OR DISTRICT IS NULL))");
        } else {
          where.append("AND DISTRICT = '").append(filters.get("district")).append("')");
        }
      } else if (filters.get("state") != null) {
        if (!isCountOnly && isGrpByLoc) {
          query.append("(SELECT DISTRICT FROM KIOSK WHERE KIOSKID = KID) DISTRICT,");
          query.append("(SELECT DISTRICT_ID FROM KIOSK WHERE KIOSKID = KID) DISTRICT_ID,");
          groupBy.append("DISTRICT,");
          groupBy.append("DISTRICT_ID,");
        }
        where.append(" AND KID IN(SELECT KIOSKID FROM KIOSK WHERE STATE = '")
            .append(filters.get("state")).append("'")
            .append(" AND COUNTRY = '").append(filters.get("country")).append("')");
      } else {
        if (!isCountOnly && isGrpByLoc) {
          query.append("(SELECT STATE FROM KIOSK WHERE KIOSKID = KID) STATE,");
          query.append("(SELECT STATE_ID FROM KIOSK WHERE KIOSKID = KID) STATE_ID,");
          groupBy.append("STATE,");
          groupBy.append("STATE_ID,");
        }
        where.append(" AND KID IN (SELECT KIOSKID FROM KIOSK WHERE COUNTRY = '")
            .append(filters.get("country")).append("')");
      }
      if (filters.get("mTag") != null) {
        where.append(" AND MID IN(SELECT MATERIALID from MATERIAL_TAGS WHERE ID IN(")
            .append("(SELECT ID FROM TAG WHERE NAME IN(").append(filters.get("mTag"))
            .append(") AND TYPE=").append(ITag.MATERIAL_TAG).append("))")
            .append(")");
      } else if (filters.get("mId") != null) {
        where.append(" AND MID = ").append(filters.get("mId"));
      }
      if (filters.get("eTag") != null) {
        where.append(" AND KID IN(SELECT DISTINCT(KIOSKID) from KIOSK_TAGS WHERE ID IN(")
            .append("(SELECT ID FROM TAG WHERE NAME IN(").append(filters.get("eTag"))
            .append(") AND TYPE=").append(ITag.KIOSK_TAG).append("))")
            .append(")");
      } else if (filters.get("eeTag") != null) {
        where.append(" AND KID NOT IN(SELECT DISTINCT(KIOSKID) from KIOSK_TAGS WHERE ID IN(")
            .append("(SELECT ID FROM TAG WHERE NAME IN(").append(filters.get("eeTag"))
            .append(") AND TYPE=").append(ITag.KIOSK_TAG).append("))")
            .append(")");
      }
    }
    query.append("COUNT(1) COUNT FROM INVNTRY");
    query.append(where);
    if (groupBy.length() > " GROUP BY ".length()) {
      groupBy.setLength(groupBy.length() - 1);
      query.append(groupBy);
    }
    return query.toString();
  }

  private String getMDEntQuery(Long domainId, Map<String, String> filters) {
    return getMDEntQuery(domainId, filters, false);
  }

  private String getMDEntQuery(Long domainId, Map<String, String> filters, boolean isCountOnly) {
    EntityActivityQueryGenerator
        queryGenerator =
        EntityActivityQueryGenerator.getEntityActivityQueryGenerator()
            .withDomainId(domainId)
            .withIsCount(isCountOnly);
    if (filters != null) {
      queryGenerator.withCountry(filters.get("country"))
          .withState(filters.get("state"))
          .withDistrict(filters.get("district"))
          .withMaterialTags(filters.get("mTag"))
          .withEntityTags(filters.get("eTag"))
          .withExcludeEntityTags(filters.get("eeTag"))
          .withPeriod(Integer.valueOf(filters.get("period")));
      if (filters.get("mId") != null) {
        queryGenerator.withMaterialId(Long.valueOf(filters.get("mId")));
      }

      if (filters.get("date") != null) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Calendar cal = new GregorianCalendar();
        try {
          cal.setTime(sdf.parse(filters.get("date")));
          queryGenerator.withAsOf(cal.getTime());
        } catch (ParseException e) {
          xLogger
              .warn("This should never happen, unable to parse date {0}", filters.get("date"), e);
          throw new IllegalArgumentException(
              "Unable to parse Date string " + filters.get("date") + " " + e.getMessage());
        }
      }
    }
    return queryGenerator.generate();
  }

  private String getMDAllEntQuery(Long domainId, Map<String, String> filters) {
    return getMDAllEntQuery(domainId, filters, false);
  }

  private String getMDAllEntQuery(Long domainId, Map<String, String> filters, boolean isCountOnly) {
    StringBuilder query = new StringBuilder("SELECT ");
    StringBuilder groupBy = new StringBuilder();
    if (!isCountOnly) {
      groupBy.append(" GROUP BY ");
    }
    StringBuilder where = new StringBuilder();
    where.append(" WHERE KIOSKID IN (SELECT KIOSKID_OID FROM KIOSK_DOMAINS WHERE DOMAIN_ID = ")
        .append(domainId).append(")");
    if (filters != null) {
      if (filters.get("district") != null) {
        if (!isCountOnly) {
          query.append("NAME,CAST(KIOSKID AS CHAR) AS KID,");
          groupBy.append("NAME,KID");
        }
        where.append(" AND STATE = '").append(filters.get("state")).append("'")
            .append(" AND COUNTRY = '").append(filters.get("country")).append("'");
        if ("".equals(filters.get("district"))) {
          where.append("AND (DISTRICT = '' OR DISTRICT IS NULL)");
        } else {
          where.append("AND DISTRICT = '").append(filters.get("district")).append("'");
        }
      } else if (filters.get("state") != null) {
        if (!isCountOnly) {
          query.append("DISTRICT,");
          query.append("DISTRICT_ID,");
          groupBy.append("DISTRICT");
          groupBy.append(",DISTRICT_ID");
        }
        where.append(" AND STATE = '").append(filters.get("state")).append("'")
            .append(" AND COUNTRY = '").append(filters.get("country")).append("'");
      } else {
        if (!isCountOnly) {
          query.append(" STATE,");
          query.append(" STATE_ID,");
          groupBy.append(" STATE");
          groupBy.append(", STATE_ID");
        }
        where.append(" AND COUNTRY = '").append(filters.get("country")).append("'");
      }

      if (filters.get("mTag") != null) {
        where.append(
            " AND KIOSKID IN (SELECT DISTINCT(KID) FROM INVNTRY WHERE MID IN (SELECT MATERIALID from MATERIAL_TAGS WHERE ID IN(")
            .append("(SELECT ID FROM TAG WHERE NAME IN(").append(filters.get("mTag"))
            .append(") AND TYPE=").append(ITag.MATERIAL_TAG).append("))")
            .append("))");
      } else if (filters.get("mId") != null) {
        where.append(" AND KIOSKID IN (SELECT DISTINCT(KID) FROM INVNTRY WHERE MID = ")
            .append(filters.get("mId"))
            .append(")");
      }
      if (filters.get("eTag") != null) {
        where.append(" AND KIOSKID IN(SELECT DISTINCT(KIOSKID) from KIOSK_TAGS WHERE ID IN(")
            .append("(SELECT ID FROM TAG WHERE NAME IN(").append(filters.get("eTag"))
            .append(") AND TYPE=").append(ITag.KIOSK_TAG).append("))")
            .append(")");
      } else if (filters.get("eeTag") != null) {
        where.append(" AND KIOSKID NOT IN(SELECT DISTINCT(KIOSKID) from KIOSK_TAGS WHERE ID IN(")
            .append("(SELECT ID FROM TAG WHERE NAME IN(").append(filters.get("eeTag"))
            .append(") AND TYPE=").append(ITag.KIOSK_TAG).append("))")
            .append(")");
      }
    }
    query.append(" COUNT(1) COUNT FROM KIOSK");
    query.append(where);
    query.append(groupBy);
    return query.toString();
  }

  /**
   * Inventory Dashboard events query
   *
   * @param domainId -
   * @param filters  -
   * @return -
   */
  private String getIDEventsQuery(Long domainId, Map<String, String> filters) {
    StringBuilder query = new StringBuilder();
    query.append("SELECT ty TYPE,");
    StringBuilder groupBy = new StringBuilder(" GROUP BY TY");
    StringBuilder where = new StringBuilder();
    where.append(" WHERE `KID` IN (SELECT KIOSKID_OID FROM KIOSK_DOMAINS WHERE DOMAIN_ID = ")
        .append(domainId).append(")");
    where.append(" AND ED IS NULL");

    if (filters != null) {
      if (filters.get("district") != null) {
        query.append("KID,(SELECT NAME FROM KIOSK WHERE KIOSKID = KID) ENTITY,");
        groupBy.append(",KID");
        where.append(" AND KID IN(SELECT KIOSKID FROM KIOSK WHERE STATE = '")
            .append(filters.get("state")).append("'")
            .append(" AND COUNTRY = '").append(filters.get("country")).append("'");
        if ("No district".equals(filters.get("district"))) {
          where.append(" AND (DISTRICT IS NULL OR DISTRICT = ''))");
        } else {
          where.append(" AND DISTRICT = '").append(filters.get("district")).append("')");
        }
      } else if (filters.get("state") != null) {
        query.append("(SELECT DISTRICT FROM KIOSK WHERE KIOSKID = KID) DISTRICT,");
        groupBy.append(",DISTRICT");
        where.append(" AND KID IN(SELECT KIOSKID FROM KIOSK WHERE STATE = '")
            .append(filters.get("state")).append("'")
            .append(" AND COUNTRY = '").append(filters.get("country")).append("')");
      } else {
        query.append("(SELECT STATE FROM KIOSK WHERE KIOSKID = KID) STATE,");
        groupBy.append(",STATE");
        where.append(" AND KID IN (SELECT KIOSKID FROM KIOSK WHERE COUNTRY = '")
            .append(filters.get("country")).append("')");
      }
      if (filters.get("eTag") != null) {
        where.append(" AND KID IN(SELECT KIOSKID from KIOSK_TAGS WHERE ID =")
            .append("(SELECT ID FROM TAG WHERE NAME='").append(filters.get("eTag"))
            .append("' AND TYPE=").append(ITag.KIOSK_TAG).append(" limit 1)")
            .append(")");
      }
    }

    query.append("(SELECT NAME FROM MATERIAL WHERE MATERIALID = MID) MATERIAL,MID,");
    groupBy.append(",MATERIAL");

    if (filters != null && filters.get("period") != null) {
      int period = Integer.parseInt(filters.get("period"));
      if (period > 0) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Calendar cal = new GregorianCalendar();
        cal.add(Calendar.DAY_OF_MONTH, -period);
        where.append(" AND SD <= '").append(sdf.format(cal.getTime())).append("'");
      }
    }

    query.append("COUNT(1) COUNT FROM INVNTRYEVNTLOG");
    query.append(where);
    query.append(groupBy);
    return query.toString();
  }


  /**
   * Inventory dashboard get full inventory query
   *
   * @param domainId -
   * @param filters  -
   * @return -
   */
  private String getIDInventoryQuery(Long domainId, Map<String, String> filters) {
    StringBuilder query = new StringBuilder();
    query.append("SELECT ");
    StringBuilder groupBy = new StringBuilder(" GROUP BY ");
    StringBuilder where = new StringBuilder();
    where.append(" WHERE `KEY` IN (SELECT KEY_OID FROM INVNTRY_DOMAINS WHERE DOMAIN_ID = ")
        .append(domainId).append(")");
    boolean isEntity = false;
    if (filters != null) {
      if (filters.get("district") != null) {
        query.append("KID,(SELECT NAME FROM KIOSK WHERE KIOSKID = KID) ENTITY,");
        groupBy.append(",KID");
        where.append(" AND KID IN(SELECT KIOSKID FROM KIOSK WHERE STATE = '")
            .append(filters.get("state")).append("'")
            .append(" AND COUNTRY = '").append(filters.get("country")).append("'");
        if ("No district".equals(filters.get("district"))) {
          where.append(" AND (DISTRICT IS NULL OR DISTRICT = ''))");
        } else {
          where.append(" AND DISTRICT = '").append(filters.get("district")).append("')");
        }
        isEntity = true;
      } else if (filters.get("state") != null) {
        query.append("(SELECT DISTRICT FROM KIOSK WHERE KIOSKID = KID) DISTRICT,");
        groupBy.append("DISTRICT");
        where.append(" AND KID IN(SELECT KIOSKID FROM KIOSK WHERE STATE = '")
            .append(filters.get("state")).append("'")
            .append(" AND COUNTRY = '").append(filters.get("country")).append("')");
      } else {
        query.append("(SELECT STATE FROM KIOSK WHERE KIOSKID = KID) STATE,");
        groupBy.append("STATE");
        where.append(" AND KID IN (SELECT KIOSKID FROM KIOSK WHERE COUNTRY = '")
            .append(filters.get("country")).append("')");
      }
      if (filters.get("eTag") != null) {
        where.append(" AND KID IN(SELECT KIOSKID from KIOSK_TAGS WHERE ID =")
            .append("(SELECT ID FROM TAG WHERE NAME='").append(filters.get("eTag"))
            .append("' AND TYPE=").append(ITag.KIOSK_TAG).append(" limit 1)")
            .append(")");
      }
    }

    query.append("(SELECT NAME FROM MATERIAL WHERE MATERIALID = MID) MATERIAL,MID,");
    groupBy.append(",MATERIAL");

    if (!isEntity) {
      query.append("COUNT(1) COUNT,SUM(STK) TOTALQ FROM INVNTRY");
    } else {
      query.append("REORD,MAX,1 COUNT,STK TOTALQ,T FROM INVNTRY");
    }
    query.append(where);
    if (!isEntity) {
      query.append(groupBy);
    }
    return query.toString();
  }

  private String getEntityTempDataQuery(Map<String, String> filter) {
    Long dId = Long.parseLong(filter.get(Constants.PARAM_DOMAINID));
    return
        "SELECT KID, STAT, COUNT(1) COUNT FROM(SELECT KID,IF(ASF.STAT = 'tu', 'tu',"
            + "(SELECT IF(MAX(ABNSTATUS) = 2, 'th', IF(MAX(ABNSTATUS) = 1, 'tl', 'tn'))"
            + "FROM ASSETSTATUS AST WHERE AST.ASSETID = A.ID AND AST.TYPE = 1 AND "
            + "AST.STATUS = 3 AND AST.TS <= DATE_SUB(NOW(), INTERVAL 0 MINUTE))) STAT "
            + "FROM(SELECT A.ID, A.KID FROM ASSET A, ASSET_DOMAINS AD "
            + "WHERE A.TYPE IN (2 , 3, 5, 6, 7)AND A.KID = " + Long.parseLong(
            filter.get(Constants.ENTITY)) + " AND EXISTS "
            + "(SELECT 1 FROM ASSETRELATION R WHERE A.ID = R.ASSETID AND R.TYPE = 2) "
            + "AND 1 = (SELECT IFNULL(0 = (SELECT STATUS FROM ASSETSTATUS S WHERE TYPE = 7 AND "
            + "S.ASSETID = A.ID), 1))AND A.ID = AD.ID_OID AND AD.DOMAIN_ID = " + dId + ") A "
            + "LEFT JOIN (SELECT ASSETID, IF(MIN(STATUS) = 0, 'tk', 'tu') STAT "
            + "FROM ASSETSTATUS ASI, ASSET_DOMAINS AD WHERE ASI.TYPE = 3 AND ASI.ASSETID = AD.ID_OID "
            + "AND AD.DOMAIN_ID = " + dId + " AND TS <= DATE_SUB(NOW(), INTERVAL 0 MINUTE) "
            + "GROUP BY ASI.ASSETID) ASF ON A.ID = ASF.ASSETID) T GROUP BY T.STAT , T.KID";
  }

  private String getEntityInvDataQuery(Map<String, String> filter) {
    StringBuilder
        sb =
        new StringBuilder(
            "SELECT TY TYPE, COUNT(1) AS COUNT FROM INVNTRYEVNTLOG WHERE KID = " + Long.parseLong(
                filter.get(Constants.ENTITY)));
    if (filter.get(Constants.MATERIAL_TAG) != null) {
      sb.append(
          " AND MID IN (SELECT MATERIALID FROM MATERIAL_TAGS WHERE ID IN (SELECT ID FROM TAG WHERE NAME IN (")
          .
              append(String.valueOf(filter.get(Constants.MATERIAL_TAG))).append(")))");
    }
    sb.append(" AND ED IS NULL GROUP BY TY");
    return sb.toString();
  }

  public Integer getInvTotalCount(Map<String, String> filter) throws SQLException{
    PersistenceManager pm = PMF.getReadOnlyPM().getPersistenceManager();
    StringBuilder
        sb =
        new StringBuilder("SELECT COUNT(1) AS COUNT FROM INVNTRY WHERE KID = " + Long.parseLong(
            filter.get(Constants.ENTITY)));
    if (filter.get(Constants.MATERIAL_TAG) != null) {
      sb.append(
          " AND MID IN (SELECT MATERIALID FROM MATERIAL_TAGS WHERE ID IN (SELECT ID FROM TAG WHERE NAME IN (")
          .append(String.valueOf(filter.get(Constants.MATERIAL_TAG))).append(")))");
    }
    Query query = pm.newQuery("javax.jdo.query.SQL",sb.toString());
    query.setUnique(true);
    Object object = query.execute();
    return ((Long) object).intValue();
  }

  public String getAssetStatusDashboardQuery(Long domainId, Map<String, String> filters) {
    AssetsDashboardQueryGenerator dashboardQueryGenerator = new AssetsDashboardQueryGenerator();

    if (filters != null) {
      dashboardQueryGenerator.withDomainId(domainId)
          .withIncludeEntityTags(filters.get("eTag"))
          .withExcludeEntityTags(filters.get("eeTag"))
          .withAssetTypes(filters.get("type"))
          .withCountry(filters.get("country"))
          .withState(filters.get("state"))
          .withDistrict(filters.get("district"));
      if (StringUtils.isNotEmpty(filters.get("status")) && StringUtils
          .isNotEmpty(filters.get("period"))) {
        dashboardQueryGenerator.withWorkingStatus(Integer.parseInt(filters.get("status")))
            .withPeriod(Integer.parseInt(filters.get("period")));
      }
    }
    return dashboardQueryGenerator.generate();
  }
}
