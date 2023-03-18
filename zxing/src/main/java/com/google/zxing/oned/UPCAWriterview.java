/*
 * Copyright 2010 ZXing authors
 *
 * Licensed under the Apache License, version 2.0 (the "License");
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

import com.google.zxing.BarcodeFormatview;
import com.google.zxing.EncodeHintTypeview;
import com.google.zxing.Writerview;
import com.google.zxing.WriterExceptionview;
import com.google.zxing.common.BitMatrixview;

import java.util.Map;

/**
 * This object renders a UPC-A code as a {@link BitMatrixview}.
 *
 * @author qwandor@google.com (Andrew Walbran)
 */
public final class UPCAWriterview implements Writerview {

  private final EAN13Writerview subWriter = new EAN13Writerview();

  @Override
  public BitMatrixview encode(String contents, BarcodeFormatview format, int width, int height)
      throws WriterExceptionview {
    return encode(contents, format, width, height, null);
  }

  @Override
  public BitMatrixview encode(String contents,
                          BarcodeFormatview format,
                          int width,
                          int height,
                          Map<EncodeHintTypeview,?> hints) throws WriterExceptionview {
    if (format != BarcodeFormatview.UPC_A) {
      throw new IllegalArgumentException("Can only encode UPC-A, but got " + format);
    }
    // Transform a UPC-A code into the equivalent EAN-13 code and write it that way
    return subWriter.encode('0' + contents, BarcodeFormatview.EAN_13, width, height, hints);
  }

}
