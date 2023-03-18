/*
 * Copyright 2008 ZXing authors
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

import com.google.zxing.BarcodeFormatview;
import com.google.zxing.BinaryBitmapview;
import com.google.zxing.ChecksumExceptionvew;
import com.google.zxing.DecodeHintTypeview;
import com.google.zxing.FormatExceptionview;
import com.google.zxing.NotFoundExceptionview;
import com.google.zxing.Resultview;
import com.google.zxing.common.BitArrayview;

import java.util.Map;

/**
 * <p>Implements decoding of the UPC-A format.</p>
 *
 * @author dswitkin@google.com (Daniel Switkin)
 * @author Sean Owen
 */
public final class UPCAReaderview extends UPCEANReaderview {

  private final UPCEANReaderview ean13Reader = new EAN13Readerview();

  @Override
  public Resultview decodeRow(int rowNumber,
                              BitArrayview row,
                              int[] startGuardRange,
                              Map<DecodeHintTypeview,?> hints)
      throws NotFoundExceptionview, FormatExceptionview, ChecksumExceptionvew {
    return maybeReturnResult(ean13Reader.decodeRow(rowNumber, row, startGuardRange, hints));
  }

  @Override
  public Resultview decodeRow(int rowNumber, BitArrayview row, Map<DecodeHintTypeview,?> hints)
      throws NotFoundExceptionview, FormatExceptionview, ChecksumExceptionvew {
    return maybeReturnResult(ean13Reader.decodeRow(rowNumber, row, hints));
  }

  @Override
  public Resultview decode(BinaryBitmapview image) throws NotFoundExceptionview, FormatExceptionview {
    return maybeReturnResult(ean13Reader.decode(image));
  }

  @Override
  public Resultview decode(BinaryBitmapview image, Map<DecodeHintTypeview,?> hints)
      throws NotFoundExceptionview, FormatExceptionview {
    return maybeReturnResult(ean13Reader.decode(image, hints));
  }

  @Override
  BarcodeFormatview getBarcodeFormat() {
    return BarcodeFormatview.UPC_A;
  }

  @Override
  protected int decodeMiddle(BitArrayview row, int[] startRange, StringBuilder resultString)
      throws NotFoundExceptionview {
    return ean13Reader.decodeMiddle(row, startRange, resultString);
  }

  private static Resultview maybeReturnResult(Resultview resultview) throws FormatExceptionview {
    String text = resultview.getText();
    if (text.charAt(0) == '0') {
      return new Resultview(text.substring(1), null, resultview.getResultPoints(), BarcodeFormatview.UPC_A);
    } else {
      throw FormatExceptionview.getFormatInstance();
    }
  }

}
