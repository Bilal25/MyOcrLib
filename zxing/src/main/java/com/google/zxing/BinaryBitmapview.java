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

package com.google.zxing;

import com.google.zxing.common.BitArrayview;
import com.google.zxing.common.BitMatrixview;

/**
 * This class is the core bitmap class used by ZXing to represent 1 bit data. Reader objects
 * accept a BinaryBitmap and attempt to decode it.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
public final class BinaryBitmapview {

  private final BinarizerView binarizerView;
  private BitMatrixview matrix;

  public BinaryBitmapview(BinarizerView binarizerView) {
    if (binarizerView == null) {
      throw new IllegalArgumentException("Binarizer must be non-null.");
    }
    this.binarizerView = binarizerView;
  }

  /**
   * @return The width of the bitmap.
   */
  public int getWidth() {
    return binarizerView.getWidth();
  }

  /**
   * @return The height of the bitmap.
   */
  public int getHeight() {
    return binarizerView.getHeight();
  }

  /**
   * Converts one row of luminance data to 1 bit data. May actually do the conversion, or return
   * cached data. Callers should assume this method is expensive and call it as seldom as possible.
   * This method is intended for decoding 1D barcodes and may choose to apply sharpening.
   *
   * @param y The row to fetch, which must be in [0, bitmap height)
   * @param row An optional preallocated array. If null or too small, it will be ignored.
   *            If used, the Binarizer will call BitArrayview.clear(). Always use the returned object.
   * @return The array of bits for this row (true means black).
   * @throws NotFoundExceptionview if row can't be binarized
   */
  public BitArrayview getBlackRow(int y, BitArrayview row) throws NotFoundExceptionview {
    return binarizerView.getBlackRow(y, row);
  }

  /**
   * Converts a 2D array of luminance data to 1 bit. As above, assume this method is expensive
   * and do not call it repeatedly. This method is intended for decoding 2D barcodes and may or
   * may not apply sharpening. Therefore, a row from this matrix may not be identical to one
   * fetched using getBlackRow(), so don't mix and match between them.
   *
   * @return The 2D array of bits for the image (true means black).
   * @throws NotFoundExceptionview if image can't be binarized to make a matrix
   */
  public BitMatrixview getBlackMatrix() throws NotFoundExceptionview {
    // The matrix is created on demand the first time it is requested, then cached. There are two
    // reasons for this:
    // 1. This work will never be done if the caller only installs 1D Reader objects, or if a
    //    1D Reader finds a barcode before the 2D Readers run.
    // 2. This work will only be done once even if the caller installs multiple 2D Readers.
    if (matrix == null) {
      matrix = binarizerView.getBlackMatrix();
    }
    return matrix;
  }

  /**
   * @return Whether this bitmap can be cropped.
   */
  public boolean isCropSupported() {
    return binarizerView.getLuminanceSource().isCropSupported();
  }

  /**
   * Returns a new object with cropped image data. Implementations may keep a reference to the
   * original data rather than a copy. Only callable if isCropSupported() is true.
   *
   * @param left The left coordinate, which must be in [0,getWidth())
   * @param top The top coordinate, which must be in [0,getHeight())
   * @param width The width of the rectangle to crop.
   * @param height The height of the rectangle to crop.
   * @return A cropped version of this object.
   */
  public BinaryBitmapview crop(int left, int top, int width, int height) {
    LuminanceSourceview newSource = binarizerView.getLuminanceSource().crop(left, top, width, height);
    return new BinaryBitmapview(binarizerView.createBinarizer(newSource));
  }

  /**
   * @return Whether this bitmap supports counter-clockwise rotation.
   */
  public boolean isRotateSupported() {
    return binarizerView.getLuminanceSource().isRotateSupported();
  }

  /**
   * Returns a new object with rotated image data by 90 degrees counterclockwise.
   * Only callable if {@link #isRotateSupported()} is true.
   *
   * @return A rotated version of this object.
   */
  public BinaryBitmapview rotateCounterClockwise() {
    LuminanceSourceview newSource = binarizerView.getLuminanceSource().rotateCounterClockwise();
    return new BinaryBitmapview(binarizerView.createBinarizer(newSource));
  }

  /**
   * Returns a new object with rotated image data by 45 degrees counterclockwise.
   * Only callable if {@link #isRotateSupported()} is true.
   *
   * @return A rotated version of this object.
   */
  public BinaryBitmapview rotateCounterClockwise45() {
    LuminanceSourceview newSource = binarizerView.getLuminanceSource().rotateCounterClockwise45();
    return new BinaryBitmapview(binarizerView.createBinarizer(newSource));
  }

  @Override
  public String toString() {
    try {
      return getBlackMatrix().toString();
    } catch (NotFoundExceptionview e) {
      return "";
    }
  }

}
