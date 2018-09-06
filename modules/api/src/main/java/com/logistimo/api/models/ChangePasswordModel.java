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

package com.logistimo.api.models;

import com.google.gson.annotations.SerializedName;

import lombok.Getter;

/**
 * Created by naveensnair on 28/10/15.
 */
@Getter
public class ChangePasswordModel {
  /**
   * User ID for which password has to be changed
   */
  private String uid;
  /**
   * New password
   */
  private String npd;
  /**
   * Confirm password
   */
  private String cpd;
  private String key;
  /**
   * One time password
   */
  private String otp;
  /**
   * Old Password
   */
  @SerializedName("old_password")
  private String oldPassword;
  /**
   * is enhanced flag
   */
  @SerializedName("is_enhanced")
  private boolean isEnhanced = false;

  private String salt;
}
