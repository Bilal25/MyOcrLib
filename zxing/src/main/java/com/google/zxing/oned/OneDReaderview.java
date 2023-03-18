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

import com.google.zxing.BinaryBitmapview;
import com.google.zxing.ChecksumExceptionvew;
import com.google.zxing.DecodeHintTypeview;
import com.google.zxing.FormatExceptionview;
import com.google.zxing.NotFoundExceptionview;
import com.google.zxing.Readerview;
import com.google.zxing.ReaderExceptionview;
import com.google.zxing.Resultview;
import com.google.zxing.ResultMetadataTypeview;
import com.google.zxing.ResultPointview;
import com.google.zxing.common.BitArrayview;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

/**
 * Encapsulates functionality and implementation that is common to all families
 * of one-dimensional barcodes.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 * @author Sean Owen
 */
public abstract class OneDReaderview implements Readerview {

  @Override
  public Resultview decode(BinaryBitmapview image) throws NotFoundExceptionview, FormatExceptionview {
    return decode(image, null);
  }

  // Note that we don't try rotation without the try harder flag, even if rotation was supported.
  @Override
  public Resultview decode(BinaryBitmapview image,
                           Map<DecodeHintTypeview,?> hints) throws NotFoundExceptionview, FormatExceptionview {
    try {
      return doDecode(image, hints);
    } catch (NotFoundExceptionview nfe) {
      boolean tryHarder = hints != null && hints.containsKey(DecodeHintTypeview.TRY_HARDER);
      if (tryHarder && image.isRotateSupported()) {
        BinaryBitmapview rotatedImage = image.rotateCounterClockwise();
        Resultview resultview = doDecode(rotatedImage, hints);
        // Record that we found it rotated 90 degrees CCW / 270 degrees CW
        Map<ResultMetadataTypeview,?> metadata = resultview.getResultMetadata();
        int orientation = 270;
        if (metadata != null && metadata.containsKey(ResultMetadataTypeview.ORIENTATION)) {
          // But if we found it reversed in doDecode(), add in that result here:
          orientation = (orientation +
              (Integer) metadata.get(ResultMetadataTypeview.ORIENTATION)) % 360;
        }
        resultview.putMetadata(ResultMetadataTypeview.ORIENTATION, orientation);
        // Update result points
        ResultPointview[] points = resultview.getResultPoints();
        if (points != null) {
          int height = rotatedImage.getHeight();
          for (int i = 0; i < points.length; i++) {
            points[i] = new ResultPointview(height - points[i].getY() - 1, points[i].getX());
          }
        }
        return resultview;
      } else {
        throw nfe;
      }
    }
  }

  @Override
  public void reset() {
    // do nothing
  }

  /**
   * We're going to examine rows from the middle outward, searching alternately above and below the
   * middle, and farther out each time. rowStep is the number of rows between each successive
   * attempt above and below the middle. So we'd scan row middle, then middle - rowStep, then
   * middle + rowStep, then middle - (2 * rowStep), etc.
   * rowStep is bigger as the image is taller, but is always at least 1. We've somewhat arbitrarily
   * decided that moving up and down by about 1/16 of the image is pretty good; we try more of the
   * image if "trying harder".
   *
   * @param image The image to decode
   * @param hints Any hints that were requested
   * @return The contents of the decoded barcode
   * @throws NotFoundExceptionview Any spontaneous errors which occur
   */
  private Resultview doDecode(BinaryBitmapview image,
                              Map<DecodeHintTypeview,?> hints) throws NotFoundExceptionview {
    int width = image.getWidth();
    int height = image.getHeight();
    BitArrayview row = new BitArrayview(width);

    boolean tryHarder = hints != null && hints.containsKey(DecodeHintTypeview.TRY_HARDER);
    int rowStep = Math.max(1, height >> (tryHarder ? 8 : 5));
    int maxLines;
    if (tryHarder) {
      maxLines = height; // Look at the whole image, not just the center
    } else {
      maxLines = 15; // 15 rows spaced 1/32 apart is roughly the middle half of the image
    }

    int middle = height / 2;
    for (int x = 0; x < maxLines; x++) {

      // Scanning from the middle out. Determine which row we're looking at next:
      int rowStepsAboveOrBelow = (x + 1) / 2;
      boolean isAbove = (x & 0x01) == 0; // i.e. is x even?
      int rowNumber = middle + rowStep * (isAbove ? rowStepsAboveOrBelow : -rowStepsAboveOrBelow);
      if (rowNumber < 0 || rowNumber >= height) {
        // Oops, if we run off the top or bottom, stop
        break;
      }

      // Estimate black point for this row and load it:
      try {
        row = image.getBlackRow(rowNumber, row);
      } catch (NotFoundExceptionview ignored) {
        continue;
      }

      // While we have the image data in a BitArrayview, it's fairly cheap to reverse it in place to
      // handle decoding upside down barcodes.
      for (int attempt = 0; attempt < 2; attempt++) {
        if (attempt == 1) { // trying again?
          row.reverse(); // reverse the row and continue
          // This means we will only ever draw result points *once* in the life of this method
          // since we want to avoid drawing the wrong points after flipping the row, and,
          // don't want to clutter with noise from every single row scan -- just the scans
          // that start on the center line.
          if (hints != null && hints.containsKey(DecodeHintTypeview.NEED_RESULT_POINT_CALLBACK)) {
            Map<DecodeHintTypeview,Object> newHints = new EnumMap<>(DecodeHintTypeview.class);
            newHints.putAll(hints);
            newHints.remove(DecodeHintTypeview.NEED_RESULT_POINT_CALLBACK);
            hints = newHints;
          }
        }
        try {
          // Look for a barcode
          Resultview resultview = decodeRow(rowNumber, row, hints);
          // We found our barcode
          if (attempt == 1) {
            // But it was upside down, so note that
            resultview.putMetadata(ResultMetadataTypeview.ORIENTATION, 180);
            // And remember to flip the result points horizontally.
            ResultPointview[] points = resultview.getResultPoints();
            if (points != null) {
              points[0] = new ResultPointview(width - points[0].getX() - 1, points[0].getY());
              points[1] = new ResultPointview(width - points[1].getX() - 1, points[1].getY());
            }
          }
          return resultview;
        } catch (ReaderExceptionview re) {
          // continue -- just couldn't decode this row
        }
      }
    }

    throw NotFoundExceptionview.getNotFoundInstance();
  }

  /**
   * Records the size of successive runs of white and black pixels in a row, starting at a given point.
   * The values are recorded in the given array, and the number of runs recorded is equal to the size
   * of the array. If the row starts on a white pixel at the given start point, then the first count
   * recorded is the run of white pixels starting from that point; likewise it is the count of a run
   * of black pixels if the row begin on a black pixels at that point.
   *
   * @param row row to count from
   * @param start offset into row to start at
   * @param counters array into which to record counts
   * @throws NotFoundExceptionview if counters cannot be filled entirely from row before running out
   *  of pixels
   */
  protected static void recordPattern(BitArrayview row,
                                      int start,
                                      int[] counters) throws NotFoundExceptionview {
    int numCounters = counters.length;
    Arrays.fill(counters, 0, numCounters, 0);
    int end = row.getSize();
    if (start >= end) {
      throw NotFoundExceptionview.getNotFoundInstance();
    }
    boolean isWhite = !row.get(start);
    int counterPosition = 0;
    int i = start;
    while (i < end) {
      if (row.get(i) != isWhite) {
        counters[counterPosition]++;
      } else {
        if (++counterPosition == numCounters) {
          break;
        } else {
          counters[counterPosition] = 1;
          isWhite = !isWhite;
        }
      }
      i++;
    }
    // If we read fully the last section of pixels and filled up our counters -- or filled
    // the last counter but ran off the side of the image, OK. Otherwise, a problem.
    if (!(counterPosition == numCounters || (counterPosition == numCounters - 1 && i == end))) {
      throw NotFoundExceptionview.getNotFoundInstance();
    }
  }

  protected static void recordPatternInReverse(BitArrayview row, int start, int[] counters)
      throws NotFoundExceptionview {
    // This could be more efficient I guess
    int numTransitionsLeft = counters.length;
    boolean last = row.get(start);
    while (start > 0 && numTransitionsLeft >= 0) {
      if (row.get(--start) != last) {
        numTransitionsLeft--;
        last = !last;
      }
    }
    if (numTransitionsLeft >= 0) {
      throw NotFoundExceptionview.getNotFoundInstance();
    }
    recordPattern(row, start + 1, counters);
  }

  /**
   * Determines how closely a set of observed counts of runs of black/white values matches a given
   * target pattern. This is reported as the ratio of the total variance from the expected pattern
   * proportions across all pattern elements, to the length of the pattern.
   *
   * @param counters observed counters
   * @param pattern expected pattern
   * @param maxIndividualVariance The most any counter can differ before we give up
   * @return ratio of total variance between counters and pattern compared to total pattern size
   */
  protected static float patternMatchVariance(int[] counters,
                                              int[] pattern,
                                              float maxIndividualVariance) {
    int numCounters = counters.length;
    int total = 0;
    int patternLength = 0;
    for (int i = 0; i < numCounters; i++) {
      total += counters[i];
      patternLength += pattern[i];
    }
    if (total < patternLength) {
      // If we don't even have one pixel per unit of bar width, assume this is too small
      // to reliably match, so fail:
      return Float.POSITIVE_INFINITY;
    }

    float unitBarWidth = (float) total / patternLength;
    maxIndividualVariance *= unitBarWidth;

    float totalVariance = 0.0f;
    for (int x = 0; x < numCounters; x++) {
      int counter = counters[x];
      float scaledPattern = pattern[x] * unitBarWidth;
      float variance = counter > scaledPattern ? counter - scaledPattern : scaledPattern - counter;
      if (variance > maxIndividualVariance) {
        return Float.POSITIVE_INFINITY;
      }
      totalVariance += variance;
    }
    return totalVariance / total;
  }

  /**
   * <p>Attempts to decode a one-dimensional barcode format given a single row of
   * an image.</p>
   *
   * @param rowNumber row number from top of the row
   * @param row the black/white pixel data of the row
   * @param hints decode hints
   * @return {@link Resultview} containing encoded string and start/end of barcode
   * @throws NotFoundExceptionview if no potential barcode is found
   * @throws ChecksumExceptionvew if a potential barcode is found but does not pass its checksum
   * @throws FormatExceptionview if a potential barcode is found but format is invalid
   */
  public abstract Resultview decodeRow(int rowNumber, BitArrayview row, Map<DecodeHintTypeview,?> hints)
      throws NotFoundExceptionview, ChecksumExceptionvew, FormatExceptionview;

}
