/*
 * Copyright 2009 ZXing authors
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

package com.google.zxing.multi;

import com.google.zxing.BinaryBitmapview;
import com.google.zxing.DecodeHintTypeview;
import com.google.zxing.NotFoundExceptionview;
import com.google.zxing.NotFoundExceptionview;
import com.google.zxing.Resultview;
import com.google.zxing.Resultview;

import java.util.Map;

/**
 * Implementation of this interface attempt to read several barcodes from one image.
 *
 * @see com.google.zxing.Readerview
 * @author Sean Owen
 */
public interface MultipleBarcodeReaderview {

  Resultview[] decodeMultiple(BinaryBitmapview image) throws NotFoundExceptionview;

  Resultview[] decodeMultiple(BinaryBitmapview image,
                          Map<DecodeHintTypeview,?> hints) throws NotFoundExceptionview;

}
