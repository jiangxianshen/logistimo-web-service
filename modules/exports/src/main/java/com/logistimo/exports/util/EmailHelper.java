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

package com.logistimo.exports.util;

import com.logistimo.communications.MessageHandlingException;
import com.logistimo.communications.service.EmailService;
import com.logistimo.communications.service.MessageService;
import com.logistimo.config.models.GeneralConfig;
import com.logistimo.constants.CharacterConstants;
import com.logistimo.domains.service.DomainsService;
import com.logistimo.entity.IJobStatus;
import com.logistimo.reports.constants.ReportType;
import com.logistimo.services.ServiceException;
import com.logistimo.services.utils.ConfigUtil;
import com.logistimo.users.entity.IUserAccount;
import com.logistimo.users.service.UsersService;
import com.logistimo.utils.StringUtil;

import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author Mohan Raja
 */
@Component
public class EmailHelper {

  UsersService us;
  DomainsService ds;

  @Autowired
  private void setUserService(UsersService us) {
    this.us = us;
  }

  @Autowired
  private void setDomainService(DomainsService ds) {
    this.ds = ds;
  }

  public void sendMail(IJobStatus jobStatus, String filePath)
      throws ServiceException, IOException, MessageHandlingException {
    sendMail(jobStatus, filePath, null);
  }
  public void sendMail(IJobStatus jobStatus, String filePath, String customMessage)
      throws MessageHandlingException, IOException, ServiceException {
    Map<String, String> model = jobStatus.getMetadataMap();
    IUserAccount u = us.getUserAccount(model.get(ExportConstants.USER_ID));
    String reportName = ReportType.getReportName(model.get(ExportConstants.REPORT_TYPE));
    final String exportTime = model.get(ExportConstants.EXPORT_TIME);
    String subject = getMailSubject(model, reportName, exportTime);
    List<String> userIds = StringUtil.getList(u.getEmail());
    List<String> addresses = new ArrayList<>();
    String[] addressArray = userIds.toArray(new String[userIds.size()]);
    Collections.addAll(addresses, addressArray);
    String message;
    if (StringUtils.isNotBlank(customMessage)) {
      message = customMessage;
    } else {
      final String downloadLinkText = getDownloadLinkText(filePath, reportName, exportTime);
      message = constructEmailMessage(u, reportName, downloadLinkText);
    }
    MessageService ms =
        MessageService.getInstance(MessageService.EMAIL, u.getCountry(), true, u.getDomainId(),
            u.getUserId(), null);
    ((EmailService) ms).sendHTML(null, addresses, subject, message, null);
  }

  private String getMailSubject(Map<String, String> model, String reportName, String exportTime)
      throws ServiceException {
    return "[Export] " + reportName + " for " +
        ds.getDomain(Long.valueOf(model.get(ExportConstants.DOMAIN_ID))).getName() + " on " + exportTime;
  }

  private String getDownloadLinkText(String filePath, String reportName, String exportTime) {
    String host = ConfigUtil.get("logi.host.server");
    String path = host == null ? "http://localhost:50070/webhdfs/v1" : "/media";
    String localStr = host == null ? "?op=OPEN" : "";
    return "<a href=\"" + (host == null ? "" : host) + path
        + "/user/logistimoapp/dataexport/" + filePath + "/" + getFileName(reportName, exportTime)
        + localStr + "\">";
  }

  private String constructEmailMessage(IUserAccount u, String reportName, String downloadLinkText) {
    /**
     Dear <first name of the user>,
     As requested, the report '<report title>' is generated and can be viewed by clicking on this link <link>. Please contact <support info> for any further queries.
     Thank you,
     < Program/Product name > Team
     Note: If you have not requested this file but have still received it, please inform <support info> immediately"
     */
    StringBuilder message = new StringBuilder();
    message.append("<br/>Dear ").append(u.getFirstName()).append(",<br/><br/>");
    final String support = ConfigUtil.get("support.email", GeneralConfig.DEFAULT_SUPPORT_EMAIL);
    message.append("The export of ").append(CharacterConstants.SINGLE_QUOTES)
        .append(reportName).append(CharacterConstants.SINGLE_QUOTES)
        .append(" is now complete. Please click here ")
        .append(downloadLinkText).append("</a>. to download the file.")
        .append("<br/><br/>");
    message.append("Thank you,<br/>");
    message.append(ConfigUtil.get("support.team", GeneralConfig.DEFAULT_SUPPORT_TEAM)).append(
        "<br/><br/>");
    message.append(
        "Note: If you have not requested this file but have still received it, please inform ")
        .append(support).append(" immediately");
    return message.toString();
  }

  public String getFileName(String fileName, String fileTime) {
    return fileName.replace(CharacterConstants.SPACE, CharacterConstants.UNDERSCORE) +
        CharacterConstants.UNDERSCORE + fileTime.replaceAll("[ :\\-]", CharacterConstants.UNDERSCORE) + ".csv";
  }
}
