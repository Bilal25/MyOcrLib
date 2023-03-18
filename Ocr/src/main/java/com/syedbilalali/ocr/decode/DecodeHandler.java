/*
 * Copyright (C) 2010 ZXing authors
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.syedbilalali.ocr.decode;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;

import com.google.zxing.BarcodeFormatview;
import com.google.zxing.BinaryBitmapview;
import com.google.zxing.DecodeHintTypeview;
import com.google.zxing.MultiFormatReaderview;
import com.google.zxing.PlanarYUVLuminanceSourceview;
import com.google.zxing.Resultview;
import com.google.zxing.common.GlobalHistogramBinarizerView;
import com.google.zxing.common.HybridBinarizerView;
import com.syedbilalali.ocr.R;
import com.syedbilalali.ocr.ScannerActivity;
import com.syedbilalali.ocr.tess.TessEngine;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Hashtable;
import java.util.Map;

final class DecodeHandler extends Handler {

    private final ScannerActivity mActivity;
    private final MultiFormatReaderview mMultiFormatReader;
    private final Map<DecodeHintTypeview, Object> mHints;
    private byte[] mRotatedData;
    
    DecodeHandler(ScannerActivity activity) {
        this.mActivity = activity;
        mMultiFormatReader = new MultiFormatReaderview();
        mHints = new Hashtable<>();
        mHints.put(DecodeHintTypeview.CHARACTER_SET, "utf-8");
        mHints.put(DecodeHintTypeview.TRY_HARDER, Boolean.TRUE);
        Collection<BarcodeFormatview> barcodeFormats = new ArrayList<>();
        barcodeFormats.add(BarcodeFormatview.CODE_39);
        barcodeFormats.add(BarcodeFormatview.CODE_128); // 快递单常用格式39,128
        barcodeFormats.add(BarcodeFormatview.QR_CODE); //扫描格式自行添加
        mHints.put(DecodeHintTypeview.POSSIBLE_FORMATS, barcodeFormats);
    }

    @Override
    public void handleMessage(Message message) {
        if (message.what == R.id.decode) {
            decode((byte[]) message.obj, message.arg1, message.arg2);
        } else if (message.what == R.id.quit) {
            Looper looper = Looper.myLooper();
            if (null != looper) {
                looper.quit();
            }
        }
    }

    /**
     * Decode the data within the viewfinder rectangle, and time how long it took. For efficiency, reuse the same reader
     * objects from one decode to the next.
     *
     * @param data The YUV preview frame.
     * @param width The width of the preview frame.
     * @param height The height of the preview frame.
     */
    private void decode(byte[] data, int width, int height) {
        if (null == mRotatedData) {
            mRotatedData = new byte[width * height];
        } else {
            if (mRotatedData.length < width * height) {
                mRotatedData = new byte[width * height];
            }
        }
        Arrays.fill(mRotatedData, (byte) 0);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (x + y * width >= data.length) {
                    break;
                }
                mRotatedData[x * height + height - y - 1] = data[x + y * width];
            }
        }
        int tmp = width; // Here we are swapping, that's the difference to #11
        width = height;
        height = tmp;

        Resultview rawResult = null;
        try {
            Rect rect = mActivity.getCropRect();
            if (rect == null) {
                return;
            }

            PlanarYUVLuminanceSourceview source = new PlanarYUVLuminanceSourceview(mRotatedData, width, height, rect.left, rect.top, rect.width(), rect.height(), false);

            if (mActivity.isQRCode()){
                /*
                 HybridBinarizer算法使用了更高级的算法，针对渐变图像更优，也就是准确率高。
                 但使用GlobalHistogramBinarizer识别效率确实比HybridBinarizer要高一些。
                 */
                rawResult = mMultiFormatReader.decode(new BinaryBitmapview(new GlobalHistogramBinarizerView(source)), mHints);
                if (rawResult == null) {
                    rawResult = mMultiFormatReader.decode(new BinaryBitmapview(new HybridBinarizerView(source)), mHints);
                }
            }else{
                TessEngine tessEngine = TessEngine.Generate();
                Bitmap bitmap = source.renderCroppedGreyscaleBitmap();
                String result = tessEngine.detectText(bitmap);
                if(!TextUtils.isEmpty(result)){
                    rawResult = new Resultview(result, null, null, null);
                    rawResult.setBitmap(bitmap);
                }
            }

        } catch (Exception ignored) {
        } finally {
            mMultiFormatReader.reset();
        }

        if (rawResult != null) {
            Message message = Message.obtain(mActivity.getCaptureActivityHandler(), R.id.decode_succeeded, rawResult);
            message.sendToTarget();
        } else {
            Message message = Message.obtain(mActivity.getCaptureActivityHandler(), R.id.decode_failed);
            message.sendToTarget();
        }
    }
}
