/*
 * Copyright 2009 ZXing authors
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

import java.util.Map;

import com.google.zxing.BarcodeFormatview;
import com.google.zxing.EncodeHintTypeview;
import com.google.zxing.FormatExceptionview;
import com.google.zxing.WriterExceptionview;
import com.google.zxing.common.BitMatrixview;

/**
 * This object renders an UPC-E code as a {@link BitMatrixview}.
 *
 * @author 0979097955s@gmail.com (RX)
 */
public final class UPCEWriterview extends UPCEANWriterview {

  private static final int CODE_WIDTH = 3 + // start guard
      (7 * 6) + // bars
      6; // end guard

  @Override
  public BitMatrixview encode(String contents,
                              BarcodeFormatview format,
                              int width,
                              int height,
                              Map<EncodeHintTypeview, ?> hints) throws WriterExceptionview {
    if (format != BarcodeFormatview.UPC_E) {
      throw new IllegalArgumentException("Can only encode UPC_E, but got " + format);
    }

    return super.encode(contents, format, width, height, hints);
  }

  @Override
  public boolean[] encode(String contents) {
    int length = contents.length();
    switch (length) {
      case 7:
        // No check digit present, calculate it and add it
        int check;
        try {
          check = UPCEANReaderview.getStandardUPCEANChecksum(UPCEReaderview.convertUPCEtoUPCA(contents));
        } catch (FormatExceptionview fe) {
          throw new IllegalArgumentException(fe);
        }
        contents += check;
        break;
      case 8:
        try {
          if (!UPCEANReaderview.checkStandardUPCEANChecksum(contents)) {
            throw new IllegalArgumentException("Contents do not pass checksum");
          }
        } catch (FormatExceptionview ignored) {
          throw new IllegalArgumentException("Illegal contents");
        }
        break;
      default:
        throw new IllegalArgumentException(
            "Requested contents should be 8 digits long, but got " + length);
    }

    int firstDigit = Character.digit(contents.charAt(0), 10);
    if (firstDigit != 0 && firstDigit != 1) {
      throw new IllegalArgumentException("Number system must be 0 or 1");
    }

    int checkDigit = Character.digit(contents.charAt(7), 10);
    int parities = UPCEReaderview.NUMSYS_AND_CHECK_DIGIT_PATTERNS[firstDigit][checkDigit];
    boolean[] result = new boolean[CODE_WIDTH];
    int pos = 0;

    pos += appendPattern(result, pos, UPCEANReaderview.START_END_PATTERN, true);

    for (int i = 1; i <= 6; i++) {
      int digit = Character.digit(contents.charAt(i), 10);
      if ((parities >> (6 - i) & 1) == 1) {
        digit += 10;
      }
      pos += appendPattern(result, pos, UPCEANReaderview.L_AND_G_PATTERNS[digit], false);
    }

    appendPattern(result, pos, UPCEANReaderview.END_PATTERN, false);

    return result;
  }

}
