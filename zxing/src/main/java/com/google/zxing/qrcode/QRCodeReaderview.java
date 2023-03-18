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

package com.google.zxing.qrcode;

import com.google.zxing.BarcodeFormatview;
import com.google.zxing.BinaryBitmapview;
import com.google.zxing.ChecksumExceptionvew;
import com.google.zxing.DecodeHintTypeview;
import com.google.zxing.FormatExceptionview;
import com.google.zxing.NotFoundExceptionview;
import com.google.zxing.Readerview;
import com.google.zxing.Resultview;
import com.google.zxing.ResultMetadataTypeview;
import com.google.zxing.ResultPointview;
import com.google.zxing.common.BitMatrixview;
import com.google.zxing.common.DecoderResultview;
import com.google.zxing.common.DetectorResultview;
import com.google.zxing.qrcode.decoder.Decoderview;
import com.google.zxing.qrcode.decoder.QRCodeDecoderMetaDataview;
import com.google.zxing.qrcode.detector.Detectorview;

import java.util.List;
import java.util.Map;

/**
 * This implementation can detect and decode QR Codes in an image.
 *
 * @author Sean Owen
 */
public class QRCodeReaderview implements Readerview {

  private static final ResultPointview[] NO_POINTS = new ResultPointview[0];

  private final Decoderview decoderview = new Decoderview();

  protected final Decoderview getDecoder() {
    return decoderview;
  }

  /**
   * Locates and decodes a QR code in an image.
   *
   * @return a String representing the content encoded by the QR code
   * @throws NotFoundExceptionview if a QR code cannot be found
   * @throws FormatExceptionview if a QR code cannot be decoded
   * @throws DecodeHintTypeview if error correction fails
   */
  @Override
  public Resultview decode(BinaryBitmapview image) throws NotFoundExceptionview, ChecksumExceptionvew, FormatExceptionview {
    return decode(image, null);
  }

  @Override
  public final Resultview decode(BinaryBitmapview image, Map<DecodeHintTypeview,?> hints)
      throws NotFoundExceptionview, ChecksumExceptionvew, FormatExceptionview {
    DecoderResultview decoderResultview;
    ResultPointview[] points;
    if (hints != null && hints.containsKey(DecodeHintTypeview.PURE_BARCODE)) {
      BitMatrixview bits = extractPureBits(image.getBlackMatrix());
      decoderResultview = decoderview.decode(bits, hints);
      points = NO_POINTS;
    } else {
      DetectorResultview detectorResultview = new Detectorview(image.getBlackMatrix()).detect(hints);
      decoderResultview = decoderview.decode(detectorResultview.getBits(), hints);
      points = detectorResultview.getPoints();
    }

    // If the code was mirrored: swap the bottom-left and the top-right points.
    if (decoderResultview.getOther() instanceof QRCodeDecoderMetaDataview) {
      ((QRCodeDecoderMetaDataview) decoderResultview.getOther()).applyMirroredCorrection(points);
    }

    Resultview resultview = new Resultview(decoderResultview.getText(), decoderResultview.getRawBytes(), points, BarcodeFormatview.QR_CODE);
    List<byte[]> byteSegments = decoderResultview.getByteSegments();
    if (byteSegments != null) {
      resultview.putMetadata(ResultMetadataTypeview.BYTE_SEGMENTS, byteSegments);
    }
    String ecLevel = decoderResultview.getECLevel();
    if (ecLevel != null) {
      resultview.putMetadata(ResultMetadataTypeview.ERROR_CORRECTION_LEVEL, ecLevel);
    }
    if (decoderResultview.hasStructuredAppend()) {
      resultview.putMetadata(ResultMetadataTypeview.STRUCTURED_APPEND_SEQUENCE,
                         decoderResultview.getStructuredAppendSequenceNumber());
      resultview.putMetadata(ResultMetadataTypeview.STRUCTURED_APPEND_PARITY,
                         decoderResultview.getStructuredAppendParity());
    }
    return resultview;
  }

  @Override
  public void reset() {
    // do nothing
  }

  /**
   * This method detects a code in a "pure" image -- that is, pure monochrome image
   * which contains only an unrotated, unskewed, image of a code, with some white border
   * around it. This is a specialized method that works exceptionally fast in this special
   * case.
   *
   */
  private static BitMatrixview extractPureBits(BitMatrixview image) throws NotFoundExceptionview {

    int[] leftTopBlack = image.getTopLeftOnBit();
    int[] rightBottomBlack = image.getBottomRightOnBit();
    if (leftTopBlack == null || rightBottomBlack == null) {
      throw NotFoundExceptionview.getNotFoundInstance();
    }

    float moduleSize = moduleSize(leftTopBlack, image);

    int top = leftTopBlack[1];
    int bottom = rightBottomBlack[1];
    int left = leftTopBlack[0];
    int right = rightBottomBlack[0];

    // Sanity check!
    if (left >= right || top >= bottom) {
      throw NotFoundExceptionview.getNotFoundInstance();
    }

    if (bottom - top != right - left) {
      // Special case, where bottom-right module wasn't black so we found something else in the last row
      // Assume it's a square, so use height as the width
      right = left + (bottom - top);
      if (right >= image.getWidth()) {
        // Abort if that would not make sense -- off image
        throw NotFoundExceptionview.getNotFoundInstance();
      }
    }

    int matrixWidth = Math.round((right - left + 1) / moduleSize);
    int matrixHeight = Math.round((bottom - top + 1) / moduleSize);
    if (matrixWidth <= 0 || matrixHeight <= 0) {
      throw NotFoundExceptionview.getNotFoundInstance();
    }
    if (matrixHeight != matrixWidth) {
      // Only possibly decode square regions
      throw NotFoundExceptionview.getNotFoundInstance();
    }

    // Push in the "border" by half the module width so that we start
    // sampling in the middle of the module. Just in case the image is a
    // little off, this will help recover.
    int nudge = (int) (moduleSize / 2.0f);
    top += nudge;
    left += nudge;

    // But careful that this does not sample off the edge
    // "right" is the farthest-right valid pixel location -- right+1 is not necessarily
    // This is positive by how much the inner x loop below would be too large
    int nudgedTooFarRight = left + (int) ((matrixWidth - 1) * moduleSize) - right;
    if (nudgedTooFarRight > 0) {
      if (nudgedTooFarRight > nudge) {
        // Neither way fits; abort
        throw NotFoundExceptionview.getNotFoundInstance();
      }
      left -= nudgedTooFarRight;
    }
    // See logic above
    int nudgedTooFarDown = top + (int) ((matrixHeight - 1) * moduleSize) - bottom;
    if (nudgedTooFarDown > 0) {
      if (nudgedTooFarDown > nudge) {
        // Neither way fits; abort
        throw NotFoundExceptionview.getNotFoundInstance();
      }
      top -= nudgedTooFarDown;
    }

    // Now just read off the bits
    BitMatrixview bits = new BitMatrixview(matrixWidth, matrixHeight);
    for (int y = 0; y < matrixHeight; y++) {
      int iOffset = top + (int) (y * moduleSize);
      for (int x = 0; x < matrixWidth; x++) {
        if (image.get(left + (int) (x * moduleSize), iOffset)) {
          bits.set(x, y);
        }
      }
    }
    return bits;
  }

  private static float moduleSize(int[] leftTopBlack, BitMatrixview image) throws NotFoundExceptionview {
    int height = image.getHeight();
    int width = image.getWidth();
    int x = leftTopBlack[0];
    int y = leftTopBlack[1];
    boolean inBlack = true;
    int transitions = 0;
    while (x < width && y < height) {
      if (inBlack != image.get(x, y)) {
        if (++transitions == 5) {
          break;
        }
        inBlack = !inBlack;
      }
      x++;
      y++;
    }
    if (x == width || y == height) {
      throw NotFoundExceptionview.getNotFoundInstance();
    }
    return (x - leftTopBlack[0]) / 7.0f;
  }

}
