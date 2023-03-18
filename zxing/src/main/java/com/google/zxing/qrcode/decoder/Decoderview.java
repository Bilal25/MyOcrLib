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

package com.google.zxing.qrcode.decoder;

import com.google.zxing.ChecksumExceptionvew;
import com.google.zxing.DecodeHintTypeview;
import com.google.zxing.FormatExceptionview;
import com.google.zxing.common.BitMatrixview;
import com.google.zxing.common.DecoderResultview;
import com.google.zxing.common.reedsolomon.GenericGFview;
import com.google.zxing.common.reedsolomon.ReedSolomonDecoderview;
import com.google.zxing.common.reedsolomon.ReedSolomonExceptionview;

import java.util.Map;

/**
 * <p>The main class which implements QR Code decoding -- as opposed to locating and extracting
 * the QR Code from an image.</p>
 *
 * @author Sean Owen
 */
public final class Decoderview {

  private final ReedSolomonDecoderview rsDecoder;

  public Decoderview() {
    rsDecoder = new ReedSolomonDecoderview(GenericGFview.QR_CODE_FIELD_256);
  }

  public DecoderResultview decode(boolean[][] image) throws ChecksumExceptionvew, FormatExceptionview {
    return decode(image, null);
  }

  /**
   * <p>Convenience method that can decode a QR Code represented as a 2D array of booleans.
   * "true" is taken to mean a black module.</p>
   *
   * @param image booleans representing white/black QR Code modules
   * @param hints decoding hints that should be used to influence decoding
   * @return text and bytes encoded within the QR Code
   * @throws FormatExceptionview if the QR Code cannot be decoded
   * @throws ChecksumExceptionvew if error correction fails
   */
  public DecoderResultview decode(boolean[][] image, Map<DecodeHintTypeview,?> hints)
      throws ChecksumExceptionvew, FormatExceptionview {
    return decode(BitMatrixview.parse(image), hints);
  }

  public DecoderResultview decode(BitMatrixview bits) throws ChecksumExceptionvew, FormatExceptionview {
    return decode(bits, null);
  }

  /**
   * <p>Decodes a QR Code represented as a {@link BitMatrixview}. A 1 or "true" is taken to mean a black module.</p>
   *
   * @param bits booleans representing white/black QR Code modules
   * @param hints decoding hints that should be used to influence decoding
   * @return text and bytes encoded within the QR Code
   * @throws FormatExceptionview if the QR Code cannot be decoded
   * @throws ChecksumExceptionvew if error correction fails
   */
  public DecoderResultview decode(BitMatrixview bits, Map<DecodeHintTypeview,?> hints)
      throws FormatExceptionview, ChecksumExceptionvew {

    // Construct a parser and read version, error-correction level
    BitMatrixParserview parser = new BitMatrixParserview(bits);
    FormatExceptionview fe = null;
    ChecksumExceptionvew ce = null;
    try {
      return decode(parser, hints);
    } catch (FormatExceptionview e) {
      fe = e;
    } catch (ChecksumExceptionvew e) {
      ce = e;
    }

    try {

      // Revert the bit matrix
      parser.remask();

      // Will be attempting a mirrored reading of the version and format info.
      parser.setMirror(true);

      // Preemptively read the version.
      parser.readVersion();

      // Preemptively read the format information.
      parser.readFormatInformation();

      /*
       * Since we're here, this means we have successfully detected some kind
       * of version and format information when mirrored. This is a good sign,
       * that the QR code may be mirrored, and we should try once more with a
       * mirrored content.
       */
      // Prepare for a mirrored reading.
      parser.mirror();

      DecoderResultview result = decode(parser, hints);

      // Success! Notify the caller that the code was mirrored.
      result.setOther(new QRCodeDecoderMetaDataview(true));

      return result;

    } catch (FormatExceptionview | ChecksumExceptionvew e) {
      // Throw the exception from the original reading
      if (fe != null) {
        throw fe;
      }
      if (ce != null) {
        throw ce;
      }
      throw e;

    }
  }

  private DecoderResultview decode(BitMatrixParserview parser, Map<DecodeHintTypeview,?> hints)
      throws FormatExceptionview, ChecksumExceptionvew {
    Versionv versionv = parser.readVersion();
    ErrorCorrectionLevelview ecLevel = parser.readFormatInformation().getErrorCorrectionLevel();

    // Read codewords
    byte[] codewords = parser.readCodewords();
    // Separate into data blocks
    DataBlockview[] dataBlockviews = DataBlockview.getDataBlocks(codewords, versionv, ecLevel);

    // Count total number of data bytes
    int totalBytes = 0;
    for (DataBlockview dataBlockview : dataBlockviews) {
      totalBytes += dataBlockview.getNumDataCodewords();
    }
    byte[] resultBytes = new byte[totalBytes];
    int resultOffset = 0;

    // Error-correct and copy data blocks together into a stream of bytes
    for (DataBlockview dataBlockview : dataBlockviews) {
      byte[] codewordBytes = dataBlockview.getCodewords();
      int numDataCodewords = dataBlockview.getNumDataCodewords();
      correctErrors(codewordBytes, numDataCodewords);
      for (int i = 0; i < numDataCodewords; i++) {
        resultBytes[resultOffset++] = codewordBytes[i];
      }
    }

    // Decode the contents of that stream of bytes
    return DecodedBitStreamParserview.decode(resultBytes, versionv, ecLevel, hints);
  }

  /**
   * <p>Given data and error-correction codewords received, possibly corrupted by errors, attempts to
   * correct the errors in-place using Reed-Solomon error correction.</p>
   *
   * @param codewordBytes data and error correction codewords
   * @param numDataCodewords number of codewords that are data bytes
   * @throws ChecksumExceptionvew if error correction fails
   */
  private void correctErrors(byte[] codewordBytes, int numDataCodewords) throws ChecksumExceptionvew {
    int numCodewords = codewordBytes.length;
    // First read into an array of ints
    int[] codewordsInts = new int[numCodewords];
    for (int i = 0; i < numCodewords; i++) {
      codewordsInts[i] = codewordBytes[i] & 0xFF;
    }
    try {
      rsDecoder.decode(codewordsInts, codewordBytes.length - numDataCodewords);
    } catch (ReedSolomonExceptionview ignored) {
      throw ChecksumExceptionvew.getChecksumInstance();
    }
    // Copy back into array of bytes -- only need to worry about the bytes that were data
    // We don't care about errors in the error-correction codewords
    for (int i = 0; i < numDataCodewords; i++) {
      codewordBytes[i] = (byte) codewordsInts[i];
    }
  }

}
