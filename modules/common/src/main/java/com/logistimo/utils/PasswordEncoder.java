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
package com.logistimo.utils;

import com.logistimo.exception.SystemException;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.security.MessageDigest;

/**
 * @author juhee
 */
public class PasswordEncoder {

  private static BCryptPasswordEncoder bCryptPasswordEncoder = new BCryptPasswordEncoder(10);

  private static String convertToHex(byte[] data) {
    StringBuffer buf = new StringBuffer();
    for (int i = 0; i < data.length; i++) {
      int halfbyte = (data[i] >>> 4) & 0x0F;
      int two_halfs = 0;
      do {
        if ((0 <= halfbyte) && (halfbyte <= 9)) {
          buf.append((char) ('0' + halfbyte));
        } else {
          buf.append((char) ('a' + (halfbyte - 10)));
        }
        halfbyte = data[i] & 0x0F;
      } while (two_halfs++ < 1);
    }
    return buf.toString();
  }

  public static String MD5(String text){
    MessageDigest md;
    try {
      md = MessageDigest.getInstance("MD5");
      md.update(text.getBytes("iso-8859-1"), 0, text.length());
      return convertToHex(md.digest());
    } catch (Exception e) {
      throw new SystemException(e);
    }
  }

  public static String bcrypt(String text) {
    return bCryptPasswordEncoder.encode(text);
  }

  public static boolean bcryptMatches(String password, String dbPassword) {
    return bCryptPasswordEncoder.matches(password, dbPassword);
  }

  /*public static String encode(String text) {
    return bcrypt(text);
  }*/

  public static String sha512(String text, String salt) {
    MessageDigest md;
    try {
      md = MessageDigest.getInstance("SHA-512");
      md.update(salt.getBytes("UTF-8"));
      md.update(text.getBytes("UTF-8"), 0, text.length());
      return convertToHex(md.digest());
    } catch (Exception e) {
      throw new SystemException(e);
    }
  }
}
