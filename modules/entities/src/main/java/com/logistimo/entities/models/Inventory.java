
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

package com.logistimo.entities.models;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.math.BigDecimal;

public class Inventory {

  @SerializedName("stock_on_hand")
  @Expose
  private BigDecimal stockOnHand;

  @Expose
  private BigDecimal min;

  @Expose
  private BigDecimal max;

  @SerializedName("allocated_stock")
  @Expose
  private BigDecimal allocatedStock;

  @SerializedName("available_stock")
  @Expose
  private BigDecimal availableStock;

  @SerializedName("duration_of_stock")
  @Expose
  private DurationOfStock durationOfStock;

  @Expose
  private Predictions predictions;


  public BigDecimal getStockOnHand() {
    return stockOnHand;
  }

  public void setStockOnHand(BigDecimal stockOnHand) {
    this.stockOnHand = stockOnHand;
  }

  public BigDecimal getMin() {
    return min;
  }

  public void setMin(BigDecimal min) {
    this.min = min;
  }

  public BigDecimal getMax() {
    return max;
  }

  public void setMax(BigDecimal max) {
    this.max = max;
  }

  public BigDecimal getAllocatedStock() {
    return allocatedStock;
  }

  public void setAllocatedStock(BigDecimal allocatedStock) {
    this.allocatedStock = allocatedStock;
  }

  public BigDecimal getAvailableStock() {
    return availableStock;
  }

  public void setAvailableStock(BigDecimal availableStock) {
    this.availableStock = availableStock;
  }

  public DurationOfStock getDurationOfStock() {
    return durationOfStock;
  }

  public void setDurationOfStock(DurationOfStock durationOfStock) {
    this.durationOfStock = durationOfStock;
  }

  public Predictions getPredictions() {
    return predictions;
  }

  public void setPredictions(Predictions predictions) {
    this.predictions = predictions;
  }
}
