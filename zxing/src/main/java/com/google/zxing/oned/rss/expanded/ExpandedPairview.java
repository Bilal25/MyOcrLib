/*
 * Copyright (C) 2010 ZXing authors
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

/*
 * These authors would like to acknowledge the Spanish Ministry of Industry,
 * Tourism and Trade, for the support in the project TSI020301-2008-2
 * "PIRAmIDE: Personalizable Interactions with Resources on AmI-enabled
 * Mobile Dynamic Environments", led by Treelogic
 * ( http://www.treelogic.com/ ):
 *
 *   http://www.piramidepse.com/
 */

package com.google.zxing.oned.rss.expanded;

import com.google.zxing.oned.rss.DataCharacterview;
import com.google.zxing.oned.rss.FinderPatternview;

/**
 * @author Pablo Orduña, University of Deusto (pablo.orduna@deusto.es)
 */
final class ExpandedPairview {

  private final boolean mayBeLast;
  private final DataCharacterview leftChar;
  private final DataCharacterview rightChar;
  private final FinderPatternview finderPatternview;

  ExpandedPairview(DataCharacterview leftChar,
                   DataCharacterview rightChar,
                   FinderPatternview finderPatternview,
                   boolean mayBeLast) {
    this.leftChar = leftChar;
    this.rightChar = rightChar;
    this.finderPatternview = finderPatternview;
    this.mayBeLast = mayBeLast;
  }

  boolean mayBeLast() {
    return this.mayBeLast;
  }

  DataCharacterview getLeftChar() {
    return this.leftChar;
  }

  DataCharacterview getRightChar() {
    return this.rightChar;
  }

  FinderPatternview getFinderPattern() {
    return this.finderPatternview;
  }

  public boolean mustBeLast() {
    return this.rightChar == null;
  }

  @Override
  public String toString() {
    return
        "[ " + leftChar + " , " + rightChar + " : " +
        (finderPatternview == null ? "null" : finderPatternview.getValue()) + " ]";
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof ExpandedPairview)) {
      return false;
    }
    ExpandedPairview that = (ExpandedPairview) o;
    return
        equalsOrNull(leftChar, that.leftChar) &&
        equalsOrNull(rightChar, that.rightChar) &&
        equalsOrNull(finderPatternview, that.finderPatternview);
  }

  private static boolean equalsOrNull(Object o1, Object o2) {
    return o1 == null ? o2 == null : o1.equals(o2);
  }

  @Override
  public int hashCode() {
    return hashNotNull(leftChar) ^ hashNotNull(rightChar) ^ hashNotNull(finderPatternview);
  }

  private static int hashNotNull(Object o) {
    return o == null ? 0 : o.hashCode();
  }

}
