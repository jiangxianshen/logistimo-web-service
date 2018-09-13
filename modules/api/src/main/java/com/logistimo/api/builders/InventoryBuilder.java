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

package com.logistimo.api.builders;

import com.logistimo.api.models.EntityModel;
import com.logistimo.api.models.InventoryAbnStockModel;
import com.logistimo.api.models.InventoryBatchMaterialModel;
import com.logistimo.api.models.InventoryDetailModel;
import com.logistimo.api.models.InventoryDomainModel;
import com.logistimo.api.models.InventoryMinMaxLogModel;
import com.logistimo.api.models.InventoryModel;
import com.logistimo.api.models.InvntryBatchModel;
import com.logistimo.api.models.MediaModels;
import com.logistimo.api.models.mobile.CurrentStock;
import com.logistimo.auth.utils.SecurityUtils;
import com.logistimo.common.builder.MediaBuilder;
import com.logistimo.config.models.DomainConfig;
import com.logistimo.config.models.InventoryConfig;
import com.logistimo.config.models.OptimizerConfig;
import com.logistimo.constants.Constants;
import com.logistimo.dao.JDOUtils;
import com.logistimo.domains.entity.IDomain;
import com.logistimo.domains.service.DomainsService;
import com.logistimo.entities.auth.EntityAuthoriser;
import com.logistimo.entities.entity.IKiosk;
import com.logistimo.entities.service.EntitiesService;
import com.logistimo.inventory.dao.IInvntryDao;
import com.logistimo.inventory.entity.IInvAllocation;
import com.logistimo.inventory.entity.IInventoryMinMaxLog;
import com.logistimo.inventory.entity.IInvntry;
import com.logistimo.inventory.entity.IInvntryBatch;
import com.logistimo.inventory.entity.IInvntryEvntLog;
import com.logistimo.inventory.service.InventoryManagementService;
import com.logistimo.logger.XLog;
import com.logistimo.materials.entity.IHandlingUnit;
import com.logistimo.materials.entity.IMaterial;
import com.logistimo.materials.model.HandlingUnitModel;
import com.logistimo.materials.service.IHandlingUnitService;
import com.logistimo.materials.service.MaterialCatalogService;
import com.logistimo.media.endpoints.IMediaEndPoint;
import com.logistimo.media.entity.IMedia;
import com.logistimo.orders.OrderUtils;
import com.logistimo.pagination.Results;
import com.logistimo.security.SecureUserDetails;
import com.logistimo.services.ServiceException;
import com.logistimo.tags.TagUtil;
import com.logistimo.users.entity.IUserAccount;
import com.logistimo.users.service.UsersService;
import com.logistimo.utils.BigUtil;
import com.logistimo.utils.CommonUtils;
import com.logistimo.utils.LocalDateUtil;

import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class InventoryBuilder {

  private static final XLog xLogger = XLog.getLog(InventoryBuilder.class);

  @Autowired
  IInvntryDao invDao;

  @Autowired
  EntitiesService entitiesService;

  @Autowired
  MaterialCatalogService materialCatalogService;

  @Autowired
  UsersService usersService;

  @Autowired
  InventoryManagementService inventoryManagementService;

  @Autowired
  DomainsService domainsService;

  @Autowired
  IHandlingUnitService handlingUnitService;

  public Results buildInventoryModelListAsResult(Results results,
                                                 Long domainId, Long entityId)
      throws ServiceException {
    List<InventoryModel> newInventory = getInventoryModelList(results, domainId, entityId);
    return new Results<>(newInventory, results.getCursor(), results.getNumFound(),
        results.getOffset());
  }

  public List<InventoryModel> getInventoryModelList(Results results,
                                                    Long domainId, Long entityId)
      throws ServiceException {
    List<InventoryModel> newInventory = null;
    if (results != null && results.getResults() != null) {
      List inventory = results.getResults();
      newInventory = new ArrayList<>(inventory.size());
      Map<Long, String> domainNames = new HashMap<>(1);
      int itemCount = results.getOffset() + 1;
      DomainConfig domainConfig = DomainConfig.getInstance(domainId);
      IKiosk ki = null;
      if (entityId != null) {
        ki = entitiesService.getKiosk(entityId, false);
      }
      for (Object invntry : inventory) {
        if (invntry instanceof IInvntryEvntLog) {
          invntry = invDao.getInvntry((IInvntryEvntLog) invntry);
        }
        InventoryModel item = buildInventoryModel((IInvntry) invntry,
            domainConfig, ki, itemCount, domainNames);
        if (item != null) {
          newInventory.add(item);
          itemCount++;
        }
      }
    }
    return newInventory;
  }

  public InventoryDetailModel buildMInventoryDetail(IInvntry inventory,
                                                    Long domainId, Long entityId, boolean embed)
      throws ServiceException {
    DomainConfig domainConfig = DomainConfig.getInstance(domainId);
    IKiosk kiosk = entitiesService.getKiosk(entityId, false);
    return buildMInventoryDetailModel(inventory,
        domainConfig, kiosk, embed);
  }

  public InventoryDetailModel buildMInventoryDetailModel(IInvntry invntry,
                                                         DomainConfig domainConfig,
                                                         IKiosk kiosk, boolean embed)
      throws ServiceException {
    InventoryDetailModel model = new InventoryDetailModel();
    model.mId = invntry.getMaterialId();
    model.eId = invntry.getKioskId();
    IMaterial material;
    MediaModels m;
    model.imgPath = null;
    try {
      material = materialCatalogService.getMaterial(model.mId);
      model.mnm = material.getName();
      model.mat_tgs = material.getTags();
      model.description = material.getDescription();
      IMediaEndPoint endPoint = JDOUtils.createInstance(IMediaEndPoint.class);
      MediaBuilder mediaBuilder = new MediaBuilder();

      List<IMedia> mediaList = endPoint.getMedias(String.valueOf(model.mId));
      m = new MediaModels(mediaBuilder.constructMediaModelList(mediaList));
      if (!m.items.isEmpty()) {
        model.imgPath = new ArrayList<>(m.items.size());
      }
      for (int i = 0; i < m.items.size(); i++) {
        model.imgPath.add(m.items.get(i).servingUrl);
      }
    } catch (Exception e) {
      // CONTINUE: could not find item material
      xLogger.warn("Issue with single inventory detail ", e);
      return null;
    }
    if (kiosk == null) {
      try {
        kiosk = entitiesService.getKiosk(model.eId);
        model.enm = kiosk != null ? kiosk.getName() : invntry.getKioskName();
      } catch (ServiceException e) {
        xLogger.warn("Kiosk associated with material in inventory not found", e);
        return null;
      }
    } else {
      model.enm = kiosk.getName();
    }

    model.loc = new EntityModel();
    if(kiosk != null) {
      model.loc.ct = kiosk.getCity();
      model.loc.ctid = kiosk.getCityId();
      model.loc.st = kiosk.getState();
      model.loc.stid = kiosk.getStateId();
      model.loc.ctr = kiosk.getCountry();
      model.loc.ctrid = kiosk.getCountryId();
      model.loc.ds = kiosk.getDistrict();
      model.loc.dsid = kiosk.getDistrictId();
      model.loc.tlk = kiosk.getTaluk();
      model.setBatchEnabled(material.isBatchEnabled() && kiosk.isBatchMgmtEnabled());
    }

    model.currentStock = new CurrentStock();
    model.currentStock.count = invntry.getStock();
    model.currentStock.date =
        LocalDateUtil
            .format(invntry.getTimestamp(), SecurityUtils.getLocale(), SecurityUtils.getTimezone());
    model.currentStock.lastUpdatedTimeStamp =
        LocalDateUtil.formatCustom(invntry.getTimestamp(), Constants.ISO_PATTERN,
            SecurityUtils.getTimezone());
    model.min = invntry.getNormalizedSafetyStock();
    model.max = invntry.getMaxStock();
    model.it = invntry.getInTransitStock();
    model.as = invntry.getAllocatedStock();
    model.ls = invntry.getPredictedDaysOfStock();
    model.sad = inventoryManagementService.getStockAvailabilityPeriod(invntry, domainConfig);
    model.sap = domainConfig.getInventoryConfig().getDisplayCRFreq();

    IInvntryEvntLog lastEventLog = invDao.getInvntryEvntLog(invntry);

    if (lastEventLog != null) {
      if (invntry.getStockEvent() != -1) {
        model.ed = new Date().getTime() - lastEventLog.getStartDate().getTime();
      }
      model.se = invntry.getStockEvent();
    }
    if(embed) {
      setBatchModel(invntry, model);
      setHandlingUnits(invntry, model);
    }
    model.setTemperatureSensitive(material.isTemperatureSensitive());
    model.setMinTemperature(material.getTemperatureMin());
    model.setMaxTemperature(material.getTemperatureMax());
    model.setShortMaterialId(material.getShortCode());
    model.setAvailableStock(invntry.getAvailableStock());
    model.setConsumptionRateDaily(invntry.getConsumptionRateDaily());
    model.setConsumptionRateWeekly(invntry.getConsumptionRateWeekly());
    model.setConsumptionRateMonthly(invntry.getConsumptionRateMonthly());
    model.setLastUpdatedTimestamp(LocalDateUtil
        .formatCustom(invntry.getTimestamp(), Constants.ISO_PATTERN, SecurityUtils.getTimezone()));
    model.setManufacturerPrice(material.getMSRP());
    model.setRetailerPrice(material.getRetailerPrice());
    model.setCustomMaterialId(material.getCustomId());
    model.setMinimumDurationStock(invntry.getMinDuration());
    model.setMaximumDurationStock(invntry.getMaxDuration());
    return model;
  }

  protected void setBatchModel(IInvntry invntry, InventoryDetailModel model) throws ServiceException {
    List<IInvntryBatch>
        batches =
        inventoryManagementService.getBatches(invntry.getMaterialId(), invntry.getKioskId(),
            null).getResults();
    if(batches != null && CollectionUtils.isNotEmpty(batches)) {
      Map<Boolean, List<InvntryBatchModel>> batchModels = batches.stream().filter(
          batch -> BigUtil.greaterThanZero(batch.getQuantity()))
          .map(batch -> {
            InvntryBatchModel batchModel = new InvntryBatchModel();
            batchModel.bid = batch.getBatchId();
            batchModel.bexp = batch.getBatchExpiry();
            batchModel.bmfdt = batch.getBatchManufacturedDate();
            batchModel.bmfnm = batch.getBatchManufacturer();
            batchModel.q = batch.getQuantity();
            batchModel.t = batch.getTimestamp();
            batchModel.isExp = batch.isExpired();
            batchModel.astk = batch.getAllocatedStock();
            batchModel.atpstk = batch.getAvailableStock();
            batchModel.q = batch.getQuantity();
            return batchModel;
          }).collect(Collectors.groupingBy(batchModel -> batchModel.isExp));
      model.setBatches(batchModels.get(false));
      model.setExpiredBatches(batchModels.get(true));
    }
  }

  protected void setHandlingUnits(IInvntry invntry, InventoryDetailModel invntryModel) {
    Map<String, String> handlingUnits = handlingUnitService.getHandlingUnitDataByMaterialId(invntry.getMaterialId());
    if(handlingUnits != null) {
      HandlingUnitModel model = new HandlingUnitModel();
      model.setHandlingUnitId(handlingUnits.get(IHandlingUnit.HUID));
      model.setName(handlingUnits.get(IHandlingUnit.NAME));
      model.setQuantity(new BigDecimal(handlingUnits.get(IHandlingUnit.QUANTITY)));
      invntryModel.setHandlingUnitModel(model);
      invntryModel.setEnforceHandlingUnit(true);
    } else {
      invntryModel.setEnforceHandlingUnit(false);
    }
  }

  public InventoryModel buildInventoryModel(IInvntry invntry, DomainConfig domainConfig,
                                            IKiosk kiosk, int itemCount,
                                            Map<Long, String> domainNames) {
    SecureUserDetails sUser = SecurityUtils.getUserDetails();
    InventoryModel model = new InventoryModel();
    model.sno = itemCount;
    model.invId = invntry.getKey();
    model.mId = invntry.getMaterialId();
    model.sdid = invntry.getDomainId();
    model.kId = invntry.getKioskId();
    try {
      model.updtBy = invntry.getUpdatedBy();
      model.fn = usersService.getUserAccount(model.updtBy).getFullName();
    } catch (Exception ignored) {
      // ignore
    }
    IMaterial material;
    try {
      material = materialCatalogService.getMaterial(model.mId);
      String domainName = domainNames.get(invntry.getDomainId());
      if (domainName == null) {
        IDomain domain = null;
        try {
          domain = domainsService.getDomain(invntry.getDomainId());

        } catch (Exception e) {
          xLogger.warn("Error while fetching Domain {0}", invntry.getDomainId(), e);
        }
        if (domain != null) {
          domainName = domain.getName();
        } else {
          domainName = Constants.EMPTY;
        }
        domainNames.put(invntry.getDomainId(), domainName);
      }
      model.sdname = domainName;
    } catch (Exception e) {
      // CONTINUE: could not find item material
      return null;
    }
    if (kiosk == null) {
      try {
        kiosk = entitiesService.getKiosk(model.kId);
      } catch (ServiceException e) {
        xLogger.warn("Kiosk associated with material in inventory not found", e);
        return null;
      }
    }
    model.enm = kiosk != null ? kiosk.getName() : invntry.getKioskName();
    if (kiosk != null) {
      model.lt = kiosk.getLatitude();
      model.ln = kiosk.getLongitude();
      model.add = CommonUtils.getAddress(kiosk.getCity(), kiosk.getTaluk(), kiosk.getDistrict(), kiosk.getState());
      model.em = new EntityModel();
      model.em.ct = kiosk.getCity();
      model.em.ctid = kiosk.getCityId();
      model.em.st = kiosk.getState();
      model.em.stid = kiosk.getStateId();
      model.em.ctr = kiosk.getCountry();
      model.em.ctrid = kiosk.getCountryId();
      model.em.ds = kiosk.getDistrict();
      model.em.dsid = kiosk.getDistrictId();
      model.em.tlk = kiosk.getTaluk();
    }
    model.mnm = material.getName();
    if(invntry.getTimestamp() != null) {
      model.t =
          LocalDateUtil.format(invntry.getTimestamp(), sUser.getLocale(), sUser.getTimezone());
    }
    String empty = "";
    IInvntryEvntLog lastEventLog = invDao.getInvntryEvntLog(invntry);

    if (lastEventLog != null) {
      model.eventType = lastEventLog.getType();
      if (invntry.getStockEvent() != -1) {
        model.period = new Date().getTime() - lastEventLog.getStartDate().getTime();
      }
      model.event = invntry.getStockEvent();
    }
    model.stk = invntry.getStock();
    model.ldtdmd = invntry.getLeadTimeDemand();
    model.ldt = invntry.getLeadTime();
    model.max = invntry.getMaxStock();
    model.ordp = invntry.getOrderPeriodicity();
    model.reord = invntry.getNormalizedSafetyStock();
    model.rvpdmd = invntry.getRevPeriodDemand();
    model.sfstk = invntry.getSafetyStock();
    model.stdv = invntry.getStdevRevPeriodDemand();
    model.tgs = invntry.getTags(TagUtil.TYPE_MATERIAL);

    model.rp =
        BigUtil.equalsZero(invntry.getRetailerPrice()) ? material.getRetailerPrice()
            : invntry.getRetailerPrice();

    model.tx =
        BigUtil.notEqualsZero(invntry.getTax()) ? invntry.getTax()
            : kiosk != null ? kiosk.getTax() : BigDecimal.ZERO;

    model.enOL = OrderUtils.isReorderAllowed(invntry.getInventoryModel());
    if (material.isTemperatureSensitive()) {
      model.tmin = material.getTemperatureMin();
      model.tmax = material.getTemperatureMax();
    }

    InventoryConfig ic = domainConfig.getInventoryConfig();
    if (ic != null) {
      if (InventoryConfig.CR_MANUAL == ic.getConsumptionRate()) {
        model.crd =
            inventoryManagementService.getDailyConsumptionRate(invntry, ic.getConsumptionRate(), ic.getManualCRFreq());
        model.crw = model.crd.multiply(Constants.WEEKLY_COMPUTATION);
        model.crm = model.crd.multiply(Constants.MONTHLY_COMPUTATION);
        model.crMnl = invntry.getConsumptionRateManual();
      } else if (InventoryConfig.CR_AUTOMATIC == ic.getConsumptionRate()) {
        model.crd = invntry.getConsumptionRateDaily();
        model.crw = invntry.getConsumptionRateWeekly();
        model.crm = invntry.getConsumptionRateMonthly();
      }
      if (Constants.FREQ_DAILY.equals(ic.getDisplayCRFreq())) {
        model.sap = inventoryManagementService.getStockAvailabilityPeriod(model.crd, invntry.getStock());
      } else if (Constants.FREQ_WEEKLY.equals(ic.getDisplayCRFreq())) {
        model.sap = inventoryManagementService.getStockAvailabilityPeriod(model.crw, invntry.getStock());
      } else if (Constants.FREQ_MONTHLY.equals(ic.getDisplayCRFreq())) {
        model.sap = inventoryManagementService.getStockAvailabilityPeriod(model.crm, invntry.getStock());
      }
    }
    if (kiosk != null && kiosk.isOptimizationOn()) {
      model.im = invntry.getInventoryModel();
      model.sl = invntry.getServiceLevel();
    }

    if (kiosk != null) {
      model.be = kiosk.isBatchMgmtEnabled() && material.isBatchEnabled();
    }
    model.ts = material.isTemperatureSensitive();

    model.eoq = invntry.getEconomicOrderQuantity();
    model.omsg = invntry.getOptMessage();
    model.pst =
        invntry.getPSTimestamp() != null ? LocalDateUtil
            .format(invntry.getPSTimestamp(), sUser.getLocale(), sUser.getTimezone()) : empty;
    model.dqt =
        invntry.getDQTimestamp() != null ? LocalDateUtil
            .format(invntry.getDQTimestamp(), sUser.getLocale(), sUser.getTimezone()) : empty;

    // Parameter update times, if any
    model.reordT =
        (invntry.getReorderLevelUpdatedTime() == null ? null : LocalDateUtil
            .format(invntry.getReorderLevelUpdatedTime(), sUser.getLocale(), sUser.getTimezone()));
    model.maxT =
        (invntry.getMaxUpdatedTime() == null ? null : LocalDateUtil
            .format(invntry.getMaxUpdatedTime(), sUser.getLocale(), sUser.getTimezone()));
    model.crMnlT =
        (invntry.getMnlConsumptionRateUpdatedTime() == null ? null : LocalDateUtil
            .format(invntry.getMnlConsumptionRateUpdatedTime(), sUser.getLocale(),
                sUser.getTimezone()));
    model.rpT =
        (invntry.getRetailerPriceUpdatedTime() == null ? null : LocalDateUtil
            .format(invntry.getRetailerPriceUpdatedTime(), sUser.getLocale(), sUser.getTimezone()));
    model.pdos = invntry.getPredictedDaysOfStock();

    try {
      Map<String, String> hu = handlingUnitService.getHandlingUnitDataByMaterialId(invntry.getMaterialId());
      if (hu != null) {
        model.huQty = new BigDecimal(hu.get(IHandlingUnit.QUANTITY));
        model.huName = hu.get(IHandlingUnit.NAME);
      }
    } catch (Exception e) {
      xLogger.warn("Error while fetching Handling Unit {0}", invntry.getMaterialId(), e);
    }
    model.minDur = invntry.getMinDuration();
    model.maxDur = invntry.getMaxDuration();
    model.atpstk = invntry.getAvailableStock();
    model.tstk = invntry.getInTransitStock();
    model.astk = invntry.getAllocatedStock();

    return model;
  }

  public IInvntry buildInvntry(Long domainId, String kioskName, Long kioskId, InventoryModel invm,
                               IInvntry in, String user, boolean isDurationOfStock,
                               boolean isManual, String minMaxDur, InventoryManagementService ims) {
    in.setDomainId(domainId);
    in.setKioskId(kioskId);
    in.setKioskName(kioskName);
    in.setMaterialId(invm.mId);
    in.setMaterialName(invm.mnm);
    in.setKioskName(invm.enm);
    if (isDurationOfStock) {
      in.setMinDuration(invm.minDur);
      in.setMaxDuration(invm.maxDur);
    } else {
      in.setMinDuration(null);
      in.setMaxDuration(null);
      in.setReorderLevel(invm.reord);
      in.setMaxStock(invm.max);
    }
    in.setUpdatedBy(user);
    if (invm.crMnl != null) {
      in.setConsumptionRateManual(invm.crMnl);
    } else {
      in.setConsumptionRateManual(BigDecimal.ZERO);
    }
    if (isManual && isDurationOfStock) {
      BigDecimal cr = ims.getDailyConsumptionRate(in);
      BigDecimal mul = BigDecimal.ONE;
      if (Constants.FREQ_WEEKLY.equals(minMaxDur)) {
        mul = Constants.WEEKLY_COMPUTATION;
      } else if (Constants.FREQ_MONTHLY.equals(minMaxDur)) {
        mul = Constants.MONTHLY_COMPUTATION;
      }
      in.setReorderLevel(invm.minDur.multiply(mul).multiply(cr));
      in.setMaxStock(invm.maxDur.multiply(mul).multiply(cr));
    }
    in.setRetailerPrice(invm.rp);
    in.setTax(invm.tx);
    //in.setKey(Invntry.createKey(kioskId, invm.mId));
    if ("yes".equals(invm.b)) {
      in.setStock(BigDecimal.ONE);
    } else {
      in.setStock(invm.stk);
    }
    in.setTmin(invm.tmin);
    in.setTmax(invm.tmax);
    in.setInventoryModel(invm.im);
    in.setServiceLevel(invm.sl);
    return in;
  }

  public List<InventoryAbnStockModel> buildAbnormalStockModelList(List<IInvntryEvntLog> evntLogs,
                                                                  Locale locale, String timezone) {
    List<InventoryAbnStockModel> modelList = new ArrayList<>(evntLogs.size());
    Map<Long, String> domainNames = new HashMap<>(0);
    for (IInvntryEvntLog evntLog : evntLogs) {
      InventoryAbnStockModel model =
          buildAbnormalStockModel(evntLog, locale, timezone, domainNames);
      if (model != null) {
        modelList.add(model);
      }
    }
    return modelList;
  }

  public InventoryAbnStockModel buildAbnormalStockModel(IInvntryEvntLog evntLog, Locale locale,
                                                        String timezone,
                                                        Map<Long, String> domainNames) {
    InventoryAbnStockModel model = new InventoryAbnStockModel();
    try {
      IKiosk k;
      IMaterial m;
      try {
        k = entitiesService.getKiosk(evntLog.getKioskId());
      } catch (Exception e) {
        xLogger.warn("Unable to fetch the kiosk details for " + evntLog.getKioskId());
        return null;
      }
      try {
        m = materialCatalogService.getMaterial(evntLog.getMaterialId());
      } catch (Exception e) {
        xLogger.warn("Unable to fetch the material details for " + evntLog.getMaterialId());
        return null;
      }
      model.enm = k.getName();
      model.add = CommonUtils.getAddress(k.getCity(), k.getTaluk(), k.getDistrict(), k.getState());
      model.mnm = m.getName();
      IInvntry invntry = invDao.getInvntry(evntLog);
      model.st = invntry.getStock();
      model.min = invntry.getNormalizedSafetyStock();
      model.max = invntry.getMaxStock();
      Date eDt = evntLog.getEndDate() == null ? new Date() : evntLog.getEndDate();
      model.du = eDt.getTime() - evntLog.getStartDate().getTime();
      model.stDt = evntLog.getStartDate();
      model.edDt = eDt;
      model.sdid = invntry.getDomainId();
      String domainName = domainNames.get(invntry.getDomainId());
      if (domainName == null) {
        IDomain domain = null;
        try {
          domain = domainsService.getDomain(invntry.getDomainId());
        } catch (Exception e) {
          xLogger.fine("Unable to fetch the domain details for domain " + invntry.getDomainId());
        }
        if (domain != null) {
          domainName = domain.getName();
        } else {
          domainName = Constants.EMPTY;
        }
        domainNames.put(invntry.getDomainId(), domainName);
      }
      model.sdname = domainName;
      if (model.stDt != null) {
        model.stDtstr = LocalDateUtil.format(evntLog.getStartDate(), locale, timezone);
      }
      model.edDtstr =
          evntLog.getEndDate() != null ? LocalDateUtil.format(eDt, locale, timezone) : "Now";
      model.kid = evntLog.getKioskId();
      model.mid = evntLog.getMaterialId();
      model.type = evntLog.getType();
      model.minDur = invntry.getMinDuration();
      model.maxDur = invntry.getMaxDuration();
    } catch (Exception e) {
      xLogger.warn("Failed to stock event to abnormal stock list: {0}", e);
      model = null;
    }
    return model;
  }

  public InventoryDomainModel buildInventoryDomainModel(IKiosk kiosk) {
    boolean optimizationOn = kiosk == null || kiosk.isOptimizationOn();

    Long domainId = SecurityUtils.getCurrentDomainId();
    DomainConfig dc = DomainConfig.getInstance(domainId);
    InventoryConfig ic = dc.getInventoryConfig();
    OptimizerConfig oc = dc.getOptimizerConfig();
    boolean isDemandForecast = (oc.getCompute() == OptimizerConfig.COMPUTE_FORECASTEDDEMAND);
    boolean isEOQ = (oc.getCompute() == OptimizerConfig.COMPUTE_EOQ);
    optimizationOn = (optimizationOn && isEOQ);
    boolean allowDisplayConsumptionRates = (ic != null && ic.displayCR());
    String
        crUnits =
        (allowDisplayConsumptionRates ? InventoryConfig
            .getFrequencyDisplay(ic.getDisplayCRFreq(), false, SecurityUtils.getLocale()) : null);

    InventoryDomainModel model = new InventoryDomainModel();
    model.cr = allowDisplayConsumptionRates;
    model.cu = crUnits;
    model.ieoq = isEOQ;
    model.idf = isDemandForecast;
    model.ioon = optimizationOn;
    model.cur = kiosk != null ? kiosk.getCurrency() : null;
    return model;
  }

  public List<InventoryBatchMaterialModel> buildInventoryBatchMaterialModels(int offset,
                                                                             Locale locale,
                                                                             String timezone,
                                                                             EntitiesService as,
                                                                             MaterialCatalogService mc,
                                                                             List<IInvntryBatch> inventory,
                                                                             List<IKiosk> myKiosks) {
    List<InventoryBatchMaterialModel> models = new ArrayList<>(0);
    if (inventory != null && inventory.size() > 0) {
      models = new ArrayList<>(inventory.size());
      Map<Long, String> domainNames = new HashMap<>(1);
      int slno = offset;
      for (IInvntryBatch invBatch : inventory) {
        Long kioskID = invBatch.getKioskId();
        String domainName = domainNames.get(invBatch.getDomainId());
        IKiosk k;
        IMaterial thisMaterial;
        try {
          k = as.getKiosk(kioskID, false);
          if (domainName == null) {
            IDomain domain = null;
            try {
              domain = domainsService.getDomain(invBatch.getDomainId());
            } catch (Exception e) {
              xLogger.warn("Error while fetching Domain {0}", invBatch.getDomainId());
            }
            if (domain != null) {
              domainName = domain.getName();
            } else {
              domainName = Constants.EMPTY;
            }
            domainNames.put(invBatch.getDomainId(), domainName);
          }

          if (myKiosks != null && !myKiosks.contains(k)) {
            continue;
          }
          thisMaterial = mc.getMaterial(invBatch.getMaterialId());
        } catch (Exception e) {
          continue;
        }
        Date timestamp = invBatch.getTimestamp();
        BigDecimal stock = invBatch.getQuantity();
        InventoryBatchMaterialModel mod = new InventoryBatchMaterialModel();
        mod.slno = slno + 1;
        mod.mat = thisMaterial.getName();
        mod.mId = thisMaterial.getMaterialId();
        mod.ent = k.getName();
        mod.eid = k.getKioskId();
        mod.add = CommonUtils.getAddress(k.getCity(), k.getTaluk(), k.getDistrict(), k.getState());
        mod.bat = invBatch.getBatchId();
        mod.exp =
            invBatch.getBatchExpiry() != null ? LocalDateUtil
                .format(invBatch.getBatchExpiry(), locale, timezone, true) : "";
        mod.manr = invBatch.getBatchManufacturer() != null ? invBatch.getBatchManufacturer() : "";
        mod.mand =
            invBatch.getBatchManufacturedDate() != null ? LocalDateUtil
                .format(invBatch.getBatchManufacturedDate(), locale, timezone, true) : "";
        mod.cst = BigUtil.getFormattedValue(stock);
        mod.lup = LocalDateUtil.format(timestamp, locale, timezone);
        mod.sdid = invBatch.getDomainId();
        mod.sdname = domainName;
        models.add(mod);
        slno++;
      }
    }
    return models;
  }

  public List<InvntryBatchModel> buildInvntryBatchModel(Results<IInvntryBatch> results, boolean allBatches,
                                                        SecureUserDetails sUser, Long allocOrderId)
      throws ServiceException {
    if (results.getResults() != null) {
      List<InvntryBatchModel> modelList = new ArrayList<>();
      List<IInvntryBatch> iInvntryBatches = results.getResults();
      Integer hasPerm = 0;
      Long prevKId = null;
      List<IInvAllocation> allocations;
      Map<String, BigDecimal> orderAllocations = null;
      if (allocOrderId != null) {
        allocations =
            inventoryManagementService.getAllocationsByTypeId(null, null, IInvAllocation.Type.ORDER,
                String.valueOf(allocOrderId));
        if (allocations != null && allocations.size() > 0) {
          orderAllocations = new HashMap<>();
          for (IInvAllocation allocation : allocations) {
            if (allocation.getBatchId() == null) {
              continue;
            }
            orderAllocations.put(allocation.getBatchId(), allocation.getQuantity());
          }
        }
      }
      for (IInvntryBatch batch : iInvntryBatches) {
        if (BigUtil.greaterThanZero(batch.getQuantity()) && (allBatches || !batch.isExpired())) {
          InvntryBatchModel model = new InvntryBatchModel();
          model.dId = batch.getDomainIds();
          model.sdId = batch.getDomainId();
          model.kId = batch.getKioskId();
          model.mId = batch.getMaterialId();
          model.q = batch.getQuantity();
          model.bid = batch.getBatchId();
          model.bexp = batch.getBatchExpiry();
          model.bmfnm = batch.getBatchManufacturer();
          model.bmfdt = batch.getBatchManufacturedDate();
          model.t = batch.getTimestamp();
          model.mtgs = batch.getTags(TagUtil.TYPE_MATERIAL);
          model.ktgs = batch.getTags(TagUtil.TYPE_ENTITY);
          model.vld = batch.getVld();
          model.isExp = batch.isExpired();
          model.astk = batch.getAllocatedStock();
          model.atpstk = batch.getAvailableStock();
          if (model.kId.equals(prevKId)) {
            model.perm = hasPerm;
          } else if (sUser != null) {
            try {
              model.perm =
                  EntityAuthoriser
                      .authoriseEntityPerm(batch.getKioskId(), sUser.getRole(),
                          sUser.getUsername(), sUser.getDomainId());
            } catch (ServiceException e) {
              model.perm = 0;
            }
            hasPerm = model.perm;
          }
          if (orderAllocations != null && orderAllocations.containsKey(model.bid)) {
            model.oastk = orderAllocations.get(model.bid);
          }
          modelList.add(model);
          prevKId = model.kId;
        }
      }
      return modelList;
    }
    return null;
  }

  public List<InventoryMinMaxLogModel> buildInventoryMinMaxLogModel(List<IInventoryMinMaxLog> logs) {
    SecureUserDetails sUser = SecurityUtils.getUserDetails();
    List<InventoryMinMaxLogModel> models = null;
    if (logs != null) {
      models = new ArrayList<>(logs.size());
      for (IInventoryMinMaxLog invLog : logs) {
        InventoryMinMaxLogModel invModel = new InventoryMinMaxLogModel();
        invModel.invkey = invLog.getInventoryId();
        invModel.cr = invLog.getConsumptionRate();
        invModel.kid = invLog.getKioskId();
        invModel.mid = invLog.getMaterialId();
        invModel.min = invLog.getMin();
        invModel.max = invLog.getMax();
        invModel.minDur = invLog.getMinDuration();
        invModel.maxDur = invLog.getMaxDuration();
        invModel.t =
            LocalDateUtil.format(invLog.getCreatedTime(), sUser.getLocale(), sUser.getTimezone());
        invModel.freq =
            IInventoryMinMaxLog.Frequency.getDisplayFrequency(invLog.getMinMaxFrequency());
        if (invLog.getSource() != null && invLog.getSource() == 0) {
          invModel.source = "u";
          try {
            IUserAccount account = usersService.getUserAccount(invLog.getUser());
            if (account != null) {
              invModel.uid = account.getUserId();
              invModel.username = account.getFullName();
            }
          } catch (Exception e) {
            xLogger.warn("Error in fetching user details for {0}", invLog.getUser(), e);
          }
        } else {
          invModel.source = "s";
        }
        models.add(invModel);
      }
    }
    return models;
  }
}
