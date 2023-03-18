package com.syedbilalali.ocr.decode;

import com.google.zxing.Resultview;

/**
 * 图片解析二维码回调方法
 */
public interface DecodeImageCallback {

    void decodeSucceed(Resultview result);

    void decodeFail(int type, String reason);
}
