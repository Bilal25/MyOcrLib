/*
 * Copyright 2008 ZXing authors
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
import com.google.zxing.DecodeHintTypeview;
import com.google.zxing.NotFoundExceptionview;
import com.google.zxing.Readerview;
import com.google.zxing.ReaderExceptionview;
import com.google.zxing.Resultview;
import com.google.zxing.common.BitArrayview;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

/**
 * <p>A reader that can read all available UPC/EAN formats. If a caller wants to try to
 * read all such formats, it is most efficient to use this implementation rather than invoke
 * individual readers.</p>
 *
 * @author Sean Owen
 */
public final class MultiFormatUPCEANReaderview extends OneDReaderview {

  private final UPCEANReaderview[] readers;

  public MultiFormatUPCEANReaderview(Map<DecodeHintTypeview,?> hints) {
    @SuppressWarnings("unchecked")
    Collection<BarcodeFormatview> possibleFormats = hints == null ? null :
        (Collection<BarcodeFormatview>) hints.get(DecodeHintTypeview.POSSIBLE_FORMATS);
    Collection<UPCEANReaderview> readers = new ArrayList<>();
    if (possibleFormats != null) {
      if (possibleFormats.contains(BarcodeFormatview.EAN_13)) {
        readers.add(new EAN13Readerview());
      } else if (possibleFormats.contains(BarcodeFormatview.UPC_A)) {
        readers.add(new UPCAReaderview());
      }
      if (possibleFormats.contains(BarcodeFormatview.EAN_8)) {
        readers.add(new EAN8Readerview());
      }
      if (possibleFormats.contains(BarcodeFormatview.UPC_E)) {
        readers.add(new UPCEReaderview());
      }
    }
    if (readers.isEmpty()) {
      readers.add(new EAN13Readerview());
      // UPC-A is covered by EAN-13
      readers.add(new EAN8Readerview());
      readers.add(new UPCEReaderview());
    }
    this.readers = readers.toArray(new UPCEANReaderview[readers.size()]);
  }

  @Override
  public Resultview decodeRow(int rowNumber,
                              BitArrayview row,
                              Map<DecodeHintTypeview,?> hints) throws NotFoundExceptionview {
    // Compute this location once and reuse it on multiple implementations
    int[] startGuardPattern = UPCEANReaderview.findStartGuardPattern(row);
    for (UPCEANReaderview reader : readers) {
      try {
        Resultview resultview = reader.decodeRow(rowNumber, row, startGuardPattern, hints);
        // Special case: a 12-digit code encoded in UPC-A is identical to a "0"
        // followed by those 12 digits encoded as EAN-13. Each will recognize such a code,
        // UPC-A as a 12-digit string and EAN-13 as a 13-digit string starting with "0".
        // Individually these are correct and their readers will both read such a code
        // and correctly call it EAN-13, or UPC-A, respectively.
        //
        // In this case, if we've been looking for both types, we'd like to call it
        // a UPC-A code. But for efficiency we only run the EAN-13 decoder to also read
        // UPC-A. So we special case it here, and convert an EAN-13 result to a UPC-A
        // result if appropriate.
        //
        // But, don't return UPC-A if UPC-A was not a requested format!
        boolean ean13MayBeUPCA =
            resultview.getBarcodeFormat() == BarcodeFormatview.EAN_13 &&
                resultview.getText().charAt(0) == '0';
        @SuppressWarnings("unchecked")
        Collection<BarcodeFormatview> possibleFormats =
            hints == null ? null : (Collection<BarcodeFormatview>) hints.get(DecodeHintTypeview.POSSIBLE_FORMATS);
        boolean canReturnUPCA = possibleFormats == null || possibleFormats.contains(BarcodeFormatview.UPC_A);
  
        if (ean13MayBeUPCA && canReturnUPCA) {
          // Transfer the metdata across
          Resultview resultviewUPCA = new Resultview(resultview.getText().substring(1),
                                         resultview.getRawBytes(),
                                         resultview.getResultPoints(),
                                         BarcodeFormatview.UPC_A);
          resultviewUPCA.putAllMetadata(resultview.getResultMetadata());
          return resultviewUPCA;
        }
        return resultview;
      } catch (ReaderExceptionview ignored) {
        // continue
      }
    }

    throw NotFoundExceptionview.getNotFoundInstance();
  }

  @Override
  public void reset() {
    for (Readerview readerview : readers) {
      readerview.reset();
    }
  }

}
