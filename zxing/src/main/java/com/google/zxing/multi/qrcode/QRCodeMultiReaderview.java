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

package com.google.zxing.multi.qrcode;

import com.google.zxing.BarcodeFormatview;
import com.google.zxing.BinaryBitmapview;
import com.google.zxing.DecodeHintTypeview;
import com.google.zxing.NotFoundExceptionview;
import com.google.zxing.ReaderExceptionview;
import com.google.zxing.Resultview;
import com.google.zxing.ResultMetadataTypeview;
import com.google.zxing.ResultPointview;
import com.google.zxing.common.DecoderResultview;
import com.google.zxing.common.DetectorResultview;
import com.google.zxing.multi.MultipleBarcodeReaderview;
import com.google.zxing.multi.qrcode.detector.MultiDetectorview;
import com.google.zxing.qrcode.QRCodeReaderview;
import com.google.zxing.qrcode.decoder.QRCodeDecoderMetaDataview;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.Comparator;

/**
 * This implementation can detect and decode multiple QR Codes in an image.
 *
 * @author Sean Owen
 * @author Hannes Erven
 */
public final class QRCodeMultiReaderview extends QRCodeReaderview implements MultipleBarcodeReaderview {

  private static final Resultview[] EMPTY_RESULTVIEW_ARRAY = new Resultview[0];
  private static final ResultPointview[] NO_POINTS = new ResultPointview[0];

  @Override
  public Resultview[] decodeMultiple(BinaryBitmapview image) throws NotFoundExceptionview {
    return decodeMultiple(image, null);
  }

  @Override
  public Resultview[] decodeMultiple(BinaryBitmapview image, Map<DecodeHintTypeview,?> hints) throws NotFoundExceptionview {
    List<Resultview> resultviews = new ArrayList<>();
    DetectorResultview[] detectorResultviews = new MultiDetectorview(image.getBlackMatrix()).detectMulti(hints);
    for (DetectorResultview detectorResultview : detectorResultviews) {
      try {
        DecoderResultview decoderResultview = getDecoder().decode(detectorResultview.getBits(), hints);
        ResultPointview[] points = detectorResultview.getPoints();
        // If the code was mirrored: swap the bottom-left and the top-right points.
        if (decoderResultview.getOther() instanceof QRCodeDecoderMetaDataview) {
          ((QRCodeDecoderMetaDataview) decoderResultview.getOther()).applyMirroredCorrection(points);
        }
        Resultview resultview = new Resultview(decoderResultview.getText(), decoderResultview.getRawBytes(), points,
                                   BarcodeFormatview.QR_CODE);
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
        resultviews.add(resultview);
      } catch (ReaderExceptionview re) {
        // ignore and continue 
      }
    }
    if (resultviews.isEmpty()) {
      return EMPTY_RESULTVIEW_ARRAY;
    } else {
      resultviews = processStructuredAppend(resultviews);
      return resultviews.toArray(new Resultview[resultviews.size()]);
    }
  }

  private static List<Resultview> processStructuredAppend(List<Resultview> resultviews) {
    boolean hasSA = false;

    // first, check, if there is at least on SA result in the list
    for (Resultview resultview : resultviews) {
      if (resultview.getResultMetadata().containsKey(ResultMetadataTypeview.STRUCTURED_APPEND_SEQUENCE)) {
        hasSA = true;
        break;
      }
    }
    if (!hasSA) {
      return resultviews;
    }

    // it is, second, split the lists and built a new result list
    List<Resultview> newResultviews = new ArrayList<>();
    List<Resultview> saResultviews = new ArrayList<>();
    for (Resultview resultview : resultviews) {
      newResultviews.add(resultview);
      if (resultview.getResultMetadata().containsKey(ResultMetadataTypeview.STRUCTURED_APPEND_SEQUENCE)) {
        saResultviews.add(resultview);
      }
    }
    // sort and concatenate the SA list items
    Collections.sort(saResultviews, new SAComparator());
    StringBuilder concatedText = new StringBuilder();
    int rawBytesLen = 0;
    int byteSegmentLength = 0;
    for (Resultview saResultview : saResultviews) {
      concatedText.append(saResultview.getText());
      rawBytesLen += saResultview.getRawBytes().length;
      if (saResultview.getResultMetadata().containsKey(ResultMetadataTypeview.BYTE_SEGMENTS)) {
        @SuppressWarnings("unchecked")
        Iterable<byte[]> byteSegments =
            (Iterable<byte[]>) saResultview.getResultMetadata().get(ResultMetadataTypeview.BYTE_SEGMENTS);
        for (byte[] segment : byteSegments) {
          byteSegmentLength += segment.length;
        }
      }
    }
    byte[] newRawBytes = new byte[rawBytesLen];
    byte[] newByteSegment = new byte[byteSegmentLength];
    int newRawBytesIndex = 0;
    int byteSegmentIndex = 0;
    for (Resultview saResultview : saResultviews) {
      System.arraycopy(saResultview.getRawBytes(), 0, newRawBytes, newRawBytesIndex, saResultview.getRawBytes().length);
      newRawBytesIndex += saResultview.getRawBytes().length;
      if (saResultview.getResultMetadata().containsKey(ResultMetadataTypeview.BYTE_SEGMENTS)) {
        @SuppressWarnings("unchecked")
        Iterable<byte[]> byteSegments =
            (Iterable<byte[]>) saResultview.getResultMetadata().get(ResultMetadataTypeview.BYTE_SEGMENTS);
        for (byte[] segment : byteSegments) {
          System.arraycopy(segment, 0, newByteSegment, byteSegmentIndex, segment.length);
          byteSegmentIndex += segment.length;
        }
      }
    }
    Resultview newResultview = new Resultview(concatedText.toString(), newRawBytes, NO_POINTS, BarcodeFormatview.QR_CODE);
    if (byteSegmentLength > 0) {
      Collection<byte[]> byteSegmentList = new ArrayList<>();
      byteSegmentList.add(newByteSegment);
      newResultview.putMetadata(ResultMetadataTypeview.BYTE_SEGMENTS, byteSegmentList);
    }
    newResultviews.add(newResultview);
    return newResultviews;
  }

  private static final class SAComparator implements Comparator<Resultview>, Serializable {
    @Override
    public int compare(Resultview a, Resultview b) {
      int aNumber = (int) a.getResultMetadata().get(ResultMetadataTypeview.STRUCTURED_APPEND_SEQUENCE);
      int bNumber = (int) b.getResultMetadata().get(ResultMetadataTypeview.STRUCTURED_APPEND_SEQUENCE);
      if (aNumber < bNumber) {
        return -1;
      }
      if (aNumber > bNumber) {
        return 1;
      }
      return 0;
    }
  }

}
