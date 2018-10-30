/*
 * Copyright © 2018 Logistimo.
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

package com.logistimo.orders.service.impl;

import com.logistimo.constants.CharacterConstants;
import com.logistimo.constants.Constants;
import com.logistimo.constants.QueryConstants;
import com.logistimo.dao.JDOUtils;
import com.logistimo.entities.entity.IKiosk;
import com.logistimo.entities.service.EntitiesService;
import com.logistimo.inventory.entity.IInvAllocation;
import com.logistimo.inventory.entity.IInvntryBatch;
import com.logistimo.inventory.service.InventoryManagementService;
import com.logistimo.logger.XLog;
import com.logistimo.materials.entity.IMaterial;
import com.logistimo.materials.service.MaterialCatalogService;
import com.logistimo.models.orders.DiscrepancyModel;
import com.logistimo.orders.entity.IDemandItem;
import com.logistimo.orders.entity.IOrder;
import com.logistimo.orders.service.IDemandService;
import com.logistimo.orders.service.OrderManagementService;
import com.logistimo.pagination.PageParams;
import com.logistimo.pagination.QueryParams;
import com.logistimo.pagination.Results;
import com.logistimo.proto.JsonTagsZ;
import com.logistimo.services.ServiceException;
import com.logistimo.services.impl.PMF;
import com.logistimo.shipments.service.impl.ShipmentService;
import com.logistimo.tags.dao.ITagDao;
import com.logistimo.tags.entity.ITag;
import com.logistimo.utils.BigUtil;
import com.logistimo.utils.LocalDateUtil;
import com.logistimo.utils.StringUtil;
import com.sun.rowset.CachedRowSetImpl;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.jdo.PersistenceManager;
import javax.jdo.Query;
import javax.jdo.datastore.JDOConnection;
import javax.sql.rowset.CachedRowSet;

/**
 * Created by smriti on 9/30/16.
 */
@Service
public class DemandService implements IDemandService {
  private static final XLog xLogger = XLog.getLog(DemandService.class);
  private ITagDao tagDao;
  private InventoryManagementService inventoryManagementService;
  private EntitiesService entitiesService;
  private MaterialCatalogService materialCatalogService;
  private OrderManagementService orderManagementService;
  private ShipmentService shipmentService;

  @Autowired
  public void setTagDao(ITagDao tagDao) {
    this.tagDao = tagDao;
  }

  @Autowired
  public void setInventoryManagementService(
      InventoryManagementService inventoryManagementService) {
    this.inventoryManagementService = inventoryManagementService;
  }

  @Autowired
  public void setEntitiesService(EntitiesService entitiesService) {
    this.entitiesService = entitiesService;
  }

  @Autowired
  public void setMaterialCatalogService(MaterialCatalogService materialCatalogService) {
    this.materialCatalogService = materialCatalogService;
  }

  @Autowired
  public void setOrderManagementService(OrderManagementService orderManagementService) {
    this.orderManagementService = orderManagementService;
  }

  @Autowired
  public void setShipmentService(ShipmentService shipmentService) {
    this.shipmentService = shipmentService;
  }

  @Override
  public Results getDemandItems(Long domainId, Long kioskId, Long mId, String eTag, String mTag,
                                Boolean excludeTransfer, Boolean showBackOrder,
                                String orderType, Integer offset, Integer size) {
    StringBuilder
        query =
        new StringBuilder("SELECT IFNULL(D.LKID,''), IFNULL(D.Q,''), IFNULL(D.SQ,'')," +
            "IFNULL((SELECT NAME FROM MATERIAL WHERE D.MID = MATERIALID),'') MNAME," +
            "IFNULL(I.STK,''),IFNULL(I.ATPSTK,''),IFNULL(I.TSTK,''), IFNULL(I.REORD,''), IFNULL(I.MAX,''),"
            +
            "IFNULL(D.MID,''), IFNULL(D.OID,'') FROM (SELECT MID, SUM(Q) Q, SUM(SQ) SQ, GROUP_CONCAT(OID) OID,");
    if (IOrder.TYPE_SALE.equals(orderType)) {
      query.append("(SELECT SKID FROM `ORDER` WHERE ID = OID) LKID");
    } else {
      query.append("(SELECT KID FROM `ORDER` WHERE ID = OID) LKID");
    }
    query.append(" FROM DEMANDITEM WHERE OID IN(SELECT ID FROM `ORDER` WHERE ");
    List<String> parameters = new ArrayList<>();

    if (IOrder.TYPE_SALE.equals(orderType)) {
      query.append("SKID IN(");
    } else if (IOrder.TYPE_PURCHASE.equals(orderType)) {
      query.append("KID IN(");
    }
    query.append("SELECT KIOSKID FROM KIOSK WHERE KIOSKID ");
    if (kioskId != null) {
      query.append("=? ");
      parameters.add(String.valueOf(kioskId));
    } else {
      query.append("IN (SELECT KIOSKID_OID FROM KIOSK_DOMAINS WHERE DOMAIN_ID =?)");
      parameters.add(String.valueOf(domainId));
    }
    if (eTag != null && !eTag.isEmpty()) {
      query.append(
          " AND KIOSKID IN (SELECT KIOSKID FROM KIOSK_TAGS WHERE ID IN(SELECT ID FROM TAG WHERE NAME=?))");
      parameters.add(eTag);
    }
    query.append("ORDER BY NAME").append(CharacterConstants.C_BRACKET);
    if (showBackOrder) {
      query.append("AND ST = ?");
      parameters.add(IOrder.BACKORDERED);
    } else {
      query.append("AND (ST = ? OR ST = ? OR ST = ?)");
      parameters.add(IOrder.PENDING);
      parameters.add(IOrder.CONFIRMED);
      parameters.add(IOrder.BACKORDERED);
    }
    if (excludeTransfer) {
      query.append(" AND OTY != ?");
      parameters.add(String.valueOf(IOrder.TRANSFER));
    }
    query.append(CharacterConstants.C_BRACKET);
    if (mId != null) {
      query.append("AND MID IN (SELECT MATERIALID FROM MATERIAL WHERE MATERIALID=?)");
      parameters.add(String.valueOf(mId));
    } else if (mTag != null) {
      query.append(
          "AND MID IN(SELECT MATERIALID FROM MATERIAL_TAGS WHERE ID IN (SELECT ID FROM TAG WHERE NAME = ?))");
      parameters.add(String.valueOf(mTag));
    }

    query.append(" GROUP BY LKID, MID LIMIT ")
        .append(offset)
        .append(CharacterConstants.COMMA)
        .append(size)
        .append(")D").append(CharacterConstants.SPACE)
        .append("LEFT JOIN INVNTRY I ON D.LKID = I.KID AND D.MID = I.MID");
    try {
      return new Results(getResults(query, parameters), "", -1, offset);
    } catch (Exception e) {
      xLogger
          .warn("Error while fetching demand item for domain: {0}, entity: {1}", domainId, kioskId,
              e);
    }
    return null;

  }

  private List getResults(StringBuilder query, List<String> parameters) throws SQLException {
    PersistenceManager pm = PMF.get().getPersistenceManager();
    JDOConnection conn = null;
    PreparedStatement statement = null;
    CachedRowSet rowSet = null;
    try {
      conn = pm.getDataStoreConnection();
      java.sql.Connection sqlConn = (java.sql.Connection) conn;
      rowSet = new CachedRowSetImpl();
      statement = sqlConn.prepareStatement(query.toString());
      int i = 1;
      for (String p : parameters) {
        statement.setString(i++, p);
      }
      rowSet.populate(statement.executeQuery());
      List res = new ArrayList();
      while (rowSet.next()) {
        Object[] o = new Object[rowSet.getMetaData().getColumnCount()];
        for (int j = 1; j <= o.length; j++) {
          o[j - 1] = rowSet.getObject(j);
        }
        res.add(o);
      }
      return res;
    } finally {

      try {
        if (rowSet != null) {
          rowSet.close();
        }
      } catch (Exception ignored) {
        xLogger.warn("Exception while closing rowSet", ignored);
      }

      try {
        if (statement != null) {
          statement.close();
        }
      } catch (Exception ignored) {
        xLogger.warn("Exception while closing statement", ignored);
      }

      try {
        if (conn != null) {
          conn.close();
        }
      } catch (Exception ignored) {
        xLogger.warn("Exception while closing connection", ignored);
      }
      pm.close();
    }
  }

  public List<IDemandItem> getDemandItems(Long orderId) {
    return getDemandItems(orderId, null);
  }

  /**
   * Get all demand items by order Id
   *
   * @param orderId Order Id
   * @return -
   */
  @Override
  public List<IDemandItem> getDemandItems(Long orderId, PersistenceManager pm) {
    PersistenceManager localPM = pm;
    boolean useLocalPM = false;
    if (localPM == null) {
      localPM = PMF.get().getPersistenceManager();
      useLocalPM = true;
    }
    Query q = null;
    try {
      q = localPM.newQuery("javax.jdo.query.SQL", "SELECT * FROM DEMANDITEM WHERE OID=?");
      q.setClass(JDOUtils.getImplClass(IDemandItem.class));
      List<IDemandItem> items = (List<IDemandItem>) q.executeWithArray(orderId);
      if (items != null) {
        items = (List<IDemandItem>) localPM.detachCopyAll(items);
      }
      return items;
    } catch (Exception e) {
      xLogger.severe("Error while fetching demand items for order {0}", orderId, e);
    } finally {
      if (q != null) {
        q.closeAll();
      }
      if (useLocalPM) {
        localPM.close();
      }
    }
    return null;
  }

  @Override
  public void clearAllocations(Long kioskId, Long materialId, Long orderId, Boolean excludeTransfer,
                               Boolean backOrder) throws ServiceException {
    PersistenceManager pm = PMF.get().getPersistenceManager();
    try {
      List<Long> oId = new ArrayList<>();
      StringBuilder query = new StringBuilder();
      List<String> parameters = new ArrayList<>();
      if (orderId == null) {
        query.append("SELECT ID FROM `ORDER` WHERE SKID=?");
        parameters.add(String.valueOf(kioskId));
        if (excludeTransfer) {
          query.append(" AND OTY=?");
          parameters.add(String.valueOf(IOrder.NONTRANSFER)); // todo: need to use constant
        }
        if (backOrder) {
          query.append(" AND ST = ?");
          parameters.add(IOrder.BACKORDERED);
        }
        Query q = pm.newQuery("javax.jdo.query.SQL", query.toString());
        oId = (List<Long>) q.executeWithArray(parameters.toArray());
      } else {
        oId.add(orderId);
      }
      for (Long id : oId) {
        String tag = IInvAllocation.Type.ORDER + CharacterConstants.COLON + id;
        inventoryManagementService.clearAllocationByTag(kioskId, materialId, tag);
      }
    } catch (ServiceException e) {
      throw e;
    } catch (Exception e) {
      xLogger
          .warn("Error while clearing allocations for kiosk {0}, material {1}", kioskId, materialId,
              e);
      throw new ServiceException(
          "Error while clearing allocations for kiosk " + kioskId + " material " + materialId, e);
    } finally {
      pm.close();
    }
  }

  @Override
  public Results getDemandDetails(Long kioskId, Long materialId, Boolean excludeTransfer,
                                  Boolean showbackOrder,
                                  String orderType, boolean includeShipped)
      throws ServiceException {
    String kId = IOrder.TYPE_SALE.equals(orderType) ? "SKID" : "KID";
    List<String> parameters = new ArrayList<>();
    //todo: Last empty string has to be removed and appropriate changes have to be made in Demand Builder.
    StringBuilder
        query =
        new StringBuilder("SELECT IFNULL(D.KID,''),IFNULL(D.Q,''),IFNULL(D.SQ,'')," +
            "IFNULL((SELECT NAME FROM MATERIAL WHERE MATERIALID = ?),'') MNAME,IFNULL(I.STK,'')," +
            "IFNULL(I.ATPSTK,''),IFNULL(I.TSTK,''),IFNULL(I.REORD,''),IFNULL(I.MAX,''), " +
            "IFNULL(D.OID,''),IFNULL(D.OTY,''),IFNULL(D.ST, ''),IFNULL(D.FQ, ''), '' " +
            "FROM (SELECT OID,MID,Q,SQ,FQ, (SELECT OTY FROM `ORDER` WHERE ID = OID) OTY, (SELECT ST FROM `ORDER` WHERE ID = OID) ST, "
            +
            "'',");
    parameters.add(String.valueOf(materialId));
    if (IOrder.TYPE_SALE.equals(orderType)) {
      query.append("(SELECT KID FROM `ORDER` WHERE ID = OID) KID");
    } else {
      query.append("(SELECT SKID FROM `ORDER` WHERE ID = OID) KID");
    }
    query.append(" FROM DEMANDITEM WHERE OID IN(SELECT ID FROM `ORDER` WHERE ").append(kId)
        .append("=?");
    parameters.add(String.valueOf(kioskId));
    if (showbackOrder) {
      query.append(" AND ST = ?");
      parameters.add(IOrder.BACKORDERED);
    } else {
      query.append(" AND ST IN (?,?,?");
      parameters.add(IOrder.PENDING);
      parameters.add(IOrder.CONFIRMED);
      parameters.add(IOrder.BACKORDERED);
      if (includeShipped) {
        query.append(",?)");
        parameters.add(IOrder.COMPLETED);
      } else {
        query.append(")");
      }
    }
    if (excludeTransfer) {
      query.append(" AND OTY != ?");
      parameters.add(String.valueOf(IOrder.TRANSFER));
    }
    query.append(CharacterConstants.C_BRACKET);
    query.append("AND MID = ?)D LEFT JOIN INVNTRY I ON D.KID = I.KID AND D.MID = I.MID");
    parameters.add(String.valueOf(materialId));
    try {
      return new Results(getResults(query, parameters), "", -1, 0);
    } catch (Exception e) {
      xLogger
          .warn("Error in getting demand details for kiosk {0} & material {1}", kioskId, materialId,
              e);
    }
    return null;
  }

  @Override
  public Results getDemandItemsWithDiscrepancies(Long domainId, String oType,
                                                 Boolean excludeTransfer, Long kioskId,
                                                 List<Long> kioskIds, Long materialId,
                                                 String kioskTag, String materialTag, Date from,
                                                 Date to, Long orderId, String discType,
                                                 PageParams pageParams) throws ServiceException {
    if (domainId == null) {
      throw new ServiceException("Domain ID is mandatory");
    }
    PersistenceManager pm = PMF.get().getPersistenceManager();
    Query cntQuery = null;
    Results res = null;
    QueryParams qp;
    String limitStr = null;
    if (pageParams != null) {
      limitStr =
          " LIMIT " + pageParams.getOffset() + CharacterConstants.COMMA + pageParams.getSize();
    }
    try {
      qp =
          getQueryParams(domainId, oType, excludeTransfer, kioskId, kioskIds, materialId, kioskTag,
              materialTag, from, to, orderId, discType, pageParams);
      qp.query += limitStr;
    } catch (ServiceException se) {
      xLogger.warn("Domain ID is mandatory");
      return res;
    }
    Query q = pm.newQuery("javax.jdo.query.SQL", qp.query);
    final String orderBy = " ORDER BY OSCT DESC";

    List demandItems;
    List discrepancies;
    try {
      demandItems = (List) q.executeWithArray(qp.listParams.toArray());
      discrepancies = getDiscrepancyModels(demandItems);
      int startIndex = qp.query.indexOf("SELECT ", 0);
      int endIndex = qp.query.indexOf(" FROM DEMANDITEM", 0);
      String subString = qp.query.substring(startIndex + 7, endIndex);
      String
          cntQueryStr =
          qp.query.replace(subString, QueryConstants.ROW_COUNT)
              .replace(orderBy, CharacterConstants.EMPTY);
      if (limitStr != null) {
        cntQueryStr = cntQueryStr.replace(limitStr, CharacterConstants.EMPTY);
      }
      cntQuery = pm.newQuery("javax.jdo.query.SQL", cntQueryStr);
      cntQuery.setUnique(true);
      Long count = (Long) cntQuery.executeWithArray(qp.listParams.toArray());
      res =
          new Results(discrepancies, null, count.intValue(),
              pageParams == null ? 0 : pageParams.getOffset());
    } catch (Exception e) {
      xLogger.severe("Failed to get discrepancy report data ", e);
    } finally {
      if (q != null) {
        try {
          q.closeAll();
        } catch (Exception ignored) {
          xLogger.warn("Exception while closing query", ignored);
        }
      }
      if (cntQuery != null) {
        try {
          cntQuery.closeAll();
        } catch (Exception ignored) {
          xLogger.warn("Exception while closing query", ignored);
        }
      }
      pm.close();
    }
    return res;
  }

  @Override
  public QueryParams getQueryParams(Long domainId, String oType, Boolean excludeTransfer,
                                              Long kioskId, List<Long> kioskIds, Long materialId,
                                              String kioskTag, String materialTag, Date from,
                                              Date to, Long orderId, String discType,
                                              PageParams pageParams) throws ServiceException {
    if (domainId == null) {
      throw new ServiceException("Domain ID is mandatory");
    }
    StringBuilder
        sqlQuery =
        new StringBuilder(
            "SELECT ID,KID,MID,OID,ROQ,OQ,RSN,SQ,SDRSN,FQ,SDID,ST, (SELECT IF(EXISTS(SELECT 1 FROM MATERIAL WHERE MATERIALID = D.MID AND BM=0)," +
                "(SELECT GROUP_CONCAT(CONCAT(SID, '||', FDRSN) SEPARATOR ';') FROM SHIPMENTITEM SI WHERE SID IN (SELECT ID FROM SHIPMENT WHERE ORDERID = OID) " +
                "AND SI.MID = D.MID), (SELECT GROUP_CONCAT(CONCAT(SI.SID, '||', SIB.BID, '||', SIB.FDRSN) SEPARATOR ';') " +
                "FROM SHIPMENTITEM SI, SHIPMENTITEMBATCH SIB WHERE SI.ID = SIB.SIID AND SI.SID IN (SELECT ID FROM SHIPMENT WHERE ORDERID = OID) AND SI.MID = D.MID))) FDREASONS, " +
                "(SELECT OX.STON from `ORDER` OX where OX.ID = OID) OSCT FROM DEMANDITEM D ");
    List<String> parameters = new ArrayList<>();
    if (orderId != null) {
      sqlQuery.append("WHERE OID = ?");
      parameters.add(String.valueOf(orderId));
    } else {
      StringBuilder orderQuery =
          buildQueryForOids(domainId, oType, excludeTransfer, kioskId, kioskIds, kioskTag,
              parameters);
        sqlQuery.append("WHERE OID IN (").append(orderQuery).append(")");
    }
    if (materialId != null) {
      sqlQuery.append(" AND MID = ?");
      parameters.add(String.valueOf(materialId));
    } else if (materialTag != null && !materialTag.isEmpty()) {
      sqlQuery.append(" AND MID IN (SELECT MATERIALID from MATERIAL_TAGS where ID = ?)");
      parameters.add(String.valueOf(tagDao.getTagFilter(materialTag, ITag.MATERIAL_TAG)));
    }
    SimpleDateFormat sdf = new SimpleDateFormat(Constants.DATETIME_CSV_FORMAT);
    if (from != null) {
      sqlQuery.append(" AND T >= TIMESTAMP(?)");
      parameters.add(sdf.format(from));
    }
    if (to != null) {
      to = LocalDateUtil.getOffsetDate(to, 1, java.util.Calendar.DAY_OF_MONTH);
      sqlQuery.append(" AND T < TIMESTAMP(?)");
      parameters.add(sdf.format(to));
    }
    if(StringUtils.isBlank(discType)) {
      discType = CharacterConstants.EMPTY;
    }
    switch (discType) {
      case IDemandItem.ORDERING_DISCREPANCY:
        sqlQuery.append(" AND (ROQ != -1 AND OQ != ROQ AND ST != 'cn')");
        break;
      case IDemandItem.SHIPPING_DISCREPANCY:
        sqlQuery.append(" AND (SQ != OQ AND (ST='cm' OR ST='fl'))");
        break;
      case IDemandItem.FULFILLMENT_DISCREPANCY:
        sqlQuery.append(" AND (FQ != SQ AND ST = 'fl')");
        break;
      default:
        // All discrepancies
        sqlQuery.append(
            " AND ((ROQ != -1 AND OQ != ROQ AND ST != 'cn') OR (SQ != OQ AND (ST ='cm' OR ST='fl')) OR (FQ != SQ AND ST = 'fl'))");
    }

    final String orderBy = " ORDER BY OSCT DESC";
    sqlQuery.append(orderBy);
    return new QueryParams(sqlQuery.toString(), parameters, QueryParams.QTYPE.SQL,
        IDemandItem.class);
  }

  private StringBuilder buildQueryForOids(Long domainId, String oType, Boolean excludeTransfer,
                                          Long kioskId, List<Long> kioskIds, String kioskTag,
                                          List<String> parameters) {
    StringBuilder orderQuery = new StringBuilder("SELECT ID FROM `ORDER` WHERE ");
    if (excludeTransfer) {
      orderQuery.append("OTY != ?");
      parameters.add(String.valueOf(IOrder.TRANSFER));
      orderQuery.append(" AND ");
    }
    // kioskIds can be present with out without kioskId
    if (!CollectionUtils.isEmpty(kioskIds)) {
      StringBuilder kioskIdQuery = new StringBuilder();
      List<String> kids = new ArrayList<>(kioskIds.size());
      for (Long id : kioskIds) {
        kioskIdQuery.append(CharacterConstants.QUESTION);
        kids.add(String.valueOf(id));
        kioskIdQuery.append(CharacterConstants.COMMA);
      }
      kioskIdQuery.setLength(kioskIdQuery.length() - 1);

      orderQuery.append("(KID IN (");
      orderQuery.append(kioskIdQuery);
      parameters.addAll(kids);

      orderQuery.append(") OR SKID IN (");
      orderQuery.append(kioskIdQuery);
      parameters.addAll(kids);

      orderQuery.append("))");
    } else {
      orderQuery.append("(KID IN (SELECT KIOSKID_OID FROM KIOSK_DOMAINS WHERE DOMAIN_ID = ?)");
      parameters.add(String.valueOf(domainId));
      orderQuery.append(" OR ");
      orderQuery.append("SKID IN (SELECT KIOSKID_OID FROM KIOSK_DOMAINS WHERE DOMAIN_ID = ?))");
      parameters.add(String.valueOf(domainId));

    }

    if (kioskId != null) {
      String kidParam = IOrder.TYPE_PURCHASE.equals(oType) ? "KID" : "SKID";
      orderQuery.append(" AND ").append(kidParam).append(" = ?");
      parameters.add(String.valueOf(kioskId));
    } else if (StringUtils.isNotBlank(kioskTag)) {
      orderQuery.append(
          " AND (KID IN (SELECT KIOSKID FROM KIOSK_TAGS WHERE ID IN(SELECT ID FROM TAG WHERE NAME=?))");
      parameters.add(kioskTag);
      orderQuery.append(" OR ");
      orderQuery.append(
          "SKID IN (SELECT KIOSKID FROM KIOSK_TAGS WHERE ID IN(SELECT ID FROM TAG WHERE NAME=?)))");
      parameters.add(kioskTag);
    }

    return orderQuery;
  }

  /**
   * Get list of discrepancy models from the list of query result objects
   *
   * @param objects List of objects returned by the query
   * @return -
   */
  public List<DiscrepancyModel> getDiscrepancyModels(List objects) throws ServiceException {
    List<DiscrepancyModel> modelItems = new ArrayList<>(objects.size());
    Iterator iterator = objects.iterator();
    while (iterator.hasNext()) {
      DiscrepancyModel model = new DiscrepancyModel();
      IKiosk c, v = null;
      IMaterial m;
      IOrder o;
      Object[] di = (Object[]) iterator.next();
      try {
        c = entitiesService.getKiosk((Long) di[1], false);
        m = materialCatalogService.getMaterial((Long) di[2]);
        o = orderManagementService.getOrder((Long) di[3]);
        if (o.getServicingKiosk() != null) {
          try {
            v = entitiesService.getKiosk(o.getServicingKiosk(), false);
          } catch (Exception e) {
            xLogger.warn(
                "Ignoring exception while getting discrepancy model for demand item {0} with vendor id {1}",
                di[0], o.getServicingKiosk());
          }
        }
        model.id = Long.parseLong(String.valueOf(di[0]));
        model.oId = (Long.parseLong(String.valueOf(di[3])));
        model.rId = o.getSalesReferenceID();
        model.oty = o.getOrderType();
        model.mId = (Long.parseLong(String.valueOf(di[2])));
        model.cmId = m.getCustomId();
        model.mnm = m.getName();
        model.oq =
            (di[5] != null ? new BigDecimal(String.valueOf(di[5]))
                : new BigDecimal(0)); // Original quantity
        model.odrsn = di[6] != null ? String.valueOf(di[6]) : null;
        model.roq = (di[4] != null ? new BigDecimal(String.valueOf(di[4])) : new BigDecimal(0));
        model.sq =
            (di[7] != null ? new BigDecimal(String.valueOf(di[7]))
                : new BigDecimal(0)); // Shipped quantity
        model.sdrsn = di[8] != null ? String.valueOf(di[8]) : null;
        model.sdid = (Long.parseLong(String.valueOf(di[10])));
        model.fq =
            (di[9] != null ? new BigDecimal(String.valueOf(di[9]))
                : new BigDecimal(0)); // Fulfilled quantity
        String fdRsnsStr = di[12] != null ? String.valueOf(di[12]) : null;
        String[] fdRsnsArray = StringUtil.getArray(fdRsnsStr, ";");
        if (fdRsnsArray != null) {
          model.fdrsns = (List) Arrays.asList(fdRsnsArray);
        }

        // String diSt = di[11] != null ? String.valueOf(di[11]) : null;
        model.st = o.getStatus();
        SimpleDateFormat sdf = new SimpleDateFormat(Constants.DATETIME_CSV_FORMAT);
        model.stt = di[13] != null ? sdf.parse(String.valueOf(di[13])) : null;
        model.oct = o.getCreatedOn();
        model.cId = (Long.parseLong(String.valueOf(di[1])));
        model.ccId = c.getCustomId();
        model.cnm = c.getName();
        model.vId = v != null ? v.getKioskId() : null;
        model.vnm = v != null ? v.getName() : null;
        model.cvId = v != null ? v.getCustomId() : null;
        model.discTypes = new ArrayList<>(1);
        if (BigUtil.notEquals(model.oq, model.roq) && model.roq.intValue() != -1
            && !IOrder.CANCELLED.equals(model.st)) {
          model.discTypes.add(IDemandItem.ORDERING_DISCREPANCY);
        }
        if (BigUtil.notEquals(model.sq, model.oq) && (IOrder.COMPLETED.equals(model.st) || IOrder.FULFILLED.equals(model.st))) {
          model.discTypes.add(IDemandItem.SHIPPING_DISCREPANCY);
        }
        if (BigUtil.notEquals(model.fq, model.sq) && (IOrder.FULFILLED.equals(model.st))) {
          model.discTypes.add(IDemandItem.FULFILLMENT_DISCREPANCY);
        }

        modelItems.add(model);
      } catch (Exception e) {
        xLogger.warn("Exception while getting Discrepancy model for demanditem id {0}, order: {1}:",
            di[0], di[7], e);
      }
    }
    return modelItems;
  }

  @Override
  public Map<String, Object> getDemandItemAsMap(Long id, String currency, Locale locale,
                                                String timezone, boolean forceIntegerQuantity) {
    Map<String, Object> map = new HashMap<>(1);
    PersistenceManager pm = PMF.get().getPersistenceManager();
    try {
      IDemandItem di = JDOUtils.getObjectById(IDemandItem.class, id, pm);
      map.put(JsonTagsZ.MATERIAL_ID, di.getMaterialId().toString());
      map.put(JsonTagsZ.QUANTITY, BigUtil.getFormattedValue(di.getQuantity()));
      map.put(JsonTagsZ.ORIGINAL_ORDERED_QUANTITY,
          BigUtil.getFormattedValue(di.getOriginalQuantity()));
      map.put(JsonTagsZ.RECOMMENDED_ORDER_QUANTITY,
          BigUtil.getFormattedValue(di.getRecommendedOrderQuantity()));

      IMaterial m = materialCatalogService.getMaterial(di.getMaterialId());
      String customMaterialId = m.getCustomId();
      if (customMaterialId != null && !customMaterialId.isEmpty()) {
        map.put(JsonTagsZ.CUSTOM_MATERIALID, customMaterialId);
      }

      map.put(JsonTagsZ.ALLOCATED_QUANTITY, BigUtil.getFormattedValue(
          getAllocatedQuantityForDemandItem(di.getIdAsString(), di.getOrderId(),
              di.getMaterialId())));
      List<Map<String, String>> batches = null;
      if (m.isBatchEnabled()) {
        batches =
            getBatchMetadataForDemandItem(di.getIdAsString(), di.getOrderId(), di.getMaterialId(),
                locale, timezone);
      }

      // Add batches if has inventory allocation by batches is present
      if (batches != null && !batches.isEmpty()) {
        map.put(JsonTagsZ.BATCHES, batches);
      }

      map.put(JsonTagsZ.FULFILLED_QUANTITY, BigUtil.getFormattedValue(di.getFulfilledQuantity()));
      if (di.getReason() != null) {
        map.put(JsonTagsZ.REASON, di.getReason()); // Will be deprecated
        map.put(JsonTagsZ.REASON_FOR_IGNORING_RECOMMENDED_QUANTITY, di.getReason());
      }

      if (di.getShippedDiscrepancyReason() != null) {
        map.put(JsonTagsZ.REASONS_FOR_EDITING_ORDER_QUANTITY, di.getShippedDiscrepancyReason());
      }

      if (di.getTimestamp() != null) {
        map.put(JsonTagsZ.TIMESTAMP, LocalDateUtil.format(di.getTimestamp(), locale, timezone));
      }
      if (di.getUnitPrice() != null) {
        map.put(JsonTagsZ.RETAILER_PRICE, di.getUnitPrice().toString());
      }
      if (di.getCurrency() != null && !di.getCurrency().equals(currency)) {
        map.put(JsonTagsZ.CURRENCY, di.getCurrency());
      }
      if (di.getStatus() != null) {
        map.put(JsonTagsZ.ORDER_STATUS, di.getStatus());
      }
      if (di.getMessage() != null) {
        map.put(JsonTagsZ.MESSAGE, di.getMessage());
      }
      if (di.getDiscount() != null && BigUtil.notEqualsZero(di.getDiscount())) {
        map.put(JsonTagsZ.DISCOUNT, BigUtil.getFormattedValue(di.getDiscount()));
      }
    } catch (Exception e) {
      xLogger.severe("Exception while getting demand item with id {0} as map", id);
    } finally {
      if (!pm.isClosed()) {
        pm.close();
      }
    }
    return map;
  }

  @Override
  public BigDecimal getAllocatedQuantityForDemandItem(String id, Long oId, Long mId) {
    BigDecimal alq = new BigDecimal(0);
    try {
      IOrder o = orderManagementService.getOrder(oId);
      Long lkId = o.getServicingKiosk();
      List<IInvAllocation>
          iAllocs =
          inventoryManagementService.getAllocationsByTypeId(lkId, mId, IInvAllocation.Type.ORDER, oId.toString());
      if (iAllocs != null && !iAllocs.isEmpty()) {
        for (IInvAllocation iAlloc : iAllocs) {
          alq = alq.add(iAlloc.getQuantity());
        }
      }
    } catch (Exception e) {
      xLogger.warn("Exception while getting allocated quantity for the demand item {0}, order: {1}",
          id, oId, e);
    }
    return alq;
  }

  @Override
  public String getMaterialStatusForDemandItem(String id, Long oId, Long mId) {

    try {
      IOrder o = orderManagementService.getOrder(oId);
      Long lkId = o.getServicingKiosk();
      List<IInvAllocation>
          iAllocs =
          inventoryManagementService.getAllocationsByTypeId(lkId, mId, IInvAllocation.Type.ORDER, oId.toString());
      if (iAllocs != null && !iAllocs.isEmpty()) {
        return iAllocs.get(0).getMaterialStatus();

      }
    } catch (Exception e) {
      xLogger
          .warn("Exception while getting material status for the demand item {0}, order: {1}", id,
              oId, e);
    }
    return null;
  }

  private List<Map<String, String>> getBatchMetadataForDemandItem(String id, Long oId, Long mId,
                                                                  Locale locale, String timezone) {
    List<Map<String, String>> batches = new ArrayList();
    try {
      IOrder o = orderManagementService.getOrder(oId);
      Long lkId = o.getServicingKiosk();
      List<IInvAllocation>
          iAllocs =
          inventoryManagementService.getAllocationsByTypeId(lkId, mId, IInvAllocation.Type.ORDER, oId.toString());

      if (iAllocs != null && !iAllocs.isEmpty()) {
        for (IInvAllocation iAlloc : iAllocs) {
          if (iAlloc.getBatchId() != null && !iAlloc.getBatchId().isEmpty()) {
            Map<String, String> bMap = new HashMap();
            IInvntryBatch b = inventoryManagementService.getInventoryBatch(lkId, mId, iAlloc.getBatchId(), null);
            bMap.put(JsonTagsZ.BATCH_ID, b.getBatchId());
            if (b.getBatchExpiry() != null) {
              bMap.put(JsonTagsZ.BATCH_EXPIRY,
                  LocalDateUtil.formatCustom(b.getBatchExpiry(), Constants.DATE_FORMAT, timezone));
            }
            if (b.getBatchManufacturer() != null && !b.getBatchManufacturer().isEmpty()) {
              bMap.put(JsonTagsZ.BATCH_MANUFACTUER_NAME, b.getBatchManufacturer());
            }
            if (b.getBatchManufacturedDate() != null) {
              bMap.put(JsonTagsZ.BATCH_MANUFACTURED_DATE, LocalDateUtil
                  .formatCustom(b.getBatchManufacturedDate(), Constants.DATE_FORMAT, timezone));
            }
            if (b.getTimestamp() != null) {
              bMap.put(JsonTagsZ.TIMESTAMP,
                  LocalDateUtil.format(b.getTimestamp(), locale, timezone));
            }
            bMap.put(JsonTagsZ.ALLOCATED_QUANTITY, BigUtil.getFormattedValue(iAlloc.getQuantity()));
            batches.add(bMap);
          }
        }
      }
    } catch (Exception e) {
      xLogger.warn("Exception while getting inventory allocation for the demand item {0}", id, e);
    }
    return batches;
  }

  /**
   * Get the vector of demand items (either standalone or can be associated with an order)
   */
  @SuppressWarnings("rawtypes")
  @Override
  public List<Map> getDemandItems(Collection<? extends IDemandItem> items, String currency,
                                         Locale locale, String timezone,
                                         boolean forceIntegerQuantity) {
    if (items == null || items.size() == 0) {
      return Collections.emptyList();
    }
    List<Map> materialsList = new ArrayList<>(1);
    Iterator<IDemandItem> it = (Iterator<IDemandItem>) items.iterator();
    while (it.hasNext()) {
      IDemandItem item = it.next();
      // Get the default material map
      Map<String, Object> itemMap = item.toMap(currency, locale, timezone, forceIntegerQuantity);
      // Add to vector
      materialsList.add(itemMap);
    }

    return materialsList;
  }
}
