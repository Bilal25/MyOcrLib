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

import android.graphics.Bitmap;

import java.util.EnumMap;
import java.util.Map;

/**
 * <p>Encapsulates the result of decoding a barcode within an image.</p>
 *
 * @author Sean Owen
 */
public final class Resultview {

  private final String text;
  private final byte[] rawBytes;
  private final int numBits;
  private ResultPointview[] resultPointviews;
  private final BarcodeFormatview format;
  private Map<ResultMetadataTypeview,Object> resultMetadata;
  private final long timestamp;
  private Bitmap bitmap;

  public Resultview(String text,
                    byte[] rawBytes,
                    ResultPointview[] resultPointviews,
                    BarcodeFormatview format) {
    this(text, rawBytes, resultPointviews, format, System.currentTimeMillis());
  }

  public Resultview(String text,
                    byte[] rawBytes,
                    ResultPointview[] resultPointviews,
                    BarcodeFormatview format,
                    long timestamp) {
    this(text, rawBytes, rawBytes == null ? 0 : 8 * rawBytes.length,
            resultPointviews, format, timestamp);
  }

  public Resultview(String text,
                    byte[] rawBytes,
                    int numBits,
                    ResultPointview[] resultPointviews,
                    BarcodeFormatview format,
                    long timestamp) {
    this.text = text;
    this.rawBytes = rawBytes;
    this.numBits = numBits;
    this.resultPointviews = resultPointviews;
    this.format = format;
    this.resultMetadata = null;
    this.timestamp = timestamp;
  }

  /**
   * @return raw text encoded by the barcode
   */
  public String getText() {
    return text;
  }

  /**
   * @return raw bytes encoded by the barcode, if applicable, otherwise {@code null}
   */
  public byte[] getRawBytes() {
    return rawBytes;
  }

  /**
   * @return how many bits of {@link #getRawBytes()} are valid; typically 8 times its length
   * @since 3.3.0
   */
  public int getNumBits() {
    return numBits;
  }

  /**
   * @return points related to the barcode in the image. These are typically points
   *         identifying finder patterns or the corners of the barcode. The exact meaning is
   *         specific to the type of barcode that was decoded.
   */
  public ResultPointview[] getResultPoints() {
    return resultPointviews;
  }

  /**
   * @return {@link BarcodeFormatview} representing the format of the barcode that was decoded
   */
  public BarcodeFormatview getBarcodeFormat() {
    return format;
  }

  /**
   * @return {@link Map} mapping {@link ResultMetadataTypeview} keys to values. May be
   *   {@code null}. This contains optional metadata about what was detected about the barcode,
   *   like orientation.
   */
  public Map<ResultMetadataTypeview,Object> getResultMetadata() {
    return resultMetadata;
  }

  public void putMetadata(ResultMetadataTypeview type, Object value) {
    if (resultMetadata == null) {
      resultMetadata = new EnumMap<>(ResultMetadataTypeview.class);
    }
    resultMetadata.put(type, value);
  }

  public void putAllMetadata(Map<ResultMetadataTypeview,Object> metadata) {
    if (metadata != null) {
      if (resultMetadata == null) {
        resultMetadata = metadata;
      } else {
        resultMetadata.putAll(metadata);
      }
    }
  }

  public void addResultPoints(ResultPointview[] newPoints) {
    ResultPointview[] oldPoints = resultPointviews;
    if (oldPoints == null) {
      resultPointviews = newPoints;
    } else if (newPoints != null && newPoints.length > 0) {
      ResultPointview[] allPoints = new ResultPointview[oldPoints.length + newPoints.length];
      System.arraycopy(oldPoints, 0, allPoints, 0, oldPoints.length);
      System.arraycopy(newPoints, 0, allPoints, oldPoints.length, newPoints.length);
      resultPointviews = allPoints;
    }
  }

  public long getTimestamp() {
    return timestamp;
  }

  @Override
  public String toString() {
    return text;
  }

  public Bitmap getBitmap() {
    return bitmap;
  }

  public void setBitmap(Bitmap bitmap) {
    this.bitmap = bitmap;
  }
}
