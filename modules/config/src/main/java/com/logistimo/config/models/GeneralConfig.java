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

package com.logistimo.config.models;

import com.logistimo.config.entity.IConfig;
import com.logistimo.config.service.ConfigurationMgmtService;
import com.logistimo.config.service.impl.ConfigurationMgmtServiceImpl;
import com.logistimo.context.StaticApplicationContext;
import com.logistimo.logger.XLog;
import com.logistimo.services.ObjectNotFoundException;
import com.logistimo.services.ServiceException;
import com.logistimo.services.utils.ConfigUtil;

import org.json.JSONObject;

/**
 * Created by vani on 04/08/15.
 */
public class GeneralConfig {
  // Default support configuration
  public static final String DEFAULT_SUPPORT_EMAIL = "support@logistimo.com";
  public static final String DEFAULT_SUPPORT_PHONE = "180030101947";
  public static final String DEFAULT_SUPPORT_TEAM = "Logistimo team";
  // Default feedback configuration
  public static final String DEFAULT_FEEDBACK_EMAIL = "feedback@logistimo.com";
  // JSON Tags
  public static final String SUPPORT_EMAIL = "supportemail";
  public static final String SUPPORT_PHONE = "supportphone";
  public static final String APPURLS = "appurls";
  public static final String FEEDBACK_EMAIL = "feedbackemail";
  public static final String SMS_LIMITS = "smslimits";
  public static final String USER = "user";
  public static final String DOMAIN = "domain";
  public static final String DEDUP_DUR = "dedupMinutes";
  public static final String APP_UPGRADE = "aupg";
  public static final String DB_RFINT = "dboardrefreshinterval";
  public static final String ES_RFINT = "eventrefreshinterval";
  public static final String REMEMBER_DEVICE_IN_MINUTES = "rememberdevice";

  /**
   * Remember device for 30 days - default
   */
  private static final Integer REMEMBER_DEVICE_DURATION_IN_MINUTES = 43200;
  // Logger
  private static final XLog xLogger = XLog.getLog(GeneralConfig.class);
  private String supportEmail;
  private String supportPhone;
  private String feedbackEmail;
  private Integer smsMaxCountUser = 25;
  private Integer smsMaxCountDomain = 5000;
  private Integer smsDedupDuration = 10;
  private JSONObject applicationUrls;
  private JSONObject aupg;
  private Integer dashboardRefreshIntervalInMinutes = 30;
  private Integer eventsRefreshIntervalInMinutes = 1440;
  private String inventoryPolicy = "AllowAllTransactionsPolicy";
  private String blockedAppVersion = "";
  private String upgradeToVersion = "";
  private int rememberDeviceInMinutes;


  public GeneralConfig() {

  }

  public GeneralConfig(String jsonString) throws ConfigurationException {
    xLogger.fine("Entering GeneralConfig constructor. jsonString: {0}", jsonString);
    try {
      if (jsonString != null && !jsonString.isEmpty()) {
        JSONObject jsonObject = new JSONObject(jsonString);
        try {
          this.supportEmail = jsonObject.getString(SUPPORT_EMAIL);
        } catch (Exception e) {
          this.supportEmail = ConfigUtil.get("support.email", DEFAULT_SUPPORT_EMAIL);
        }
        try {
          this.feedbackEmail = jsonObject.getString(FEEDBACK_EMAIL);
        } catch (Exception e) {
          this.feedbackEmail = ConfigUtil.get("support.email", DEFAULT_FEEDBACK_EMAIL);
        }
        try {
          this.supportPhone = jsonObject.getString(SUPPORT_PHONE);
        } catch (Exception e) {
          this.supportPhone = ConfigUtil.get("support.phone", DEFAULT_SUPPORT_PHONE);
        }
        this.applicationUrls = jsonObject.getJSONObject(APPURLS);
        try {
          this.aupg = jsonObject.getJSONObject(APP_UPGRADE);
        } catch (Exception ignored) {
          // do nothing
        }
        try {
          JSONObject smsLimit = jsonObject.getJSONObject(SMS_LIMITS);
          try {
            this.smsMaxCountUser = smsLimit.getInt(USER);
          } catch (Exception ignored) {
            // do nothing
          }

          try {
            this.smsMaxCountDomain = smsLimit.getInt(DOMAIN);
          } catch (Exception ignored) {
            // do nothing
          }

          try {
            this.smsDedupDuration = smsLimit.getInt(DEDUP_DUR);
          } catch (Exception ignored) {
            // do nothing
          }
        } catch (Exception ignored) {
          // do nothing
        }
        try {
          this.dashboardRefreshIntervalInMinutes = jsonObject.getInt(DB_RFINT);
        } catch (Exception e) {
          this.dashboardRefreshIntervalInMinutes = 30;
        }
        try {
          this.eventsRefreshIntervalInMinutes = jsonObject.getInt(ES_RFINT);
        } catch (Exception e) {
          this.eventsRefreshIntervalInMinutes = 1440;
        }
        try {
          this.inventoryPolicy = jsonObject.getString("inventory_policy");
        } catch (Exception e) {
          this.inventoryPolicy = "AllowAllTransactionsPolicy";
        }
        try {
          this.blockedAppVersion = jsonObject.getString("block_iota_versions");
        } catch (Exception e) {
          this.blockedAppVersion = "";
        }
        try {
          this.upgradeToVersion = jsonObject.getString("upgrade_iota_version");
        } catch (Exception e) {
          this.upgradeToVersion = "";
        }
        try {
          this.rememberDeviceInMinutes = jsonObject.getInt(REMEMBER_DEVICE_IN_MINUTES);
        } catch (Exception e) {
          this.rememberDeviceInMinutes = REMEMBER_DEVICE_DURATION_IN_MINUTES;
        }
      }
    } catch (Exception e) {
      throw new ConfigurationException("Invalid Json for general configuration. " + e.getMessage());
    }
    xLogger.fine(
        "Exiting GeneralConfig constructor, supportEmail: {0}, supportPhone: {1}, appUrls: {2}",
        supportEmail, supportPhone, applicationUrls);
  }

  // Get an instance of the GeneralConfig
  public static GeneralConfig getInstance() throws ConfigurationException {
    try {
      ConfigurationMgmtService cms = StaticApplicationContext
          .getBean(ConfigurationMgmtServiceImpl.class);
      IConfig c = cms.getConfiguration(IConfig.GENERALCONFIG);
      return new GeneralConfig(c.getConfig());
    } catch (ObjectNotFoundException | ServiceException e) {
      throw new ConfigurationException(e.getMessage());
    }
  }

  public String getSupportEmail() {
    return this.supportEmail;
  }

  public void setSupportEmail(String supportEmail) {
    this.supportEmail = supportEmail;
  }

  public String getSupportPhone() {
    return this.supportPhone;
  }

  public void setSupportPhone(String supportPhone) {
    this.supportPhone = supportPhone;
  }

  public JSONObject getAppUrls() {
    return applicationUrls;
  }

  public void setAppUrls(JSONObject appUrls) {
    applicationUrls = appUrls;
  }

  public JSONObject getAupg() {
    return aupg;
  }

  public void setAupg(JSONObject aupg) {
    this.aupg = aupg;
  }

  public String getFeedbackEmail() {
    return feedbackEmail;
  }

  public void setFeedbackEmail(String feedbackEmail) {
    this.feedbackEmail = feedbackEmail;
  }

  public Integer getSmsMaxCountUser() {
    return smsMaxCountUser;
  }

  public void setSmsMaxCountUser(Integer smsMaxCountUser) {
    this.smsMaxCountUser = smsMaxCountUser;
  }

  public Integer getSmsMaxCountDomain() {
    return smsMaxCountDomain;
  }

  public void setSmsMaxCountDomain(Integer smsMaxCountDomain) {
    this.smsMaxCountDomain = smsMaxCountDomain;
  }

  public Integer getSmsDedupDuration() {
    return smsDedupDuration;
  }

  public void setSmsDedupDuration(Integer smsDedupDuration) {
    this.smsDedupDuration = smsDedupDuration;
  }

  public Integer getDashboardRefreshIntervalInMinutes() {
    return dashboardRefreshIntervalInMinutes;
  }

  public void setDashboardRefreshIntervalInMinutes(Integer dashboardRefreshIntervalInMinutes) {
    this.dashboardRefreshIntervalInMinutes = dashboardRefreshIntervalInMinutes;
  }

  public Integer getEventsRefreshIntervalInMinutes() {
    return eventsRefreshIntervalInMinutes;
  }

  public void setEventsRefreshIntervalInMinutes(Integer eventsRefreshIntervalInMinutes) {
    this.eventsRefreshIntervalInMinutes = eventsRefreshIntervalInMinutes;
  }

  public String getInventoryPolicy() {
    return inventoryPolicy;
  }

  public String getBlockedAppVersion() {
    return blockedAppVersion;
  }

  public String getUpgradeToVersion() {
    return upgradeToVersion;
  }

  public Integer getRememberDeviceInMinutes() { return rememberDeviceInMinutes; }

  public void setRememberDeviceInMinutes(int rememberDeviceInMinutes) { this.rememberDeviceInMinutes = rememberDeviceInMinutes; }
}
