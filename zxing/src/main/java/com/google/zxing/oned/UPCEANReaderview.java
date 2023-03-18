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
import com.google.zxing.ChecksumExceptionvew;
import com.google.zxing.DecodeHintTypeview;
import com.google.zxing.FormatExceptionview;
import com.google.zxing.NotFoundExceptionview;
import com.google.zxing.ReaderExceptionview;
import com.google.zxing.Resultview;
import com.google.zxing.ResultMetadataTypeview;
import com.google.zxing.ResultPointview;
import com.google.zxing.ResultPointCallbackview;
import com.google.zxing.common.BitArrayview;

import java.util.Arrays;
import java.util.Map;

/**
 * <p>Encapsulates functionality and implementation that is common to UPC and EAN families
 * of one-dimensional barcodes.</p>
 *
 * @author dswitkin@google.com (Daniel Switkin)
 * @author Sean Owen
 * @author alasdair@google.com (Alasdair Mackintosh)
 */
public abstract class UPCEANReaderview extends OneDReaderview {

  // These two values are critical for determining how permissive the decoding will be.
  // We've arrived at these values through a lot of trial and error. Setting them any higher
  // lets false positives creep in quickly.
  private static final float MAX_AVG_VARIANCE = 0.48f;
  private static final float MAX_INDIVIDUAL_VARIANCE = 0.7f;

  /**
   * Start/end guard pattern.
   */
  static final int[] START_END_PATTERN = {1, 1, 1,};

  /**
   * Pattern marking the middle of a UPC/EAN pattern, separating the two halves.
   */
  static final int[] MIDDLE_PATTERN = {1, 1, 1, 1, 1};
  /**
   * end guard pattern.
   */
  static final int[] END_PATTERN = {1, 1, 1, 1, 1, 1};
  /**
   * "Odd", or "L" patterns used to encode UPC/EAN digits.
   */
  static final int[][] L_PATTERNS = {
      {3, 2, 1, 1}, // 0
      {2, 2, 2, 1}, // 1
      {2, 1, 2, 2}, // 2
      {1, 4, 1, 1}, // 3
      {1, 1, 3, 2}, // 4
      {1, 2, 3, 1}, // 5
      {1, 1, 1, 4}, // 6
      {1, 3, 1, 2}, // 7
      {1, 2, 1, 3}, // 8
      {3, 1, 1, 2}  // 9
  };

  /**
   * As above but also including the "even", or "G" patterns used to encode UPC/EAN digits.
   */
  static final int[][] L_AND_G_PATTERNS;

  static {
    L_AND_G_PATTERNS = new int[20][];
    System.arraycopy(L_PATTERNS, 0, L_AND_G_PATTERNS, 0, 10);
    for (int i = 10; i < 20; i++) {
      int[] widths = L_PATTERNS[i - 10];
      int[] reversedWidths = new int[widths.length];
      for (int j = 0; j < widths.length; j++) {
        reversedWidths[j] = widths[widths.length - j - 1];
      }
      L_AND_G_PATTERNS[i] = reversedWidths;
    }
  }

  private final StringBuilder decodeRowStringBuffer;
  private final UPCEANExtensionSupportview extensionReader;
  private final EANManufacturerOrgSupportview eanManSupport;

  protected UPCEANReaderview() {
    decodeRowStringBuffer = new StringBuilder(20);
    extensionReader = new UPCEANExtensionSupportview();
    eanManSupport = new EANManufacturerOrgSupportview();
  }

  static int[] findStartGuardPattern(BitArrayview row) throws NotFoundExceptionview {
    boolean foundStart = false;
    int[] startRange = null;
    int nextStart = 0;
    int[] counters = new int[START_END_PATTERN.length];
    while (!foundStart) {
      Arrays.fill(counters, 0, START_END_PATTERN.length, 0);
      startRange = findGuardPattern(row, nextStart, false, START_END_PATTERN, counters);
      int start = startRange[0];
      nextStart = startRange[1];
      // Make sure there is a quiet zone at least as big as the start pattern before the barcode.
      // If this check would run off the left edge of the image, do not accept this barcode,
      // as it is very likely to be a false positive.
      int quietStart = start - (nextStart - start);
      if (quietStart >= 0) {
        foundStart = row.isRange(quietStart, start, false);
      }
    }
    return startRange;
  }

  @Override
  public Resultview decodeRow(int rowNumber, BitArrayview row, Map<DecodeHintTypeview,?> hints)
      throws NotFoundExceptionview, ChecksumExceptionvew, FormatExceptionview {
    return decodeRow(rowNumber, row, findStartGuardPattern(row), hints);
  }

  /**
   * <p>Like {@link #decodeRow(int, BitArrayview, Map)}, but
   * allows caller to inform method about where the UPC/EAN start pattern is
   * found. This allows this to be computed once and reused across many implementations.</p>
   *
   * @param rowNumber row index into the image
   * @param row encoding of the row of the barcode image
   * @param startGuardRange start/end column where the opening start pattern was found
   * @param hints optional hints that influence decoding
   * @return {@link Resultview} encapsulating the result of decoding a barcode in the row
   * @throws NotFoundExceptionview if no potential barcode is found
   * @throws ChecksumExceptionvew if a potential barcode is found but does not pass its checksum
   * @throws FormatExceptionview if a potential barcode is found but format is invalid
   */
  public Resultview decodeRow(int rowNumber,
                              BitArrayview row,
                              int[] startGuardRange,
                              Map<DecodeHintTypeview,?> hints)
      throws NotFoundExceptionview, ChecksumExceptionvew, FormatExceptionview {

    ResultPointCallbackview resultPointCallbackview = hints == null ? null :
        (ResultPointCallbackview) hints.get(DecodeHintTypeview.NEED_RESULT_POINT_CALLBACK);

    if (resultPointCallbackview != null) {
      resultPointCallbackview.foundPossibleResultPoint(new ResultPointview(
          (startGuardRange[0] + startGuardRange[1]) / 2.0f, rowNumber
      ));
    }

    StringBuilder result = decodeRowStringBuffer;
    result.setLength(0);
    int endStart = decodeMiddle(row, startGuardRange, result);

    if (resultPointCallbackview != null) {
      resultPointCallbackview.foundPossibleResultPoint(new ResultPointview(
          endStart, rowNumber
      ));
    }

    int[] endRange = decodeEnd(row, endStart);

    if (resultPointCallbackview != null) {
      resultPointCallbackview.foundPossibleResultPoint(new ResultPointview(
          (endRange[0] + endRange[1]) / 2.0f, rowNumber
      ));
    }


    // Make sure there is a quiet zone at least as big as the end pattern after the barcode. The
    // spec might want more whitespace, but in practice this is the maximum we can count on.
    int end = endRange[1];
    int quietEnd = end + (end - endRange[0]);
    if (quietEnd >= row.getSize() || !row.isRange(end, quietEnd, false)) {
      throw NotFoundExceptionview.getNotFoundInstance();
    }

    String resultString = result.toString();
    // UPC/EAN should never be less than 8 chars anyway
    if (resultString.length() < 8) {
      throw FormatExceptionview.getFormatInstance();
    }
    if (!checkChecksum(resultString)) {
      throw ChecksumExceptionvew.getChecksumInstance();
    }

    float left = (startGuardRange[1] + startGuardRange[0]) / 2.0f;
    float right = (endRange[1] + endRange[0]) / 2.0f;
    BarcodeFormatview format = getBarcodeFormat();
    Resultview decodeResultview = new Resultview(resultString,
        null, // no natural byte representation for these barcodes
        new ResultPointview[]{
            new ResultPointview(left, rowNumber),
            new ResultPointview(right, rowNumber)},
        format);

    int extensionLength = 0;

    try {
      Resultview extensionResultview = extensionReader.decodeRow(rowNumber, row, endRange[1]);
      decodeResultview.putMetadata(ResultMetadataTypeview.UPC_EAN_EXTENSION, extensionResultview.getText());
      decodeResultview.putAllMetadata(extensionResultview.getResultMetadata());
      decodeResultview.addResultPoints(extensionResultview.getResultPoints());
      extensionLength = extensionResultview.getText().length();
    } catch (ReaderExceptionview re) {
      // continue
    }

    int[] allowedExtensions =
        hints == null ? null : (int[]) hints.get(DecodeHintTypeview.ALLOWED_EAN_EXTENSIONS);
    if (allowedExtensions != null) {
      boolean valid = false;
      for (int length : allowedExtensions) {
        if (extensionLength == length) {
          valid = true;
          break;
        }
      }
      if (!valid) {
        throw NotFoundExceptionview.getNotFoundInstance();
      }
    }

    if (format == BarcodeFormatview.EAN_13 || format == BarcodeFormatview.UPC_A) {
      String countryID = eanManSupport.lookupCountryIdentifier(resultString);
      if (countryID != null) {
        decodeResultview.putMetadata(ResultMetadataTypeview.POSSIBLE_COUNTRY, countryID);
      }
    }

    return decodeResultview;
  }

  /**
   * @param s string of digits to check
   * @return {@link #checkStandardUPCEANChecksum(CharSequence)}
   * @throws FormatExceptionview if the string does not contain only digits
   */
  boolean checkChecksum(String s) throws FormatExceptionview {
    return checkStandardUPCEANChecksum(s);
  }

  /**
   * Computes the UPC/EAN checksum on a string of digits, and reports
   * whether the checksum is correct or not.
   *
   * @param s string of digits to check
   * @return true iff string of digits passes the UPC/EAN checksum algorithm
   * @throws FormatExceptionview if the string does not contain only digits
   */
  static boolean checkStandardUPCEANChecksum(CharSequence s) throws FormatExceptionview {
    int length = s.length();
    if (length == 0) {
      return false;
    }
    int check = Character.digit(s.charAt(length - 1), 10);
    return getStandardUPCEANChecksum(s.subSequence(0, length - 1)) == check;
  }

  static int getStandardUPCEANChecksum(CharSequence s) throws FormatExceptionview {
    int length = s.length();
    int sum = 0;
    for (int i = length - 1; i >= 0; i -= 2) {
      int digit = s.charAt(i) - '0';
      if (digit < 0 || digit > 9) {
        throw FormatExceptionview.getFormatInstance();
      }
      sum += digit;
    }
    sum *= 3;
    for (int i = length - 2; i >= 0; i -= 2) {
      int digit = s.charAt(i) - '0';
      if (digit < 0 || digit > 9) {
        throw FormatExceptionview.getFormatInstance();
      }
      sum += digit;
    }
    return (1000 - sum) % 10;
  }

  int[] decodeEnd(BitArrayview row, int endStart) throws NotFoundExceptionview {
    return findGuardPattern(row, endStart, false, START_END_PATTERN);
  }

  static int[] findGuardPattern(BitArrayview row,
                                int rowOffset,
                                boolean whiteFirst,
                                int[] pattern) throws NotFoundExceptionview {
    return findGuardPattern(row, rowOffset, whiteFirst, pattern, new int[pattern.length]);
  }

  /**
   * @param row row of black/white values to search
   * @param rowOffset position to start search
   * @param whiteFirst if true, indicates that the pattern specifies white/black/white/...
   * pixel counts, otherwise, it is interpreted as black/white/black/...
   * @param pattern pattern of counts of number of black and white pixels that are being
   * searched for as a pattern
   * @param counters array of counters, as long as pattern, to re-use
   * @return start/end horizontal offset of guard pattern, as an array of two ints
   * @throws NotFoundExceptionview if pattern is not found
   */
  private static int[] findGuardPattern(BitArrayview row,
                                        int rowOffset,
                                        boolean whiteFirst,
                                        int[] pattern,
                                        int[] counters) throws NotFoundExceptionview {
    int width = row.getSize();
    rowOffset = whiteFirst ? row.getNextUnset(rowOffset) : row.getNextSet(rowOffset);
    int counterPosition = 0;
    int patternStart = rowOffset;
    int patternLength = pattern.length;
    boolean isWhite = whiteFirst;
    for (int x = rowOffset; x < width; x++) {
      if (row.get(x) != isWhite) {
        counters[counterPosition]++;
      } else {
        if (counterPosition == patternLength - 1) {
          if (patternMatchVariance(counters, pattern, MAX_INDIVIDUAL_VARIANCE) < MAX_AVG_VARIANCE) {
            return new int[]{patternStart, x};
          }
          patternStart += counters[0] + counters[1];
          System.arraycopy(counters, 2, counters, 0, counterPosition - 1);
          counters[counterPosition - 1] = 0;
          counters[counterPosition] = 0;
          counterPosition--;
        } else {
          counterPosition++;
        }
        counters[counterPosition] = 1;
        isWhite = !isWhite;
      }
    }
    throw NotFoundExceptionview.getNotFoundInstance();
  }

  /**
   * Attempts to decode a single UPC/EAN-encoded digit.
   *
   * @param row row of black/white values to decode
   * @param counters the counts of runs of observed black/white/black/... values
   * @param rowOffset horizontal offset to start decoding from
   * @param patterns the set of patterns to use to decode -- sometimes different encodings
   * for the digits 0-9 are used, and this indicates the encodings for 0 to 9 that should
   * be used
   * @return horizontal offset of first pixel beyond the decoded digit
   * @throws NotFoundExceptionview if digit cannot be decoded
   */
  static int decodeDigit(BitArrayview row, int[] counters, int rowOffset, int[][] patterns)
      throws NotFoundExceptionview {
    recordPattern(row, rowOffset, counters);
    float bestVariance = MAX_AVG_VARIANCE; // worst variance we'll accept
    int bestMatch = -1;
    int max = patterns.length;
    for (int i = 0; i < max; i++) {
      int[] pattern = patterns[i];
      float variance = patternMatchVariance(counters, pattern, MAX_INDIVIDUAL_VARIANCE);
      if (variance < bestVariance) {
        bestVariance = variance;
        bestMatch = i;
      }
    }
    if (bestMatch >= 0) {
      return bestMatch;
    } else {
      throw NotFoundExceptionview.getNotFoundInstance();
    }
  }

  /**
   * Get the format of this decoder.
   *
   * @return The 1D format.
   */
  abstract BarcodeFormatview getBarcodeFormat();

  /**
   * Subclasses override this to decode the portion of a barcode between the start
   * and end guard patterns.
   *
   * @param row row of black/white values to search
   * @param startRange start/end offset of start guard pattern
   * @param resultString {@link StringBuilder} to append decoded chars to
   * @return horizontal offset of first pixel after the "middle" that was decoded
   * @throws NotFoundExceptionview if decoding could not complete successfully
   */
  protected abstract int decodeMiddle(BitArrayview row,
                                      int[] startRange,
                                      StringBuilder resultString) throws NotFoundExceptionview;

}
