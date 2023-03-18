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

package com.google.zxing.qrcode.encoder;

import com.google.zxing.EncodeHintTypeview;
import com.google.zxing.WriterExceptionview;
import com.google.zxing.common.BitArrayview;
import com.google.zxing.common.CharacterSetECIview;
import com.google.zxing.common.reedsolomon.GenericGFview;
import com.google.zxing.common.reedsolomon.ReedSolomonEncoderview;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevelview;
import com.google.zxing.qrcode.decoder.Modeview;
import com.google.zxing.qrcode.decoder.Versionv;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

/**
 * @author satorux@google.com (Satoru Takabayashi) - creator
 * @author dswitkin@google.com (Daniel Switkin) - ported from C++
 */
public final class Encoderview {

  // The original table is defined in the table 5 of JISX0510:2004 (p.19).
  private static final int[] ALPHANUMERIC_TABLE = {
      -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,  // 0x00-0x0f
      -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,  // 0x10-0x1f
      36, -1, -1, -1, 37, 38, -1, -1, -1, -1, 39, 40, -1, 41, 42, 43,  // 0x20-0x2f
      0,   1,  2,  3,  4,  5,  6,  7,  8,  9, 44, -1, -1, -1, -1, -1,  // 0x30-0x3f
      -1, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24,  // 0x40-0x4f
      25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, -1, -1, -1, -1, -1,  // 0x50-0x5f
  };

  static final String DEFAULT_BYTE_MODE_ENCODING = "ISO-8859-1";

  private Encoderview() {
  }

  // The mask penalty calculation is complicated.  See Table 21 of JISX0510:2004 (p.45) for details.
  // Basically it applies four rules and summate all penalties.
  private static int calculateMaskPenalty(ByteMatrixview matrix) {
    return MaskUtilview.applyMaskPenaltyRule1(matrix)
        + MaskUtilview.applyMaskPenaltyRule2(matrix)
        + MaskUtilview.applyMaskPenaltyRule3(matrix)
        + MaskUtilview.applyMaskPenaltyRule4(matrix);
  }

  /**
   * @param content text to encode
   * @param ecLevel error correction level to use
   * @return {@link QRCodeview} representing the encoded QR code
   * @throws WriterExceptionview if encoding can't succeed, because of for example invalid content
   *   or configuration
   */
  public static QRCodeview encode(String content, ErrorCorrectionLevelview ecLevel) throws WriterExceptionview {
    return encode(content, ecLevel, null);
  }

  public static QRCodeview encode(String content,
                                  ErrorCorrectionLevelview ecLevel,
                                  Map<EncodeHintTypeview,?> hints) throws WriterExceptionview {

    // Determine what character encoding has been specified by the caller, if any
    String encoding = DEFAULT_BYTE_MODE_ENCODING;
    boolean hasEncodingHint = hints != null && hints.containsKey(EncodeHintTypeview.CHARACTER_SET);
    if (hasEncodingHint) {
      encoding = hints.get(EncodeHintTypeview.CHARACTER_SET).toString();
    }

    // Pick an encoding modeview appropriate for the content. Note that this will not attempt to use
    // multiple modes / segments even if that were more efficient. Twould be nice.
    Modeview modeview = chooseMode(content, encoding);

    // This will store the header information, like modeview and
    // length, as well as "header" segments like an ECI segment.
    BitArrayview headerBits = new BitArrayview();

    // Append ECI segment if applicable
    if (modeview == Modeview.BYTE && (hasEncodingHint || !DEFAULT_BYTE_MODE_ENCODING.equals(encoding))) {
      CharacterSetECIview eci = CharacterSetECIview.getCharacterSetECIByName(encoding);
      if (eci != null) {
        appendECI(eci, headerBits);
      }
    }

    // (With ECI in place,) Write the modeview marker
    appendModeInfo(modeview, headerBits);

    // Collect data within the main segment, separately, to count its size if needed. Don't add it to
    // main payload yet.
    BitArrayview dataBits = new BitArrayview();
    appendBytes(content, modeview, dataBits, encoding);

    Versionv versionv;
    if (hints != null && hints.containsKey(EncodeHintTypeview.QR_VERSION)) {
      int versionNumber = Integer.parseInt(hints.get(EncodeHintTypeview.QR_VERSION).toString());
      versionv = Versionv.getVersionForNumber(versionNumber);
      int bitsNeeded = calculateBitsNeeded(modeview, headerBits, dataBits, versionv);
      if (!willFit(bitsNeeded, versionv, ecLevel)) {
        throw new WriterExceptionview("Data too big for requested versionv");
      }
    } else {
      versionv = recommendVersion(ecLevel, modeview, headerBits, dataBits);
    }

    BitArrayview headerAndDataBits = new BitArrayview();
    headerAndDataBits.appendBitArray(headerBits);
    // Find "length" of main segment and write it
    int numLetters = modeview == Modeview.BYTE ? dataBits.getSizeInBytes() : content.length();
    appendLengthInfo(numLetters, versionv, modeview, headerAndDataBits);
    // Put data together into the overall payload
    headerAndDataBits.appendBitArray(dataBits);

    Versionv.ECBlocks ecBlocks = versionv.getECBlocksForLevel(ecLevel);
    int numDataBytes = versionv.getTotalCodewords() - ecBlocks.getTotalECCodewords();

    // Terminate the bits properly.
    terminateBits(numDataBytes, headerAndDataBits);

    // Interleave data bits with error correction code.
    BitArrayview finalBits = interleaveWithECBytes(headerAndDataBits,
                                               versionv.getTotalCodewords(),
                                               numDataBytes,
                                               ecBlocks.getNumBlocks());

    QRCodeview qrCodeview = new QRCodeview();

    qrCodeview.setECLevel(ecLevel);
    qrCodeview.setMode(modeview);
    qrCodeview.setVersion(versionv);

    //  Choose the mask pattern and set to "qrCodeview".
    int dimension = versionv.getDimensionForVersion();
    ByteMatrixview matrix = new ByteMatrixview(dimension, dimension);
    int maskPattern = chooseMaskPattern(finalBits, ecLevel, versionv, matrix);
    qrCodeview.setMaskPattern(maskPattern);

    // Build the matrix and set it to "qrCodeview".
    MatrixUtilview.buildMatrix(finalBits, ecLevel, versionv, maskPattern, matrix);
    qrCodeview.setMatrix(matrix);

    return qrCodeview;
  }

  /**
   * Decides the smallest version of QR code that will contain all of the provided data.
   *
   * @throws WriterExceptionview if the data cannot fit in any version
   */
  private static Versionv recommendVersion(ErrorCorrectionLevelview ecLevel,
                                           Modeview modeview,
                                           BitArrayview headerBits,
                                           BitArrayview dataBits) throws WriterExceptionview {
    // Hard part: need to know version to know how many bits length takes. But need to know how many
    // bits it takes to know version. First we take a guess at version by assuming version will be
    // the minimum, 1:
    int provisionalBitsNeeded = calculateBitsNeeded(modeview, headerBits, dataBits, Versionv.getVersionForNumber(1));
    Versionv provisionalversion = chooseVersion(provisionalBitsNeeded, ecLevel);

    // Use that guess to calculate the right version. I am still not sure this works in 100% of cases.
    int bitsNeeded = calculateBitsNeeded(modeview, headerBits, dataBits, provisionalversion);
    return chooseVersion(bitsNeeded, ecLevel);
  }

  private static int calculateBitsNeeded(Modeview modeview,
                                         BitArrayview headerBits,
                                         BitArrayview dataBits,
                                         Versionv versionv) {
    return headerBits.getSize() + modeview.getCharacterCountBits(versionv) + dataBits.getSize();
  }

  /**
   * @return the code point of the table used in alphanumeric mode or
   *  -1 if there is no corresponding code in the table.
   */
  static int getAlphanumericCode(int code) {
    if (code < ALPHANUMERIC_TABLE.length) {
      return ALPHANUMERIC_TABLE[code];
    }
    return -1;
  }

  public static Modeview chooseMode(String content) {
    return chooseMode(content, null);
  }

  /**
   * Choose the best mode by examining the content. Note that 'encoding' is used as a hint;
   * if it is Shift_JIS, and the input is only double-byte Kanji, then we return {@link Modeview#KANJI}.
   */
  private static Modeview chooseMode(String content, String encoding) {
    if ("Shift_JIS".equals(encoding) && isOnlyDoubleByteKanji(content)) {
      // Choose Kanji mode if all input are double-byte characters
      return Modeview.KANJI;
    }
    boolean hasNumeric = false;
    boolean hasAlphanumeric = false;
    for (int i = 0; i < content.length(); ++i) {
      char c = content.charAt(i);
      if (c >= '0' && c <= '9') {
        hasNumeric = true;
      } else if (getAlphanumericCode(c) != -1) {
        hasAlphanumeric = true;
      } else {
        return Modeview.BYTE;
      }
    }
    if (hasAlphanumeric) {
      return Modeview.ALPHANUMERIC;
    }
    if (hasNumeric) {
      return Modeview.NUMERIC;
    }
    return Modeview.BYTE;
  }

  private static boolean isOnlyDoubleByteKanji(String content) {
    byte[] bytes;
    try {
      bytes = content.getBytes("Shift_JIS");
    } catch (UnsupportedEncodingException ignored) {
      return false;
    }
    int length = bytes.length;
    if (length % 2 != 0) {
      return false;
    }
    for (int i = 0; i < length; i += 2) {
      int byte1 = bytes[i] & 0xFF;
      if ((byte1 < 0x81 || byte1 > 0x9F) && (byte1 < 0xE0 || byte1 > 0xEB)) {
        return false;
      }
    }
    return true;
  }

  private static int chooseMaskPattern(BitArrayview bits,
                                       ErrorCorrectionLevelview ecLevel,
                                       Versionv versionv,
                                       ByteMatrixview matrix) throws WriterExceptionview {

    int minPenalty = Integer.MAX_VALUE;  // Lower penalty is better.
    int bestMaskPattern = -1;
    // We try all mask patterns to choose the best one.
    for (int maskPattern = 0; maskPattern < QRCodeview.NUM_MASK_PATTERNS; maskPattern++) {
      MatrixUtilview.buildMatrix(bits, ecLevel, versionv, maskPattern, matrix);
      int penalty = calculateMaskPenalty(matrix);
      if (penalty < minPenalty) {
        minPenalty = penalty;
        bestMaskPattern = maskPattern;
      }
    }
    return bestMaskPattern;
  }

  private static Versionv chooseVersion(int numInputBits, ErrorCorrectionLevelview ecLevel) throws WriterExceptionview {
    for (int versionNum = 1; versionNum <= 40; versionNum++) {
      Versionv versionv = Versionv.getVersionForNumber(versionNum);
      if (willFit(numInputBits, versionv, ecLevel)) {
        return versionv;
      }
    }
    throw new WriterExceptionview("Data too big");
  }
  
  /**
   * @return true if the number of input bits will fit in a code with the specified versionv and
   * error correction level.
   */
  private static boolean willFit(int numInputBits, Versionv versionv, ErrorCorrectionLevelview ecLevel) {
      // In the following comments, we use numbers of versionv 7-H.
      // numBytes = 196
      int numBytes = versionv.getTotalCodewords();
      // getNumECBytes = 130
      Versionv.ECBlocks ecBlocks = versionv.getECBlocksForLevel(ecLevel);
      int numEcBytes = ecBlocks.getTotalECCodewords();
      // getNumDataBytes = 196 - 130 = 66
      int numDataBytes = numBytes - numEcBytes;
      int totalInputBytes = (numInputBits + 7) / 8;
      return numDataBytes >= totalInputBytes;
  }

  /**
   * Terminate bits as described in 8.4.8 and 8.4.9 of JISX0510:2004 (p.24).
   */
  static void terminateBits(int numDataBytes, BitArrayview bits) throws WriterExceptionview {
    int capacity = numDataBytes * 8;
    if (bits.getSize() > capacity) {
      throw new WriterExceptionview("data bits cannot fit in the QR Code" + bits.getSize() + " > " +
          capacity);
    }
    for (int i = 0; i < 4 && bits.getSize() < capacity; ++i) {
      bits.appendBit(false);
    }
    // Append termination bits. See 8.4.8 of JISX0510:2004 (p.24) for details.
    // If the last byte isn't 8-bit aligned, we'll add padding bits.
    int numBitsInLastByte = bits.getSize() & 0x07;    
    if (numBitsInLastByte > 0) {
      for (int i = numBitsInLastByte; i < 8; i++) {
        bits.appendBit(false);
      }
    }
    // If we have more space, we'll fill the space with padding patterns defined in 8.4.9 (p.24).
    int numPaddingBytes = numDataBytes - bits.getSizeInBytes();
    for (int i = 0; i < numPaddingBytes; ++i) {
      bits.appendBits((i & 0x01) == 0 ? 0xEC : 0x11, 8);
    }
    if (bits.getSize() != capacity) {
      throw new WriterExceptionview("Bits size does not equal capacity");
    }
  }

  /**
   * Get number of data bytes and number of error correction bytes for block id "blockID". Store
   * the result in "numDataBytesInBlock", and "numECBytesInBlock". See table 12 in 8.5.1 of
   * JISX0510:2004 (p.30)
   */
  static void getNumDataBytesAndNumECBytesForBlockID(int numTotalBytes,
                                                     int numDataBytes,
                                                     int numRSBlocks,
                                                     int blockID,
                                                     int[] numDataBytesInBlock,
                                                     int[] numECBytesInBlock) throws WriterExceptionview {
    if (blockID >= numRSBlocks) {
      throw new WriterExceptionview("Block ID too large");
    }
    // numRsBlocksInGroup2 = 196 % 5 = 1
    int numRsBlocksInGroup2 = numTotalBytes % numRSBlocks;
    // numRsBlocksInGroup1 = 5 - 1 = 4
    int numRsBlocksInGroup1 = numRSBlocks - numRsBlocksInGroup2;
    // numTotalBytesInGroup1 = 196 / 5 = 39
    int numTotalBytesInGroup1 = numTotalBytes / numRSBlocks;
    // numTotalBytesInGroup2 = 39 + 1 = 40
    int numTotalBytesInGroup2 = numTotalBytesInGroup1 + 1;
    // numDataBytesInGroup1 = 66 / 5 = 13
    int numDataBytesInGroup1 = numDataBytes / numRSBlocks;
    // numDataBytesInGroup2 = 13 + 1 = 14
    int numDataBytesInGroup2 = numDataBytesInGroup1 + 1;
    // numEcBytesInGroup1 = 39 - 13 = 26
    int numEcBytesInGroup1 = numTotalBytesInGroup1 - numDataBytesInGroup1;
    // numEcBytesInGroup2 = 40 - 14 = 26
    int numEcBytesInGroup2 = numTotalBytesInGroup2 - numDataBytesInGroup2;
    // Sanity checks.
    // 26 = 26
    if (numEcBytesInGroup1 != numEcBytesInGroup2) {
      throw new WriterExceptionview("EC bytes mismatch");
    }
    // 5 = 4 + 1.
    if (numRSBlocks != numRsBlocksInGroup1 + numRsBlocksInGroup2) {
      throw new WriterExceptionview("RS blocks mismatch");
    }
    // 196 = (13 + 26) * 4 + (14 + 26) * 1
    if (numTotalBytes !=
        ((numDataBytesInGroup1 + numEcBytesInGroup1) *
            numRsBlocksInGroup1) +
            ((numDataBytesInGroup2 + numEcBytesInGroup2) *
                numRsBlocksInGroup2)) {
      throw new WriterExceptionview("Total bytes mismatch");
    }

    if (blockID < numRsBlocksInGroup1) {
      numDataBytesInBlock[0] = numDataBytesInGroup1;
      numECBytesInBlock[0] = numEcBytesInGroup1;
    } else {
      numDataBytesInBlock[0] = numDataBytesInGroup2;
      numECBytesInBlock[0] = numEcBytesInGroup2;
    }
  }

  /**
   * Interleave "bits" with corresponding error correction bytes. On success, store the result in
   * "result". The interleave rule is complicated. See 8.6 of JISX0510:2004 (p.37) for details.
   */
  static BitArrayview interleaveWithECBytes(BitArrayview bits,
                                            int numTotalBytes,
                                            int numDataBytes,
                                            int numRSBlocks) throws WriterExceptionview {

    // "bits" must have "getNumDataBytes" bytes of data.
    if (bits.getSizeInBytes() != numDataBytes) {
      throw new WriterExceptionview("Number of bits and data bytes does not match");
    }

    // Step 1.  Divide data bytes into blocks and generate error correction bytes for them. We'll
    // store the divided data bytes blocks and error correction bytes blocks into "blocks".
    int dataBytesOffset = 0;
    int maxNumDataBytes = 0;
    int maxNumEcBytes = 0;

    // Since, we know the number of reedsolmon blocks, we can initialize the vector with the number.
    Collection<BlockPairview> blocks = new ArrayList<>(numRSBlocks);

    for (int i = 0; i < numRSBlocks; ++i) {
      int[] numDataBytesInBlock = new int[1];
      int[] numEcBytesInBlock = new int[1];
      getNumDataBytesAndNumECBytesForBlockID(
          numTotalBytes, numDataBytes, numRSBlocks, i,
          numDataBytesInBlock, numEcBytesInBlock);

      int size = numDataBytesInBlock[0];
      byte[] dataBytes = new byte[size];
      bits.toBytes(8 * dataBytesOffset, dataBytes, 0, size);
      byte[] ecBytes = generateECBytes(dataBytes, numEcBytesInBlock[0]);
      blocks.add(new BlockPairview(dataBytes, ecBytes));

      maxNumDataBytes = Math.max(maxNumDataBytes, size);
      maxNumEcBytes = Math.max(maxNumEcBytes, ecBytes.length);
      dataBytesOffset += numDataBytesInBlock[0];
    }
    if (numDataBytes != dataBytesOffset) {
      throw new WriterExceptionview("Data bytes does not match offset");
    }

    BitArrayview result = new BitArrayview();

    // First, place data blocks.
    for (int i = 0; i < maxNumDataBytes; ++i) {
      for (BlockPairview block : blocks) {
        byte[] dataBytes = block.getDataBytes();
        if (i < dataBytes.length) {
          result.appendBits(dataBytes[i], 8);
        }
      }
    }
    // Then, place error correction blocks.
    for (int i = 0; i < maxNumEcBytes; ++i) {
      for (BlockPairview block : blocks) {
        byte[] ecBytes = block.getErrorCorrectionBytes();
        if (i < ecBytes.length) {
          result.appendBits(ecBytes[i], 8);
        }
      }
    }
    if (numTotalBytes != result.getSizeInBytes()) {  // Should be same.
      throw new WriterExceptionview("Interleaving error: " + numTotalBytes + " and " +
          result.getSizeInBytes() + " differ.");
    }

    return result;
  }

  static byte[] generateECBytes(byte[] dataBytes, int numEcBytesInBlock) {
    int numDataBytes = dataBytes.length;
    int[] toEncode = new int[numDataBytes + numEcBytesInBlock];
    for (int i = 0; i < numDataBytes; i++) {
      toEncode[i] = dataBytes[i] & 0xFF;
    }
    new ReedSolomonEncoderview(GenericGFview.QR_CODE_FIELD_256).encode(toEncode, numEcBytesInBlock);

    byte[] ecBytes = new byte[numEcBytesInBlock];
    for (int i = 0; i < numEcBytesInBlock; i++) {
      ecBytes[i] = (byte) toEncode[numDataBytes + i];
    }
    return ecBytes;
  }

  /**
   * Append modeview info. On success, store the result in "bits".
   */
  static void appendModeInfo(Modeview modeview, BitArrayview bits) {
    bits.appendBits(modeview.getBits(), 4);
  }


  /**
   * Append length info. On success, store the result in "bits".
   */
  static void appendLengthInfo(int numLetters, Versionv versionv, Modeview modeview, BitArrayview bits) throws WriterExceptionview {
    int numBits = modeview.getCharacterCountBits(versionv);
    if (numLetters >= (1 << numBits)) {
      throw new WriterExceptionview(numLetters + " is bigger than " + ((1 << numBits) - 1));
    }
    bits.appendBits(numLetters, numBits);
  }

  /**
   * Append "bytes" in "modeview" modeview (encoding) into "bits". On success, store the result in "bits".
   */
  static void appendBytes(String content,
                          Modeview modeview,
                          BitArrayview bits,
                          String encoding) throws WriterExceptionview {
    switch (modeview) {
      case NUMERIC:
        appendNumericBytes(content, bits);
        break;
      case ALPHANUMERIC:
        appendAlphanumericBytes(content, bits);
        break;
      case BYTE:
        append8BitBytes(content, bits, encoding);
        break;
      case KANJI:
        appendKanjiBytes(content, bits);
        break;
      default:
        throw new WriterExceptionview("Invalid modeview: " + modeview);
    }
  }

  static void appendNumericBytes(CharSequence content, BitArrayview bits) {
    int length = content.length();
    int i = 0;
    while (i < length) {
      int num1 = content.charAt(i) - '0';
      if (i + 2 < length) {
        // Encode three numeric letters in ten bits.
        int num2 = content.charAt(i + 1) - '0';
        int num3 = content.charAt(i + 2) - '0';
        bits.appendBits(num1 * 100 + num2 * 10 + num3, 10);
        i += 3;
      } else if (i + 1 < length) {
        // Encode two numeric letters in seven bits.
        int num2 = content.charAt(i + 1) - '0';
        bits.appendBits(num1 * 10 + num2, 7);
        i += 2;
      } else {
        // Encode one numeric letter in four bits.
        bits.appendBits(num1, 4);
        i++;
      }
    }
  }

  static void appendAlphanumericBytes(CharSequence content, BitArrayview bits) throws WriterExceptionview {
    int length = content.length();
    int i = 0;
    while (i < length) {
      int code1 = getAlphanumericCode(content.charAt(i));
      if (code1 == -1) {
        throw new WriterExceptionview();
      }
      if (i + 1 < length) {
        int code2 = getAlphanumericCode(content.charAt(i + 1));
        if (code2 == -1) {
          throw new WriterExceptionview();
        }
        // Encode two alphanumeric letters in 11 bits.
        bits.appendBits(code1 * 45 + code2, 11);
        i += 2;
      } else {
        // Encode one alphanumeric letter in six bits.
        bits.appendBits(code1, 6);
        i++;
      }
    }
  }

  static void append8BitBytes(String content, BitArrayview bits, String encoding)
      throws WriterExceptionview {
    byte[] bytes;
    try {
      bytes = content.getBytes(encoding);
    } catch (UnsupportedEncodingException uee) {
      throw new WriterExceptionview(uee);
    }
    for (byte b : bytes) {
      bits.appendBits(b, 8);
    }
  }

  static void appendKanjiBytes(String content, BitArrayview bits) throws WriterExceptionview {
    byte[] bytes;
    try {
      bytes = content.getBytes("Shift_JIS");
    } catch (UnsupportedEncodingException uee) {
      throw new WriterExceptionview(uee);
    }
    int length = bytes.length;
    for (int i = 0; i < length; i += 2) {
      int byte1 = bytes[i] & 0xFF;
      int byte2 = bytes[i + 1] & 0xFF;
      int code = (byte1 << 8) | byte2;
      int subtracted = -1;
      if (code >= 0x8140 && code <= 0x9ffc) {
        subtracted = code - 0x8140;
      } else if (code >= 0xe040 && code <= 0xebbf) {
        subtracted = code - 0xc140;
      }
      if (subtracted == -1) {
        throw new WriterExceptionview("Invalid byte sequence");
      }
      int encoded = ((subtracted >> 8) * 0xc0) + (subtracted & 0xff);
      bits.appendBits(encoded, 13);
    }
  }

  private static void appendECI(CharacterSetECIview eci, BitArrayview bits) {
    bits.appendBits(Modeview.ECI.getBits(), 4);
    // This is correct for values up to 127, which is all we need now.
    bits.appendBits(eci.getValue(), 8);
  }

}
