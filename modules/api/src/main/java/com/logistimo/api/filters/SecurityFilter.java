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

package com.logistimo.api.filters;

import com.logistimo.api.auth.AuthenticationUtil;
import com.logistimo.auth.SecurityConstants;
import com.logistimo.auth.SecurityMgr;
import com.logistimo.auth.utils.SecurityUtils;
import com.logistimo.auth.utils.SessionMgr;
import com.logistimo.constants.Constants;
import com.logistimo.exception.UnauthorizedException;
import com.logistimo.logger.XLog;
import com.logistimo.security.SecureUserDetails;
import com.logistimo.services.ObjectNotFoundException;

import org.apache.commons.lang.StringUtils;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author Arun
 */
public class SecurityFilter implements Filter {

  public static final String TASK_URL = "/task/";
  public static final String ACTION = "action";
  private static final XLog xLogger = XLog.getLog(SecurityFilter.class);
  // Authentication request
  private static final String HOME_URL_NEW = "/v2/index.html";
  private static final String
      ISSUE_WITH_API_CLIENT_AUTHENTICATION =
      "Issue with api client authentication";

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain filterChain)
      throws IOException, ServletException {
    xLogger.fine("Entered doFilter");
    HttpServletRequest req = (HttpServletRequest) request;
    HttpServletResponse resp = (HttpServletResponse) response;
    String servletPath = req.getServletPath();
    if (req.getCharacterEncoding() == null) {
      request.setCharacterEncoding(Constants.UTF8);
    }
    // BACKWARD COMPATIBILITY - in case people have already bookmarked these links
    if ("/".equals(servletPath) || "".equals(servletPath) || "/index.jsp".equals(servletPath)) {
      resp.sendRedirect(HOME_URL_NEW);
      return;
    }
    SecureUserDetails
        userDetails = null;
    // END BACKWARD COMPATIBILITY
    if (
        servletPath.startsWith("/s/") || (
            servletPath.startsWith(TASK_URL) && StringUtils
                .isBlank(req.getHeader(Constants.X_APP_ENGINE_TASK_NAME)))) {
      //this is meant for internal api client
      if (StringUtils.isNotBlank(req.getHeader(Constants.X_ACCESS_USER))) {
        try {
          SecurityMgr.setSessionDetails(req.getHeader(Constants.X_ACCESS_USER));
        } catch (UnauthorizedException | ObjectNotFoundException e) {
          xLogger.warn(ISSUE_WITH_API_CLIENT_AUTHENTICATION, e.getMessage());
          resp.sendError(HttpServletResponse.SC_UNAUTHORIZED, e.getMessage());
          return;
        } catch (Exception e) {
          xLogger.severe(ISSUE_WITH_API_CLIENT_AUTHENTICATION, e);
          resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
          return;
        }
      } else if (AuthenticationUtil.hasAccessToken(req)) {
        try {
          AuthenticationUtil.authenticateTokenAndSetSession(req,resp);
        } catch (UnauthorizedException e) {
          xLogger.warn(ISSUE_WITH_API_CLIENT_AUTHENTICATION, e.getMessage());
          SecurityUtils.clearTokenCookie(req,resp);
          resp.sendError(HttpServletResponse.SC_UNAUTHORIZED, e.getMessage());
          return;
        } catch (Exception e) {
          xLogger.severe(ISSUE_WITH_API_CLIENT_AUTHENTICATION, e);
          resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
          return;
        }
      }
      userDetails = SecurityMgr.getUserDetailsIfPresent();
      if (userDetails == null) { // session not authenticated yet; direct to login screen
        resp.sendRedirect(HOME_URL_NEW);  // login please
        return;
      } else {
        String role = userDetails.getRole();
        if (SecurityConstants.ROLE_KIOSKOWNER
            .equals(role)) { // Kiosk owner cannot access this interface
          SessionMgr.cleanupSession(req.getSession());
          resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Operator is not allowed to access");
          return;
        }
        if ((servletPath.contains("/admin/") || servletPath.startsWith(TASK_URL))
            && !SecurityConstants.ROLE_SUPERUSER.equals(role)) { // only superuser can access
          SessionMgr.cleanupSession(req.getSession());
          resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Access forbidden");
          return;
        }

      }
    }
    try {
      if (filterChain != null) {
        filterChain.doFilter(request, response);
      }
    } finally {
      if (userDetails != null) {
        SecurityUtils.setUserDetails(null);
      }
    }
  }

  @Override
  public void destroy() {
    //no cleanup required
  }

  @Override
  public void init(FilterConfig arg0) throws ServletException {
    //nothing to init
  }
}
