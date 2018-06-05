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

package com.logistimo.api.models;

import com.logistimo.api.models.configuration.ReasonConfigModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by mohan raja on 23/11/14
 */
public class TransactionDomainConfigModel {
  public List<ReasonWrapper> reasons;
  public Set<String> transactionTypesWithReasonMandatory;
  public int noc; // No of customers
  public int nov; // No of vendors
  public boolean isMan; // Is Manager
  public List<TransactionModel> customers; // Customers
  public List<TransactionModel> vendors; // Vendors
  public List<TransactionModel> dest; // Destinations
  public boolean showCInv; // Show Customer Inventory
  public String atdi;
  public String atdr;
  public String atdp;
  public String atdw;
  public String atdt;
  public String atdri;
  public String atdro;
  public Map<String, String> atdc;

  class ReasonWrapper {
    String type;
    ReasonConfigModel reasonConfigModel;
    ReasonWrapper(String type, ReasonConfigModel reasonConfigModel) {
      this.type = type;
      this.reasonConfigModel = reasonConfigModel;
    }
  }

  public void addReason(String type, ReasonConfigModel reasonConfigModel) {
    ReasonWrapper reasonWrapper = new ReasonWrapper(type,reasonConfigModel);
    if (reasons == null) {
      reasons = new ArrayList<>();
    }
    reasons.add(reasonWrapper);
  }
}
