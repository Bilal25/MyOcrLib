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

package com.google.zxing.oned.rss;

import com.google.zxing.BarcodeFormatview;
import com.google.zxing.DecodeHintTypeview;
import com.google.zxing.NotFoundExceptionview;
import com.google.zxing.Resultview;
import com.google.zxing.ResultPointview;
import com.google.zxing.ResultPointCallbackview;
import com.google.zxing.common.BitArrayview;
import com.google.zxing.common.detector.MathUtilsview;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Decodes RSS-14, including truncated and stacked variants. See ISO/IEC 24724:2006.
 */
public final class RSS14Readerview extends AbstractRSSReaderview {

  private static final int[] OUTSIDE_EVEN_TOTAL_SUBSET = {1,10,34,70,126};
  private static final int[] INSIDE_ODD_TOTAL_SUBSET = {4,20,48,81};
  private static final int[] OUTSIDE_GSUM = {0,161,961,2015,2715};
  private static final int[] INSIDE_GSUM = {0,336,1036,1516};
  private static final int[] OUTSIDE_ODD_WIDEST = {8,6,4,3,1};
  private static final int[] INSIDE_ODD_WIDEST = {2,4,6,8};

  private static final int[][] FINDER_PATTERNS = {
      {3,8,2,1},
      {3,5,5,1},
      {3,3,7,1},
      {3,1,9,1},
      {2,7,4,1},
      {2,5,6,1},
      {2,3,8,1},
      {1,5,7,1},
      {1,3,9,1},
  };

  private final List<Pairview> possibleLeftPairviews;
  private final List<Pairview> possibleRightPairviews;

  public RSS14Readerview() {
    possibleLeftPairviews = new ArrayList<>();
    possibleRightPairviews = new ArrayList<>();
  }

  @Override
  public Resultview decodeRow(int rowNumber,
                              BitArrayview row,
                              Map<DecodeHintTypeview,?> hints) throws NotFoundExceptionview {
    Pairview leftPairview = decodePair(row, false, rowNumber, hints);
    addOrTally(possibleLeftPairviews, leftPairview);
    row.reverse();
    Pairview rightPairview = decodePair(row, true, rowNumber, hints);
    addOrTally(possibleRightPairviews, rightPairview);
    row.reverse();
    for (Pairview left : possibleLeftPairviews) {
      if (left.getCount() > 1) {
        for (Pairview right : possibleRightPairviews) {
          if (right.getCount() > 1 && checkChecksum(left, right)) {
            return constructResult(left, right);
          }
        }
      }
    }
    throw NotFoundExceptionview.getNotFoundInstance();
  }

  private static void addOrTally(Collection<Pairview> possiblePairviews, Pairview pairview) {
    if (pairview == null) {
      return;
    }
    boolean found = false;
    for (Pairview other : possiblePairviews) {
      if (other.getValue() == pairview.getValue()) {
        other.incrementCount();
        found = true;
        break;
      }
    }
    if (!found) {
      possiblePairviews.add(pairview);
    }
  }

  @Override
  public void reset() {
    possibleLeftPairviews.clear();
    possibleRightPairviews.clear();
  }

  private static Resultview constructResult(Pairview leftPairview, Pairview rightPairview) {
    long symbolValue = 4537077L * leftPairview.getValue() + rightPairview.getValue();
    String text = String.valueOf(symbolValue);

    StringBuilder buffer = new StringBuilder(14);
    for (int i = 13 - text.length(); i > 0; i--) {
      buffer.append('0');
    }
    buffer.append(text);

    int checkDigit = 0;
    for (int i = 0; i < 13; i++) {
      int digit = buffer.charAt(i) - '0';
      checkDigit += (i & 0x01) == 0 ? 3 * digit : digit;
    }
    checkDigit = 10 - (checkDigit % 10);
    if (checkDigit == 10) {
      checkDigit = 0;
    }
    buffer.append(checkDigit);

    ResultPointview[] leftPoints = leftPairview.getFinderPattern().getResultPoints();
    ResultPointview[] rightPoints = rightPairview.getFinderPattern().getResultPoints();
    return new Resultview(
        buffer.toString(),
        null,
        new ResultPointview[] { leftPoints[0], leftPoints[1], rightPoints[0], rightPoints[1], },
        BarcodeFormatview.RSS_14);
  }

  private static boolean checkChecksum(Pairview leftPairview, Pairview rightPairview) {
    //int leftFPValue = leftPairview.getFinderPattern().getValue();
    //int rightFPValue = rightPairview.getFinderPattern().getValue();
    //if ((leftFPValue == 0 && rightFPValue == 8) ||
    //    (leftFPValue == 8 && rightFPValue == 0)) {
    //}
    int checkValue = (leftPairview.getChecksumPortion() + 16 * rightPairview.getChecksumPortion()) % 79;
    int targetCheckValue =
        9 * leftPairview.getFinderPattern().getValue() + rightPairview.getFinderPattern().getValue();
    if (targetCheckValue > 72) {
      targetCheckValue--;
    }
    if (targetCheckValue > 8) {
      targetCheckValue--;
    }
    return checkValue == targetCheckValue;
  }

  private Pairview decodePair(BitArrayview row, boolean right, int rowNumber, Map<DecodeHintTypeview,?> hints) {
    try {
      int[] startEnd = findFinderPattern(row, right);
      FinderPatternview pattern = parseFoundFinderPattern(row, rowNumber, right, startEnd);

      ResultPointCallbackview resultPointCallbackview = hints == null ? null :
        (ResultPointCallbackview) hints.get(DecodeHintTypeview.NEED_RESULT_POINT_CALLBACK);

      if (resultPointCallbackview != null) {
        float center = (startEnd[0] + startEnd[1]) / 2.0f;
        if (right) {
          // row is actually reversed
          center = row.getSize() - 1 - center;
        }
        resultPointCallbackview.foundPossibleResultPoint(new ResultPointview(center, rowNumber));
      }

      DataCharacterview outside = decodeDataCharacter(row, pattern, true);
      DataCharacterview inside = decodeDataCharacter(row, pattern, false);
      return new Pairview(1597 * outside.getValue() + inside.getValue(),
                      outside.getChecksumPortion() + 4 * inside.getChecksumPortion(),
                      pattern);
    } catch (NotFoundExceptionview ignored) {
      return null;
    }
  }

  private DataCharacterview decodeDataCharacter(BitArrayview row, FinderPatternview pattern, boolean outsideChar)
      throws NotFoundExceptionview {

    int[] counters = getDataCharacterCounters();
    counters[0] = 0;
    counters[1] = 0;
    counters[2] = 0;
    counters[3] = 0;
    counters[4] = 0;
    counters[5] = 0;
    counters[6] = 0;
    counters[7] = 0;

    if (outsideChar) {
      recordPatternInReverse(row, pattern.getStartEnd()[0], counters);
    } else {
      recordPattern(row, pattern.getStartEnd()[1] + 1, counters);
      // reverse it
      for (int i = 0, j = counters.length - 1; i < j; i++, j--) {
        int temp = counters[i];
        counters[i] = counters[j];
        counters[j] = temp;
      }
    }

    int numModules = outsideChar ? 16 : 15;
    float elementWidth = MathUtilsview.sum(counters) / (float) numModules;

    int[] oddCounts = this.getOddCounts();
    int[] evenCounts = this.getEvenCounts();
    float[] oddRoundingErrors = this.getOddRoundingErrors();
    float[] evenRoundingErrors = this.getEvenRoundingErrors();

    for (int i = 0; i < counters.length; i++) {
      float value = counters[i] / elementWidth;
      int count = (int) (value + 0.5f); // Round
      if (count < 1) {
        count = 1;
      } else if (count > 8) {
        count = 8;
      }
      int offset = i / 2;
      if ((i & 0x01) == 0) {
        oddCounts[offset] = count;
        oddRoundingErrors[offset] = value - count;
      } else {
        evenCounts[offset] = count;
        evenRoundingErrors[offset] = value - count;
      }
    }

    adjustOddEvenCounts(outsideChar, numModules);

    int oddSum = 0;
    int oddChecksumPortion = 0;
    for (int i = oddCounts.length - 1; i >= 0; i--) {
      oddChecksumPortion *= 9;
      oddChecksumPortion += oddCounts[i];
      oddSum += oddCounts[i];
    }
    int evenChecksumPortion = 0;
    int evenSum = 0;
    for (int i = evenCounts.length - 1; i >= 0; i--) {
      evenChecksumPortion *= 9;
      evenChecksumPortion += evenCounts[i];
      evenSum += evenCounts[i];
    }
    int checksumPortion = oddChecksumPortion + 3 * evenChecksumPortion;

    if (outsideChar) {
      if ((oddSum & 0x01) != 0 || oddSum > 12 || oddSum < 4) {
        throw NotFoundExceptionview.getNotFoundInstance();
      }
      int group = (12 - oddSum) / 2;
      int oddWidest = OUTSIDE_ODD_WIDEST[group];
      int evenWidest = 9 - oddWidest;
      int vOdd = RSSUtilsview.getRSSvalue(oddCounts, oddWidest, false);
      int vEven = RSSUtilsview.getRSSvalue(evenCounts, evenWidest, true);
      int tEven = OUTSIDE_EVEN_TOTAL_SUBSET[group];
      int gSum = OUTSIDE_GSUM[group];
      return new DataCharacterview(vOdd * tEven + vEven + gSum, checksumPortion);
    } else {
      if ((evenSum & 0x01) != 0 || evenSum > 10 || evenSum < 4) {
        throw NotFoundExceptionview.getNotFoundInstance();
      }
      int group = (10 - evenSum) / 2;
      int oddWidest = INSIDE_ODD_WIDEST[group];
      int evenWidest = 9 - oddWidest;
      int vOdd = RSSUtilsview.getRSSvalue(oddCounts, oddWidest, true);
      int vEven = RSSUtilsview.getRSSvalue(evenCounts, evenWidest, false);
      int tOdd = INSIDE_ODD_TOTAL_SUBSET[group];
      int gSum = INSIDE_GSUM[group];
      return new DataCharacterview(vEven * tOdd + vOdd + gSum, checksumPortion);
    }

  }

  private int[] findFinderPattern(BitArrayview row, boolean rightFinderPattern)
      throws NotFoundExceptionview {

    int[] counters = getDecodeFinderCounters();
    counters[0] = 0;
    counters[1] = 0;
    counters[2] = 0;
    counters[3] = 0;

    int width = row.getSize();
    boolean isWhite = false;
    int rowOffset = 0;
    while (rowOffset < width) {
      isWhite = !row.get(rowOffset);
      if (rightFinderPattern == isWhite) {
        // Will encounter white first when searching for right finder pattern
        break;
      }
      rowOffset++;
    }

    int counterPosition = 0;
    int patternStart = rowOffset;
    for (int x = rowOffset; x < width; x++) {
      if (row.get(x) != isWhite) {
        counters[counterPosition]++;
      } else {
        if (counterPosition == 3) {
          if (isFinderPattern(counters)) {
            return new int[]{patternStart, x};
          }
          patternStart += counters[0] + counters[1];
          counters[0] = counters[2];
          counters[1] = counters[3];
          counters[2] = 0;
          counters[3] = 0;
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

  private FinderPatternview parseFoundFinderPattern(BitArrayview row, int rowNumber, boolean right, int[] startEnd)
      throws NotFoundExceptionview {
    // Actually we found elements 2-5
    boolean firstIsBlack = row.get(startEnd[0]);
    int firstElementStart = startEnd[0] - 1;
    // Locate element 1
    while (firstElementStart >= 0 && firstIsBlack != row.get(firstElementStart)) {
      firstElementStart--;
    }
    firstElementStart++;
    int firstCounter = startEnd[0] - firstElementStart;
    // Make 'counters' hold 1-4
    int[] counters = getDecodeFinderCounters();
    System.arraycopy(counters, 0, counters, 1, counters.length - 1);
    counters[0] = firstCounter;
    int value = parseFinderValue(counters, FINDER_PATTERNS);
    int start = firstElementStart;
    int end = startEnd[1];
    if (right) {
      // row is actually reversed
      start = row.getSize() - 1 - start;
      end = row.getSize() - 1 - end;
    }
    return new FinderPatternview(value, new int[] {firstElementStart, startEnd[1]}, start, end, rowNumber);
  }

  private void adjustOddEvenCounts(boolean outsideChar, int numModules) throws NotFoundExceptionview {

    int oddSum = MathUtilsview.sum(getOddCounts());
    int evenSum = MathUtilsview.sum(getEvenCounts());

    boolean incrementOdd = false;
    boolean decrementOdd = false;
    boolean incrementEven = false;
    boolean decrementEven = false;

    if (outsideChar) {
      if (oddSum > 12) {
        decrementOdd = true;
      } else if (oddSum < 4) {
        incrementOdd = true;
      }
      if (evenSum > 12) {
        decrementEven = true;
      } else if (evenSum < 4) {
        incrementEven = true;
      }
    } else {
      if (oddSum > 11) {
        decrementOdd = true;
      } else if (oddSum < 5) {
        incrementOdd = true;
      }
      if (evenSum > 10) {
        decrementEven = true;
      } else if (evenSum < 4) {
        incrementEven = true;
      }
    }

    int mismatch = oddSum + evenSum - numModules;
    boolean oddParityBad = (oddSum & 0x01) == (outsideChar ? 1 : 0);
    boolean evenParityBad = (evenSum & 0x01) == 1;
    /*if (mismatch == 2) {
      if (!(oddParityBad && evenParityBad)) {
        throw ReaderException.getInstance();
      }
      decrementOdd = true;
      decrementEven = true;
    } else if (mismatch == -2) {
      if (!(oddParityBad && evenParityBad)) {
        throw ReaderException.getInstance();
      }
      incrementOdd = true;
      incrementEven = true;
    } else */ if (mismatch == 1) {
      if (oddParityBad) {
        if (evenParityBad) {
          throw NotFoundExceptionview.getNotFoundInstance();
        }
        decrementOdd = true;
      } else {
        if (!evenParityBad) {
          throw NotFoundExceptionview.getNotFoundInstance();
        }
        decrementEven = true;
      }
    } else if (mismatch == -1) {
      if (oddParityBad) {
        if (evenParityBad) {
          throw NotFoundExceptionview.getNotFoundInstance();
        }
        incrementOdd = true;
      } else {
        if (!evenParityBad) {
          throw NotFoundExceptionview.getNotFoundInstance();
        }
        incrementEven = true;
      }
    } else if (mismatch == 0) {
      if (oddParityBad) {
        if (!evenParityBad) {
          throw NotFoundExceptionview.getNotFoundInstance();
        }
        // Both bad
        if (oddSum < evenSum) {
          incrementOdd = true;
          decrementEven = true;
        } else {
          decrementOdd = true;
          incrementEven = true;
        }
      } else {
        if (evenParityBad) {
          throw NotFoundExceptionview.getNotFoundInstance();
        }
        // Nothing to do!
      }
    } else {
      throw NotFoundExceptionview.getNotFoundInstance();
    }

    if (incrementOdd) {
      if (decrementOdd) {
        throw NotFoundExceptionview.getNotFoundInstance();
      }
      increment(getOddCounts(), getOddRoundingErrors());
    }
    if (decrementOdd) {
      decrement(getOddCounts(), getOddRoundingErrors());
    }
    if (incrementEven) {
      if (decrementEven) {
        throw NotFoundExceptionview.getNotFoundInstance();
      }
      increment(getEvenCounts(), getOddRoundingErrors());
    }
    if (decrementEven) {
      decrement(getEvenCounts(), getEvenRoundingErrors());
    }

  }

}
