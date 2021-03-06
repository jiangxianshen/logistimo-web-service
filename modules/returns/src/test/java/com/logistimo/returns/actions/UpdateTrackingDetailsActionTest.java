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

package com.logistimo.returns.actions;

import com.logistimo.returns.service.ReturnsRepository;
import com.logistimo.returns.utility.ReturnsGsonMapper;
import com.logistimo.returns.utility.ReturnsTestConstant;
import com.logistimo.returns.utility.ReturnsTestUtility;
import com.logistimo.returns.vo.ReturnsTrackingDetailsVO;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.powermock.modules.junit4.PowerMockRunner;
import org.testng.Assert;

import java.io.IOException;

import static com.logistimo.returns.utility.ReturnsTestUtility.getTrackingDetails;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Created by pratheeka on 09/08/18.
 */
@RunWith(PowerMockRunner.class)
public class UpdateTrackingDetailsActionTest {


  @Mock
  private ReturnsRepository returnsRepository;

  @InjectMocks
  private UpdateReturnsTrackingDetailAction updateReturnsTrackingDetailAction;

  @Mock
  private GetReturnsAction getReturnsAction;

  @Test
  public void testUpdateTrackingDetails() throws IOException {

    when(returnsRepository.getReturnTrackingDetails(1l))
        .thenReturn(getTrackingDetails(ReturnsTestConstant.RETURNS_TRACKING_DETAILS));
    when(returnsRepository.getReturnTrackingDetails(2l)).thenReturn(null);
    when(getReturnsAction.invoke(any())).thenReturn(ReturnsTestUtility.getReturnsVO());
    ReturnsTrackingDetailsVO updatedTrackingVO =
        getTrackingDetails(ReturnsTestConstant.UPDATED_RETURNS_TRACKING_DETAILS);
    when(returnsRepository.saveReturnsTrackingDetails(any())).thenReturn(updatedTrackingVO);
    ReturnsTrackingDetailsVO savedTrackingDetails =
        updateReturnsTrackingDetailAction.invoke(updatedTrackingVO, 1l);
    Assert.assertNotNull(savedTrackingDetails.getTransporter());
    Assert.assertNotNull(savedTrackingDetails.getTrackingId());
    savedTrackingDetails = updateReturnsTrackingDetailAction.invoke(updatedTrackingVO, 2l);
    Assert.assertNotNull(savedTrackingDetails.getTransporter());
    Assert.assertNotNull(savedTrackingDetails.getTrackingId());

  }


}
