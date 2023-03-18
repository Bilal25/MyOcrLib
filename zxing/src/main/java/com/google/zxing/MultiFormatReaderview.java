/*
 * Copyright 2007 ZXing authors
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

package com.google.zxing;

import com.google.zxing.oned.MultiFormatOneDReaderview;
import com.google.zxing.qrcode.QRCodeReaderview;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

/**
 * MultiFormatReader is a convenience class and the main entry point into the library for most uses.
 * By default it attempts to decode all barcode formats that the library supports. Optionally, you
 * can provide a hints object to request different behavior, for example only decoding QR codes.
 *
 * @author Sean Owen
 * @author dswitkin@google.com (Daniel Switkin)
 */
public final class MultiFormatReaderview implements Readerview {

  private Map<DecodeHintTypeview,?> hints;
  private Readerview[] readerviews;

  /**
   * This version of decode honors the intent of Reader.decode(BinaryBitmap) in that it
   * passes null as a hint to the decoders. However, that makes it inefficient to call repeatedly.
   * Use setHints() followed by decodeWithState() for continuous scan applications.
   *
   * @param image The pixel data to decode
   * @return The contents of the image
   * @throws NotFoundExceptionview Any errors which occurred
   */
  @Override
  public Resultview decode(BinaryBitmapview image) throws NotFoundExceptionview {
    setHints(null);
    return decodeInternal(image);
  }

  /**
   * Decode an image using the hints provided. Does not honor existing state.
   *
   * @param image The pixel data to decode
   * @param hints The hints to use, clearing the previous state.
   * @return The contents of the image
   * @throws NotFoundExceptionview Any errors which occurred
   */
  @Override
  public Resultview decode(BinaryBitmapview image, Map<DecodeHintTypeview,?> hints) throws NotFoundExceptionview {
    setHints(hints);
    return decodeInternal(image);
  }

  /**
   * Decode an image using the state set up by calling setHints() previously. Continuous scan
   * clients will get a <b>large</b> speed increase by using this instead of decode().
   *
   * @param image The pixel data to decode
   * @return The contents of the image
   * @throws NotFoundExceptionview Any errors which occurred
   */
  public Resultview decodeWithState(BinaryBitmapview image) throws NotFoundExceptionview {
    // Make sure to set up the default state so we don't crash
    if (readerviews == null) {
      setHints(null);
    }
    return decodeInternal(image);
  }

  /**
   * This method adds state to the MultiFormatReader. By setting the hints once, subsequent calls
   * to decodeWithState(image) can reuse the same set of readers without reallocating memory. This
   * is important for performance in continuous scan clients.
   *
   * @param hints The set of hints to use for subsequent calls to decode(image)
   */
  public void setHints(Map<DecodeHintTypeview,?> hints) {
    this.hints = hints;

    boolean tryHarder = hints != null && hints.containsKey(DecodeHintTypeview.TRY_HARDER);
    @SuppressWarnings("unchecked")
    Collection<BarcodeFormatview> formats =
        hints == null ? null : (Collection<BarcodeFormatview>) hints.get(DecodeHintTypeview.POSSIBLE_FORMATS);
    Collection<Readerview> readerviews = new ArrayList<>();
    if (formats != null) {
      boolean addOneDReader =
          formats.contains(BarcodeFormatview.UPC_A) ||
          formats.contains(BarcodeFormatview.UPC_E) ||
          formats.contains(BarcodeFormatview.EAN_13) ||
          formats.contains(BarcodeFormatview.EAN_8) ||
          formats.contains(BarcodeFormatview.CODABAR) ||
          formats.contains(BarcodeFormatview.CODE_39) ||
          formats.contains(BarcodeFormatview.CODE_93) ||
          formats.contains(BarcodeFormatview.CODE_128) ||
          formats.contains(BarcodeFormatview.ITF) ||
          formats.contains(BarcodeFormatview.RSS_14) ||
          formats.contains(BarcodeFormatview.RSS_EXPANDED);
      // Put 1D readers upfront in "normal" mode
      if (addOneDReader && !tryHarder) {
        readerviews.add(new MultiFormatOneDReaderview(hints));
      }
      if (formats.contains(BarcodeFormatview.QR_CODE)) {
        readerviews.add(new QRCodeReaderview());
      }
      // At end in "try harder" mode
      if (addOneDReader && tryHarder) {
        readerviews.add(new MultiFormatOneDReaderview(hints));
      }
    }
    if (readerviews.isEmpty()) {
      if (!tryHarder) {
        readerviews.add(new MultiFormatOneDReaderview(hints));
      }

      readerviews.add(new QRCodeReaderview());

      if (tryHarder) {
        readerviews.add(new MultiFormatOneDReaderview(hints));
      }
    }
    this.readerviews = readerviews.toArray(new Readerview[readerviews.size()]);
  }

  @Override
  public void reset() {
    if (readerviews != null) {
      for (Readerview readerview : readerviews) {
        readerview.reset();
      }
    }
  }

  private Resultview decodeInternal(BinaryBitmapview image) throws NotFoundExceptionview {
    if (readerviews != null) {
      for (Readerview readerview : readerviews) {
        try {
          return readerview.decode(image, hints);
        } catch (ReaderExceptionview re) {
          // continue
        }
      }
    }
    throw NotFoundExceptionview.getNotFoundInstance();
  }

}
