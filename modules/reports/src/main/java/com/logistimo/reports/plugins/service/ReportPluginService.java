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

package com.logistimo.reports.plugins.service;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import com.logistimo.assets.service.AssetManagementService;
import com.logistimo.auth.utils.SecurityUtils;
import com.logistimo.config.models.DomainConfig;
import com.logistimo.constants.CharacterConstants;
import com.logistimo.constants.Constants;
import com.logistimo.domains.entity.IDomain;
import com.logistimo.domains.service.DomainsService;
import com.logistimo.exception.BadRequestException;
import com.logistimo.inventory.entity.IInventoryMinMaxLog;
import com.logistimo.inventory.service.InventoryManagementService;
import com.logistimo.logger.XLog;
import com.logistimo.reports.ReportsConstants;
import com.logistimo.reports.constants.ReportCompareField;
import com.logistimo.reports.constants.ReportViewType;
import com.logistimo.reports.models.ReportMinMaxHistoryFilters;
import com.logistimo.reports.plugins.IExternalServiceClient;
import com.logistimo.reports.plugins.internal.ExportModel;
import com.logistimo.reports.plugins.internal.ExternalServiceClient;
import com.logistimo.reports.plugins.internal.QueryHelper;
import com.logistimo.reports.plugins.internal.QueryRequestModel;
import com.logistimo.reports.plugins.models.ReportChartModel;
import com.logistimo.reports.plugins.models.TableResponseModel;
import com.logistimo.reports.utils.ReportsUtil;
import com.logistimo.security.SecureUserDetails;
import com.logistimo.services.ServiceException;
import com.logistimo.utils.LocalDateUtil;

import org.apache.commons.lang.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.lang.reflect.Type;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.Response;

/**
 * @author Mohan Raja
 */
@Service
public class ReportPluginService {

  private static final XLog xLogger = XLog.getLog(ReportPluginService.class);

  private static final String JSON_REPORT_TYPE = "type";
  private static final String JSON_REPORT_COMPARE = "compare";
  private static final String JSON_REPORT_VIEW_TYPE = "viewtype";
  private static final String INVALID_REQUEST = "Invalid request";

  @Autowired ReportServiceCollection reportServiceCollection;

  private DomainsService domainsService;
  private AssetManagementService assetManagementService;
  private InventoryManagementService inventoryManagementService;

  @Autowired
  public void setDomainsService(DomainsService domainsService) {
    this.domainsService = domainsService;
  }

  @Autowired
  public void setAssetManagementService(AssetManagementService assetManagementService) {
    this.assetManagementService = assetManagementService;
  }

  @Autowired
  public void setInventoryManagementService(InventoryManagementService inventoryManagementService) {
    this.inventoryManagementService = inventoryManagementService;
  }

  public List<ReportChartModel> getReportData(Long domainId, String json) {
    try {
      JSONObject jsonObject = new JSONObject(json);
      IExternalServiceClient<QueryRequestModel>
          externalServiceClient = ExternalServiceClient.getNewCallistoInstance();

      ReportCompareField compareField =
          ReportCompareField.getField(jsonObject.getString(JSON_REPORT_COMPARE));

      QueryRequestModel model = new QueryRequestModel();
      model.filters = QueryHelper.parseFilters(domainId, jsonObject);
      if (model.filters.containsKey(QueryHelper.TOKEN + QueryHelper.QUERY_CITY)) {
        model.filters.remove(QueryHelper.TOKEN + QueryHelper.QUERY_TALUK);
        model.filters.remove(QueryHelper.TOKEN + QueryHelper.QUERY_DISTRICT);
      }
      final String type = jsonObject.getString(JSON_REPORT_TYPE);
      final IReportService reportBuilder = reportServiceCollection.getReportService(type);

      model.filters.put(QueryHelper.TOKEN_COLUMNS, reportBuilder.getColumns(model.filters, ReportViewType.OVERVIEW));

      model.queryId = QueryHelper.getQueryID(model.filters, type);
      Response response = externalServiceClient.postRequest(model);
      return reportBuilder.buildReportsData(response.readEntity(String.class), compareField,
          model.filters);
    } catch (Exception e) {
      xLogger.severe("Error while getting the report data", e);
      return Collections.emptyList();
    }
  }

  public TableResponseModel getReportTableData(Long domainId, String json) {
    try {
      JSONObject jsonObject = new JSONObject(json);
      ReportViewType viewType =
          ReportViewType.getViewType(jsonObject.getString(JSON_REPORT_VIEW_TYPE));

      if (viewType == null) {
        xLogger.warn(
            "Invalid report view type found {0}", jsonObject.getString(JSON_REPORT_VIEW_TYPE));
        throw new BadRequestException(INVALID_REQUEST);
      }

      QueryRequestModel model = constructQueryRequestModel(domainId, jsonObject, viewType);
      final IReportService reportBuilder =
          reportServiceCollection.getReportService(jsonObject.getString(JSON_REPORT_TYPE));
      IExternalServiceClient<QueryRequestModel>
          externalServiceClient = ExternalServiceClient.getNewCallistoInstance();
      Response response = externalServiceClient.postRequest(model);
      return reportBuilder.buildReportTableData(response.readEntity(String.class), viewType, model);
    } catch (Exception e) {
      xLogger.warn("Error while getting report table data", e);
      return null;
    }
  }

  /**
   * Get the last aggregated time for each report based on the report type
   *
   * @param reportType Report type
   */
  public Date getLastAggregatedTime(String reportType) {
    if (StringUtils.isBlank(reportType)) {
      xLogger.warn(
          "Invalid report type received {0}", reportType);
      throw new BadRequestException(INVALID_REQUEST);
    }
    String aggregationRunTimeKey = ReportsUtil.getAggregationReportType(reportType);
    if (StringUtils.isBlank(aggregationRunTimeKey)) {
      xLogger.warn(
          "report type not configured {0}", reportType);
      throw new BadRequestException(INVALID_REQUEST);
    }
    //Set the filters
    QueryRequestModel model = new QueryRequestModel();
    model.filters = new HashMap<>(1);
    model.filters.put(QueryHelper.TOKEN_RUN_TIME, aggregationRunTimeKey);
    model.queryId = QueryHelper.QUERY_LAST_RUN_TIME;
    //Request callisto for data
    IExternalServiceClient<QueryRequestModel>
        externalServiceClient =
        ExternalServiceClient.getNewCallistoInstance();
    Response response = externalServiceClient.postRequest(model);
    //parse response
    if (response != null) {
      JSONObject jsonObject = new JSONObject(response.readEntity(String.class));
      JSONArray rows = jsonObject.has(ReportsConstants.ROWS) ? jsonObject.getJSONArray
          (ReportsConstants.ROWS) : null;
      if (rows != null && !rows.isNull(0) && rows.getJSONArray(0).get(0) != null) {
        try {
          return LocalDateUtil
              .parseCustom((String) rows.getJSONArray(0).get(0), Constants.ANALYTICS_DATE_FORMAT,
                  null);
        } catch (ParseException e) {
          xLogger.warn("Exception parsing date", e);
        }
      }
    }
    return null;
  }

  public DateTime getAggregationRunTime(String reportType){
    Date lastRunTime=getLastAggregatedTime(reportType);
    if(lastRunTime==null){
      lastRunTime=new Date();
    }
    return new DateTime(lastRunTime);
  }

  public ExportModel buildExportModel(String json) throws ParseException, ServiceException {
    JSONObject jsonObject = new JSONObject(json);
    final String reportViewType = jsonObject.getString(JSON_REPORT_VIEW_TYPE);
    ReportViewType viewType =
        ReportViewType.getViewType(reportViewType);
    Long domainId = SecurityUtils.getCurrentDomainId();
    final Map<String, String> filters = QueryHelper.parseFilters(domainId, jsonObject);
    final QueryRequestModel model =
        constructQueryRequestModel(domainId, jsonObject, viewType);
    ExportModel eModel = new ExportModel();
    IDomain domain=domainsService.getDomain(domainId);
    final SecureUserDetails userDetails = SecurityUtils.getUserDetails();
    eModel.userId = userDetails.getUsername();
    eModel.timezone = userDetails.getTimezone();
    eModel.locale = userDetails.getLocale().getLanguage();
    if(!filters.containsKey(QueryHelper.TOKEN + QueryHelper.QUERY_DVID) &&
        model.filters.containsKey(QueryHelper.TOKEN + QueryHelper.QUERY_DVID)) {
      model.filters.put(QueryHelper.TOKEN + QueryHelper.QUERY_DVID, CharacterConstants.EMPTY);
    }
    if(!filters.containsKey(QueryHelper.TOKEN + QueryHelper.QUERY_VENDOR_ID) &&
        model.filters.containsKey(QueryHelper.TOKEN + QueryHelper.QUERY_VENDOR_ID)) {
      model.filters.put(QueryHelper.TOKEN + QueryHelper.QUERY_VENDOR_ID, CharacterConstants.EMPTY);
    }
    if(!filters.containsKey(QueryHelper.TOKEN + QueryHelper.QUERY_ATYPE) &&
        model.filters.containsKey(QueryHelper.TOKEN + QueryHelper.QUERY_ATYPE)) {
      model.filters.put(QueryHelper.TOKEN + QueryHelper.QUERY_ATYPE, CharacterConstants.EMPTY);
    }
    eModel.filters = model.filters;
    eModel.templateId = jsonObject.getString(JSON_REPORT_TYPE);
    eModel.additionalData = new HashMap<>();
    eModel.additionalData.put("typeId", getExportType(reportViewType));
    eModel.additionalData.put("reportViewType", reportViewType);
    eModel.additionalData.put("queryId", model.queryId);
    eModel.additionalData.put("reportType", eModel.templateId);
    eModel.additionalData.put("domainName", domain.getName());
    eModel.additionalData.put("primaryMetricIndex", jsonObject.getString("primaryMetricIndex"));
    eModel.additionalData.put("secondaryMetricIndex", jsonObject.getString("secondaryMetricIndex"));
    eModel.additionalData.put("tertiaryMetricIndex", jsonObject.getString("tertiaryMetricIndex"));
    DomainConfig dc = DomainConfig.getInstance(SecurityUtils.getCurrentDomainId());
    eModel.additionalData.put("domainTimezone", dc.getTimezone());
    eModel.additionalData.put("exportTime", LocalDateUtil
        .formatCustom(new Date(), Constants.DATETIME_CSV_FORMAT, userDetails.getTimezone()));
    DateTime lastRunTime=getAggregationRunTime(eModel.templateId);
    eModel.additionalData.put("lastRunTime", DateTimeFormat.forPattern(Constants.DATETIME_CSV_FORMAT).print(lastRunTime));

    Type type = new TypeToken<Map<String, String>>() {
    }.getType();
    eModel.titles = new Gson().fromJson(jsonObject.get("titles").toString(), type);

    return eModel;
  }

  private String getExportType(String viewtype) {
    final ReportViewType viewType = ReportViewType.getViewType(viewtype);
    if (viewType != null) {
      switch (viewType) {
        case BY_MATERIAL:
          return "T_MID";
        case BY_ENTITY:
          return "T_KID";
        case BY_REGION:
          return "T_REG";
        case BY_MANUFACTURER:
          return "T_MNT";
        case BY_MODEL:
          return "T_AMT";
        case BY_ASSET:
          return "T_AT";
        case BY_ASSET_TYPE:
          return "T_ATT";
        case BY_ENTITY_TAGS:
          return "T_KTT";
        case BY_USER:
          return "T_UT";
        case BY_CUSTOMER:
          return "T_CT";
        default:
          return null;
      }
    }
    return null;
  }

  private void finaliseFilters(ReportViewType viewType, QueryRequestModel model,
                               Map<String, String> retainFilters) {
    switch (viewType) {
      case BY_MATERIAL:
        finaliseFilterByMaterial(model, retainFilters);
        break;
      case BY_ENTITY:
        finaliseFilterByEntity(model, retainFilters);
        break;
      case BY_USER:
        model.filters.remove(QueryHelper.TOKEN + QueryHelper.QUERY_USER);
        if (retainFilters.containsKey(QueryHelper.TOKEN + QueryHelper.QUERY_USER_TAG)) {
          model.filters.put(QueryHelper.TOKEN + QueryHelper.QUERY_USER
                  + CharacterConstants.UNDERSCORE + QueryHelper.QUERY,
              QueryHelper.QUERY_USER + CharacterConstants.UNDERSCORE + QueryHelper.QUERY_USER_TAG
                  + CharacterConstants.UNDERSCORE + QueryHelper.QUERY);
          model.filters.put(QueryHelper.TOKEN + QueryHelper.QUERY_USER_TAG,
              retainFilters.get(QueryHelper.TOKEN + QueryHelper.QUERY_USER_TAG));
        } else {
          model.filters.put(QueryHelper.TOKEN + QueryHelper.QUERY_USER
                  + CharacterConstants.UNDERSCORE + QueryHelper.QUERY,
              QueryHelper.QUERY_USER + CharacterConstants.UNDERSCORE + QueryHelper.QUERY);
        }
        break;
      case BY_ENTITY_TAGS:
        model.filters.remove(QueryHelper.TOKEN + QueryHelper.QUERY_ENTITY_TAG);
        break;
      case BY_REGION:
        finaliseFilterByRegion(model);
        break;
      case BY_MODEL:
        model.filters.remove(QueryHelper.TOKEN + QueryHelper.QUERY_DMODEL);
        break;
      case BY_CUSTOMER:
        finaliseFilterByCustomer(model);
        break;
      default:
        break;
    }
  }

  private void finaliseFilterByRegion(QueryRequestModel model) {
    if(model.filters.get(QueryHelper.TOKEN + QueryHelper.QUERY_COUNTRY) == null ) {
      model.filters.remove(QueryHelper.TOKEN + QueryHelper.QUERY_COUNTRY);
    }
    if(model.filters.get(QueryHelper.TOKEN + QueryHelper.QUERY_STATE) == null ) {
      model.filters.remove(QueryHelper.TOKEN + QueryHelper.QUERY_STATE);
    }
    if(model.filters.get(QueryHelper.TOKEN + QueryHelper.QUERY_DISTRICT) == null ) {
      model.filters.remove(QueryHelper.TOKEN + QueryHelper.QUERY_DISTRICT);
    }
    if(model.filters.get(QueryHelper.TOKEN + QueryHelper.QUERY_TALUK) == null ) {
      model.filters.remove(QueryHelper.TOKEN + QueryHelper.QUERY_TALUK);
    }
    if(model.filters.get(QueryHelper.TOKEN + QueryHelper.QUERY_CITY) == null ) {
      model.filters.remove(QueryHelper.TOKEN + QueryHelper.QUERY_CITY);
    }
  }

  private void finaliseFilterByEntity(QueryRequestModel model, Map<String, String> retainFilters) {
    model.filters.remove(QueryHelper.TOKEN + QueryHelper.QUERY_ENTITY);
    String locationType = CharacterConstants.EMPTY;
    if (retainFilters.containsKey(QueryHelper.TOKEN + QueryHelper.QUERY_COUNTRY)) {
      locationType = QueryHelper.QUERY_COUNTRY;
      model.filters.put(QueryHelper.TOKEN + QueryHelper.QUERY_COUNTRY,
          retainFilters.get(QueryHelper.TOKEN + QueryHelper.QUERY_COUNTRY));
    }
    if (retainFilters.containsKey(QueryHelper.TOKEN + QueryHelper.QUERY_STATE)) {
      locationType = QueryHelper.QUERY_STATE;
      model.filters.put(QueryHelper.TOKEN + QueryHelper.QUERY_STATE,
          retainFilters.get(QueryHelper.TOKEN + QueryHelper.QUERY_STATE));
    }
    if (retainFilters.containsKey(QueryHelper.TOKEN + QueryHelper.QUERY_DISTRICT)) {
      locationType = QueryHelper.QUERY_DISTRICT;
      model.filters.put(QueryHelper.TOKEN + QueryHelper.QUERY_DISTRICT,
          retainFilters.get(QueryHelper.TOKEN + QueryHelper.QUERY_DISTRICT));
    }
    if (retainFilters.containsKey(QueryHelper.TOKEN + QueryHelper.QUERY_TALUK)) {
      locationType = QueryHelper.QUERY_TALUK;
      model.filters.put(QueryHelper.TOKEN + QueryHelper.QUERY_TALUK,
          retainFilters.get(QueryHelper.TOKEN + QueryHelper.QUERY_TALUK));
    }
    if (retainFilters.containsKey(QueryHelper.TOKEN + QueryHelper.QUERY_CITY)) {
      locationType = QueryHelper.QUERY_CITY;
      model.filters.put(QueryHelper.TOKEN + QueryHelper.QUERY_CITY,
          retainFilters.get(QueryHelper.TOKEN + QueryHelper.QUERY_CITY));
    }
    if (StringUtils.isNotEmpty(locationType)) {
      locationType = CharacterConstants.UNDERSCORE + locationType;
    }
    if (retainFilters.containsKey(QueryHelper.TOKEN + QueryHelper.QUERY_ENTITY_TAG)) {
      model.filters.put(QueryHelper.TOKEN + QueryHelper.QUERY_ENTITY
              + CharacterConstants.UNDERSCORE + QueryHelper.QUERY,
          QueryHelper.QUERY_ENTITY + CharacterConstants.UNDERSCORE + QueryHelper.QUERY_ENTITY_TAG
              + locationType + CharacterConstants.UNDERSCORE + QueryHelper.QUERY);
      model.filters.put(QueryHelper.TOKEN + QueryHelper.QUERY_ENTITY_TAG,
          retainFilters.get(QueryHelper.TOKEN + QueryHelper.QUERY_ENTITY_TAG));
    } else if (StringUtils.isNotEmpty(locationType)) {
      model.filters.put(QueryHelper.TOKEN + QueryHelper.QUERY_ENTITY
              + CharacterConstants.UNDERSCORE + QueryHelper.QUERY,
          QueryHelper.QUERY_ENTITY + locationType + CharacterConstants.UNDERSCORE
              + QueryHelper.QUERY);
    } else {
      model.filters.put(QueryHelper.TOKEN + QueryHelper.QUERY_ENTITY
              + CharacterConstants.UNDERSCORE + QueryHelper.QUERY,
          QueryHelper.QUERY_ENTITY + CharacterConstants.UNDERSCORE + QueryHelper.QUERY);
    }
  }

  private QueryRequestModel constructQueryRequestModel(Long domainId, JSONObject jsonObject,
                                                       ReportViewType viewType)
      throws ParseException {
    QueryRequestModel model = new QueryRequestModel();
    model.filters = QueryHelper.parseFilters(domainId, jsonObject);
    if (model.filters.containsKey(QueryHelper.TOKEN + QueryHelper.QUERY_CITY)) {
      model.filters.remove(QueryHelper.TOKEN + QueryHelper.QUERY_TALUK);
      model.filters.remove(QueryHelper.TOKEN + QueryHelper.QUERY_DISTRICT);
    }

    String
        startTime =
        getReportTableStartTime(jsonObject, model.filters.get(QueryHelper.TOKEN_END_TIME));
    if (StringUtils.isNotEmpty(startTime)) {
      model.filters.put(QueryHelper.TOKEN_START_TIME, startTime);
    }

    final String type = jsonObject.getString(JSON_REPORT_TYPE);
    final IReportService reportBuilder =
        reportServiceCollection.getReportService(type);
    model.filters.put(QueryHelper.TOKEN_COLUMNS, reportBuilder.getTableColumns(model.filters,
        viewType));

    Map<String, String> retainFilters = new HashMap<>();

    prepareFilters(domainId, viewType, model, retainFilters);

    model.queryId = viewType.toString().toUpperCase() + CharacterConstants.UNDERSCORE
        + QueryHelper.getQueryID(model.filters, type);
    if (viewType.toString().equals(ReportViewType.BY_ASSET.toString())) {
      model.queryId = "DID_DVID";
      model.derivedResultsId = "ATE_DID_DVID";
      String[]
          arr =
          StringUtils.split(model.filters.get(QueryHelper.TOKEN + QueryHelper.QUERY_DVID), ',');
      for (int i = 0; i < arr.length; i++) {
        arr[i] = arr[i].substring(1, arr[i].length() - 1);
      }
      model.rowHeadings = Arrays.asList(arr);
    }
    finaliseFilters(viewType, model, retainFilters);
    if(model.filters.containsKey(QueryHelper.LOCATION_BY)) {
      if(model.filters.containsKey(QueryHelper.TOKEN + QueryHelper.QUERY_DISTRICT)) {
        model.queryId = model.queryId.replace("RT", "RT_D");
      } else {
        model.queryId = model.queryId.replace("RT", "RT_D_ALL");
      }
    }
    return model;
  }

  private void finaliseFilterByMaterial(QueryRequestModel model,
                                        Map<String, String> retainFilters) {
    model.filters.remove(QueryHelper.TOKEN + QueryHelper.QUERY_MATERIAL);
    if (retainFilters.containsKey(QueryHelper.TOKEN + QueryHelper.QUERY_MATERIAL_TAG)) {
      model.filters.put(QueryHelper.TOKEN + QueryHelper.QUERY_MATERIAL
              + CharacterConstants.UNDERSCORE + QueryHelper.QUERY,
          QueryHelper.QUERY_MATERIAL + CharacterConstants.UNDERSCORE
              + QueryHelper.QUERY_MATERIAL_TAG + CharacterConstants.UNDERSCORE
              + QueryHelper.QUERY);
      model.filters.put(QueryHelper.TOKEN + QueryHelper.QUERY_MATERIAL_TAG,
          retainFilters.get(QueryHelper.TOKEN + QueryHelper.QUERY_MATERIAL_TAG));
    } else {
      model.filters
          .put(QueryHelper.TOKEN + QueryHelper.QUERY_MATERIAL + CharacterConstants.UNDERSCORE
                  + QueryHelper.QUERY,
              QueryHelper.QUERY_MATERIAL + CharacterConstants.UNDERSCORE + QueryHelper.QUERY);
    }
  }

  private void finaliseFilterByCustomer(QueryRequestModel model) {
    model.filters.remove(QueryHelper.TOKEN + QueryHelper.QUERY_LKID);
    model.filters.put(QueryHelper.TOKEN + QueryHelper.QUERY_LKID + CharacterConstants.UNDERSCORE
            + QueryHelper.QUERY,
        QueryHelper.QUERY_LKID + CharacterConstants.UNDERSCORE + QueryHelper.QUERY);
  }

  private void prepareFilters(Long domainId, ReportViewType viewType,
                              QueryRequestModel model, Map<String, String> retainFilters) {
    switch (viewType) {
      case BY_MATERIAL:
        prepareFiltersByMaterial(model, retainFilters);
        break;
      case BY_ENTITY:
        perpareFiltersByEntity(model, retainFilters);
        break;
      case BY_REGION:
        prepareFiltersByRegion(domainId, model);
        break;
      case BY_MODEL:
        model.filters.put(QueryHelper.TOKEN + QueryHelper.QUERY_DMODEL, null);
        break;
      case BY_ASSET:
        if(!model.filters.containsKey(QueryHelper.TOKEN + QueryHelper.QUERY_DVID)) {
          model.filters.put(QueryHelper.TOKEN + QueryHelper.QUERY_DVID,
              assetManagementService.getMonitoredAssetIdsForReport(model.filters));
        }
        break;
      case BY_ENTITY_TAGS:
        model.filters.put(QueryHelper.TOKEN + QueryHelper.QUERY_ENTITY_TAG, null);
        break;
      case BY_MANUFACTURER:
        if(!model.filters.containsKey(QueryHelper.TOKEN + QueryHelper.QUERY_VENDOR_ID)) {
          model.filters.put(QueryHelper.TOKEN + QueryHelper.QUERY_VENDOR_ID,
              assetManagementService.getVendorIdsForReports(
                  model.filters.get(QueryHelper.TOKEN + QueryHelper.QUERY_DOMAIN)));
        }
        break;
      case BY_USER:
        prepareFiltersByUser(model, retainFilters);
        break;
      case BY_ASSET_TYPE:
        model.filters.remove(QueryHelper.TOKEN + QueryHelper.QUERY_MTYPE);
        if(!model.filters.containsKey(QueryHelper.TOKEN + QueryHelper.QUERY_ATYPE)) {
          model.filters.put(QueryHelper.TOKEN + QueryHelper.QUERY_ATYPE, assetManagementService.getAssetTypesForReports(
              model.filters.get(QueryHelper.TOKEN + QueryHelper.QUERY_DOMAIN), "1"));
        }
        break;
      case BY_CUSTOMER:
        prepareFiltersByCustomer(model);
        break;
      default:
        break;
    }
  }

  private String getLocationReportBy(String locationBy) {
    switch (locationBy) {
      case QueryHelper.DISTRICT:
        return QueryHelper.LOCATION_DISTRICT;
      case QueryHelper.TALUK:
        return QueryHelper.LOCATION_TALUK;
      case QueryHelper.CITY:
      default:
        return QueryHelper.LOCATION_CITY;
    }
  }

  private void prepareFiltersByRegion(Long domainId, QueryRequestModel model) {
    try {
      if(model.filters.containsKey(QueryHelper.TOKEN + QueryHelper.QUERY_COUNTRY)) {
        if (model.filters.containsKey(QueryHelper.TOKEN + QueryHelper.QUERY_DISTRICT)) {
          model.filters.put(QueryHelper.TOKEN_LOCATION, QueryHelper.LOCATION_TALUK);
          model.filters.put(QueryHelper.TOKEN + QueryHelper.QUERY_TALUK, null);
        } else { // with state filter
          String locationBy = getLocationReportBy(model.filters.get(QueryHelper.LOCATION_BY));
          model.filters.put(QueryHelper.TOKEN_LOCATION, locationBy);
          switch (locationBy) {
            case QueryHelper.LOCATION_CITY:
              model.filters.put(QueryHelper.TOKEN + QueryHelper.QUERY_CITY, null);
              break;
            case QueryHelper.LOCATION_TALUK:
              model.filters.put(QueryHelper.TOKEN + QueryHelper.QUERY_TALUK, null);
            default:
              model.filters.put(QueryHelper.TOKEN + QueryHelper.QUERY_DISTRICT, null);
          }
        }
      } else {
        DomainConfig dc = DomainConfig.getInstance(domainId);
        if (StringUtils.isNotEmpty(dc.getCountry())) {
          if (StringUtils.isNotEmpty(dc.getState())) {
            if (StringUtils.isNotEmpty(dc.getDistrict())) {
              model.filters.put(QueryHelper.TOKEN_LOCATION, QueryHelper.LOCATION_TALUK);
              model.filters.put(QueryHelper.TOKEN + QueryHelper.QUERY_TALUK, null);
              model.filters.put(QueryHelper.TOKEN + QueryHelper.QUERY_DISTRICT, null);
              model.filters.put(QueryHelper.TOKEN + QueryHelper.QUERY_STATE, null);
              model.filters.put(QueryHelper.TOKEN + QueryHelper.QUERY_COUNTRY, null);
            } else {
              model.filters.put(QueryHelper.TOKEN_LOCATION, QueryHelper.LOCATION_DISTRICT);
              model.filters.put(QueryHelper.TOKEN + QueryHelper.QUERY_DISTRICT, null);
              model.filters.put(QueryHelper.TOKEN + QueryHelper.QUERY_STATE, null);
              model.filters.put(QueryHelper.TOKEN + QueryHelper.QUERY_COUNTRY, null);
            }
          } else {
            model.filters.put(QueryHelper.TOKEN_LOCATION, QueryHelper.LOCATION_STATE);
            model.filters.put(QueryHelper.TOKEN + QueryHelper.QUERY_STATE, null);
            model.filters.put(QueryHelper.TOKEN + QueryHelper.QUERY_COUNTRY, null);
          }
        } else {
          model.filters.put(QueryHelper.TOKEN_LOCATION, QueryHelper.LOCATION_COUNTRY);
          model.filters.put(QueryHelper.TOKEN + QueryHelper.QUERY_COUNTRY, null);
        }
      }
    } catch (Exception e) {
      xLogger.warn("Exception in replacing location token", e);
    }

  }

  private void perpareFiltersByEntity(QueryRequestModel model, Map<String, String> retainFilters) {
    model.filters.put(QueryHelper.TOKEN + QueryHelper.QUERY_ENTITY, null);
    if (model.filters.containsKey(QueryHelper.TOKEN + QueryHelper.QUERY_ENTITY_TAG)) {
      retainFilters.put(QueryHelper.TOKEN + QueryHelper.QUERY_ENTITY_TAG,
          model.filters.remove(QueryHelper.TOKEN + QueryHelper.QUERY_ENTITY_TAG));
    }
    if (model.filters.containsKey(QueryHelper.TOKEN + QueryHelper.QUERY_COUNTRY)) {
      retainFilters.put(QueryHelper.TOKEN + QueryHelper.QUERY_COUNTRY,
          model.filters.remove(QueryHelper.TOKEN + QueryHelper.QUERY_COUNTRY));
    }
    if (model.filters.containsKey(QueryHelper.TOKEN + QueryHelper.QUERY_STATE)) {
      retainFilters.put(QueryHelper.TOKEN + QueryHelper.QUERY_STATE,
          model.filters.remove(QueryHelper.TOKEN + QueryHelper.QUERY_STATE));
    }
    if (model.filters.containsKey(QueryHelper.TOKEN + QueryHelper.QUERY_DISTRICT)) {
      retainFilters.put(QueryHelper.TOKEN + QueryHelper.QUERY_DISTRICT,
          model.filters.remove(QueryHelper.TOKEN + QueryHelper.QUERY_DISTRICT));
    }
    if (model.filters.containsKey(QueryHelper.TOKEN + QueryHelper.QUERY_TALUK)) {
      retainFilters.put(QueryHelper.TOKEN + QueryHelper.QUERY_TALUK,
          model.filters.remove(QueryHelper.TOKEN + QueryHelper.QUERY_TALUK));
    }
    if (model.filters.containsKey(QueryHelper.TOKEN + QueryHelper.QUERY_CITY)) {
      retainFilters.put(QueryHelper.TOKEN + QueryHelper.QUERY_CITY,
          model.filters.remove(QueryHelper.TOKEN + QueryHelper.QUERY_CITY));
    }
  }

  private void prepareFiltersByUser(QueryRequestModel model, Map<String, String> retainFilters) {
    model.filters.put(QueryHelper.TOKEN + QueryHelper.QUERY_USER, null);
    if (model.filters.containsKey(QueryHelper.TOKEN + QueryHelper.QUERY_USER_TAG)) {
      retainFilters.put(QueryHelper.TOKEN + QueryHelper.QUERY_USER_TAG,
          model.filters.remove(QueryHelper.TOKEN + QueryHelper.QUERY_USER_TAG));
    }
  }

  private void prepareFiltersByMaterial(QueryRequestModel model,
                                        Map<String, String> retainFilters) {
    model.filters.put(QueryHelper.TOKEN + QueryHelper.QUERY_MATERIAL, null);
    if (model.filters.containsKey(QueryHelper.TOKEN + QueryHelper.QUERY_MATERIAL_TAG)) {
      retainFilters.put(QueryHelper.TOKEN + QueryHelper.QUERY_MATERIAL_TAG,
          model.filters.remove(QueryHelper.TOKEN + QueryHelper.QUERY_MATERIAL_TAG));
    }
  }

  private void prepareFiltersByCustomer(QueryRequestModel model) {
    model.filters.put(QueryHelper.TOKEN + QueryHelper.QUERY_LKID, null);
  }

  private String getReportTableStartTime(JSONObject jsonObject, String endTime)
      throws ParseException {
    switch (jsonObject.getString(QueryHelper.PERIODICITY)) {
      case QueryHelper.PERIODICITY_MONTH:
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM");
        Calendar toDate = new GregorianCalendar();
        toDate.setTime(format.parse(endTime));
        toDate.add(Calendar.MONTH, -1 * (QueryHelper.MONTHS_LIMIT - 1));
        return format.format(toDate.getTime());
      case QueryHelper.PERIODICITY_WEEK:
        DateTimeFormatter mDateTimeFormatter = DateTimeFormat.forPattern(
            QueryHelper.DATE_FORMAT_DAILY);
        DateTime toTime = mDateTimeFormatter.parseDateTime(endTime);
        return mDateTimeFormatter.print(toTime.minusWeeks(QueryHelper.WEEKS_LIMIT - 1));
      default:
        mDateTimeFormatter = DateTimeFormat.forPattern(QueryHelper.DATE_FORMAT_DAILY);
        toTime = mDateTimeFormatter.parseDateTime(endTime);
        return mDateTimeFormatter.print(toTime.minusDays(QueryHelper.DAYS_LIMIT - 1));
    }
  }

  /**
   * Gets a list of IInventoryMinMaxLog objects for the entity ID and material ID, from and to dates specified in the filters
   * @param minMaxHistoryFilters -
   * @return List of IInventoryMinMaxLog objects or empty list (in case an exception occurs)
   */
  public List<IInventoryMinMaxLog> getMinMaxHistoryReportData(ReportMinMaxHistoryFilters minMaxHistoryFilters) {
    if (minMaxHistoryFilters == null) {
      throw new IllegalArgumentException("Invalid filters while getting min max history report data");
    }
    String domainTimezone = DomainConfig.getInstance(SecurityUtils.getCurrentDomainId()).getTimezone();
    try {
      Date fromDate = LocalDateUtil.parseCustom(minMaxHistoryFilters.getFrom(),QueryHelper.DATE_FORMAT_DAILY, domainTimezone);
      Date toDate = LocalDateUtil.parseCustom(minMaxHistoryFilters.getTo(),QueryHelper.DATE_FORMAT_DAILY, domainTimezone);
      return inventoryManagementService.fetchMinMaxLogByInterval(minMaxHistoryFilters.getEntityId(), minMaxHistoryFilters.getMaterialId(), fromDate , toDate);
    } catch (Exception e) {
      xLogger.severe("Error while getting min max history report data", e);
      return Collections.emptyList();
    }
  }
}
