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
package com.logistimo.bulkuploads;

import com.logistimo.AppFactory;
import com.logistimo.assets.AssetUtil;
import com.logistimo.assets.entity.IAsset;
import com.logistimo.assets.entity.IAssetRelation;
import com.logistimo.assets.service.AssetManagementService;
import com.logistimo.assets.service.impl.AssetManagementServiceImpl;
import com.logistimo.auth.SecurityConstants;
import com.logistimo.auth.SecurityMgr;
import com.logistimo.auth.SecurityUtil;
import com.logistimo.auth.service.AuthenticationService;
import com.logistimo.auth.service.impl.AuthenticationServiceImpl;
import com.logistimo.bulkuploads.headers.AssetsHeader;
import com.logistimo.bulkuploads.headers.IHeader;
import com.logistimo.bulkuploads.headers.InventoryHeader;
import com.logistimo.bulkuploads.headers.KiosksHeader;
import com.logistimo.bulkuploads.headers.MaterialsHeader;
import com.logistimo.bulkuploads.headers.MnlTransactionHeader;
import com.logistimo.bulkuploads.headers.TransactionsHeader;
import com.logistimo.bulkuploads.headers.UsersHeader;
import com.logistimo.config.entity.IConfig;
import com.logistimo.config.models.AssetConfig;
import com.logistimo.config.models.AssetSystemConfig;
import com.logistimo.config.models.ConfigurationException;
import com.logistimo.config.models.DomainConfig;
import com.logistimo.config.models.InventoryConfig;
import com.logistimo.config.service.ConfigurationMgmtService;
import com.logistimo.config.service.impl.ConfigurationMgmtServiceImpl;
import com.logistimo.constants.CharacterConstants;
import com.logistimo.constants.Constants;
import com.logistimo.constants.SourceConstants;
import com.logistimo.context.StaticApplicationContext;
import com.logistimo.dao.JDOUtils;
import com.logistimo.domains.entity.IDomainPermission;
import com.logistimo.domains.service.DomainsService;
import com.logistimo.domains.service.impl.DomainsServiceImpl;
import com.logistimo.entities.entity.IKiosk;
import com.logistimo.entities.entity.IKioskLink;
import com.logistimo.entities.service.EntitiesService;
import com.logistimo.entities.service.EntitiesServiceImpl;
import com.logistimo.entity.IUploaded;
import com.logistimo.exception.BadRequestException;
import com.logistimo.exception.TaskSchedulingException;
import com.logistimo.exception.ValidationException;
import com.logistimo.inventory.entity.IInvntry;
import com.logistimo.inventory.service.InventoryManagementService;
import com.logistimo.inventory.service.impl.InventoryManagementServiceImpl;
import com.logistimo.logger.XLog;
import com.logistimo.materials.entity.IMaterial;
import com.logistimo.materials.service.MaterialCatalogService;
import com.logistimo.materials.service.impl.MaterialCatalogServiceImpl;
import com.logistimo.services.ObjectNotFoundException;
import com.logistimo.services.Resources;
import com.logistimo.services.ServiceException;
import com.logistimo.services.UploadService;
import com.logistimo.services.impl.PMF;
import com.logistimo.services.impl.UploadServiceImpl;
import com.logistimo.services.taskqueue.ITaskService;
import com.logistimo.tags.TagUtil;
import com.logistimo.users.entity.IUserAccount;
import com.logistimo.users.service.UsersService;
import com.logistimo.users.service.impl.UsersServiceImpl;
import com.logistimo.utils.BigUtil;
import com.logistimo.utils.FieldLimits;
import com.logistimo.utils.LocalDateUtil;
import com.logistimo.utils.PasswordEncoder;
import com.logistimo.utils.PatternConstants;
import com.logistimo.utils.StringUtil;
import com.logistimo.validations.PasswordValidator;

import org.apache.commons.lang.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeSet;

import javax.jdo.PersistenceManager;
import javax.jdo.Query;


/**
 * @author Arun
 */
public class BulkUploadMgr {

  // Types
  public static final String TYPE_USERS = "users";
  public static final String TYPE_MATERIALS = "materials";
  public static final String TYPE_KIOSKS = "kiosks";
  public static final String TYPE_TRANSACTIONS = "transactions";
  public static final String TYPE_INVENTORY = "inventory";
  public static final String TYPE_ASSETS = "assets";
  public static final String TYPE_TRANSACTIONS_CUM_INVENTORY_METADATA = "transactionscuminventorymetadata";
  public static final String TYPE_TIMEZONES = "timezones";
  // Operations
  public static final String OP_ADD = "a";
  public static final String OP_EDIT = "e";
  public static final String OP_DELETE = "d";
  // Delimiters
  public static final String MESSAGE_DELIMITER = "@@@@@";
  public static final String INTERLINE_DELIMITER = "%%%%%";
  public static final String INTRALINE_DELIMITER = ":::::";
  // Inventory model - user-specified replenishment
  public static final String INVNTRY_MODEL_USERSPECIFIED = "us";
  public static final int ERROR_MESSAGE_MAX_LENGTH = 1800;
  // need in bulk-upload, so that empty cell can be supported to indicate that no change be made
  // Logger
  private static final XLog xLogger = XLog.getLog(BulkUploadMgr.class);
  public static final String ASSET_YOM = "yom";
  public static final String DEV_YOM = "dev.yom";
  public static final int LOWER_BOUND_FOR_YOM = 1980;
  public static final String TEMP_MIN = "Temperature Min.";
  public static final String TEMP_MAX = "Temperature Max.";
  private static ITaskService taskService = AppFactory.get().getTaskService();

  private static final String MAX_LENGTH_MSG = " cannot be greater than ";
  private static final String CHARACTERS = " characters";
  private static final String TEXT_FIELD_MAX_LENGTH_MSG = MAX_LENGTH_MSG + FieldLimits.TEXT_FIELD_MAX_LENGTH + CHARACTERS + CharacterConstants.DOT;
  private static final String MOBILE_PHONE_MAX_LENGTH_MSG = MAX_LENGTH_MSG + FieldLimits.MOBILE_PHONE_MAX_LENGTH + CHARACTERS + CharacterConstants.DOT;
  private static final String LAND_PHONE_MAX_LENGTH_MSG = MAX_LENGTH_MSG + FieldLimits.LAND_PHONE_MAX_LENGTH + CHARACTERS + CharacterConstants.DOT;
  private static final String EMAIL_MAX_LENGTH_MSG = MAX_LENGTH_MSG + FieldLimits.EMAIL_MAX_LENGTH + CHARACTERS + CharacterConstants.DOT;
  private static final String MATERIAL_SHORT_NAME_MAX_LENGTH_MSG = MAX_LENGTH_MSG + FieldLimits.MATERIAL_SHORTNAME_MAX_LENGTH + CHARACTERS;
  private static final String MATERIAL_DESC_MAX_LENGTH_MSG = MAX_LENGTH_MSG + FieldLimits.MATERIAL_DESCRIPTION_MAX_LENGTH + CHARACTERS + CharacterConstants.DOT;
  private static final String MATERIAL_ADD_INFO_MAX_LENGTH_MSG = MAX_LENGTH_MSG + FieldLimits.MATERIAL_ADDITIONAL_INFO_MAX_LENGTH + CHARACTERS + CharacterConstants.DOT;
  private static final String STREET_ADDRESS_MAX_LENGTH_MSG = MAX_LENGTH_MSG + FieldLimits.STREET_ADDRESS_MAX_LENGTH + CHARACTERS + CharacterConstants.DOT;
  private static final String ERROR_COUNT_MSG = "Remaining number of errors: ";
  private static final String ERRORS_TRUNCATED_MSG = "... Message truncated due to too many errors. ";
  private static final String SALT_HASH_SEPARATOR = "####";

  private BulkUploadMgr() {

  }
  // Get the display name of a given type
  public static String getDisplayName(String type, Locale locale) {
    ResourceBundle bundle = Resources.get().getBundle("Messages", locale);
    switch(type) {
      case TYPE_USERS:
        return bundle.getString("users");
      case TYPE_MATERIALS:
        return bundle.getString("materials");
      case TYPE_KIOSKS:
        return bundle.getString("kiosks");
      case TYPE_TRANSACTIONS:
      case TYPE_TRANSACTIONS_CUM_INVENTORY_METADATA:
        return bundle.getString("transactions");
      case TYPE_INVENTORY:
        return bundle.getString("inventory");
      case TYPE_ASSETS:
        return bundle.getString("assets");
      default:
        return null;
    }
  }

  // Get the operation display name
  public static String getOpDisplayName(String op) {
    if (OP_ADD.equals(op)) {
      return "add";
    } else if (OP_EDIT.equals(op)) {
      return "edit";
    } else if (OP_DELETE.equals(op)) {
      return "delete";
    } else {
      return "unknown";
    }
  }

  // Get the key to the Uploaded object
  public static String getUploadedKey(Long domainId, String type, String userId) {
    // Get the user's role
    String role = null;
    try {
      UsersService as = StaticApplicationContext.getBean(UsersServiceImpl.class);
      role = as.getUserAccount(userId).getRole();
    } catch (Exception e) {
      xLogger.warn(
          "{0} when getting user's role for uploaded type {1} from user {2} in domain {3}: {4}",
          e.getClass().getName(), type, userId, domainId, e.getMessage(), e);
    }
    String key = domainId + "." + type;
    if (!isUploadMasterData(type)
        || SecurityUtil.compareRoles(role, SecurityConstants.ROLE_DOMAINOWNER)
        < 0) // its not master data (i.e. transactions) and user is below Admin. - i.e. manager or below (master data is visible to all admins)
    {
      key += "." + userId;
    }
    return JDOUtils.createUploadedKey(key, "0", Constants.LANG_DEFAULT);
  }

  // Get an uploaded object
  public static IUploaded getUploaded(Long domainId, String type, String userId) {
    xLogger.fine("Entered getUploaded");
    IUploaded uploaded = null;
    String key = getUploadedKey(domainId, type, userId);
    try {
      UploadService us = StaticApplicationContext.getBean(UploadServiceImpl.class);
      uploaded = us.getUploaded(key);
    } catch (ObjectNotFoundException e) {
      xLogger.info("No Uploaded yet for key {0}...", key);
    } catch (Exception e) {
      xLogger.warn("{0} when getting uploaded object for type {1}, userId {2}: {3}",
          e.getClass().getName(), type, userId, e.getMessage());
    }
    xLogger.fine("Exiting getUploaded");
    return uploaded;
  }

  // Get a line of error message. Trim the message to 1800 characters and append a message with the remaining error count
  public static String getErrorMessageString(long offset, String csvLine, String operation,
                                             String message, int errorCount) {
    StringBuilder errMsgSb = new StringBuilder();
    errMsgSb.append(offset).append(BulkUploadMgr.INTRALINE_DELIMITER).append(csvLine).append(BulkUploadMgr.INTRALINE_DELIMITER).append(operation).append(BulkUploadMgr.INTRALINE_DELIMITER).append(message);
    if (errMsgSb.length() <= ERROR_MESSAGE_MAX_LENGTH) {
      return errMsgSb.toString();
    }
    return getTrimmedErrorMessageString(errMsgSb.toString(), errorCount);
  }

  // Given a line, get a message object
  public static List<ErrMessage> getErrorMessageObjects(String uploadedKey) {
    xLogger.fine("Entered getErrorMessageObjects: uploadedKey = {0}", uploadedKey);
    if (uploadedKey == null) {
      return Collections.emptyList();
    }
    List<String> allMsgs = getUploadedMessages(uploadedKey);
    if (allMsgs == null || allMsgs.isEmpty()) {
      return Collections.emptyList();
    }
    List<ErrMessage> errors = new ArrayList<>();
    for (String allMsg : allMsgs) {
      String[] array = allMsg.split(INTRALINE_DELIMITER);
      if (array.length < 4) {
        return Collections.emptyList();
      }
      ErrMessage err = new ErrMessage();
      try {
        err.offset = Long.parseLong(array[0]);
        err.csvLine = array[1];
        err.operation = array[2];
        if (array[3] != null) {
          String[] msgs = array[3].split(MESSAGE_DELIMITER);
          err.messages = new ArrayList<>();
          Collections.addAll(err.messages, msgs);
        }
        errors.add(err);
      } catch (Exception e) {
        xLogger.warn("Exception {0} when getting ErrMessage object for key {1}: {2}",
            e.getClass().getName(), uploadedKey, e.getMessage());
      }
    }
    return errors;
  }

  // Get the CSV header format for a given type
  public static String getCSVFormat(String type, Locale locale) {
    if (type == null) {
      return null;
    }
    if (locale == null) {
      locale = new Locale(Constants.LANG_DEFAULT, "");
    }
    IHeader header = null;
    if (TYPE_USERS.equals(type)) {
      header = new UsersHeader();
    } else if (TYPE_MATERIALS.equals(type)) {
      header = new MaterialsHeader();
    } else if (TYPE_KIOSKS.equals(type)) {
      header = new KiosksHeader();
    } else if (TYPE_TRANSACTIONS.equals(type)) {
      header = new TransactionsHeader();
    } else if (TYPE_INVENTORY.equals(type)) {
      header = new InventoryHeader();
    } else if (TYPE_TRANSACTIONS_CUM_INVENTORY_METADATA.equals(type)) {
      header = new MnlTransactionHeader();
    } else if (TYPE_TIMEZONES.equals(type)) {
      return getTimezonesCSV();
    } else if (TYPE_ASSETS.equals(type)) {
      header = new AssetsHeader();
    }
    return header != null ? header.getUploadableCSVHeader(locale, type) : null;
  }

  public static EntityContainer processEntity(String type, String csvLine, Long domainId,
                                              String sourceUserId, int source) {
    xLogger.fine("Entered BulkUploadMgr.processEntity");
    if (type == null || csvLine == null || csvLine.isEmpty()) {
      return null;
    }
    EntityContainer entityContainer = null;
    String[] tokens = StringUtil.getCSVTokens(csvLine);
    UsersService usersService = StaticApplicationContext.getBean(UsersService.class);
    SecurityMgr.setSessionDetails(usersService.getUserAccount(sourceUserId));
    if (TYPE_USERS.equals(type)) {
      entityContainer = processUserEntity(tokens, domainId, sourceUserId);
    } else if (TYPE_MATERIALS.equals(type)) {
      entityContainer = processMaterialEntity(tokens, domainId, sourceUserId);
    } else if (TYPE_KIOSKS.equals(type)) {
      entityContainer = processKioskEntity(tokens, domainId, sourceUserId, source);
    } else if (TYPE_INVENTORY.equals(type)) {
      entityContainer = processInventoryEntity(tokens, domainId, sourceUserId);
    } else if (TYPE_ASSETS.equals(type)) {
      entityContainer = processAssetEntity(tokens, domainId, sourceUserId);
    } else {
      xLogger.warn("Unkown type {0} in BulkUploadMgr.processEntity() by user {1} in domain {2}",
          type, sourceUserId, domainId);
    }

    xLogger.fine("Exiting BulkUploadMgr.processEntity");
    return entityContainer;
  }

  // Get the Entity for a given CSV record of a given type
  public static EntityContainer processEntity(String type, String csvLine, Long domainId,
                                              String sourceUserId) {
    return processEntity(type, csvLine, domainId, sourceUserId, SourceConstants.UPLOAD);
  }

  private static EntityContainer processAssetEntity(String[] tokens, Long domainId,
                                                    String sourceUserId) {
    xLogger.fine("Entered processAssetEntity");
    ResourceBundle backendMessages;
    EntityContainer ec = new EntityContainer();

    try {
      if (tokens == null || tokens.length == 0) {
        throw new ServiceException("No fields specified");
      }

      UsersService as = StaticApplicationContext.getBean(UsersServiceImpl.class);
      AssetManagementService ams = StaticApplicationContext.getBean(
          AssetManagementServiceImpl.class);
      IUserAccount su = as.getUserAccount(sourceUserId);
      backendMessages = Resources.get().getBundle("BackendMessages", su.getLocale());
      //Entity Name
      int i = 0;
      int size = tokens.length;
      String eName = tokens[i].trim();
      if (eName.length() > 50) {
        throw new ServiceException(backendMessages.getString("kiosk")
            + " name: Name is greater than 50 characters. Please specify a valid " + backendMessages
            .getString("kiosks.lowercase") + " name.");
      }

      Long kioskId = null;
      PersistenceManager pm = PMF.get().getPersistenceManager();
      List<String> tags = new ArrayList<>(1);
      Query q = pm.newQuery("select kioskId from " + JDOUtils.getImplClass(IKiosk.class).getName()
              + " where dId.contains(domainIdParam) && nName == nameParam parameters Long domainIdParam, String nameParam");
      try {
        @SuppressWarnings("unchecked")
        List<Long> list = (List<Long>) q.execute(domainId, eName.toLowerCase());
        if (list != null && !list.isEmpty()) {
          kioskId = list.get(0);
            EntitiesService entitiesService = StaticApplicationContext.getBean(
                EntitiesServiceImpl.class);
            tags = entitiesService.getAssetTagsToRegister(kioskId);
        }
        xLogger.fine(
            "BulkUploadMgr.processAssetEntity: resolved kiosk {0} to {1}; list returned {2} items",
            eName.toLowerCase(), kioskId, (list == null ? "NULL" : list.size()));
      } finally {
        try {
          q.closeAll();
        } catch (Exception ignored) {

        }
        pm.close();
      }

      //Skipping entity details, moving to Fridge details
      i += 5;
      IAsset monitoredAsset = null;
      Map<String, Object> variableMap = new HashMap<>(7), metaDataMap = new HashMap<>(1);
      if (i < size) {
        variableMap.put(AssetUtil.ASSET_NAME, tokens[i].trim());
        if (++i < size) {
          variableMap.put(AssetUtil.SERIAL_NUMBER, tokens[i].trim());
          if (++i < size) {
            variableMap.put(AssetUtil.MANUFACTURER_NAME, tokens[i].trim().toLowerCase());
            if (++i < size) {
              variableMap.put(AssetUtil.ASSET_MODEL, tokens[i].trim());
              metaDataMap.put(AssetUtil.DEV_MODEL, tokens[i].trim());
              variableMap.put(AssetUtil.TAGS, tags);
              if(++i < size) {
                addYearOfManufacture(variableMap,metaDataMap,tokens[i].trim(),backendMessages);
              }
              if (++i < size) {
                addUsersToVariableMap(variableMap,tokens[i].trim(),domainId,AssetUtil.OWNERS);
              }
              if (++i < size) {
                addUsersToVariableMap(variableMap,tokens[i].trim(),domainId,AssetUtil.MAINTAINERS);
              }
              monitoredAsset = AssetUtil.verifyAndRegisterAsset(domainId, sourceUserId, kioskId,
                  variableMap, metaDataMap);
            } else {
              throw new ServiceException("No fields specified after manufacturer name");
            }
          } else {
            throw new ServiceException("No fields specified after serial number");
          }
        } else {
          throw new ServiceException("No fields specified after asset type");
        }
      }

      //Processing sensor devices
      IAsset sensorAsset = null;
      if (++i < size) {
        variableMap = new HashMap<>(7);
        metaDataMap = new HashMap<>(5);
        String serialNumber = tokens[i].trim();
        if (!serialNumber.isEmpty()) {
          //check config to get default temperature logger
          variableMap.put(AssetUtil.SERIAL_NUMBER, serialNumber);
          variableMap.put(AssetUtil.ASSET_TYPE, IAsset.TEMP_DEVICE);

          if (++i < size) {
            String mobileNumber = getAssetValidPhone(tokens[i].trim());
            if (mobileNumber != null) {
              metaDataMap.put(AssetUtil.GSM_SIM_PHN_NUMBER, mobileNumber);
            } else {
              ec.messages.add("Mobile phone: Number (" + tokens[i].trim()
                  + ") format is invalid. It should be +[country-code][phone-number-without-spacesORdashes]");
            }
          }
          if (++i < size) {
            metaDataMap.put(AssetUtil.GSM_SIM_SIMID, tokens[i].trim());
          }
          if (++i < size) {
            metaDataMap.put(AssetUtil.GSM_SIM_NETWORK_PROVIDER, tokens[i].trim());
          }

          if (++i < size) {
            String mobileNumber = getAssetValidPhone(tokens[i].trim());
            if (mobileNumber != null) {
              metaDataMap.put(AssetUtil.GSM_ALTSIM_PHN_NUMBER, mobileNumber);
            } else {
              ec.messages.add("Alternate mobile phone: Number (" + tokens[i].trim()
                  + ") format is invalid. It should be +[country-code][phone-number-without-spacesORdashes]");
            }
          }
          if (++i < size) {
            metaDataMap.put(AssetUtil.GSM_ALTSIM_SIMID, tokens[i].trim());
          }
          if (++i < size) {
            metaDataMap.put(AssetUtil.GSM_ALTSIM_NETWORK_PROVIDER, tokens[i].trim());
          }

          if (++i < size) {
            metaDataMap.put(AssetUtil.DEV_IMEI, tokens[i].trim());
          }

          String manufacturer = null;
          String model = null;
          if (++i < size) {
            manufacturer = tokens[i].trim();
          }
          if (++i < size) {
            model = tokens[i].trim();
          }
          if (++i < size) {
            addYearOfManufacture(variableMap,metaDataMap,tokens[i].trim(),backendMessages);
          }
          if (++i < size) {
            addUsersToVariableMap(variableMap,tokens[i].trim(),domainId,AssetUtil.OWNERS);
          }
          if (++i < size) {
            addUsersToVariableMap(variableMap,tokens[i].trim(),domainId,AssetUtil.MAINTAINERS);
          }
          if ((StringUtils.isEmpty(manufacturer) && StringUtils.isEmpty(model)) || (
              StringUtils.isNotEmpty(manufacturer) && StringUtils.isNotEmpty(model))) {
            AssetConfig ac = DomainConfig.getInstance(domainId).getAssetConfig();
            List<String> vendorIds = fetchVendorsForType(ac.getVendorIds(), IAsset.TEMP_DEVICE);
            List<String> models = ac.getAssetModels();

            AssetSystemConfig asc = AssetSystemConfig.getInstance();
            if (asc == null) {
              throw new ConfigurationException();
            }

            if (StringUtils.isEmpty(manufacturer)) { //update from configuration
              if (vendorIds != null && vendorIds.size() == 1) {
                List<String> modelIds =
                    fetchAssetModelsForType(models, IAsset.TEMP_DEVICE, vendorIds.get(0));
                if (modelIds != null && modelIds.size() == 1) {
                  variableMap.put(AssetUtil.MANUFACTURER_NAME,
                      asc.getManufacturerName(IAsset.TEMP_DEVICE, vendorIds.get(0)));
                  variableMap.put(AssetUtil.ASSET_MODEL, modelIds.get(0));
                  metaDataMap.put(AssetUtil.DEV_MODEL, modelIds.get(0));
                } else {
                  throw new ServiceException(
                      backendMessages.getString("monitoring.asset.model.default.error"));
                }
              } else {
                throw new ServiceException(
                    backendMessages.getString("monitoring.asset.manufacturer.default.error"));
              }
            } else {
              String vendorId = asc.getManufacturerId(IAsset.TEMP_DEVICE, manufacturer);
              if (vendorIds != null && vendorIds
                  .contains(vendorId)) { //check whether valid manufacturer and model and update
                variableMap.put(AssetUtil.MANUFACTURER_NAME, manufacturer);
                String newModel = IAsset.TEMP_DEVICE + Constants.KEY_SEPARATOR + vendorId
                        + Constants.KEY_SEPARATOR + model;
                if (models.contains(newModel)) {
                  variableMap.put(AssetUtil.ASSET_MODEL, model);
                  metaDataMap.put(AssetUtil.DEV_MODEL, model);
                } else {
                  throw new ServiceException(
                      backendMessages.getString("monitoring.asset.model.name") + " '" + model + "' "
                          + backendMessages.getString("monitoring.asset.valid.model.name"));
                }
              } else {
                throw new ServiceException(
                    backendMessages.getString("monitoring.asset.manufacturer.name") + " '"
                        + manufacturer + "' " + backendMessages
                        .getString("monitoring.asset.valid.manufacturer.name"));
              }
            }
          } else {
            if (StringUtils.isEmpty(manufacturer)) {
              throw new ServiceException(
                  backendMessages.getString("monitoring.asset.missing.manufacturer"));
            } else {
              throw new ServiceException(
                  backendMessages.getString("monitoring.asset.missing.model"));
            }
          }
          variableMap.put(AssetUtil.TAGS, tags);
          sensorAsset =
              AssetUtil.verifyAndRegisterAsset(domainId, sourceUserId, kioskId, variableMap,
                  metaDataMap);
        }
      }

      if (monitoredAsset != null && sensorAsset != null) {
        IAssetRelation assetRelation = ams.getAssetRelationByRelatedAsset(sensorAsset.getId());
        if (assetRelation != null && !Objects
            .equals(assetRelation.getAssetId(), monitoredAsset.getId())) {
          throw new ServiceException("Given monitoring asset " + sensorAsset.getSerialId()
              + " is related to another asset, before adding new relationship, remove existing relationship.");
        }

        assetRelation = ams.getAssetRelationByAsset(monitoredAsset.getId());
        if (assetRelation != null && !Objects
            .equals(assetRelation.getRelatedAssetId(), sensorAsset.getId())) {
          throw new ServiceException("Given monitored asset " + monitoredAsset.getSerialId()
              + " is related to another asset, before adding new relationship, remove existing relationship.");
        }

        //Creating asset relation
        AssetUtil.createAssetRelationship(domainId, monitoredAsset, sensorAsset,
            (List<String>) variableMap.get(AssetUtil.TAGS));
      }
    } catch (ServiceException | BadRequestException e) {
      ec.messages.add(e.getMessage());
    } catch (Exception e) {
      ec.messages.add("Error: " + e.getMessage());
      xLogger.warn("Exception: {0}, Message: {1}", e.getClass().getName(), e.getMessage(), e);
    } finally {
      xLogger.fine("Exiting processAssetEntity");
    }

    return ec;
  }

  private static void addYearOfManufacture(Map<String,Object> variableMap,
                                           Map<String,Object> metaDataMap, String yom, ResourceBundle backendMessages) throws ServiceException {
    if(StringUtils.isNotEmpty(yom)){
      validateYearOfManufacture(yom, backendMessages);
      variableMap.put(ASSET_YOM, yom);
      metaDataMap.put(DEV_YOM, yom);
    }
  }

  private static void addUsersToVariableMap(Map<String,Object> variableMap, String users,Long domainId,String type) throws ServiceException {
    if (StringUtils.isNotEmpty(users)) {
      try {
        checkUsersExistInDomain(users, domainId);
        variableMap.put(type,
            new ArrayList<>(getUniqueUserIds(users, CharacterConstants.COMMA)));
      } catch (Exception e) {
        throw new ServiceException("Issue with asset users of type : " + (type.equals(AssetUtil.MAINTAINERS) ? "maintainers": "owners"));
      }
    }
  }

  private static void checkUsersExistInDomain(String users, Long domainId) {

    Set<String> errors = new HashSet<>();
    Set<String> userIdsSet = getUniqueUserIds(users,CharacterConstants.COMMA);
    UsersService as = StaticApplicationContext.getBean(UsersServiceImpl.class);
    for (String userId : userIdsSet) {
      try {
        if (!as.getUserAccount(userId).getDomainId().equals(domainId)) {
          errors.add("User with ID " + userId + " does not belong to this domain");
          continue;
        }
      } catch (ObjectNotFoundException e) {
        errors.add("User with ID '" + userId + "' not found.");
      }
    }
    if (errors.size() > 0) {
      StringBuilder builder = new StringBuilder();
      errors.stream().forEach(error ->builder.append(error).append(CharacterConstants.NEWLINE));
      throw new RuntimeException(builder.toString());
    }
  }

  private static Boolean validateYearOfManufacture(String yearOfManufactureString, ResourceBundle backendMessages)
      throws ServiceException {

    if (!yearOfManufactureString.matches("\\d+")) {
      throw new ServiceException(backendMessages.getString("year.of.manufacture.invalid.error"));
    }

    Integer yearOfManufacture = Integer.valueOf(yearOfManufactureString);

    int currentYear = Calendar.getInstance().get(Calendar.YEAR);

    if(yearOfManufacture < LOWER_BOUND_FOR_YOM) {
      throw new ServiceException(backendMessages.getString("year.of.manufacture.range"));
    }

    if(yearOfManufacture > currentYear) {
      throw new ServiceException(backendMessages.getString("year.of.manufacture.future") + " " + currentYear);
    }

    return Boolean.TRUE;
  }

  private static List<String> fetchVendorsForType(List<String> vendorIds, Integer type) {
    List<String> vendors = null;
    if (!CollectionUtils.isEmpty(vendorIds)) {
      vendors = new ArrayList<>(1);
      for (String vendor : vendorIds) {
        if (type == 1 && !vendor.contains(Constants.KEY_SEPARATOR)) {
          vendors.add(vendor);
        } else if (type != 1 && vendor.startsWith(type + Constants.KEY_SEPARATOR)) {
          vendors.add(vendor.substring(vendor.lastIndexOf(Constants.KEY_SEPARATOR) + 1));
        }
      }
    }
    return vendors;
  }

  private static List<String> fetchAssetModelsForType(List<String> modelIds, Integer type,
                                                      String manufacturer) {
    List<String> assetModels = null;
    if (!CollectionUtils.isEmpty(modelIds) && type != null && StringUtils
        .isNotEmpty(manufacturer)) {
      assetModels = new ArrayList<>(1);
      for (String model : modelIds) {
        if (model
            .startsWith(type + Constants.KEY_SEPARATOR + manufacturer + Constants.KEY_SEPARATOR)) {
          assetModels.add(model.substring(model.lastIndexOf(Constants.KEY_SEPARATOR) + 1));
        }
      }
    }
    return assetModels;
  }

  private static EntityContainer processInventoryEntity(String[] tokens, Long domainId,
                                                        String sourceUserId) {
    xLogger.fine("Entered processInventoryEntity");
    ResourceBundle backendMessages;
    EntityContainer ec = new EntityContainer();
    if (tokens == null || tokens.length == 0) {
      ec.messages.add("No fields specified");
      return ec;
    }
    try {
      UsersService as = StaticApplicationContext.getBean(UsersServiceImpl.class);
      DomainsService ds = StaticApplicationContext.getBean(DomainsServiceImpl.class);
      IUserAccount su = as.getUserAccount(sourceUserId);
      backendMessages = Resources.get().getBundle("BackendMessages", su.getLocale());
      IDomainPermission
          permission =
          ds.getLinkedDomainPermission(
              su.getRole().equalsIgnoreCase(SecurityConstants.ROLE_DOMAINOWNER) ? su.getDomainId()
                  : domainId);
      int i = 0;
      int size = tokens.length;
      String op = tokens[i].trim(); // operation
      if (!op.isEmpty()) {
        ec.operation = op;
      }

      if (++i == size) {
        ec.messages.add("No fields specified");
        return ec;
      }
      if (!OP_ADD.equals(ec.operation) && !OP_EDIT.equals(ec.operation) && !OP_DELETE
          .equals(ec.operation)) {
        ec.messages.add("Invalid Operation. Please enter a = add / e = edit / d = delete.");
        return ec;
      }

      //Entity Name
      String eName = tokens[i].trim();
      if (eName.isEmpty() || eName.length() > 50) {
        ec.messages.add(backendMessages.getString("kiosk")
            + " name: Name is not specified or is greater than 50 characters. Please specify a valid "
            + backendMessages.getString("kiosks.lowercase") + " name.");
        return ec;
      }
      Long kioskId = null;
      PersistenceManager pm = PMF.get().getPersistenceManager();
      Query
          q =
          pm.newQuery("select kioskId from " + JDOUtils.getImplClass(IKiosk.class).getName()
              + " where dId.contains(domainIdParam) && nName == nameParam parameters Long domainIdParam, String nameParam");
      try {
        @SuppressWarnings("unchecked")
        List<Long> list = (List<Long>) q.execute(domainId, eName.toLowerCase());
        if (list != null && !list.isEmpty()) {
          kioskId = list.get(0);
        }
        xLogger.fine(
            "BulkUploadMgr.processInventoryEntity: resolved kiosk {0} to {1}; list returned {2} items",
            eName.toLowerCase(), kioskId, (list == null ? "NULL" : list.size()));
      } finally {
        try {
          q.closeAll();
        } catch (Exception ignored) {

        }
        pm.close();
      }
      if (kioskId == null) {
        ec.messages.add(backendMessages.getString("kiosk") + " " + eName + " does not exist.");
        return ec;
      }

      // Material Name
      Long materialId = null;
      String mName = null;
      if (++i < size) {
        mName = tokens[i].trim();
        if (mName.isEmpty()) {
          ec.messages
              .add("Material name: Name is not specified. Please specify a valid material name.");
          return ec;
        }
        materialId = getMaterialId(domainId, mName, null);
        if (materialId == null) {
          ec.messages.add("Material '" + mName + "' not found.");
          return ec;
        }
      }

      InventoryManagementService ims =
          StaticApplicationContext.getBean(InventoryManagementServiceImpl.class);
      IInvntry invntry = ims.getInventory(kioskId, materialId);

      boolean isAdd = (OP_ADD.equals(ec.operation));
      boolean isDel = (OP_DELETE.equals(ec.operation));
      if (permission != null) {
        boolean iSU = SecurityConstants.ROLE_SUPERUSER.equalsIgnoreCase(su.getRole());
        if (isAdd && !(iSU || permission.isInventoryAdd())) {
          ec.messages.add("Cannot add inventory. User does not have permission to add.");
          return ec;
        } else if (isDel && !(iSU || permission.isInventoryRemove())) {
          ec.messages.add("Cannot delete inventory. User does not have permission to delete.");
          return ec;
        } else if (!isAdd && !isDel && !(iSU || permission.isInventoryEdit())) {
          ec.messages.add("Cannot edit inventory. User does not have permission to edit.");
          return ec;
        }
      }
      if (isDel) {
        if (invntry == null) {
          ec.messages.add(
              "Inventory material '" + mName + "' is not available at '" + backendMessages
                  .getString("kiosk.lowercase") + " " + eName + "' for delete.");
          return ec;
        }
        ims.removeInventory(domainId, kioskId, Collections.singletonList(materialId));
        return ec;

      } else if (isAdd) {
        if (invntry == null) {
          invntry = JDOUtils.createInstance(IInvntry.class);
          invntry.setDomainId(domainId);
          invntry.setKioskId(kioskId);
          invntry.setMaterialId(materialId);
          invntry.setKioskName(eName);
          invntry.setMaterialName(mName);
          invntry.setUpdatedBy(sourceUserId);
          invntry.setTimestamp(new Date());
        } else {
          ec.messages.add(
              "Inventory material '" + mName + "' is already available at '" + backendMessages
                  .getString("kiosk.lowercase") + " " + eName + "' , cannot add this.");
          return ec;
        }
      } else { //Edit
        if (invntry == null) {
          ec.messages.add(
              "Inventory material '" + mName + "' is not available at '" + backendMessages
                  .getString("kiosk.lowercase") + " " + eName + "' for edit.");
          return ec;
        }
      }

      DomainConfig dc = DomainConfig.getInstance(domainId);
      boolean isMinMaxAbsoluteQty = dc.getInventoryConfig().isMinMaxAbsolute();
      //Min.
      if (++i < size) {
        String min = tokens[i].trim();
        if (!min.isEmpty()) {
          if (isMinMaxAbsoluteQty) {
            if (min.contains(CharacterConstants.DOT)) {
              ec.messages.add("Invalid value " + min + " for Min. It should be a whole number.");
              return ec;
            }
            try {
              invntry.setReorderLevel(new BigDecimal(min));
              if (BigUtil.lesserThanZero(invntry.getReorderLevel()) || BigUtil
                  .gtMax(invntry.getReorderLevel())) {
                ec.messages.add(
                    "Invalid value " + min + " for Min. It should be between 0 and 1 trillion");
                return ec;
              }
            } catch (NumberFormatException e) {
              ec.messages.add("Invalid value " + min + " for Min. It should be a number");
              return ec;
            }
          } else {
            try {
              invntry.setMinDuration(new BigDecimal(min));
              if (BigUtil.lesserThanZero(invntry.getMinDuration()) || BigUtil
                  .gtMax(invntry.getMinDuration())) {
                ec.messages.add("Invalid value " + min
                    + " for Minimum duration of stock. It should be between 0 and 1 trillion");
                return ec;
              }
            } catch (NumberFormatException e) {
              ec.messages.add(
                  "Invalid value " + min + " for Minimum duration of stock. It should be a number");
              return ec;
            }
          }
        }
      }

      //Max.
      if (++i < size) {
        String max = tokens[i].trim();
        if (max.isEmpty()) {
          if (isAdd) {
            max = "0";
          } else {
            //restore the existing max value for validation against min.
            if (isMinMaxAbsoluteQty) {
              max = invntry.getMaxStock().toPlainString();
            } else {
              max = invntry.getMaxDuration().toPlainString();
            }
          }
        }
        if (!max.isEmpty()) {
          BigDecimal maxBD = new BigDecimal(max);
          if (isMinMaxAbsoluteQty) {
            try {
              if (max.contains(CharacterConstants.DOT)) {
                ec.messages.add("Invalid value " + max + " for Max. It should be a whole number.");
                return ec;
              }
              if (BigUtil.equalsZero(invntry.getReorderLevel()) || BigUtil
                  .lesserThan(invntry.getReorderLevel(), maxBD)) {
                invntry.setMaxStock(maxBD);
                if (BigUtil.lesserThanZero(invntry.getMaxStock()) || BigUtil
                    .gtMax(invntry.getMaxStock())) {
                  ec.messages.add(
                      "Invalid value " + max + " for Max. It should be between 0 and 1 trillion");
                  return ec;
                }
              } else {
                ec.messages
                    .add("Min is greater than or equal to max for " + invntry.getMaterialName());
                return ec;
              }
            } catch (NumberFormatException e) {
              ec.messages.add("Invalid value " + max + " for Max. It should be a number");
              return ec;
            }
          } else {
            try {
              if (BigUtil.equalsZero(invntry.getMinDuration()) || BigUtil
                  .lesserThan(invntry.getMinDuration(), maxBD)) {
                invntry.setMaxDuration(maxBD);
                if (BigUtil.lesserThanZero(invntry.getMaxDuration()) || BigUtil
                    .gtMax(invntry.getMaxDuration())) {
                  ec.messages.add("Invalid value " + max
                      + " for Maximum duration of stock. It should be between 0 and 1 trillion");
                  return ec;
                }
              } else {
                ec.messages.add(
                    "Minimum duration of stock is greater than or equal to maximum duration of stock for "
                        + invntry.getMaterialName());
                return ec;
              }
            } catch (NumberFormatException e) {
              ec.messages.add(
                  "Invalid value " + max + " for Maximum duration of stock. It should be a number");
              return ec;
            }
          }
        }
      }

      //Consumption rate(s)
      if (++i < size) {
        String cr = tokens[i].trim();
        if (!cr.isEmpty()) {
          try {
            invntry.setConsumptionRateManual(new BigDecimal(cr));
            if (BigUtil.lesserThanZero(invntry.getConsumptionRateManual()) || BigUtil
                .gtMax(invntry.getConsumptionRateManual())) {
              ec.messages.add("Invalid value " + cr
                  + " for Manual consumption rate. It should be between 0 and 1 trillion");
              return ec;
            }
            if (!isMinMaxAbsoluteQty) {
              if (InventoryConfig.CR_MANUAL == dc.getInventoryConfig().getConsumptionRate()) {
                invntry.setReorderLevel(
                    invntry.getMinDuration().multiply(invntry.getConsumptionRateManual()));
                invntry.setMaxStock(
                    invntry.getMaxDuration().multiply(invntry.getConsumptionRateManual()));
              }
            }
          } catch (NumberFormatException e) {
            ec.messages
                .add("Invalid value " + cr + " for Consumption rate(s). It should be a number");
            return ec;
          }
        }
      }

      //Retailer's price
      if (++i < size) {
        String price = tokens[i].trim();
        if (!price.isEmpty()) {
          try {
            invntry.setRetailerPrice(new BigDecimal(price));
            if (BigUtil.lesserThanZero(invntry.getRetailerPrice()) || BigUtil
                .gtMax(invntry.getRetailerPrice())) {
              ec.messages.add(
                  "Invalid value " + price + " for Price. It should be between 0 and 1 trillion");
              return ec;
            }
          } catch (NumberFormatException e) {
            ec.messages
                .add("Invalid value " + price + " for Retailer's price. It should be a number");
            return ec;
          }
        }
      }

      //Tax
      if (++i < size) {
        String tax = tokens[i].trim();
        if (!tax.isEmpty()) {
          try {
            invntry.setTax(new BigDecimal(tax));
            if (BigUtil.lesserThanZero(invntry.getTax()) || BigUtil.gtMax(invntry.getTax())) {
              ec.messages
                  .add("Invalid value " + tax + " for Tax. It should be between 0 and 1 trillion");
              return ec;
            }
          } catch (NumberFormatException e) {
            ec.messages.add("Invalid value " + tax + " for Tax. It should be a number");
            return ec;
          }
        }
      }

      //Inventory Model
      if (++i < size) {
        String im = tokens[i].trim();
        if (!im.isEmpty()) {
          if (INVNTRY_MODEL_USERSPECIFIED.equals(im)) {
            invntry.setInventoryModel(IInvntry.MODEL_NONE);
          } else if (IInvntry.MODEL_SQ.equals(im)) {
            invntry.setInventoryModel(IInvntry.MODEL_SQ);
          } else {
            ec.messages.add("Inventory model '" + im + "' is not supported.");
            return ec;
          }
        }
      }

      //Service Level
      if (++i < size) {
        String ser = tokens[i].trim();
        if (!ser.isEmpty()) {
          if (invntry.getInventoryModel() == null || IInvntry.MODEL_NONE
              .equals(invntry.getInventoryModel())) {
            ec.messages
                .add("Cannot set service-level for user specified replenishment inventory model.");
            return ec;
          } else {
            try {
              int s = Integer.parseInt(ser);
              if (s < 65 || s > 99) {
                throw new NumberFormatException();
              }
              invntry.setServiceLevel(s);
            } catch (NumberFormatException e) {
              ec.messages.add("Service level: " + ser
                  + " is not valid. Please specify a valid number between 65-99.");
              return ec;
            }
          }
        }
      }

      if (isAdd) {
        // For bulk upload no overwrite for inventory details
        ims.addInventory(domainId, Collections.singletonList(invntry), false, sourceUserId);
      } else {
        ims.updateInventory(Collections.singletonList(invntry), sourceUserId);
      }
    } catch (Exception e) {
      ec.messages.add("Error: " + e.getMessage());
      xLogger.warn("Exception: {0}, Message: {1}", e.getClass().getName(), e.getMessage(), e);
    } finally {
      xLogger.fine("Exiting processInventoryEntity");
    }
    return ec;
  }

  private static EntityContainer processUserEntity(String[] tokens, Long domainId,
                                                   String sourceUserId) {
    xLogger.fine("Entered processUserEntity");
    ResourceBundle backendMessages;
    EntityContainer ec = new EntityContainer();
    if (tokens == null || tokens.length == 0) {
      ec.messages.add("No fields specified");
      return ec;
    }
    try {
      UsersService as = StaticApplicationContext.getBean(UsersServiceImpl.class);
      AuthenticationService aus = StaticApplicationContext.getBean(AuthenticationServiceImpl.class);
      IUserAccount u = null;
      IUserAccount su = as.getUserAccount(sourceUserId);
      DomainsService ds = StaticApplicationContext.getBean(DomainsServiceImpl.class);
      backendMessages = Resources.get().getBundle("BackendMessages", su.getLocale());
      IDomainPermission userDomainPermission = ds.getLinkedDomainPermission(su.getDomainId());
      IDomainPermission currentDomainPermission = ds.getLinkedDomainPermission(domainId);
      ConfigurationMgmtService cms =
          StaticApplicationContext.getBean(ConfigurationMgmtServiceImpl.class);
      IConfig c = cms.getConfiguration(IConfig.LOCATIONS);
      IConfig ln = cms.getConfiguration(IConfig.LANGUAGES);
      JSONObject jsonLocationObject, jsonLanguageObject, intermediateJsonObject = null;
      Set<String> countryKey, languageKey;
      countryKey = languageKey = null;
      // for auditlog
      String uname = CharacterConstants.EMPTY;

      int i = 0;
      int size = tokens.length;
      // operation
      String op = tokens[i].trim();
      if (!op.isEmpty()) {
        ec.operation = op;
      }
      if (++i == size) {
        ec.messages.add("No fields specified");
        return ec;
      }
      if (!isOperationValid(ec.operation)) {
        ec.messages.add("Invalid Operation. Please enter a = add / e = edit / d = delete.");
        return ec;
      }
      // User ID
      String userId = tokens[i].trim();
      if (userId.length() < FieldLimits.USERID_MIN_LENGTH || userId.length() > FieldLimits.USERID_MAX_LENGTH) {
        ec.messages.add("User ID: '" + userId
            + "'  is empty, or not between " + FieldLimits.USERID_MIN_LENGTH + "-" + FieldLimits.USERID_MAX_LENGTH + " characters. None of these are allowed.");
        return ec;
      }
      boolean isUserIdValid = userId.matches(PatternConstants.USERID);
      if (!isUserIdValid) {
        ec.messages.add("User ID: '" + userId + "is invalid. It can have only alphabets, numbers, dot, hyphen, underscore, @ and space.");
      }
      boolean isAdd = (OP_ADD.equals(ec.operation));
      boolean isEdit = (OP_EDIT.equals(ec.operation));
      boolean isDelete = (OP_DELETE.equals(ec.operation));
      if (userDomainPermission != null && currentDomainPermission != null) {
        boolean iSU = SecurityConstants.ROLE_SUPERUSER.equals(su.getRole());
        if (isAdd && !(iSU || userDomainPermission.isUsersAdd() || currentDomainPermission
            .isUsersAdd())) {
          ec.messages
              .add("Cannot add user '" + userId + "'. User does not have permission to add.");
          return ec;
        } else if (isEdit && !(iSU || userDomainPermission.isUsersEdit() || currentDomainPermission
            .isUsersEdit())) {
          ec.messages
              .add("Cannot edit user '" + userId + "'. User does not have permission to edit.");
          return ec;
        } else if (isDelete && !(iSU || userDomainPermission.isUsersRemove()
            || currentDomainPermission.isUsersRemove())) {
          ec.messages
              .add("Cannot delete user '" + userId + "'. User does not have permission to delete.");
          return ec;
        }
      }
      // Get the object, if present
      try {
        try {
          u = as.getUserAccount(userId);
        } catch (ObjectNotFoundException ignored) {
          u = as.getUserAccount(userId.toLowerCase());
        }
        if (!isAdd && !domainId.equals(u.getDomainId())) {
          ec.messages.add("Cannot access user '" + userId + "'. Permission denied.");
          return ec;
        }
      } catch (ObjectNotFoundException e) {
        // ignore
      }
      // Delete, if needed
      if (isDelete) {
        if (u == null) {
          ec.messages.add("Cannot delete. User " + userId + " does not exist");
          return ec;
        }
        if ((su.getRole().equals(SecurityConstants.ROLE_SERVICEMANAGER) && !u.getRole()
            .equals(SecurityConstants.ROLE_KIOSKOWNER))
            || (su.getRole().equals(SecurityConstants.ROLE_DOMAINOWNER) && u.getRole()
            .equals(SecurityConstants.ROLE_SUPERUSER))) {
          ec.messages.add("Cannot delete user '" + userId + "'. Permission denied.");
          return ec;
        }
        List<String> userIds = new ArrayList<>(1);
        userIds.add(userId.toLowerCase());
        as.deleteAccounts(domainId, userIds, sourceUserId);
        return ec;
      }
      // Check operation and instantiate accordingly
      if (isAdd) {
        if (u != null) {
          ec.messages
              .add("User with ID " + userId + " already exists. Cannot add this user again.");
          return ec;
        }
        // Set the other metadata - such as domainId, registeredby and creation timestamps
        u = JDOUtils.createInstance(IUserAccount.class);
        u.setUserId(userId.toLowerCase());
        u.setRegisteredBy(sourceUserId);
        u.setDomainId(domainId);
        u.setUpdatedBy(sourceUserId);
        u.setEnabled(true);
        u.setMemberSince(new Date());
      } else { // edit
        if (u == null) {
          ec.messages.add("User with ID " + userId + " not found. Cannot edit/delete this user.");
          return ec;
        }
        if ((su.getRole().equals(SecurityConstants.ROLE_SERVICEMANAGER) && !userId.equals(sourceUserId)
            && !u.getRole().equals(SecurityConstants.ROLE_KIOSKOWNER))
            || (su.getRole().equals(SecurityConstants.ROLE_DOMAINOWNER) && u.getRole()
            .equals(SecurityConstants.ROLE_SUPERUSER))) {
          ec.messages.add("Cannot edit user '" + userId + "'. Permission denied.");
          return ec;
        }
        u.setUpdatedBy(sourceUserId);
      }
      if (++i == size) {
        ec.messages.add("No fields specified after user ID");
        return ec;
      }
      // Password - get the password fields now, and process them later depending on add/edit
      String password = tokens[i].trim();

      if (++i == size) {
        ec.messages.add("No fields specified after password");
        return ec;
      }

      // Confirm password
      String confirmPassword = tokens[i].trim();
      if (++i == size) {
        ec.messages.add("No fields specified after Confirm Password");
        return ec;
      }
      // Role
      String role = tokens[i].trim();
      if (!SecurityConstants.ROLE_DOMAINOWNER.equals(role) && !SecurityConstants.ROLE_SERVICEMANAGER
          .equals(role) && !SecurityConstants.ROLE_KIOSKOWNER.equals(role)) {
        ec.messages.add("Role: Invalid role '" + role + "'. Role should be one of "
            + SecurityConstants.ROLE_DOMAINOWNER + " (Administrator) or "
            + SecurityConstants.ROLE_KIOSKOWNER + " (" + backendMessages.getString("kiosk")
            + " Operator) or " + SecurityConstants.ROLE_SERVICEMANAGER + " (" + backendMessages
            .getString("kiosk") + " Manager)");
        return ec;
      } else if ((su.getRole().equals(SecurityConstants.ROLE_SERVICEMANAGER) &&
          ((!userId.equals(sourceUserId) && !role.equals(SecurityConstants.ROLE_KIOSKOWNER) || (
              userId.equals(sourceUserId) && !role.equals(SecurityConstants.ROLE_SERVICEMANAGER)))))
          || (su.getRole().equals(SecurityConstants.ROLE_DOMAINOWNER) && role
          .equals(SecurityConstants.ROLE_SUPERUSER))) {
        ec.messages.add("Cannot edit user '" + userId + "'. Permission denied.");
        return ec;
      } else {
        u.setRole(role);
      }
      if (++i == size) {
        ec.messages.add("No fields specified after Role");
        return ec;
      }

      String permission = tokens[i].trim();
      if (!isPermissionValid(permission)) {
        ec.messages.add("Permission: Invalid value '" + permission + "'. Value should be either "
            + IUserAccount.PERMISSION_DEFAULT + " (Default) or " + IUserAccount.PERMISSION_VIEW
            + " (View only) or " + IUserAccount.PERMISSION_ASSET + " (Asset view only)");
      } else {
        u.setPermission(permission.isEmpty() ? IUserAccount.PERMISSION_DEFAULT : permission);
      }
      if (++i == size) {
        ec.messages.add("No fields specified after Permission");
        return ec;
      }

      String tokenExpiry = tokens[i].trim();
      if (!isTokenExpiryValid(tokenExpiry)) {
        ec.messages.add("Token expiry: Invalid value '" + tokenExpiry + "'. Value should be between "
            + FieldLimits.TOKEN_EXPIRY_MIN + " and " + FieldLimits.TOKEN_EXPIRY_MAX + " days");
      } else {
        u.setAuthenticationTokenExpiry(tokenExpiry.isEmpty() ? 0 : Integer.parseInt(tokenExpiry));
      }
      if (++i == size) {
        ec.messages.add("No fields specified after Token expiry");
        return ec;
      }

      // First name
      String firstName = tokens[i].trim();
      if (firstName.length() < FieldLimits.FIRSTNAME_MIN_LENGTH || firstName.length() > FieldLimits.TEXT_FIELD_MAX_LENGTH) {
        ec.messages.add("First name: '" + firstName + "' should be between " + FieldLimits.FIRSTNAME_MIN_LENGTH + "-" + FieldLimits.TEXT_FIELD_MAX_LENGTH + " characters");
      } else {
        boolean isAlpha = firstName.matches(PatternConstants.FIRSTNAME);
        if (isAlpha) {
          u.setFirstName(firstName);
        } else {
          ec.messages.add("First name can have only alphabets and space : " + firstName);
        }
        //for auditlog
        uname = firstName;
      }
      if (++i == size) {
        ec.messages.add("No fields specified after First Name");
        return ec;
      }
      // Last name (optional)
      String lastName = tokens[i].trim();
      if (lastName.length() > FieldLimits.TEXT_FIELD_MAX_LENGTH) {
        ec.messages.add("Last name: '" + lastName + CharacterConstants.S_QUOTE + TEXT_FIELD_MAX_LENGTH_MSG);
      } else {
        boolean isAlpha = lastName.matches(PatternConstants.LASTNAME);
        uname += CharacterConstants.SPACE + lastName;
        if (isAlpha) {
          u.setLastName(lastName);
        } else {
          ec.messages.add("Last name can have only alphabets and space : " + lastName);
        }
      }
      if (++i == size) {
        ec.messages.add("No fields specified after Last Name");
        return ec;
      }

      // Mobile phone
      String mobilePhone = tokens[i].trim();
      if (StringUtils.isNotEmpty(mobilePhone) && mobilePhone.length() > FieldLimits.MOBILE_PHONE_MAX_LENGTH) {
        ec.messages.add("Mobile phone: '" + mobilePhone + CharacterConstants.S_QUOTE + MOBILE_PHONE_MAX_LENGTH_MSG);
      }
      String validatedMobilePhone = validPhone(mobilePhone);
      if (validatedMobilePhone != null) {
        u.setMobilePhoneNumber(validatedMobilePhone);
      } else {
        ec.messages.add("Mobile phone: Number (" + mobilePhone
            + ") format is invalid. It should be +[country-code][space][phone-number-without-spacesORdashes]; ensure space between country code and number.");
      }
      if (++i == size) {
        ec.messages.add("No fields specified after Mobile Phone");
        return ec;
      }
      // Email
      String email = tokens[i].trim();
      if (!SecurityConstants.ROLE_KIOSKOWNER.equals(role) && email.isEmpty()) {
        ec.messages.add(
            "Email: Email is mandatory for all roles other than Operator");
      }
      if (!email.isEmpty()) {
        if (email.length() > FieldLimits.EMAIL_MAX_LENGTH) {
          ec.messages.add("Email: '" + email + CharacterConstants.S_QUOTE + EMAIL_MAX_LENGTH_MSG);
        } else if (!emailValid(email)){
          ec.messages.add("Email: Email (" + email
              + ") format is invalid. It should be in the format 'testuser@email.com'");
        } else {
          u.setEmail(email);
        }
      }
      if (++i == size) {
        ec.messages.add("No fields specified after Email");
        return ec;
      }

      // Country
      String country = tokens[i].trim();
      if (!country.isEmpty()) {
        //validating country with system configuration
        if (c != null && c.getConfig() != null) {
          String jsonLocationString = c.getConfig();
          if (jsonLocationString != null) {
            jsonLocationObject = new JSONObject(jsonLocationString);
            if (!jsonLocationObject.isNull("data")) {
              intermediateJsonObject = jsonLocationObject.getJSONObject("data");
              countryKey = intermediateJsonObject.keySet();
            }
          }
        }
        if (countryKey.contains(country) && country.length() == 2) {
          u.setCountry(country);
          intermediateJsonObject = intermediateJsonObject.getJSONObject(country);
        } else {
          ec.messages.add("Country: Country code '" + country
              + "' is not available in the configuration. Please enter the proper country code.");
        }
      } else {
        ec.messages.add(
            "Country code is mandatory. Please specify proper country code. It should be a valid 2-letter ISO-3166 code");
      }
      if (++i == size) {
        ec.messages.add("No fields specified after Country");
        return ec;
      }
      String language = tokens[i].trim();
      if (!language.isEmpty()) {
        String jsonLanguageString;
        //validating language with system configuration
        if (ln != null && ln.getConfig() != null) {
          jsonLanguageString = ln.getConfig();
          if (jsonLanguageString != null) {
            jsonLanguageObject = new JSONObject(jsonLanguageString);
            languageKey = jsonLanguageObject.keySet();
          }
        }
        if (languageKey.contains(language) && language.length() == 2) {
          u.setLanguage(language);
        } else {
          ec.messages.add("Language: Language code '" + language + CharacterConstants.S_QUOTE
              + " is not available in the configuration. Please enter the proper language code.");
        }
      } else {
        ec.messages.add(
            "Language code is mandatory. Please specify proper language code. It should be a valid 2-letter ISO-630-1 code.");
      }

      if (++i == size) {
        ec.messages.add("No fields specified after language");
        return ec;
      }
      // Timezone
      String[] timezones = TimeZone.getAvailableIDs();
      String
          TIMEZONE_ID_PREFIXES =
          "^(Africa|America|Asia|Atlantic|Australia|Europe|Indian|Pacific)/.*";
      List<String> timezoneCode = new ArrayList<>();
      for (String timezone1 : timezones) {
        if (timezone1.matches(TIMEZONE_ID_PREFIXES)) {
          timezoneCode.add(timezone1);
        }
      }
      String timezone = tokens[i].trim();
      if (timezone.isEmpty() || !timezone.contains("/")) {
        ec.messages.add(
            "Timezone: Timezone is not specified or is of incorrect format (i.e. missing a /). It should be an entry from the URL given in the header.");
      } else {
        //Validating the timezone with the system configuration
        if (timezoneCode.contains(timezone)) {
          u.setTimezone(timezone);
        } else {
          ec.messages.add("Timezone:Timezone " + timezone
              + " is not available in the given configuration. Please enter the proper timezone code."
              +
              " It should be an entry from the URL given in the header");
        }
      }

      if (++i < size && !tokens[i].isEmpty()) {
        String gender = tokens[i].trim();
        if (!isGenderValid(gender)) {
          ec.messages.add("Gender: Invalid value '" + gender + "'. Value should be either "
              + IUserAccount.GENDER_MALE + " = Male or " + IUserAccount.GENDER_FEMALE
              + " = Female or " + IUserAccount.GENDER_OTHER + " = Other");
        } else {
          u.setGender(gender.toLowerCase());
        }
      }

      String dateOfBirth;
      if (++i < size && !(dateOfBirth = tokens[i].trim()).isEmpty()) {
        try {
          DateTimeFormatter formatter = DateTimeFormatter.ofPattern(Constants.DATE_FORMAT_EXCEL);
          LocalDate birthDate = LocalDate.parse(dateOfBirth, formatter);
          if (!isDateOfBirthValid(birthDate, LocalDate.now())) {
            ec.messages
                .add("Date of birth: " + dateOfBirth + " is not valid. A valid date of birth in the format " + Constants.DATE_FORMAT_EXCEL + " between today and 100 years before today should be specified.");
          } else {
            u.setBirthdate(Date.from(birthDate.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant()));
          }
        } catch (DateTimeParseException e) {
          ec.messages
              .add("Date of birth: " + dateOfBirth + " is not valid. A valid date of birth in the format " + Constants.DATE_FORMAT_EXCEL + " between today and 100 years before today should be specified.");
        }
      }
      // Land phone
      String landPhone;
      if (++i < size) {
        landPhone = tokens[i].trim();
        if (!landPhone.isEmpty()) {
          if (landPhone.length() > FieldLimits.LAND_PHONE_MAX_LENGTH) {
            ec.messages.add("Land line number: '" + landPhone + CharacterConstants.S_QUOTE + LAND_PHONE_MAX_LENGTH_MSG);
          }
          String validatedLandPhone = validPhone(landPhone);
          if (validatedLandPhone != null) {
            u.setLandPhoneNumber(validatedLandPhone);
          } else {
            ec.messages.add("Land line number: Number (" + landPhone
                + ") format is invalid. It should be +[country-code][space][phone-number-without-spacesORdashes]; ensure space between country code and number.");
          }
        } else {
          u.setLandPhoneNumber(landPhone);
        }
      }

      String state = "";
      if (u.getCountry() != null) {
        state = tokens[++i].trim();
        if (state.isEmpty()) {
          ec.messages.add("State is not specified. State is mandatory for the user");
        } else {
          //validating state with system configuration
          if (intermediateJsonObject.isNull("states")) {
            ec.messages.add(
                "States for the country" + country + " are not available in the configuration");
          } else {
            Set<String> stateCode = intermediateJsonObject.getJSONObject("states").keySet();
            if (stateCode != null && stateCode.contains(state)) {
              u.setState(state);
              intermediateJsonObject =
                  intermediateJsonObject.getJSONObject("states").getJSONObject(state);
            } else {
              ec.messages.add("State: " + state
                  + " is not available in the configuration.Please enter the proper state name");
            }
          }
        }
      }
      // District
      String district = "";
      if (++i < size && u.getState() != null) {
        district = tokens[i].trim();
        if (!district.isEmpty()) {
          //validating district with system configuration
          if (intermediateJsonObject.isNull("districts")) {
            ec.messages
                .add("Districts for the State" + state + " are not available in the configuration");
          } else {
            Set<String> districtCode = intermediateJsonObject.getJSONObject("districts").keySet();
            if (districtCode != null && districtCode.contains(district)) {
              u.setDistrict(district);
              intermediateJsonObject =
                  intermediateJsonObject.getJSONObject("districts").getJSONObject(district);
            } else {
              ec.messages.add("District: " + district
                  + " is not available in the configuration. Please enter the proper district name");
            }
          }
        } else {
          u.setDistrict(district);
        }
      }
      // Taluk
      String taluk;
      if (++i < size && u.getDistrict() != null) {
        taluk = tokens[i].trim();
        if (!taluk.isEmpty()) {
          //validating taluk with system configuration
          if (intermediateJsonObject.isNull("taluks")) {
            ec.messages.add(
                "Taluks for the district " + district + " are not available in the configuration");
          } else {
            JSONArray taluks = intermediateJsonObject.getJSONArray("taluks");
            if (taluks.length() > 0) {
              ArrayList<String> talukCode = new ArrayList<>();
              for (int j = 0; j < taluks.length(); j++) {
                String tk = taluks.getString(j);
                talukCode.add(tk);
              }
              if (!talukCode.isEmpty() && talukCode.contains(taluk)) {
                u.setTaluk(taluk);
              } else {
                ec.messages.add("Taluk: " + taluk
                    + " is not available in the configuration.Please enter the proper taluk name");
              }
            } else {
              ec.messages.add("Taluks for the district " + district
                  + " are not available in the configuration");
            }
          }
        } else {
          u.setTaluk(taluk);
        }
      }
      // Village/city
      String city;
      if (++i < size) {
        city = tokens[i].trim();
        if (city.length() > FieldLimits.TEXT_FIELD_MAX_LENGTH) {
          ec.messages.add("Village/City: '" + city + CharacterConstants.S_QUOTE + TEXT_FIELD_MAX_LENGTH_MSG);
        } else {
          u.setCity(city);
        }
      }
      // Street
      String street;
      if (++i < size) {
        street = tokens[i].trim();
        if (street.length() > FieldLimits.STREET_ADDRESS_MAX_LENGTH) {
          ec.messages.add("Street Address: '" + street + CharacterConstants.S_QUOTE + STREET_ADDRESS_MAX_LENGTH_MSG);
        } else {
          u.setStreet(street);
        }
      }
      // Pin code
      String pinCode;
      if (++i < size) {
        pinCode = tokens[i].trim();
        if (StringUtils.isNotEmpty(pinCode)) {
          if (pinCode.matches(PatternConstants.ZIPCODE)) {
            u.setPinCode(pinCode);
          } else {
            ec.messages.add("Invalid format: Zip/PIN code '" + pinCode
                + CharacterConstants.S_QUOTE + TEXT_FIELD_MAX_LENGTH_MSG + " and can contain only uppercase, lowercase, digits, hyphen and spaces.");
          }
        } else {
          u.setPinCode(pinCode);
        }
      }
      // Old password, in case of edit (and password has to be edited)
      String oldPassword = CharacterConstants.EMPTY;
      if (++i < size) {
        oldPassword = tokens[i].trim();
      }
      // Process password
      boolean processPassword = (isAdd || (isEdit && !oldPassword.isEmpty()));

      if (processPassword) {

        if (password.equals(confirmPassword)) {
          // Set password after encoding

          boolean isPasswordValid = true;
          if (isClearTextPassword(password) && (isAdd || isEdit && !password.isEmpty())) {
            try{
              PasswordValidator.validate(userId,u.getRole(),password); }
            catch (ValidationException e){
              isPasswordValid=false;
              ec.messages.add(e.getMessage());
            }
          }
          try {
            if (isPasswordValid && isAdd) {
              u.setEncodedPassword(password);
            } else if (isPasswordValid && isEdit && !oldPassword.isEmpty()) {
              //Bulkupload edit password will always be clear text
              aus.changePassword(userId, null, oldPassword, password, false);
            }
          } catch (Exception e) {
            ec.messages.add(
                "Error: System error when encoding password [" + e.getClass().getName() + ": " + e
                    .getMessage());
          }
        } else {
          ec.messages.add("Password: Password and Confirm Password field values do not match");
        }
      }

      if (++i < size) {
        String customId = tokens[i].trim();
        if (customId.length() > FieldLimits.TEXT_FIELD_MAX_LENGTH) {
          ec.messages.add("Custom ID '" + customId + CharacterConstants.S_QUOTE + TEXT_FIELD_MAX_LENGTH_MSG);
        } else {
          u.setCustomId(customId);
        }
      }

      if (++i < size) {
        String phoneBrand = tokens[i].trim();
        if (phoneBrand.length() > FieldLimits.TEXT_FIELD_MAX_LENGTH) {
          ec.messages.add("Mobile Phone Brand '" + phoneBrand + CharacterConstants.S_QUOTE + TEXT_FIELD_MAX_LENGTH_MSG);
        } else {
          u.setPhoneBrand(phoneBrand);
        }
      }

      if (++i < size) {
        String phoneModel = tokens[i].trim();
        if (phoneModel.length() > FieldLimits.TEXT_FIELD_MAX_LENGTH) {
          ec.messages.add("Mobile Phone Model '" + phoneModel + CharacterConstants.S_QUOTE + TEXT_FIELD_MAX_LENGTH_MSG);
        } else {
          u.setPhoneModelNumber(phoneModel);
        }
      }

      if (++i < size) {
        String imei = tokens[i].trim();
        if (imei.length() > FieldLimits.TEXT_FIELD_MAX_LENGTH) {
          ec.messages.add("IMEI number '" + imei + CharacterConstants.S_QUOTE + TEXT_FIELD_MAX_LENGTH_MSG);
        }
        u.setImei(imei);
      }

      if (++i < size) {
        String serviceProvider = tokens[i].trim();
        if (serviceProvider.length() > FieldLimits.TEXT_FIELD_MAX_LENGTH) {
          ec.messages.add("SIM Provider '" + serviceProvider + CharacterConstants.S_QUOTE + TEXT_FIELD_MAX_LENGTH_MSG);
        } else {
          u.setPhoneServiceProvider(serviceProvider);
        }
      }

      if (++i < size) {
        String simId = tokens[i].trim();
        if (simId.length() > FieldLimits.TEXT_FIELD_MAX_LENGTH) {
          ec.messages.add("SIM ID '" + simId + CharacterConstants.S_QUOTE + TEXT_FIELD_MAX_LENGTH_MSG);
        } else {
          u.setSimId(simId);
        }
      }
      // Tags
      if (++i < size) {
        processTags(tokens[i], domainId, ec, TagUtil.TYPE_USER, u);
      } else if (u.getTags()
          != null) { // The user being updated had tags earlier but now being edited to remove tags
        u.setTags(new ArrayList<>());
      }

      if (++i < size) {
        String guiTheme = tokens[i].trim();
        if (!isGuiThemeValid(guiTheme)) {
          ec.messages
              .add("Store app GUI theme: Invalid value '" + guiTheme + "'. Value should be either "
                  + FieldLimits.GUI_THEME_SAME_AS_IN_DOMAIN_CONFIGURATION
                  + " (Same as in domain configuration) or " + FieldLimits.GUI_THEME_DEFAULT
                  + " (Default) or " + FieldLimits.GUI_THEME_SIDEBAR_AND_LANDING_SCREEN
                  + " (Sidebar & Landing screen)");
        } else {
          int actualGuiTheme = Constants.GUI_THEME_SAME_AS_IN_DOMAIN_CONFIGURATION;
          if (!guiTheme.isEmpty()) {
            actualGuiTheme = Integer.parseInt(guiTheme) - 1;
          }
          u.setStoreAppTheme(actualGuiTheme);
        }
      }
      // If there are errors, return; do not add/update
      if (ec.hasErrors()) {
        return ec;
      }
      // Add/edit
      if (isAdd) {
        as.addAccount(domainId, u);
      } else {
        as.updateAccount(u, sourceUserId);
      }
      // Set the object id
      ec.entityId = userId;
      xLogger.info("AUDITLOG\t{0}\t{1}\tUSER\t " +
          "{2}\t{3}\t{4}", domainId, sourceUserId, ec.operation, userId, uname.trim());
    } catch (Exception e) {
      ec.messages.add("Error: " + e.getMessage());
      xLogger.warn("Error while processing bulk upload of user", e);
    }
    xLogger.fine("Exiting processUserEntity");
    return ec;
  }

  // Get the material entity from a set of tokens
  private static EntityContainer processMaterialEntity(String[] tokens, Long domainId,
                                                       String sourceUserId) {
    xLogger.info("Entered processMaterialEntity");
    EntityContainer ec = new EntityContainer();
    if (tokens == null || tokens.length == 0) {
      ec.messages.add("No fields specified");
      return ec;
    }
    // Process material fields
    try {
      UsersService as = StaticApplicationContext.getBean(UsersServiceImpl.class);
      DomainsService ds = StaticApplicationContext.getBean(DomainsServiceImpl.class);
      IUserAccount su = as.getUserAccount(sourceUserId);
      IDomainPermission
          permission =
          ds.getLinkedDomainPermission(
              su.getRole().equalsIgnoreCase(SecurityConstants.ROLE_DOMAINOWNER) ? su.getDomainId()
                  : domainId);
      MaterialCatalogService mcs = StaticApplicationContext.getBean(
          MaterialCatalogServiceImpl.class);
      IMaterial m;
      ConfigurationMgmtService cms =
          StaticApplicationContext.getBean(ConfigurationMgmtServiceImpl.class);
      ResourceBundle backendMessages = Resources.get().getBundle("BackendMessages", su.getLocale());
      IConfig cn = cms.getConfiguration(IConfig.CURRENCIES);
      JSONObject jsonCurrencyObject;
      Set<String> currencyKey = null;

      int i = 0;
      int size = tokens.length;
      String op = tokens[i].trim(); // operation
      if (!op.isEmpty()) {
        ec.operation = op;
      }
      if (++i == size) {
        ec.messages.add("No fields specified");
        return ec;
      }
      if (!OP_ADD.equals(ec.operation) && !OP_EDIT.equals(ec.operation) && !OP_DELETE
          .equals(ec.operation)) {
        ec.messages.add("Invalid Operation. Please enter a = add / e = edit / d = delete.");
        return ec;
      }
      // Material Name
      String name = tokens[i].trim();
      if (name.isEmpty()) {
        ec.messages
            .add("Material name: Name is not specified. Please specify a valid material name.");
        return ec;
      }
      if (name.length() > FieldLimits.TEXT_FIELD_MAX_LENGTH) {
        ec.messages
            .add("Material name: '" + name + "' is invalid. It should be between " + FieldLimits.MATERIAL_NAME_MIN_LENGTH + CharacterConstants.HYPHEN + FieldLimits.TEXT_FIELD_MAX_LENGTH + CHARACTERS);
      }
      // Get the material ID, if present
      Long materialId = getMaterialId(domainId, name, null);
      xLogger.fine("MATERIAL ID: {0}, operation: {1}", materialId, ec.operation);
      boolean isAdd = (OP_ADD.equals(ec.operation));
      boolean isEdit = (OP_EDIT.equals(ec.operation));
      boolean isDelete = (OP_DELETE.equals(ec.operation));
      if (permission != null) {
        boolean iSU = SecurityConstants.ROLE_SUPERUSER.equals(su.getRole());
        if (isAdd && !(iSU || permission.isMaterialAdd())) {
          ec.messages
              .add("Cannot add material '" + name + "'. User does not have permission to add.");
          return ec;
        } else if (isEdit && !(iSU || permission.isMaterialEdit())) {
          ec.messages
              .add("Cannot edit material '" + name + "'. User does not have permission to edit.");
          return ec;
        } else if (isDelete && !(iSU || permission.isMaterialRemove())) {
          ec.messages.add(
              "Cannot delete material '" + name + "'. User does not have permission to delete.");
          return ec;
        }
      }
      if (isDelete) {
        if (materialId == null) {
          ec.messages.add("Cannot delete. Material '" + name + "' could not be found.");
          return ec;
        }
        List<Long> materialIds = new ArrayList<>();
        materialIds.add(materialId);
        mcs.deleteMaterials(domainId, materialIds);
        xLogger.info("AUDITLOG\t{0}\t{1}\tMATERIAL\t " +
            "{2}\t{3}\t{4}", domainId, sourceUserId, ec.operation, materialId, name);
        return ec;
      }
      // Check operation and instantiate accordingly
      if (isAdd) {
        if (materialId != null) {
          ec.messages.add("Cannot add material '" + name + "'. It already exists.");
          return ec;
        }
        m = JDOUtils.createInstance(IMaterial.class);
        m.setName(name);
      } else { // edit
        if (materialId == null) {
          ec.messages.add("Cannot edit material '" + name + "'. It could not be found.");
          return ec;
        }
        // Get existing entity
        m = mcs.getMaterial(materialId);
      }
      // Set other material parameters, if any
      // Short-name
      String sname;
      if (++i < size) {
        sname = tokens[i].trim();
        if (sname.length() > FieldLimits.MATERIAL_SHORTNAME_MAX_LENGTH) {
          ec.messages.add("Short name '" + sname + CharacterConstants.S_QUOTE + MATERIAL_SHORT_NAME_MAX_LENGTH_MSG);
        } else {
          m.setShortName(sname);
        }
      }
      // Description
      String description;
      if (++i < size) {
        description = tokens[i].trim();
        if (description.length() > FieldLimits.MATERIAL_DESCRIPTION_MAX_LENGTH) {
          ec.messages.add("Description '" + description + CharacterConstants.S_QUOTE + MATERIAL_DESC_MAX_LENGTH_MSG);
        } else {
        m.setDescription(description);
        }
      }
      // Additional Info.
      String info;
      if (++i < size) {
        info = tokens[i].trim();
        if (info.length() > FieldLimits.MATERIAL_ADDITIONAL_INFO_MAX_LENGTH) {
          ec.messages.add("Additional info '" + info + CharacterConstants.S_QUOTE + MATERIAL_ADD_INFO_MAX_LENGTH_MSG);
        } else {
          m.setInfo(info);
        }
      }
      // Show on mobile
      boolean dispInfo = true;
      if (++i < size && ("no".equals(tokens[i].trim()))) {
        dispInfo = false;
      }
      m.setInfoDisplay(dispInfo);
      // Tags
      if (++i < size) {
        processTags(tokens[i], domainId, ec, TagUtil.TYPE_MATERIAL, m);
      }
      // Is seasonal?
      boolean seasonal = false;
      if (++i < size && ("yes".equals(tokens[i].trim()))) {
        seasonal = true;
      }
      m.setSeasonal(seasonal);
      // MSRP
      String msrp;
      if (++i < size && (!(msrp = tokens[i].trim()).isEmpty())) {
        if(msrp.matches(PatternConstants.PRICE)) {
          try {
            m.setMSRP(new BigDecimal(msrp));
          } catch (Exception e) {
            ec.messages.add("MSRP: Price " + msrp + " is invalid. It should be a valid number");
            return ec;
          }
        } else {
          ec.messages
              .add(
                  "MSRP: Price " + msrp  + " is invalid. It should be between 0 and 1 billion, rounded to two decimal places maximum");
          return ec;
        }
      }
      // Retailer price
      String retailerPrice;
      if (++i < size && (!(retailerPrice = tokens[i].trim()).isEmpty())) {
        if (retailerPrice.matches(PatternConstants.PRICE)) {
          try {
            m.setRetailerPrice(new BigDecimal(retailerPrice));
          } catch (Exception e) {
            ec.messages.add("Retailer price: Price " + retailerPrice
                + " is invalid. It should be a valid number");
            return ec;
          }
        } else {
          ec.messages
              .add(
                  "Retailer: Price " + retailerPrice  + " is invalid. It should be between 0 and 1 billion, rounded to two decimal places maximum");
          return ec;
        }
      }
      // Currency
      String currency;
      if (++i < size) {
        //Currency Validation
        currency = tokens[i].trim();
        if (!currency.isEmpty()) {
          //validating currency with system configuration
          if (cn != null && cn.getConfig() != null) {
            String jsonCurrencyString = cn.getConfig();
            jsonCurrencyObject = new JSONObject(jsonCurrencyString);
            currencyKey = jsonCurrencyObject.keySet();
            if (currencyKey.contains(currency)) {
              m.setCurrency(currency);
            } else {
              ec.messages
                  .add("Currency code:" + currency + " is not available in the configuration. " +
                      " Please enter the proper currency code.It should be a valid 3-letter ISO-4217 currency code. ");
            }
          }
        }
      }
      // New material name
      String newName;
      if (++i < size) {
        newName = tokens[i].trim();
        if (isEdit && !newName.isEmpty()) {
          if (newName.length() > FieldLimits.TEXT_FIELD_MAX_LENGTH) {
            ec.messages
                .add("Material name [new]: '" + name + "' is invalid. It should be between " + FieldLimits.MATERIAL_NAME_MIN_LENGTH + CharacterConstants.HYPHEN + FieldLimits.TEXT_FIELD_MAX_LENGTH + " characters");
          } else {
            m.setName(newName);
          }
        }
      }
      // Custom ID
      String customId;
      if (++i < size) {
        customId = tokens[i].trim();
        if (customId.length() > FieldLimits.TEXT_FIELD_MAX_LENGTH) {
          ec.messages.add("Custom ID '" + customId + CharacterConstants.S_QUOTE + TEXT_FIELD_MAX_LENGTH_MSG);
        } else {
          m.setCustomId(customId);
        }
      }
      // Enable Batch - option yes/no
      boolean batchEnabled = false;
      if (++i < size && ("yes".equals(tokens[i].trim()))) {
        batchEnabled = true;
      }
      if (isEdit && batchEnabled != m.isBatchEnabled()) {
        InventoryManagementService ims = StaticApplicationContext.getBean(
            InventoryManagementServiceImpl.class);
        if (!ims.validateMaterialBatchManagementUpdate(m.getMaterialId())) {
          if (batchEnabled) {
            ec.messages.add(backendMessages.getString("material.batch.management.enable.warning"));
          } else {
            ec.messages.add(backendMessages.getString("material.batch.management.disable.warning"));
          }
        }
      }
      m.setBatchEnabled(batchEnabled);
      // Enable Temperature Monitoring - option yes/no
      boolean tempMonitoringEnabled = false;
      if (++i < size) {
        String tmpMonEnabled = tokens[i].trim();
        if ("yes".equalsIgnoreCase(tmpMonEnabled)) {
          tempMonitoringEnabled = true;
        }
      }
      m.setTemperatureSensitive(tempMonitoringEnabled);
      // Temperature Min. if temperature monitoring is enabled.
      String temperatureMin;
      if (++i < size && tempMonitoringEnabled) {
        temperatureMin = tokens[i].trim();
        float tempMin = 0;
        if (temperatureMin.isEmpty()) {
          // Get the tempMin from Config
          tempMin = (float) getTempFromConfig(domainId, TEMP_MIN, ec);
        } else {
          if (!temperatureMin.matches(PatternConstants.TEMPERATURE)) {
            ec.messages.add(
                "Temperature Min.: " + temperatureMin + " is invalid. It should be between " + FieldLimits.TEMP_MIN_VALUE + " and " + FieldLimits.TEMP_MAX_VALUE + " rounded to two decimal places maximum.");
          } else {
            try {
              tempMin = Float.parseFloat(temperatureMin);
            } catch (Exception e) {
              ec.messages.add(
                  "Temperature Min.: " + temperatureMin
                      + " is invalid. It should be a valid number");
            }
          }
        }
        m.setTemperatureMin(tempMin);
      }
      // Temperature Max. if temperature monitoring is enabled.
      String temperatureMax;
      if (++i < size && tempMonitoringEnabled) {
        temperatureMax = tokens[i].trim();
        float tempMax = 0;
        if (temperatureMax.isEmpty()) {
          // Get the tempMax from Config
          tempMax = (float) getTempFromConfig(domainId, TEMP_MAX, ec);
        } else {
          if (!temperatureMax.matches(PatternConstants.TEMPERATURE)) {
            ec.messages.add(
                "Temperature Max.: " + temperatureMax + " is invalid. It should be between "
                    + FieldLimits.TEMP_MIN_VALUE + " and " + FieldLimits.TEMP_MAX_VALUE
                    + " rounded to two decimal places maximum.");
          } else {
            try {
              tempMax = Float.parseFloat(temperatureMax);
            } catch (Exception e) {
              ec.messages.add(
                  "Temperature Max.: " + temperatureMax
                      + " is invalid. It should be a valid number");
            }
          }
        }
        m.setTemperatureMax(tempMax);
      }
      if (tempMonitoringEnabled) {
        if (m.getTemperatureMin() == m.getTemperatureMax()) {
          ec.messages.add(
              "Temperature Min. cannot be same as Temperature Max.");
        } else if (m.getTemperatureMin() > m.getTemperatureMax()) {
          ec.messages.add(
              "Temperature Min. cannot be greater than Temperature Max.");
        }
      }
      // If there are errors, return
      if (ec.hasErrors()) {
        return ec;
      }
      // Add/edit
      m.setDomainId(domainId);
      m.setLastUpdatedBy(sourceUserId);
      if (isAdd) {
        m.setCreatedBy(sourceUserId);
        materialId = mcs.addMaterial(domainId, m);
      } else {
        mcs.updateMaterial(m, domainId);
      }
      // Set object Id
      ec.entityId = materialId;
      xLogger.info("AUDITLOG\t{0}\t{1}\tMATERIAL\t " +
          "{2}\t{3}\t{4}", domainId, sourceUserId, ec.operation, materialId, name);
    } catch (Exception e) {
      ec.messages.add("Error: " + e.getMessage());
      xLogger.info("Exception: {0}, Message: {1}", e.getClass().getName(), e.getMessage());
    }
    xLogger.info("Exiting processMaterialEntity");
    return ec;
  }

  // Get the kiosk entity from a set of tokens
  private static EntityContainer processKioskEntity(String[] tokens, Long domainId,
                                                    String sourceUserId, int source) {
    xLogger.fine("Entered processKioskEntity");
    ResourceBundle backendMessages;
    EntityContainer ec = new EntityContainer();
    if (tokens == null || tokens.length == 0) {
      ec.messages.add("No fields specified");
      return ec;
    }
    // Process material fields
    try {
      UsersService as = StaticApplicationContext.getBean(UsersServiceImpl.class);
      EntitiesService es = StaticApplicationContext.getBean(EntitiesServiceImpl.class);
      DomainsService ds = StaticApplicationContext.getBean(DomainsServiceImpl.class);
      IUserAccount su = as.getUserAccount(sourceUserId);
      backendMessages = Resources.get().getBundle("BackendMessages", su.getLocale());
      IDomainPermission
          permission =
          ds.getLinkedDomainPermission(
              su.getRole().equalsIgnoreCase(SecurityConstants.ROLE_DOMAINOWNER) ? su.getDomainId()
                  : domainId);
      ConfigurationMgmtService cms =
          StaticApplicationContext.getBean(ConfigurationMgmtServiceImpl.class);
      //Location Configuration
      IConfig c = cms.getConfiguration(IConfig.LOCATIONS);
      //Currency Configuration
      IConfig cn = cms.getConfiguration(IConfig.CURRENCIES);
      JSONObject jsonLocationObject, jsonCurrencyObject, intermediateJsonObject = null;
      Set<String> countryKey, currencyKey;
      countryKey = currencyKey = null;

      IKiosk k;

      int i = 0;
      int size = tokens.length;
      String op = tokens[i].trim(); // operation
      if (!op.isEmpty()) {
        ec.operation = op;
      }
      if (++i == size) {
        ec.messages.add("No fields specified");
        return ec;
      }
      if (!OP_ADD.equals(ec.operation) && !OP_EDIT.equals(ec.operation) && !OP_DELETE
          .equals(ec.operation)) {
        ec.messages.add("Invalid Operation. Please enter a = add / e = edit / d = delete.");
        return ec;
      }
      // Kiosk Name
      String name = tokens[i].trim();
      if (name == null || name.isEmpty() || name.length() > FieldLimits.TEXT_FIELD_MAX_LENGTH) {
        ec.messages.add(backendMessages.getString("kiosk")
            + " name: Name is not specified or is greater than " + FieldLimits.TEXT_FIELD_MAX_LENGTH + " characters. Please specify a valid "
            + backendMessages.getString("kiosk.lowercase") + " name.");
        return ec;
      }
      // Get the kiosk ID, if present
      Long kioskId = null;
      PersistenceManager pm = PMF.get().getPersistenceManager();
      Query
          q =
          pm.newQuery("select kioskId from " + JDOUtils.getImplClass(IKiosk.class).getName()
              + " where dId.contains(domainIdParam) && nName == nameParam parameters Long domainIdParam, String nameParam");
      try {
        @SuppressWarnings("unchecked")
        List<Long> list = (List<Long>) q.execute(domainId, name.toLowerCase());
        if (list != null && !list.isEmpty()) {
          kioskId = list.get(0);
        }
        xLogger.fine(
            "BulkUploadMgr.processKioskEntity: resolved kiosk {0} to {1}; list returned {2} items",
            name.toLowerCase(), kioskId, (list == null ? "NULL" : list.size()));
      } finally {
        try {
          q.closeAll();
        } catch (Exception ignored) {

        }
        pm.close();
      }
      boolean isAdd = (OP_ADD.equals(ec.operation));
      boolean isEdit = (OP_EDIT.equals(ec.operation));
      boolean isDelete = (OP_DELETE.equals(ec.operation));
      boolean iSU = false;
      if (permission != null) {
        iSU = SecurityConstants.ROLE_SUPERUSER.equals(su.getRole());
        if (isAdd && !(iSU || permission.isEntityAdd())) {
          ec.messages.add(
              "Cannot add " + backendMessages.getString("kiosk.lowercase") + " " + "'" + name
                  + "'. User does not have permission to add.");
          return ec;
        } else if (isEdit && !(iSU || permission.isEntityEdit())) {
          ec.messages.add(
              "Cannot edit " + backendMessages.getString("kiosk.lowercase") + " " + "'" + name
                  + "'. User does not have permission to edit.");
          return ec;
        } else if (isDelete && !(iSU || permission.isEntityRemove())) {
          ec.messages.add(
              "Cannot delete " + backendMessages.getString("kiosk.lowercase") + " " + "'" + name
                  + "'. User does not have permission to delete.");
          return ec;
        }
      }
      if (isDelete) {
        if (kioskId == null) {
          ec.messages.add(
              "Cannot delete. " + backendMessages.getString("kiosk.lowercase") + " " + "'" + name
                  + "' could not be found.");
          return ec;
        }
        List<Long> kioskIds = new ArrayList<>();
        kioskIds.add(kioskId);
        es.deleteKiosks(domainId, kioskIds, sourceUserId);
        xLogger.info("AUDITLOG\t{0}\t{1}\tENTITY\t " +
            "DELETE\t{3}\t{4}", domainId, sourceUserId, ec.operation, kioskId, name);
        return ec;
      }
      // Check operation and instantiate accordingly
      if (isAdd) {
        if (kioskId != null) {
          ec.messages.add(
              "Cannot add " + backendMessages.getString("kiosk.lowercase") + " " + "'" + name
                  + "'. It already exists.");
          return ec;
        }
        k = JDOUtils.createInstance(IKiosk.class);
        k.setName(name);
        k.setDomainId(domainId);
        k.setRegisteredBy(sourceUserId);
      } else { // edit
        if (kioskId == null) {
          ec.messages.add(
              "Cannot edit " + backendMessages.getString("kiosk.lowercase") + " " + "'" + name
                  + "'. It could not be found.");
          return ec;
        }
        // Get existing entity
        k = es.getKiosk(kioskId);
      }
      k.setUpdatedBy(sourceUserId);
      // Set other entity parameters, if any
      if (++i == size) {
        ec.messages.add("No fields specified beyond name");
        return ec;
      }
      // Entity manager(s) and operators
      String usersStr = tokens[i].trim();
      if (usersStr.isEmpty()) {
        ec.messages.add("User IDs not specified. At least one user ID has to be specified");
      } else {
        Set<String> userIdsSet = getUniqueUserIds(usersStr,CharacterConstants.SEMICOLON);
        if (userIdsSet == null) {
          ec.messages.add("User IDs not specified. At least one user ID has to be specified");
        } else {
          List<IUserAccount> users = new ArrayList<>();
          for (String userId : userIdsSet) {
            try {
              IUserAccount u = as.getUserAccount(userId);
              if (u.getDomainId().compareTo(domainId) != 0) {
                ec.messages.add("User with ID " + userId + " does not belong to this domain");
                continue;
              }
              users.add(u);
            } catch (ObjectNotFoundException e) {
              ec.messages.add("User with ID '" + userId + "' not found.");
            } catch (Exception e) {
              ec.messages
                  .add("Error when fetching user with ID '" + userId + "': " + e.getMessage());
            }
          }
          // Set users for this kiosk
          if (users.isEmpty()) {
            ec.messages.add("No valid users to associate with this " + backendMessages
                .getString("kiosk.lowercase"));
          } else {
            k.setUsers(users);
          }
        }
      }
      if (++i == size) {
        ec.messages.add("No fields specified beyond users");
        return ec;
      }
      // Country
      String country = tokens[i].trim();
      if (!country.isEmpty()) {
        if (c != null && c.getConfig() != null) {
          //validating country with system configuration
          String jsonLocationString = c.getConfig();
          if (jsonLocationString != null) {
            jsonLocationObject = new JSONObject(jsonLocationString);
            if (!jsonLocationObject.isNull("data")) {
              intermediateJsonObject = jsonLocationObject.getJSONObject("data");
              countryKey = intermediateJsonObject.keySet();
            }
          }
        }
        if (countryKey.contains(country) && country.length() == 2) {
          k.setCountry(country);
          intermediateJsonObject = intermediateJsonObject.getJSONObject(country);
        } else {
          ec.messages.add("Country: Country code '" + country
              + "' is not available in the configuration. Please enter the proper country code.");
        }
      } else {
        ec.messages.add(
            "Country code is mandatory. Please specify proper country code. It should be a valid 2-letter ISO-3166 code");
      }

      if (++i == size) {
        ec.messages.add("No fields specified beyond country");
        return ec;
      }
      // State
                        /*if ( state.isEmpty() )
                                ec.messages.add( "State: State not specified. " );
			else
				k.setState(state);*/
      String state = "";
      if (k.getCountry() != null) {
        state = tokens[i].trim();
        if (state.isEmpty()) {
          ec.messages.add("State is not specified. Please specify a valid state.");
        } else {
          //validating state with system configuration
          if (intermediateJsonObject.isNull("states")) {
            ec.messages.add(
                "States for the country" + country + " are not available in the configuration");
          } else {
            Set<String> stateCode = intermediateJsonObject.getJSONObject("states").keySet();
            if (stateCode != null && stateCode.contains(state)) {
              k.setState(state);
              intermediateJsonObject =
                  intermediateJsonObject.getJSONObject("states").getJSONObject(state);
            } else {
              ec.messages.add("State: " + state
                  + " is not available in the configuration. Please enter the proper state name");
            }

          }
        }

      }
      if (++i == size) {
        ec.messages.add("No fields specified beyond state");
        return ec;
      }
      // Village/city
      String city = tokens[i].trim();
      if (city.isEmpty()) {
        ec.messages
            .add("Village/City: Village/City not specified. Please specify a valid village/city");
      } else if (city.length() > FieldLimits.TEXT_FIELD_MAX_LENGTH) {
          ec.messages.add("Village/City: '" + city + CharacterConstants.S_QUOTE + TEXT_FIELD_MAX_LENGTH_MSG);
      } else {
          k.setCity(city);
      }
      // Optional fields
      // Latitude
      if (++i < size) {
        String latitude = tokens[i].trim();
        if (!latitude.isEmpty()) {
          try {
            Double.parseDouble(latitude);
            String trimmedLat = getTruncatedLatLong(latitude);
            if (!trimmedLat.matches(PatternConstants.LATITUDE)) {
              ec.messages.add("Latitude: " + latitude
                  + " is out of range. Please specify between " + FieldLimits.LATITUDE_MIN + " and "
                  + FieldLimits.LATITUDE_MAX + " rounded to eight decimal places maximum" + CharacterConstants.DOT);
            } else {
              Double d = Double.valueOf(trimmedLat);
              k.setLatitude(d);
            }
          } catch (NumberFormatException e) {
            ec.messages
                .add("Latitude: Invalid number " + latitude
                    + ". Please specify a valid number between " + FieldLimits.LATITUDE_MIN + " and "
                    + FieldLimits.LATITUDE_MAX + " rounded to eight decimal places maximum" + CharacterConstants.DOT);
          }
        }
      }

      // Longitude
      if (++i < size) {
        String longitude = tokens[i].trim();
        if (!longitude.isEmpty()) {
          // Check if it is a valid number
          try {
            Double.parseDouble(longitude);
            String trimmedLng = getTruncatedLatLong(longitude);
            if (!trimmedLng.matches(PatternConstants.LONGITUDE)) {
              ec.messages.add("Longitude: " + longitude
                  + " is out of range. Please specify between " + FieldLimits.LAT_LONG_MAX_DIGITS_AFTER_DECIMAL + " and "
                  + FieldLimits.LONGITUDE_MAX + " rounded to eight decimal places maximum" + CharacterConstants.DOT);
            } else {
              Double d = Double.valueOf(trimmedLng);
              k.setLongitude(d);
            }
          } catch (NumberFormatException e) {
            ec.messages
                .add("Longitude: Invalid number " + longitude
                    + ". Please specify a valid number between " + FieldLimits.LONGITUDE_MIN + " and "
                    + FieldLimits.LONGITUDE_MAX + " rounded to eight decimal places maximum" + CharacterConstants.DOT);
          }
        }
      }
      // District
      String district;
      if (++i < size && k.getState() != null) {
        district = tokens[i].trim();
        if (!district.isEmpty()) {
          if (intermediateJsonObject.isNull("districts")) {
            ec.messages
                .add("Districts for the State" + state + " are not available in the configuration");
          } else {
            Set<String> districtCode = intermediateJsonObject.getJSONObject("districts").keySet();
            if (districtCode != null && districtCode.contains(district)) {
              intermediateJsonObject =
                  intermediateJsonObject.getJSONObject("districts").getJSONObject(district);
            } else {
              ec.messages.add("District: " + district
                  + " is not available in the configuration. Please enter the proper district name");
            }
          }
        }
        k.setDistrict(district);
      }
      // Taluk
      String taluk;
      if (++i < size && StringUtils.isNotEmpty(k.getDistrict())) {
        taluk = tokens[i].trim();
        if (!taluk.isEmpty()) {
          if (intermediateJsonObject.isNull("taluks")) {
            ec.messages.add("Taluks for the district " + k.getDistrict()
                + " are not available in the configuration");
          } else {
            JSONArray taluks = intermediateJsonObject.getJSONArray("taluks");
            if (taluks.length() > 0) {
              ArrayList<String> talukCode = new ArrayList<>();
              for (int j = 0; j < taluks.length(); j++) {
                String tk = taluks.getString(j);
                talukCode.add(tk);
              }
              if (talukCode.isEmpty() || !talukCode.contains(taluk)) {
                ec.messages.add("Taluk: " + taluk
                    + " is not available in the configuration.Please enter the proper taluk name");
              }
            } else {
              ec.messages.add("Taluks for the district " + k.getDistrict()
                  + " are not available in the configuration");
            }
          }
        }
        k.setTaluk(taluk);
      }
      // Street address
      if (++i < size) {
        String street = tokens[i].trim();
        if (street.length() > FieldLimits.STREET_ADDRESS_MAX_LENGTH) {
          ec.messages.add("Street address: '" + street + CharacterConstants.S_QUOTE + STREET_ADDRESS_MAX_LENGTH_MSG);
        } else {
          k.setStreet(street);
        }
      }
      // Zip code
      if (++i < size) {
        String zipcode = tokens[i].trim();
        if (StringUtils.isNotEmpty(zipcode)) {
          if (zipcode.matches(PatternConstants.ZIPCODE)) {
            k.setPinCode(zipcode);
          } else {
            ec.messages.add("Invalid format: Zip/PIN code '" + zipcode
                + CharacterConstants.S_QUOTE + TEXT_FIELD_MAX_LENGTH_MSG + " and can contain only uppercase, lowercase, digits, hyphen and spaces.");
          }
        }
      }
      // Currency
      String currency;
      if (++i < size) {
        currency = tokens[i].trim();
        if (!currency.isEmpty()) {
          if (cn != null && cn.getConfig() != null) {
            String jsonCurrencyString = cn.getConfig();
            if (jsonCurrencyString != null) {
              jsonCurrencyObject = new JSONObject(jsonCurrencyString);
              currencyKey = jsonCurrencyObject.keySet();
            }
            if (currencyKey.contains(currency)) {
              k.setCurrency(currency);
            } else {
              ec.messages.add("Currency code:" + currency
                  + " is not available in the configuration. Please enter the proper currency code."
                  +
                  "It should be a valid 3-letter ISO-4217 currency code.");
            }
          }
        }
      }

      // Tax
      if (++i < size) {
        String tax = tokens[i].trim();
        if (!tax.isEmpty()) {
          try {
            if (tax.matches(PatternConstants.TAX)) {
              BigDecimal tx = new BigDecimal(tax);
              k.setTax(tx);
            } else {
              ec.messages.add(
                  "Tax: Not a valid number (" + tax + "). It should be between " + FieldLimits.TAX_MIN_VALUE
                      + " and " + FieldLimits.TAX_MAX_VALUE + " rounded to two decimal places maximum.");
            }
          } catch (NumberFormatException e) {
            ec.messages.add(
                "Tax: Not a valid number (" + tax + "). It should be between " + FieldLimits.TAX_MIN_VALUE
                    + " and " + FieldLimits.TAX_MAX_VALUE + " rounded to two decimal places maximum.");
          }
        }
      }
      // Tax ID
      if (++i < size) {
        String taxId = tokens[i].trim();
        if (taxId.length() > FieldLimits.TEXT_FIELD_MAX_LENGTH) {
          ec.messages.add("Tax ID: '" + taxId + CharacterConstants.S_QUOTE + TEXT_FIELD_MAX_LENGTH_MSG);
        } else {
          k.setTaxId(taxId);
        }
      }
      // Inventory model
      if (++i < size) {
        String invModel = tokens[i].trim();
        if (!invModel.isEmpty() && (FieldLimits.SYSTEM_DETERMINED_REPLENISHMENT.compareTo(invModel) != 0)) {
          ec.messages
              .add("Invalid value " + invModel + " for Inventory model. It should be '" + FieldLimits.SYSTEM_DETERMINED_REPLENISHMENT + "' or blank.");
        } else {
          k.setInventoryModel(invModel);
        }
      }
      // Service level
      if (++i < size) {
        String serviceLevel = tokens[i].trim();
        if (!serviceLevel.isEmpty()) {
          try {
            int svcLevel = Integer.parseInt(serviceLevel);
            if (svcLevel < FieldLimits.MIN_SERVICE_LEVEL || svcLevel > FieldLimits.MAX_SERVICE_LEVEL) {
              ec.messages.add(
                  "Service Level: " + serviceLevel + " is not valid. Please specify a valid level between " + FieldLimits.MIN_SERVICE_LEVEL + " and " + FieldLimits.MAX_SERVICE_LEVEL + CharacterConstants.DOT);
            } else {
            k.setServiceLevel(svcLevel);
            }
          } catch (NumberFormatException e) {
            ec.messages.add(
                "Service Level: " + serviceLevel + " is not valid. Please specify a valid level between " + FieldLimits.MIN_SERVICE_LEVEL + " and " + FieldLimits.MAX_SERVICE_LEVEL + CharacterConstants.DOT);
          }
        }
      }
      // New name, in case of edit
      if (++i < size) {
        String newName = tokens[i].trim();
        if (isEdit && !newName.isEmpty()) {
          if (newName.length() <= FieldLimits.TEXT_FIELD_MAX_LENGTH) {
            k.setName(newName);
          } else {
            ec.messages.add(backendMessages.getString("kiosk") + "'s new name '" + newName
                + CharacterConstants.S_QUOTE + TEXT_FIELD_MAX_LENGTH_MSG);
          }
        }
      }
      // Add all materials?
      boolean addAllMaterials = false, addSomeMaterials;
      String stockStr = null, materialNamesStr = null, vendorNamesStr = null,
          customerNamesStr =
              null;
      boolean addMaterialsToKiosk = false;
      boolean addVendorLinks = false, addCustomerLinks = false;
      if (isAdd) {
        if (++i < size) {
          addAllMaterials = ("yes".equals(tokens[i].trim()));
        }
        // Add specific materials?
        if (++i < size && !addAllMaterials) {
          materialNamesStr = tokens[i].trim();
        }
        addSomeMaterials = (materialNamesStr != null && !materialNamesStr.isEmpty());
        // Get initial stock count, if any
        if (++i < size && permission.isInventoryAdd()) {
          stockStr = tokens[i].trim();
        }
        // Add the materials to kiosk
        if (addAllMaterials || addSomeMaterials) {
          try {
            if (iSU || permission.isInventoryAdd()) {
              addMaterialsToKiosk = true;
            } else {
              ec.messages.add("No permission to add inventory. Please contact your administrator.");
            }
          } catch (Exception e) {
            ec.messages.add(
                "Error when adding materials to " + backendMessages.getString("kiosk.lowercase")
                    + " " + "'" + name + "'. Please add them manually [" + e.getMessage() + "]");
          }
        }

        // Add specific customers
        if (++i < size) {
          customerNamesStr = tokens[i].trim();
          if (!customerNamesStr.isEmpty()) {
            try {
              if (iSU || permission.isEntityRelationshipAdd()) {
                addCustomerLinks = true;
              } else {
                ec.messages.add(
                    "No permission to add " + backendMessages.getString("bck.customer.lower")
                        + "s. Please contact your administrator.");
              }
            } catch (Exception e) {
              ec.messages.add(
                  "Error when adding " + backendMessages.getString("bck.customer.lower") + "s '"
                      + customerNamesStr + "'. Please add them manually [" + e.getMessage() + "]");
            }
          }
        }
        // Add specific vendors
        if (++i < size) {
          vendorNamesStr = tokens[i].trim();
          if (!vendorNamesStr.isEmpty()) {
            try {
              if (iSU || permission.isEntityRelationshipAdd()) {
                addVendorLinks = true;
              } else {
                ec.messages.add(
                    "No permission to add " + backendMessages.getString("bck.vendor.lower")
                        + "s. Please contact your administrator.");
              }
            } catch (Exception e) {
              ec.messages.add(
                  "Error when adding " + backendMessages.getString("bck.vendor.lower") + "s " + "'"
                      + vendorNamesStr + "'. Please add them manually [" + e.getMessage() + "]");
            }
          }
        }
      }
      // Add tags, if any
      xLogger.info("Adding tags: issues so far: {0}", ec.messages);
      xLogger.info("i = {0}, size = {1}, isEdit = {2}, isAdd = {3}", i, size, isEdit, isAdd);

      if ((isAdd && ++i < size) || (isEdit && (i = i + 6) < size)) {
        processTags(tokens[i], domainId, ec, TagUtil.TYPE_ENTITY, k);
        xLogger.info("Updated {0}.", name);
      } else if (k.getTags() != null) {
        k.setTags(new ArrayList<>());
      }
      // Add customId if present.
      // Custom ID
      xLogger.info("Uploading customId...");
      String customId;
      if (++i < size) {
        customId = tokens[i].trim();
        if (customId.length() > FieldLimits.TEXT_FIELD_MAX_LENGTH) {
          ec.messages.add("Custom ID '" + customId + CharacterConstants.S_QUOTE + TEXT_FIELD_MAX_LENGTH_MSG);
        } else {
          k.setCustomId(customId);
        }
      }

      if (++i < size) {
        String enableBatch = tokens[i].trim();
        boolean enableBatchBoolean = true;
        if ("false".equalsIgnoreCase(enableBatch) || enableBatch.isEmpty()) {
          enableBatchBoolean = true;
        } else if ("true".equalsIgnoreCase(enableBatch)) {
          enableBatchBoolean = false;
        } else {
          ec.messages.add(
              "Enable Batch Management '" + enableBatch + "' should be either true/false or empty");
        }
        if (isEdit && (enableBatchBoolean != k.isBatchMgmtEnabled())) {
          InventoryManagementService ims = StaticApplicationContext.getBean(
              InventoryManagementServiceImpl.class);
          if (!ims.validateEntityBatchManagementUpdate(k.getKioskId())) {
            if (enableBatchBoolean) {
              ec.messages.add(backendMessages.getString("entity.batch.management.enable.warning"));
            } else {
              ec.messages.add(backendMessages.getString("entity.batch.management.disable.warning"));
            }
          }
        }
        k.setBatchMgmtEnabled(enableBatchBoolean);
      }

      // Check if errors exist
      if (ec.hasErrors()) {
        return ec;
      }
      // Add/edit
      if (isAdd) {
        kioskId = es.addKiosk(domainId, k);
      } else {
        es.updateKiosk(k, domainId);
      }
      // Set object id
      ec.entityId = kioskId;

      if (addMaterialsToKiosk) {
        addMaterialsToKiosk(domainId, kioskId, name, materialNamesStr, stockStr, sourceUserId,
            source,
            ec, backendMessages);
        if (ec.hasErrors()) {
          return ec;
        }
      }
      if (addVendorLinks) {
        addKioskLinks(domainId, kioskId, vendorNamesStr, IKioskLink.TYPE_VENDOR, sourceUserId, es,
            ec, backendMessages);
      }
      if (addCustomerLinks) {
        addKioskLinks(domainId, kioskId, customerNamesStr, IKioskLink.TYPE_CUSTOMER, sourceUserId,
            es, ec, backendMessages);
      }

      xLogger.info("AUDITLOG\t{0}\t{1}\tENTITY\t " +
          "{2}\t{3}\t{4}", domainId, sourceUserId, ec.operation, kioskId, name);

    } catch (Exception e) {
      ec.messages.add("Error: " + e.getMessage());
      xLogger.warn("Error while processing entity uploaded data:" , e);
    }
    xLogger.fine("Exiting processKioskEntity");
    return ec;
  }

  // Get the list of Java timezones
  public static String getTimezonesCSV() {
    xLogger.fine("Entered getTimezonesCSV");
    String csv = "Timezone Name, Timezone code";
    Map<String, String> timezoneMap = LocalDateUtil.getTimeZoneNames();
    Set<String> names = timezoneMap.keySet();
    TreeSet<String> sortedNames = new TreeSet<>(names); // sort the display names
    for (String name : sortedNames) {
      csv += "\n" + name + "," + timezoneMap.get(name);
    }
    xLogger.fine("Exiting getTimezonesCSV");
    return csv;
  }

  // Get all the error message associated with an uploaded object
  public static List<String> getUploadedMessages(String uploadedKey) {
    return AppFactory.get().getDaoUtil().getUploadedMessages(uploadedKey);
  }

  // Delete error messages associated with an uploaded object, if any
  public static void deleteUploadedMessage(String uploadedKey) {
    AppFactory.get().getDaoUtil().deleteUploadedMessage(uploadedKey);
  }

  // Phone validator - Validate the phone number and adds a '+' in the beginning of the phone number, if it is not present
  private static String validPhone(String phone) {
    // Format: +<country-code><space><phone-number>
    if (phone == null || phone.isEmpty()) {
      return null;
    }
    String[] tokens = phone.split(CharacterConstants.SPACE);
    if (tokens.length != 2) {
      return null;
    }
    if (!tokens[0].startsWith(CharacterConstants.PLUS)) {
      tokens[0] = CharacterConstants.PLUS + tokens[0];
    }
    try {
      String countryCode = tokens[0].substring(1, tokens[0].length());
      if (Long.parseLong(countryCode) < 0 || Double.parseDouble(tokens[1]) < 0) {
        return null;
      }
    } catch (Exception e) {
      return null;
    }
    return tokens[0] + CharacterConstants.SPACE + tokens[1];
  }

  private static String getAssetValidPhone(String phone) {
    if (phone == null || phone.isEmpty()) {
      return "";
    }

    if (!phone.startsWith("+")) {
      phone = "+" + phone;
    }
    try {
      Long.valueOf(phone.substring(1, phone.length()));
    } catch (Exception e) {
      return null;
    }

    return phone;
  }

  // Email validator
  private static boolean emailValid(String email) {
    // Email validation Regex as done in the UI by Angular JS
    return (email != null && email.matches(PatternConstants.EMAIL));
  }

  // Add multiple materials to a kiosk
  @SuppressWarnings("unchecked")
  private static void addMaterialsToKiosk(Long domainId, Long kioskId, String kioskName,
                                          String materialNamesStr, String stockStr,
                                          String sourceUserId, int source,
                                          EntityContainer ec, ResourceBundle backendMessages)
      throws ServiceException, TaskSchedulingException {
    xLogger.fine("Entered addMaterialsToKiosk");
    try {
      if (StringUtils.isNotEmpty(stockStr)) {
        Integer.parseInt(stockStr);
      }
    } catch (NumberFormatException e) {
      ec.messages.add("Cannot add materials to specified entity. Invalid value for initial stock");
      return;
    }
    String idsCSV = "";
    PersistenceManager pm = PMF.get().getPersistenceManager();
    try {
      if (materialNamesStr == null) {
        // Get all materials in the domain; do this via a key-only query for better performance
        Query
            q =
            pm.newQuery("select materialId from " + JDOUtils.getImplClass(IMaterial.class).getName()
                + " where dId.contains(dIdParam) parameters Long dIdParam");
        try {
          List<Long> materialIds = (List<Long>) q.execute(domainId);
          if (materialIds == null || materialIds.isEmpty()) {
            throw new ServiceException("No materials found");
          }
          for (Long materialId : materialIds) {
            if (!idsCSV.isEmpty()) {
              idsCSV += ",";
            }
            idsCSV += materialId;
          }
        } finally {
          q.closeAll();
        }
      } else {
        // Get the names
        String[] materialNames = materialNamesStr.split(";");
        for (int i = 0; i < materialNames.length; i++) {
          try {
            Long materialId = getMaterialId(domainId, materialNames[i], pm);
            if (materialId == null) {
              ec.messages
                  .add("Material '" + materialNames[i] + "' not found, and will not be added.");
            } else {
              if (i > 0) {
                idsCSV += ",";
              }
              idsCSV += getMaterialId(domainId, materialNames[i], pm);
            }
          } catch (Exception e) {
            throw new ServiceException(
                "Unable to get material " + materialNames[i] + ". Not adding any material for this "
                    + backendMessages.getString("kiosk") + " [" + e.getMessage() + "]");
          }
        }
      }
    } finally {
      pm.close();
    }
    // Prepare task for adding materials
    String url = "/task/createentity";
    Map<String, String> params = new HashMap<>();
    params.put("action", "add");
    params.put("type", "materialtokiosk");
    params.put("materialid", idsCSV);
    params.put("kioskname", kioskName);
    params.put("kioskid", kioskId.toString());
    params.put("domainid", domainId.toString());
    if (stockStr != null && !stockStr.isEmpty()) {
      params.put("stock", stockStr);
    }
    params.put("source", String.valueOf(source));
    if (sourceUserId != null && !sourceUserId.isEmpty()) {
      params.put("sourceuserid", sourceUserId);
    }
    List<String> multiValueParams = new ArrayList<>();
    multiValueParams.add("materialid");
    // Schedule task immediately to add materials to kiosk
    taskService.schedule(ITaskService.QUEUE_DEFAULT, url, params, multiValueParams, null,
        ITaskService.METHOD_POST, -1, domainId, sourceUserId, "MATERIALS_TO_KIOSK");
    xLogger.fine("Exiting addMaterialsToKiosk");
  }

  // Add a set of links to a given entity
  @SuppressWarnings("unchecked")
  private static void addKioskLinks(Long domainId, Long kioskId, String kioskNamesCSV,
                                    String linkType, String sourceUserId, EntitiesService as,
                                    EntityContainer ec, ResourceBundle backendMessages)
      throws ServiceException {
    xLogger
        .fine("Entered addKioskLinks: kioskId = {0}, kioskNamesCSV = {1}, linkType = {2}", kioskId,
            kioskNamesCSV, linkType);
    PersistenceManager pm = PMF.get().getPersistenceManager();
    String[] kioskNames = kioskNamesCSV.split(";");
    List<IKioskLink> links = new ArrayList<>();
    Date now = new Date();
    try {
      for (String kioskName : kioskNames) {
        // Get the corresponding kiosk Id (via a key-only query)
        Query
            q =
            pm.newQuery("select kioskId from " + JDOUtils.getImplClass(IKiosk.class).getName()
                + " where dId.contains(dIdParam) && nName == nameParam parameters Long dIdParam, String nameParam ");
        Long linkedKioskId = null;
        try {
          List<Long> ids = (List<Long>) q.execute(domainId, kioskName.trim().toLowerCase());
          if (ids == null || ids.isEmpty()) {
            ec.messages.add(backendMessages.getString("kiosk") + " " + "'" + kioskName
                + "' not found, and will not be added as a related " + backendMessages
                .getString("kiosk.lowercase"));
            continue;
          }
          linkedKioskId = ids.get(0);
        } finally {
          q.closeAll();
        }
        // Form kiosk link
        if (linkedKioskId != null) {
          IKioskLink kl = JDOUtils.createInstance(IKioskLink.class);
          kl.setDomainId(domainId);
          kl.setCreatedBy(sourceUserId);
          kl.setCreatedOn(now);
          kl.setId(JDOUtils.createKioskLinkId(kioskId, linkType, linkedKioskId));
          kl.setKioskId(kioskId);
          kl.setLinkedKioskId(linkedKioskId);
          kl.setLinkType(linkType);
          links.add(kl);
        }
      }
    } finally {
      pm.close();
    }
    as.addKioskLinks(domainId, links);
    xLogger.fine("Exiting addKioskLinks");
  }

  // Get the material Id, given a name
  @SuppressWarnings("unchecked")
  private static Long getMaterialId(Long domainId, String name, PersistenceManager pm)
      throws ServiceException {
    Long materialId = null;
    PersistenceManager pmLocal = pm;
    if (pm == null) {
      pmLocal = PMF.get().getPersistenceManager();
    }
    Query
        q =
        pmLocal.newQuery(
            "select materialId from " + JDOUtils.getImplClass(IMaterial.class).getName()
                + " where dId.contains(domainIdParam) && uName == unameParam parameters Long domainIdParam, String unameParam");
    try {
      List<Long> list = (List<Long>) q.execute(domainId, name.trim().toLowerCase());
      if (list != null && !list.isEmpty()) {
        materialId = list.get(0);
      }
    } finally {
      try {
        q.closeAll();
      } catch (Exception ignored) {
        // ignore
      }
      if (pm == null) {
        pmLocal.close();
      }
    }
    return materialId;
  }

  // Method that reads the temperature configuration and returns the temperature min or temperature max depending on tempType
  private static double getTempFromConfig(Long domainId, String tempType, EntityContainer ec) {
    DomainConfig dc = DomainConfig.getInstance(domainId);
    AssetConfig tc = dc.getAssetConfig();
    if (tc == null) {
      ec.messages.add(tempType + ": Temperature Configuration is not available.");
      return 0;
    }
    if (tc.isTemperatureMonitoringWithLogisticsEnabled() || tc.isTemperatureMonitoringEnabled()) {
      AssetConfig.Configuration configuration = tc.getConfiguration();
      if (configuration == null) {
        ec.messages.add(tempType + ": Temperature Configuration is not available.");
        return 0;
      }

      if (TEMP_MAX.equals(tempType) && configuration.getHighAlarm() != null) {
        return configuration.getHighAlarm().getTemp();
      } else if (TEMP_MIN.equals(tempType) && configuration.getLowAlarm() != null) {
        return configuration.getLowAlarm().getTemp();
      } else {
        ec.messages.add(tempType +": Temperature Configuration is not available.");
        return 0;
      }
    } else {
      ec.messages.add(tempType + ": Temperature Monitoring is not enabled.");
      return 0;
    }
  }

  // Check if master data is being uploaded
  public static boolean isUploadMasterData(String type) {
    return (TYPE_USERS.equals(type) || TYPE_MATERIALS.equals(type) || TYPE_KIOSKS.equals(type)
        || TYPE_INVENTORY.equals(type));
  }

  private static boolean processTags(String tokens, Long domainId, EntityContainer ec,
                                     String tagType, Object uploadable) {
    String tags = tokens.trim();
    if (!tags.matches("[^,]*")) {
      ec.messages.add("Invalid value for " + tags + ". It should not contain commas");
      return false;
    }
    String tagsFromUserCSV = tokens.trim().replaceAll(";", ","); // replace semi-colons with commas
    List<String> tagsFromUser = StringUtil.getList(tagsFromUserCSV);
    boolean hasTagsFromUser = tagsFromUser != null && !tagsFromUser.isEmpty();
    DomainConfig dc = DomainConfig.getInstance(domainId);
    String confTagsCSV = null;
    boolean forceTags = false;

    switch (tagType) {
      case TagUtil.TYPE_MATERIAL:
        confTagsCSV = dc.getMaterialTags();
        forceTags = dc.forceTagsMaterial();
        break;
      case TagUtil.TYPE_ENTITY:
        confTagsCSV = dc.getKioskTags();
        forceTags = dc.forceTagsKiosk();
        break;
      case TagUtil.TYPE_USER:
        confTagsCSV = dc.getUserTags();
        forceTags = dc.forceTagsUser();
        break;
      default:
        break;
    }

    List<String> confTags = StringUtil.getList(confTagsCSV);
    boolean hasConfTags = confTags != null && !confTags.isEmpty();
    boolean update;
    if (!hasTagsFromUser) {
      setUploadableObjTags(tagType, uploadable, tagsFromUser);
      update = true;
    } else if (forceTags) {
      if (!hasConfTags) {
        // Error message
        ec.messages.add("Tags specified should be configured in the system");
        update = false;
      } else {
        // If confTags contains tagsFromUser - get Tags equiv and set
        // Else error message
        if (confTags.containsAll(tagsFromUser)) {
          // Get config tags equivalent
          setUploadableObjTags(tagType, uploadable, tagsFromUser);
          update = true;
        } else {
          ec.messages.add("Tags specified should be configured in the system");
          update = false;
        }
      }
    } else {
      // Set tags
      setUploadableObjTags(tagType, uploadable, tagsFromUser);
      update = true;
    }
    return update;
  }

  private static Set<String> getUniqueUserIds(String userIdsStr, String separator) {
    if (StringUtils.isEmpty(userIdsStr)) {
      return null;
    }
    String[] userIds = userIdsStr.split(separator);
    if (userIds.length == 0) {
      return null;
    }
    return (new HashSet<>(Arrays.asList(userIds)));
  }

  private static String getTruncatedLatLong(String latLngToBeTrimmed) {
    int indexOfDot = latLngToBeTrimmed.indexOf(CharacterConstants.DOT);
    if (indexOfDot != -1 && (indexOfDot + FieldLimits.LAT_LONG_MAX_DIGITS_AFTER_DECIMAL + 1 <= latLngToBeTrimmed.length())) {
      latLngToBeTrimmed =
          latLngToBeTrimmed
              .substring(0, indexOfDot + FieldLimits.LAT_LONG_MAX_DIGITS_AFTER_DECIMAL + 1);
    }
    return latLngToBeTrimmed;
  }

  private static void setUploadableObjTags(String tagType, Object up, List<String> tags) {
    if (tags == null) {
      tags =
          new ArrayList<>(); // setTags(null) does not set the tags. Hence, empty tags list is required.
    }
    switch (tagType) {
      case TagUtil.TYPE_MATERIAL:
        ((IMaterial) up).setTags(tags);
        break;
      case TagUtil.TYPE_ENTITY:
        ((IKiosk) up).setTags(tags);
        break;
      case TagUtil.TYPE_USER:
        ((IUserAccount) up).setTags(tags);
        break;
      default:
        break;
    }
  }

  public static String getJobStatusDisplay(int status, Locale locale) {
    ResourceBundle messages = Resources.get().getBundle("Messages", locale);
    if (status == IUploaded.STATUS_PENDING) {
      return messages.getString("pending");
    } else if (status == IUploaded.STATUS_DONE) {
      return messages.getString("done");
    }
    return "";
  }

  private static String getTrimmedErrorMessageString(String errMessage, int errorCount) {
    StringBuilder trimmedErrMsgSb = new StringBuilder();
    trimmedErrMsgSb.append(StringUtils.substringBeforeLast(
        errMessage.substring(0, ERROR_MESSAGE_MAX_LENGTH), BulkUploadMgr.MESSAGE_DELIMITER));
    int remainingErrorCount = errorCount - (StringUtils.countMatches(trimmedErrMsgSb.toString(), MESSAGE_DELIMITER) + 1);
    trimmedErrMsgSb.append(BulkUploadMgr.MESSAGE_DELIMITER).append(CharacterConstants.SPACE).append(ERRORS_TRUNCATED_MSG).append(CharacterConstants.O_BRACKET).append(ERROR_COUNT_MSG).append(remainingErrorCount).append(CharacterConstants.C_BRACKET);
    return trimmedErrMsgSb.toString();
  }

  /**
   * This method ignores case and returns true if gender is IUserAccount.GENDER_MALE, IUserAccount.GENDER_FEMALE, IUserAccount.GENDER_OTHER or empty.
   * It returns false otherwise.
   */
  protected static boolean isGenderValid(String gender) {
    return (gender.isEmpty() || IUserAccount.GENDER_MALE.equalsIgnoreCase(gender)
        || IUserAccount.GENDER_FEMALE.equalsIgnoreCase(gender) || IUserAccount.GENDER_OTHER.equalsIgnoreCase(gender));
  }

  /**
   * This method returns true if the difference between dateOfBirth and currentDate is 100 years or less. Otherwise, returns false.
   * If dateOfBirth is after currentDate then it returns false.
   * @param dateOfBirth
   * @param currentDate
   * @return
   */
  protected static boolean isDateOfBirthValid(LocalDate dateOfBirth, LocalDate currentDate) {
    if (dateOfBirth.isAfter(currentDate)) {
      return false;
    }
    Period age = dateOfBirth.until(currentDate);
    return (age.getYears() < FieldLimits.MAX_USER_AGE || age.equals(
        Period.of(FieldLimits.MAX_USER_AGE, 0, 0)));
  }

  /**
   * This method returns true if the operation is add or edit or delete. Otherwise it returns false
   * @param operation
   * @return
   */
  protected static boolean isOperationValid(String operation) {
    return OP_ADD.equals(operation) || OP_EDIT.equals(operation) || OP_DELETE.equals(operation);
  }

  /**
   * This method returns true if the permission is valid (empty or d - Default, v - View only, a - Asset user). Otherwise, it returns false.
   * @param permission
   * @return
   */
  protected static boolean isPermissionValid(String permission) {
    return permission.isEmpty() || IUserAccount.PERMISSION_DEFAULT.equals(permission) || IUserAccount.PERMISSION_VIEW.equals(
        permission) || IUserAccount.PERMISSION_ASSET.equals(permission);
  }

  /**
   * This method returns true if the token expiry is valid (empty or a number between 0 and 999). Otherwise, it returns false.
   * @param tokenExpiryStr
   * @return
   */
  protected static boolean isTokenExpiryValid(String tokenExpiryStr) {
    if (tokenExpiryStr.isEmpty()) {
      return true;
    }
    try {
      int tokenExpiry = Integer.parseInt(tokenExpiryStr);
      return (tokenExpiry >= FieldLimits.TOKEN_EXPIRY_MIN && tokenExpiry <= FieldLimits.TOKEN_EXPIRY_MAX);
    } catch (NumberFormatException e) {
      return false;
    }
  }

  /**
   * This method returns true if the GUI theme is valid (empty or 0, 1 or 2). Otherwise, it returns false.
   * @param guiThemeStr
   * @return
   */
  protected static boolean isGuiThemeValid(String guiThemeStr) {
    if (guiThemeStr.isEmpty()) {
      return true;
    }
    try {
      int guiTheme = Integer.parseInt(guiThemeStr);
      return (guiTheme == FieldLimits.GUI_THEME_SAME_AS_IN_DOMAIN_CONFIGURATION || guiTheme == FieldLimits.GUI_THEME_DEFAULT || guiTheme == FieldLimits.GUI_THEME_SIDEBAR_AND_LANDING_SCREEN);
    } catch (NumberFormatException e) {
      return false;
    }
  }

  public static class EntityContainer {
    public String operation = OP_ADD; // operation
    public List<String> messages = new ArrayList<>(); // error messages, if any
    public Object entityId = null;

    public boolean hasErrors() {
      return !messages.isEmpty();
    }

    public String getMessages() {
      StringBuilder messageSb = new StringBuilder();
      for (String message : messages) {
        messageSb.append(message);
        messageSb.append(MESSAGE_DELIMITER);
      }
      // Remove the last MESSAGE_DELIMITER
      if (messageSb.length() >= MESSAGE_DELIMITER.length()) {
        messageSb.setLength(messageSb.length() - MESSAGE_DELIMITER.length());
      }
      return messageSb.toString();
    }

    public int getMessagesCount() {
      return messages.size();
    }
  }

  // Represents a single error message
  public static class ErrMessage {
    public long offset;
    public String csvLine;
    public String operation;
    public List<String> messages;
  }

  /**
   * This method determine call from old or new app
   *
   * @param password
   * @return true or false
   */
  private static boolean isClearTextPassword(String password) {
    return !password.contains(SALT_HASH_SEPARATOR);
  }

}
