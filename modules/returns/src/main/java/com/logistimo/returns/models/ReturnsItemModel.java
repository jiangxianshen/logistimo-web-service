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

package com.logistimo.returns.models;

import com.google.gson.annotations.SerializedName;

import com.logistimo.returns.models.submodels.ReceivedModel;
import com.logistimo.returns.models.submodels.UserModel;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import javax.validation.constraints.NotNull;

import lombok.Data;

/**
 * @author Mohan Raja
 */
@Data
public class ReturnsItemModel {

  @SerializedName("material_id")
  @NotNull
  private Long materialId;

  @SerializedName("material_name")
  private String materialName;

  @NotNull
  @SerializedName("return_quantity")
  private BigDecimal returnQuantity;

  @SerializedName("material_status")
  private String materialStatus;

  private String reason;

  private ReceivedModel received;

  @SerializedName("created_at")
  private Date createdAt;

  @SerializedName("created_by")
  private UserModel createdBy;

  @SerializedName("updated_at")
  private Date updatedAt;

  @SerializedName("updated_by")
  private UserModel updatedBy;

  private List<ReturnsItemBatchModel> batches;

}
