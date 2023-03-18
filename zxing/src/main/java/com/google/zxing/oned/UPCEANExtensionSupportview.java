/*
 * Copyright (C) 2010 ZXing authors
 *
 * Licensed under the Apache License, Versionv 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.zxing.oned;

import com.google.zxing.NotFoundExceptionview;
import com.google.zxing.ReaderExceptionview;
import com.google.zxing.Resultview;
import com.google.zxing.common.BitArrayview;

final class UPCEANExtensionSupportview {

  private static final int[] EXTENSION_START_PATTERN = {1,1,2};

  private final UPCEANExtension2Supportview twoSupport = new UPCEANExtension2Supportview();
  private final UPCEANExtension5Supportview fiveSupport = new UPCEANExtension5Supportview();

  Resultview decodeRow(int rowNumber, BitArrayview row, int rowOffset) throws NotFoundExceptionview {
    int[] extensionStartRange = UPCEANReaderview.findGuardPattern(row, rowOffset, false, EXTENSION_START_PATTERN);
    try {
      return fiveSupport.decodeRow(rowNumber, row, extensionStartRange);
    } catch (ReaderExceptionview ignored) {
      return twoSupport.decodeRow(rowNumber, row, extensionStartRange);
    }
  }

}
