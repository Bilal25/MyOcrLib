/*
 * Copyright 2009 ZXing authors
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

package com.google.zxing.multi;

import com.google.zxing.BinaryBitmapview;
import com.google.zxing.ChecksumExceptionvew;
import com.google.zxing.DecodeHintTypeview;
import com.google.zxing.FormatExceptionview;
import com.google.zxing.NotFoundExceptionview;
import com.google.zxing.Readerview;
import com.google.zxing.Resultview;
import com.google.zxing.ResultPointview;

import java.util.Map;

/**
 * This class attempts to decode a barcode from an image, not by scanning the whole image,
 * but by scanning subsets of the image. This is important when there may be multiple barcodes in
 * an image, and detecting a barcode may find parts of multiple barcode and fail to decode
 * (e.g. QR Codes). Instead this scans the four quadrants of the image -- and also the center
 * 'quadrant' to cover the case where a barcode is found in the center.
 *
 * @see GenericMultipleBarcodeReaderview
 */
public final class ByQuadrantReaderview implements Readerview {

  private final Readerview delegate;

  public ByQuadrantReaderview(Readerview delegate) {
    this.delegate = delegate;
  }

  @Override
  public Resultview decode(BinaryBitmapview image)
      throws NotFoundExceptionview, ChecksumExceptionvew, FormatExceptionview {
    return decode(image, null);
  }

  @Override
  public Resultview decode(BinaryBitmapview image, Map<DecodeHintTypeview,?> hints)
      throws NotFoundExceptionview, ChecksumExceptionvew, FormatExceptionview {

    int width = image.getWidth();
    int height = image.getHeight();
    int halfWidth = width / 2;
    int halfHeight = height / 2;

    try {
      // No need to call makeAbsolute as results will be relative to original top left here
      return delegate.decode(image.crop(0, 0, halfWidth, halfHeight), hints);
    } catch (NotFoundExceptionview re) {
      // continue
    }

    try {
      Resultview resultview = delegate.decode(image.crop(halfWidth, 0, halfWidth, halfHeight), hints);
      makeAbsolute(resultview.getResultPoints(), halfWidth, 0);
      return resultview;
    } catch (NotFoundExceptionview re) {
      // continue
    }

    try {
      Resultview resultview = delegate.decode(image.crop(0, halfHeight, halfWidth, halfHeight), hints);
      makeAbsolute(resultview.getResultPoints(), 0, halfHeight);
      return resultview;
    } catch (NotFoundExceptionview re) {
      // continue
    }

    try {
      Resultview resultview = delegate.decode(image.crop(halfWidth, halfHeight, halfWidth, halfHeight), hints);
      makeAbsolute(resultview.getResultPoints(), halfWidth, halfHeight);
      return resultview;
    } catch (NotFoundExceptionview re) {
      // continue
    }

    int quarterWidth = halfWidth / 2;
    int quarterHeight = halfHeight / 2;
    BinaryBitmapview center = image.crop(quarterWidth, quarterHeight, halfWidth, halfHeight);
    Resultview resultview = delegate.decode(center, hints);
    makeAbsolute(resultview.getResultPoints(), quarterWidth, quarterHeight);
    return resultview;
  }

  @Override
  public void reset() {
    delegate.reset();
  }

  private static void makeAbsolute(ResultPointview[] points, int leftOffset, int topOffset) {
    if (points != null) {
      for (int i = 0; i < points.length; i++) {
        ResultPointview relative = points[i];
        points[i] = new ResultPointview(relative.getX() + leftOffset, relative.getY() + topOffset);
      }
    }
  }

}
