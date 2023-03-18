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
import com.google.zxing.DecodeHintTypeview;
import com.google.zxing.NotFoundExceptionview;
import com.google.zxing.Readerview;
import com.google.zxing.ReaderExceptionview;
import com.google.zxing.Resultview;
import com.google.zxing.common.BitArrayview;
import com.google.zxing.oned.rss.RSS14Readerview;
import com.google.zxing.oned.rss.expanded.RSSExpandedReaderview;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

/**
 * @author dswitkin@google.com (Daniel Switkin)
 * @author Sean Owen
 */
public final class MultiFormatOneDReaderview extends OneDReaderview {

  private final OneDReaderview[] readers;

  public MultiFormatOneDReaderview(Map<DecodeHintTypeview,?> hints) {
    @SuppressWarnings("unchecked")
    Collection<BarcodeFormatview> possibleFormats = hints == null ? null :
        (Collection<BarcodeFormatview>) hints.get(DecodeHintTypeview.POSSIBLE_FORMATS);
    boolean useCode39CheckDigit = hints != null &&
        hints.get(DecodeHintTypeview.ASSUME_CODE_39_CHECK_DIGIT) != null;
    Collection<OneDReaderview> readers = new ArrayList<>();
    if (possibleFormats != null) {
      if (possibleFormats.contains(BarcodeFormatview.EAN_13) ||
          possibleFormats.contains(BarcodeFormatview.UPC_A) ||
          possibleFormats.contains(BarcodeFormatview.EAN_8) ||
          possibleFormats.contains(BarcodeFormatview.UPC_E)) {
        readers.add(new MultiFormatUPCEANReaderview(hints));
      }
      if (possibleFormats.contains(BarcodeFormatview.CODE_39)) {
        readers.add(new Code39Readerview(useCode39CheckDigit));
      }
      if (possibleFormats.contains(BarcodeFormatview.CODE_93)) {
        readers.add(new Code93Readerview());
      }
      if (possibleFormats.contains(BarcodeFormatview.CODE_128)) {
        readers.add(new Code128Readerview());
      }
      if (possibleFormats.contains(BarcodeFormatview.ITF)) {
         readers.add(new ITFReaderview());
      }
      if (possibleFormats.contains(BarcodeFormatview.CODABAR)) {
         readers.add(new CodaBarReaderview());
      }
      if (possibleFormats.contains(BarcodeFormatview.RSS_14)) {
         readers.add(new RSS14Readerview());
      }
      if (possibleFormats.contains(BarcodeFormatview.RSS_EXPANDED)) {
        readers.add(new RSSExpandedReaderview());
      }
    }
    if (readers.isEmpty()) {
      readers.add(new MultiFormatUPCEANReaderview(hints));
      readers.add(new Code39Readerview());
      readers.add(new CodaBarReaderview());
      readers.add(new Code93Readerview());
      readers.add(new Code128Readerview());
      readers.add(new ITFReaderview());
      readers.add(new RSS14Readerview());
      readers.add(new RSSExpandedReaderview());
    }
    this.readers = readers.toArray(new OneDReaderview[readers.size()]);
  }

  @Override
  public Resultview decodeRow(int rowNumber,
                              BitArrayview row,
                              Map<DecodeHintTypeview,?> hints) throws NotFoundExceptionview {
    for (OneDReaderview reader : readers) {
      try {
        return reader.decodeRow(rowNumber, row, hints);
      } catch (ReaderExceptionview re) {
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
