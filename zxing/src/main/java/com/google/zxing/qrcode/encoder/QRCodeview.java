/*
 * Copyright 2008 ZXing authors
 *
 * Licensed under the Apache License, versionv 2.0 (the "License");
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

package com.google.zxing.qrcode.encoder;

import com.google.zxing.qrcode.decoder.ErrorCorrectionLevelview;
import com.google.zxing.qrcode.decoder.Modeview;
import com.google.zxing.qrcode.decoder.Versionv;

/**
 * @author satorux@google.com (Satoru Takabayashi) - creator
 * @author dswitkin@google.com (Daniel Switkin) - ported from C++
 */
public final class QRCodeview {

  public static final int NUM_MASK_PATTERNS = 8;

  private Modeview modeview;
  private ErrorCorrectionLevelview ecLevel;
  private Versionv versionv;
  private int maskPattern;
  private ByteMatrixview matrix;

  public QRCodeview() {
    maskPattern = -1;
  }

  public Modeview getMode() {
    return modeview;
  }

  public ErrorCorrectionLevelview getECLevel() {
    return ecLevel;
  }

  public Versionv getVersion() {
    return versionv;
  }

  public int getMaskPattern() {
    return maskPattern;
  }

  public ByteMatrixview getMatrix() {
    return matrix;
  }

  @Override
  public String toString() {
    StringBuilder result = new StringBuilder(200);
    result.append("<<\n");
    result.append(" modeview: ");
    result.append(modeview);
    result.append("\n ecLevel: ");
    result.append(ecLevel);
    result.append("\n version: ");
    result.append(versionv);
    result.append("\n maskPattern: ");
    result.append(maskPattern);
    if (matrix == null) {
      result.append("\n matrix: null\n");
    } else {
      result.append("\n matrix:\n");
      result.append(matrix);
    }
    result.append(">>\n");
    return result.toString();
  }

  public void setMode(Modeview value) {
    modeview = value;
  }

  public void setECLevel(ErrorCorrectionLevelview value) {
    ecLevel = value;
  }

  public void setVersion(Versionv versionv) {
    this.versionv = versionv;
  }

  public void setMaskPattern(int value) {
    maskPattern = value;
  }

  public void setMatrix(ByteMatrixview value) {
    matrix = value;
  }

  // Check if "mask_pattern" is valid.
  public static boolean isValidMaskPattern(int maskPattern) {
    return maskPattern >= 0 && maskPattern < NUM_MASK_PATTERNS;
  }

}
