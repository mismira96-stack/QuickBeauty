package com.mismira.quickbeauty;

/**
 * 酉고떚 蹂댁젙 ?뚮씪誘명꽣 紐⑤뜽 (0 ~ 100)
 */
public class BeautyAdjustParams {
    private int coolTone;   // 荑⑦넠 ?쇰? ?ㅼ뾽 (0 ~ 100)
    private int faceSlim;   // ?쇨뎬 ?ш린 異뺤냼 (0 ~ 100)
    private int chinSlim;   // ?깆꽑 V?쇱씤 蹂댁젙 (0 ~ 100)
    private int bodySlim;   // 紐몃ℓ ?꾩껜 ?щ┝ (0 ~ 100)

    public BeautyAdjustParams() {
        this.coolTone = 0;
        this.faceSlim = 0;
        this.chinSlim = 0;
        this.bodySlim = 0;
    }

    public BeautyAdjustParams(int coolTone, int faceSlim, int chinSlim, int bodySlim) {
        this.coolTone = clamp(coolTone);
        this.faceSlim = clamp(faceSlim);
        this.chinSlim = clamp(chinSlim);
        this.bodySlim = clamp(bodySlim);
    }

    public BeautyAdjustParams copy() {
        return new BeautyAdjustParams(coolTone, faceSlim, chinSlim, bodySlim);
    }

    public int getCoolTone() {
        return coolTone;
    }

    public void setCoolTone(int coolTone) {
        this.coolTone = clamp(coolTone);
    }

    public int getFaceSlim() {
        return faceSlim;
    }

    public void setFaceSlim(int faceSlim) {
        this.faceSlim = clamp(faceSlim);
    }

    public int getChinSlim() {
        return chinSlim;
    }

    public void setChinSlim(int chinSlim) {
        this.chinSlim = clamp(chinSlim);
    }

    public int getBodySlim() {
        return bodySlim;
    }

    public void setBodySlim(int bodySlim) {
        this.bodySlim = clamp(bodySlim);
    }

    public boolean isDefault() {
        return coolTone == 0 && faceSlim == 0 && chinSlim == 0 && bodySlim == 0;
    }

    public void reset() {
        this.coolTone = 0;
        this.faceSlim = 0;
        this.chinSlim = 0;
        this.bodySlim = 0;
    }

    private static int clamp(int val) {
        if (val < 0) return 0;
        if (val > 100) return 100;
        return val;
    }
}
