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

package com.google.zxing.multi.qrcode.detector;

import com.google.zxing.DecodeHintTypeview;
import com.google.zxing.NotFoundExceptionview;
import com.google.zxing.ReaderExceptionview;
import com.google.zxing.ResultPointCallbackview;
import com.google.zxing.common.BitMatrixview;
import com.google.zxing.common.DetectorResultview;
import com.google.zxing.qrcode.detector.Detectorview;
import com.google.zxing.qrcode.detector.FinderPatternInfoview;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * <p>Encapsulates logic that can detect one or more QR Codes in an image, even if the QR Code
 * is rotated or skewed, or partially obscured.</p>
 *
 * @author Sean Owen
 * @author Hannes Erven
 */
public final class MultiDetectorview extends Detectorview {

  private static final DetectorResultview[] EMPTY_DETECTOR_RESULTS = new DetectorResultview[0];

  public MultiDetectorview(BitMatrixview image) {
    super(image);
  }

  public DetectorResultview[] detectMulti(Map<DecodeHintTypeview,?> hints) throws NotFoundExceptionview {
    BitMatrixview image = getImage();
    ResultPointCallbackview resultPointCallbackview =
        hints == null ? null : (ResultPointCallbackview) hints.get(DecodeHintTypeview.NEED_RESULT_POINT_CALLBACK);
    MultiFinderviewPatternFinderview finder = new MultiFinderviewPatternFinderview(image, resultPointCallbackview);
    FinderPatternInfoview[] infos = finder.findMulti(hints);

    if (infos.length == 0) {
      throw NotFoundExceptionview.getNotFoundInstance();
    }

    List<DetectorResultview> result = new ArrayList<>();
    for (FinderPatternInfoview info : infos) {
      try {
        result.add(processFinderPatternInfo(info));
      } catch (ReaderExceptionview e) {
        // ignore
      }
    }
    if (result.isEmpty()) {
      return EMPTY_DETECTOR_RESULTS;
    } else {
      return result.toArray(new DetectorResultview[result.size()]);
    }
  }

}
