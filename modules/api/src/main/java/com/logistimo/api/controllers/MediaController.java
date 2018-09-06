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

package com.logistimo.api.controllers;

import com.logistimo.AppFactory;
import com.logistimo.api.models.MediaModels;
import com.logistimo.api.util.FileValidationUtil;
import com.logistimo.auth.utils.SecurityUtils;
import com.logistimo.common.builder.MediaBuilder;
import com.logistimo.constants.Constants;
import com.logistimo.dao.JDOUtils;
import com.logistimo.exception.InvalidDataException;
import com.logistimo.exception.InvalidServiceException;
import com.logistimo.exception.ValidationException;
import com.logistimo.logger.XLog;
import com.logistimo.media.SupportedMediaTypes;
import com.logistimo.media.endpoints.IMediaEndPoint;
import com.logistimo.media.entity.IMedia;
import com.logistimo.models.MediaModel;
import com.logistimo.services.ServiceException;
import com.logistimo.services.blobstore.BlobKey;
import com.logistimo.services.blobstore.BlobstoreService;
import com.logistimo.services.impl.PMF;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.springframework.stereotype.Controller;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.multipart.commons.CommonsMultipartResolver;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.jdo.PersistenceManager;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author Mohan Raja
 */

@Controller
@RequestMapping("/mediaendpoint")
public class MediaController {
  private static final XLog xLogger = XLog.getLog(MediaController.class);

  MediaBuilder builder = new MediaBuilder();

  @RequestMapping(value = "/v1/mediaforDomain/{domainKey:.+}", method = RequestMethod.GET)
  @ResponseBody
  public MediaModels getMedia(@PathVariable String domainKey) {
    IMediaEndPoint endPoint = JDOUtils.createInstance(IMediaEndPoint.class);
    List<IMedia> mediaList = endPoint.getMedias(domainKey);
    return new MediaModels(builder.constructMediaModelList(mediaList));
  }

  @RequestMapping(value = "/v1/media/{id:.+}", method = RequestMethod.DELETE)
  @ResponseBody
  public void deleteMedia(@PathVariable Long id) {
    IMediaEndPoint endPoint = JDOUtils.createInstance(IMediaEndPoint.class);
    try {
      endPoint.removeMedia(id);
      xLogger.info(SecurityUtils.getUsername() + " deleted the media successfully for id: " + id);
    } catch (ServiceException e) {
      xLogger.warn("Error while deleting image.", e);
      throw new InvalidServiceException("Error while deleting image.");
    }
  }

  @RequestMapping(value = "/v1/media/", method = RequestMethod.POST)
  @ResponseBody
  public MediaModels uploadMedia(@RequestBody MediaModel model,
                                 @RequestHeader(value = "lg-cs", defaultValue = "0") long checksum) {
    FileValidationUtil.validateMediaFile(model.mediaType);
    FileValidationUtil.validateMediaFileSize(model.content.value);
    IMedia media = builder.constructMedia(model);
    byte[] imageBytes = Base64.decodeBase64(media.getContent().getBytes());
    FileValidationUtil.validateImageFile(imageBytes, checksum);
    IMediaEndPoint endPoint = JDOUtils.createInstance(IMediaEndPoint.class);
    IMedia m = endPoint.insertMedia(media);
    List<MediaModel> modelList = new ArrayList<>(1);
    MediaModel mm = builder.constructMediaModel(m);
    if (mm != null) {
      modelList.add(mm);
    }
    xLogger.info(
        SecurityUtils.getUsername() + " uploaded media successfully for key: " + model.domainKey);
    return new MediaModels(modelList);
  }

  @RequestMapping(value = "/v2/media/{domainId:.+}", method = RequestMethod.POST)
  @ResponseBody
  public MediaModels uploadFileMedia(@PathVariable String domainId, HttpServletRequest request,
                                     @RequestHeader(value = "lg-cs", defaultValue = "0") long checksum)
      throws IOException {
    boolean isMultipart = ServletFileUpload.isMultipartContent(request);
    MediaModels mediaResponse = new MediaModels(new ArrayList<>(1));
    if (!isMultipart) {
      throw new InvalidDataException("Media upload has to be a multi part file upload request");
    }
    PersistenceManager pm = PMF.get().getPersistenceManager();
    try {
      MultipartHttpServletRequest
          multiRequest =
          new CommonsMultipartResolver().resolveMultipart(request);
      MultiValueMap<String, MultipartFile> fileMap = multiRequest.getMultiFileMap();
      BlobstoreService blobstoreService = AppFactory.get().getBlobstoreService();
      for (String fieldName : fileMap.keySet()) {
        MultipartFile file = fileMap.getFirst(fieldName);
        String fileName = file.getOriginalFilename();
        String contentType = file.getContentType();
        long sizeInBytes = file.getSize();
        validateFileType(fileName);
        validateFileSize(sizeInBytes);
        String
            blobKey = null;
        try {
          String fileExt = fileName.substring(fileName.lastIndexOf('.') + 1);
          blobKey = blobstoreService
              .store(domainId, contentType, sizeInBytes, file.getInputStream(), "/media");
          FileValidationUtil.validateImageFile(blobstoreService.read(blobKey), checksum);
          IMedia media = JDOUtils.createInstance(IMedia.class);
          media.setBlobKey(new BlobKey(blobKey));
          media.setServingUrl(blobKey);
          media.setUploadTime(new Date());
          media.setMediaType(SupportedMediaTypes.valueOf(fileExt));
          media.setDomainKey(domainId);
          pm.makePersistent(media);
          xLogger.info(SecurityUtils.getUsername() + " uploaded media for key: " + domainId);
          mediaResponse.items.add(builder.constructMediaModel(media, fieldName));
        } catch (IllegalArgumentException e) {
          throw new InvalidDataException(
              "File type should be jpg/jpeg/png/giff but was " + fileName);
        } catch (ValidationException e) {
          if (blobKey != null && "ME006".equals(e.getCode())) {
            blobstoreService.remove(blobKey);
          }
          throw e;
        }
      }
    } catch (Exception e) {
      xLogger.severe("Error occurred while uploading image", e);
      throw e;
    } finally {
      pm.close();
    }
    return mediaResponse;
  }

  @RequestMapping(value = "/media", method = RequestMethod.GET)
  @ResponseBody
  public void serveImageURL(@RequestParam String url, HttpServletResponse response) {
    try {
      AppFactory.get().getBlobstoreService().serve(url, response);
    } catch (IOException e) {
      xLogger.warn("Error in serving image with key:" + url, e);
    }
  }

  private void validateFileType(String fileName) {
    if (fileName.split("\\.").length > 2) {
      throw new ValidationException("ME004", fileName);
    }
    String extension = fileName.substring(fileName.lastIndexOf(".") + 1);
    FileValidationUtil.validateMediaFile(extension);
  }

  private void validateFileSize(long sizeInBytes) {
    if (sizeInBytes > 0) {
      long sizeinMb = sizeInBytes / (1024 * 1024);
      if (sizeinMb > Constants.MEDIA_SIZE_IN_MB) {
        throw new ValidationException("ME005", new Object[]{null});
      }
    }
  }
}
