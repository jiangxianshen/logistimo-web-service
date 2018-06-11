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

/**
 *
 */
package com.logistimo.api.servlets;

import com.logistimo.AppFactory;
import com.logistimo.auth.SecurityConstants;
import com.logistimo.auth.utils.SessionMgr;
import com.logistimo.config.entity.IConfig;
import com.logistimo.config.service.ConfigurationMgmtService;
import com.logistimo.config.service.impl.ConfigurationMgmtServiceImpl;
import com.logistimo.constants.Constants;
import com.logistimo.constants.QueryConstants;
import com.logistimo.context.StaticApplicationContext;
import com.logistimo.dao.JDOUtils;
import com.logistimo.domains.IMultiDomain;
import com.logistimo.domains.entity.IDomain;
import com.logistimo.domains.processor.DeleteProcessor;
import com.logistimo.domains.service.DomainsService;
import com.logistimo.domains.service.impl.DomainsServiceImpl;
import com.logistimo.entity.IBBoard;
import com.logistimo.events.entity.IEvent;
import com.logistimo.inventory.entity.IInvntry;
import com.logistimo.inventory.entity.IInvntryBatch;
import com.logistimo.inventory.entity.IInvntryEvntLog;
import com.logistimo.inventory.entity.IInvntryLog;
import com.logistimo.inventory.entity.ITransaction;
import com.logistimo.inventory.optimization.entity.IOptimizerLog;
import com.logistimo.inventory.pagination.processor.InventoryResetProcessor;
import com.logistimo.logger.XLog;
import com.logistimo.mnltransactions.entity.IMnlTransaction;
import com.logistimo.orders.entity.IDemandItem;
import com.logistimo.orders.entity.IDemandItemBatch;
import com.logistimo.orders.entity.IOrder;
import com.logistimo.pagination.PageParams;
import com.logistimo.pagination.PagedExec;
import com.logistimo.pagination.QueryParams;
import com.logistimo.services.impl.PMF;
import com.logistimo.services.taskqueue.ITaskService;
import com.logistimo.users.entity.IUserAccount;
import com.logistimo.users.service.UsersService;
import com.logistimo.users.service.impl.UsersServiceImpl;
import com.logistimo.utils.HttpUtil;
import com.logistimo.utils.LocalDateUtil;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.jdo.JDOObjectNotFoundException;
import javax.jdo.PersistenceManager;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


/**
 * Administrative tasks
 *
 * @author Arun
 */
public class AdminServlet extends HttpServlet {

  private static final XLog xLogger = XLog.getLog(AdminServlet.class);

  // Request parameters
  private static final String DOMAIN_PARAM = "domainid";

  // Actions
  private static final String ACTION_RESETTRANSACTIONS = "resettransactions";
  private static final String ACTION_DELETEENTITIES = "deleteentities";
  private static final String ACTION_DELETEENTITIESBYQUERY = "deleteentitiesbyquery";
  private static final String ACTION_DELETESESSIONS = "deletesessions";
  private static final String ACTION_GETSYSCONFIG = "getsysconfig";
  private static final String ACTION_UPDATESYSCONFIG = "updatesysconfig";

  // URLs
  private static final String URL_PROD = "https://logistimo-web.appspot.com";

  private static final long serialVersionUID = 1L;

  private static ITaskService taskService = AppFactory.get().getTaskService();

  // Add a given count to the counter
  private static void resetDomainTransactions(HttpServletRequest req) {
    xLogger.fine("Entered resetDomainTransactions");
    String domainIdStr = req.getParameter(DOMAIN_PARAM);
    boolean execute = req.getParameter("execute") != null;
    if (!execute) { // schedule...
      // Schedule task to delete
      Map<String, String> params = new HashMap<>();
      params.put("action", ACTION_RESETTRANSACTIONS);
      params.put(DOMAIN_PARAM, domainIdStr);
      params.put("execute", "true");
      // Schedule delete transactions job for this domain
      try {
        taskService
            .schedule(ITaskService.QUEUE_DEFAULT, "/task/admin", params, ITaskService.METHOD_POST);
      } catch (Exception e) {
        xLogger.severe("{0} when scheduling task: {1}", e.getClass().getName(), e.getMessage());
      }
      return;
    }
    // Get domain Id
    Long domainId = Long.valueOf(domainIdStr);
    // Reset all transactions and inventory for the domain
    resetDomainTransactions(domainId);
    // Reset the bulletin board, if any
    deleteEntitiesByDate(domainIdStr, JDOUtils.getImplClass(IBBoard.class).getName(), null, null,
        null, null);
    xLogger.fine("Exiting resetDomainTransactions");
  }

  // Delete all transactions (including orders in a given domain), and inventory for a given domain
  public static void resetDomainTransactions(Long domainId) {
    xLogger.fine("Entered resetDomainTransactions");
    if (domainId == null) {
      return;
    }
    // Get the parameters
    PageParams pageParams = new PageParams(null, PageParams.DEFAULT_SIZE);
    Map<String, Object> params = new HashMap<>();
    params.put("dIdParam", domainId);
    // Get the classes to be deleted
    List<String>
        kinds =
        Arrays.asList(JDOUtils.getImplClass(ITransaction.class).getName(),
            JDOUtils.getImplClass(IInvntryLog.class).getName(),
            JDOUtils.getImplClass(IInvntryEvntLog.class).getName(),
            JDOUtils.getImplClass(IInvntryBatch.class).getName(),
            JDOUtils.getImplClass(IOrder.class).getName(),
            JDOUtils.getImplClass(IDemandItem.class).getName(),
            JDOUtils.getImplClass(IDemandItemBatch.class).getName(),
            JDOUtils.getImplClass(IOptimizerLog.class).getName(),
            JDOUtils.getImplClass(IEvent.class).getName(),
            JDOUtils.getImplClass(IMnlTransaction.class).getName());
    // Delete raw data models
    for (String kind : kinds) {
      String query = QueryConstants.SELECT_FROM + kind + " WHERE sdId == dIdParam PARAMETERS Long dIdParam";
      if (kind.equals(JDOUtils.getImplClass(IOptimizerLog.class).getName())) {
        query = QueryConstants.SELECT_FROM + kind + " WHERE dId == dIdParam PARAMETERS Long dIdParam";
      }
      try {
        xLogger.info("Deleting {0}...", kind);
        PagedExec.exec(domainId, new QueryParams(query, params), pageParams,
            DeleteProcessor.class.getName(), null, null, 0, false, false);
      } catch (Exception e) {
        xLogger.severe("{0} when deleting {1} for domain {2}: {3}", e.getClass().getName(), kind,
            domainId, e.getMessage());
      }
    }

    // Reset inventory
    String
        query =
        QueryConstants.SELECT_FROM + JDOUtils.getImplClass(IInvntry.class).getName()
            + " WHERE sdId == dIdParam PARAMETERS Long dIdParam";
    try {
      xLogger.info("Resetting inventory...");
      PagedExec.exec(domainId, new QueryParams(query, params), pageParams,
          InventoryResetProcessor.class.getName(), null, null);
    } catch (Exception e) {
      xLogger.severe("{0} when resetting inventory for domain {1}: {2}", e.getClass().getName(),
          domainId, e.getMessage());
    }
    xLogger.fine("Exiting resetDomainTransactions");
  }

  // Reset bulletin board
  private static void deleteEntitiesByDate(HttpServletRequest req) {
    xLogger.fine("Entered deleteEntitiesByDate");
    String domainIdStr = req.getParameter(DOMAIN_PARAM);
    String entityClass = req.getParameter("entity");
    String startDateField = req.getParameter("startfield");
    String startDateStr = req.getParameter("start"); // format dd/MM/yyyy
    String endDateStr = req.getParameter("end"); // format dd/MM/yyyy - optional
    String orderBy = req.getParameter("orderby");
    if (orderBy == null || orderBy.isEmpty()) {
      orderBy = "desc";
    }
    deleteEntitiesByDate(domainIdStr, entityClass, startDateField, startDateStr, endDateStr,
        orderBy);
  }

  // Delete entities based on query and its params.
  private static void deleteEntitiesByQuery(HttpServletRequest req) {
    xLogger.fine("Entered deleteEntitiesByQuery");
    String domainIdStr = req.getParameter(DOMAIN_PARAM);
    String queryStr = req.getParameter("q");
    String paramsCSV = req.getParameter("params"); // name|type|value,name|type|value,...
    if (domainIdStr == null || domainIdStr.isEmpty() || queryStr == null || queryStr.isEmpty()) {
      xLogger.severe("domainId and query are mandatory. One or both of them not specified");
      return;
    }
    Long domainId = Long.valueOf(domainIdStr);
    try {
      queryStr = URLDecoder.decode(queryStr, "UTF-8");
    } catch (UnsupportedEncodingException e) {
      xLogger.warn("Error while deleting the entities based on query and its params", e);
      return;
    }
    xLogger.info("queryStr: {0}, paramsCSV: {1}", queryStr, paramsCSV);
    // Get the params., if specified
    HashMap<String, Object> params;
    if (paramsCSV != null && !paramsCSV.isEmpty()) {
      params = new HashMap<>();
      String[] paramsArray = paramsCSV.split(",");
      for (String aParamsArray : paramsArray) {
        String[] paramValue = aParamsArray.split(":");
        if (paramValue.length < 3) {
          xLogger.severe("Parameter value is of invalid format (should be name|type|value): {0}",
              aParamsArray);
          return;
        }
        String name = paramValue[0];
        String type = paramValue[1];
        String value = paramValue[2];
        xLogger.info("params {0}: name = {1}, type = {2}, value = {3}", aParamsArray, name, type,
            value);
        Object o;
        if ("String".equals(type)) {
          o = value;
        } else if ("Long".equals(type)) {
          o = Long.valueOf(value);
        } else if ("Integer".equals(type)) {
          o = Integer.valueOf(value);
        } else if ("Boolean".equals(type)) {
          o = Boolean.valueOf(value);
        } else if ("Float".equals(type)) {
          o = Float.valueOf(value);
        } else if ("Double".equals(type)) {
          o = Double.valueOf(value);
        } else {
          xLogger.severe("Unknown type {0} in param-value {1}", type, aParamsArray);
          return;
        }
        params.put(name, o);
      }
      // Execute deletion
      try {
        PagedExec.exec(domainId, new QueryParams(queryStr, params),
            new PageParams(null, PageParams.DEFAULT_SIZE), DeleteProcessor.class.getName(), "true",
            null);
      } catch (Exception e) {
        xLogger
            .severe("{0} when doing paged exec.: {1}", e.getClass().getName(), e.getMessage(), e);
      }
    }
    xLogger.fine("Exiting deleteEntitiesByQuery");
  }

  private static void deleteEntitiesByDate(String domainIdStr, String entityClass,
                                           String startDateField, String startDateStr,
                                           String endDateStr, String orderBy) {
    xLogger.info(
        "Deleting entities by date: domainId = {0}, entityClass = {1}, startDateField = {2}, startDateStr = {3}",
        domainIdStr, entityClass, startDateField, startDateStr);
    Long domainId = null;
    if (domainIdStr != null && !domainIdStr.isEmpty()) {
      domainId = Long.valueOf(domainIdStr);
    }
    if (domainId == null) {
      xLogger.severe("Invalid domain ID");
      return;
    }
    if (entityClass == null || entityClass.isEmpty()) {
      xLogger.severe("Invalid entity class");
      return;
    }
    String queryStr = QueryConstants.SELECT_FROM + entityClass;
    try {
      Class clazz = Class.forName(entityClass);
      if (IMultiDomain.class.isAssignableFrom(clazz)) {
        queryStr += " WHERE dId.contains(dIdParam)";
      } else {
        queryStr += " WHERE dId == dIdParam";
      }
    } catch (ClassNotFoundException e) {
      xLogger.severe("Invalid entity class: {0}", entityClass, e);
      return;
    }

    String paramsStr = " PARAMETERS Long dIdParam";
    Map<String, Object> params = new HashMap<>();
    params.put("dIdParam", domainId);
    if (startDateStr != null && !startDateStr.isEmpty()) {
      if (startDateField == null || startDateField.isEmpty()) {
        xLogger.severe("No startfield specified");
        return;
      }
      SimpleDateFormat sdf = new SimpleDateFormat(Constants.DATE_FORMAT);
      try {
        Date start = sdf.parse(startDateStr);
        Calendar cal = GregorianCalendar.getInstance();
        cal.setTime(start);
        LocalDateUtil.resetTimeFields(cal);
        queryStr += " && " + startDateField + " > startParam";
        // paramsStr += ", Date startParam import java.util.Date";
        paramsStr += ", Date startParam";
        params.put("startParam",
            LocalDateUtil.getOffsetDate(cal.getTime(), -1, Calendar.MILLISECOND));
        // If endDateStr is present, add it to the queryStr and paramsStr and the params map.
        if (endDateStr != null && !endDateStr.isEmpty()) {
          Date end = sdf.parse(endDateStr);
          cal.setTime(end);
          LocalDateUtil.resetTimeFields(cal);
          queryStr += " && " + startDateField + " < endParam";
          paramsStr += ", Date endParam";
          params.put("endParam", cal.getTime());
        }
        paramsStr += "  import java.util.Date;";
        xLogger.info("paramsStr: {0}", paramsStr);
      } catch (ParseException e) {
        xLogger.warn("Error while deleting the entities by date", e);
        return;
      }
    }
    queryStr += paramsStr;
    if (startDateField != null && !startDateField.isEmpty() && orderBy != null && !orderBy
        .isEmpty()) {
      queryStr += " ORDER BY " + startDateField + " " + orderBy;
    }
    xLogger.info("queryStr: {0}", queryStr);
    QueryParams qp = new QueryParams(queryStr, params);
    try {
      PagedExec.exec(domainId, qp, new PageParams(null, PageParams.DEFAULT_SIZE),
          DeleteProcessor.class.getName(), null, null);
    } catch (Exception e) {
      xLogger.severe("{0} when doing paged-exec to delete BBoard entries in domain {1}: {2}",
          e.getClass().getName(), domainId, e.getMessage(), e);
    }
    xLogger.fine("Exiting deleteEntitiesByDate");
  }

  // Delete expired sessions from data store
  public static void deleteSessions() {
    xLogger.fine("Entered deleteSessions");
    SessionMgr.deleteSessions();
    xLogger.fine("Exiting deleteSessions");
  }

  @Override
  public void doGet(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    xLogger.fine("Entered doGet");
    String action = req.getParameter("action");
    if (ACTION_RESETTRANSACTIONS.equals(action)) {
      resetDomainTransactions(req);
    } else if (ACTION_DELETEENTITIES.equals(action)) {
      deleteEntitiesByDate(req);
    } else if (ACTION_DELETESESSIONS.equals(action)) {
      deleteSessions();
    } else if (ACTION_UPDATESYSCONFIG.equals(action)) {
      updateSysConfig(req, resp);
    } else if (ACTION_DELETEENTITIESBYQUERY.equals(action)) {
      deleteEntitiesByQuery(req);
    } else {
      xLogger.severe("Invalid action: {0}", action);
    }
    xLogger.fine("Existing doGet");
  }

  @Override
  public void doPost(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    doGet(req, resp);
  }

  // Update the system configuration, given a key, or with our standard set of config keys
  private void updateSysConfig(HttpServletRequest req, HttpServletResponse resp) {
    xLogger.fine("Entered updateSysConfig");
    List<String> keys = new ArrayList<>();
    String key = req.getParameter("key");
    boolean hasKey = key != null && !key.isEmpty();
    String host = req.getParameter("host");
    String userId = req.getParameter("userid");
    String password = req.getParameter("password");
    String email = req.getParameter("email");
    if (userId == null || userId.isEmpty() || password == null || password.isEmpty()) {
      xLogger.severe("Invalid user name or password");
      PrintWriter pw;
      try {
        pw = resp.getWriter();
        pw.write("Invalid name or password");
        pw.close();
      } catch (IOException e) {
        xLogger.warn("Invalid name or password", e);
      }
      return;
    }
    if (email == null || email.isEmpty()) {
      xLogger.severe("Invalid email. Please provide an email.");
      PrintWriter pw;
      try {
        pw = resp.getWriter();
        pw.write("Invalid email. Please provide an email");
        pw.close();
      } catch (IOException e) {
        xLogger.warn("Error while getting email", e);
      }
      return;
    }
    // Execute and update config.
    if (host == null || host.isEmpty()) {
      host = URL_PROD;
    }
    if (hasKey) {
      keys.add(key);
    } else { // Add the basic keys required
      keys.add(IConfig.COUNTRIES);
      keys.add(IConfig.CURRENCIES);
      keys.add(IConfig.LANGUAGES);
      keys.add(IConfig.LANGUAGES_MOBILE);
      keys.add(IConfig.LOCATIONS);
      keys.add(IConfig.OPTIMIZATION);
      keys.add(IConfig.REPORTS);
      keys.add(IConfig.SMSCONFIG);
      keys.add(IConfig.GENERALCONFIG);
    }
    // Get the configuration
    PersistenceManager pm = PMF.get().getPersistenceManager();
    try {
      Date now = new Date();
      String
          url =
          host + "/api/cfg?a=" + ACTION_GETSYSCONFIG + "&userid=" + userId + "&password=" + password
              + "&key=";
      ConfigurationMgmtService cms = StaticApplicationContext
          .getBean(ConfigurationMgmtServiceImpl.class);
      PrintWriter pw = resp.getWriter();
      for (String k : keys) {
        try {
          // Fetch config. string from the server
          String configStr = HttpUtil.get(url + k, null);
          xLogger.info("Got config. string for key {0}: {1}", k, configStr);
          pw.append("Got config string for key ").append(k).append(": ").append(configStr)
              .append("...\n\n\n");
          // Update config. locally
          if (configStr != null && !configStr.isEmpty()) {
            IConfig c = JDOUtils.createInstance(IConfig.class);
            c.setConfig(configStr);
            c.setKey(k);
            c.setLastUpdated(now);
            cms.addConfiguration(k, c);
          } else {
            xLogger.warn("Empty config. string returned for key {0}", k);
            pw.append("Empty config string returned for key: ").append(k);
          }
        } catch (Exception e) {
          xLogger.warn("{0} when getting config. for key {1}: {2}", e.getClass().getName(), key,
              e.getMessage(), e);
        }
      }
      // Check if this user exists; if not, create this user
      UsersService as = null;
      DomainsService ds = null;
      try {
        as = StaticApplicationContext.getBean(UsersServiceImpl.class);
        ds = StaticApplicationContext.getBean(DomainsServiceImpl.class);
        JDOUtils.getObjectById(IUserAccount.class, userId);
      } catch (JDOObjectNotFoundException e) {
        // Check if the default domain exists
        Long dId = -1l;
        try {
          pm.getObjectById(JDOUtils.getImplClass(IDomain.class), (long) -1);
        } catch (JDOObjectNotFoundException e1) {
          IDomain d = JDOUtils.createInstance(IDomain.class);
          d.setCreatedOn(new Date());
          d.setId((long) -1);
          d.setIsActive(true);
          d.setName("Default");
          d.setOwnerId(userId);
          d.setReportEnabled(true);
          dId = ds.addDomain(d);
          pw.append("Create a Default domain with ID ").append(String.valueOf(dId)).append("\n\n\n");
        }
        // Create user
        IUserAccount u = JDOUtils.createInstance(IUserAccount.class);
        u.setUserId(userId);
        u.setEncodedPassword(password);
        u.setRole(SecurityConstants.ROLE_SUPERUSER);
        u.setFirstName(userId);
        u.setMobilePhoneNumber("+91 999999999");
        u.setCountry("IN");
        u.setState("Karnataka");
        u.setLanguage("en");
        u.setTimezone("Asia/Kolkata");
        u.setEmail(email);
        as.addAccount(dId, u);
        pw.append("Created user account ").append(userId)
            .append(".\n\nYOU MAY NOW LOGIN LOCALLY USING THIS ACCOUNT.");
      } finally {
        pw.close();
      }
    } catch (Exception e) {
      xLogger.severe("{0} when getting config. for key {1}: {2}", e.getClass().getName(), key,
          e.getMessage(), e);
    } finally {
      pm.close();
    }
    xLogger.fine("Exiting updateSysConfig");
  }
}
