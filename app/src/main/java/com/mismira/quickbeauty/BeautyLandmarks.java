package com.mismira.quickbeauty;

/**
 * 媛먯????쇨뎬 諛??좎껜 ?쒕뱶留덊겕 ?뺣낫 (?먮낯 ?대?吏 ?쎌? 醫뚰몴怨?湲곗?)
 * Android ?꾨젅?꾩썙??醫낆냽???놁씠 ?쒖닔 Java濡??숈옉?섏뿬 ?⑥쐞 ?뚯뒪??諛??⑤뵒諛붿씠?ㅼ뿉??珥덇퀬???숈옉
 */
public class BeautyLandmarks {

    public static class Point {
        public float x;
        public float y;

        public Point() {}

        public Point(float x, float y) {
            this.x = x;
            this.y = y;
        }

        public void set(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }

    public static class Rect {
        public float left;
        public float top;
        public float right;
        public float bottom;

        public Rect() {}

        public Rect(float left, float top, float right, float bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        public void set(float left, float top, float right, float bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        public float width() {
            return Math.max(0, right - left);
        }

        public float height() {
            return Math.max(0, bottom - top);
        }

        public float centerX() {
            return (left + right) * 0.5f;
        }

        public float centerY() {
            return (top + bottom) * 0.5f;
        }
    }

    // ?먮낯 鍮꾪듃留?湲곗? ?ш린
    public int imageWidth;
    public int imageHeight;

    // ?쇨뎬 ?뺣낫
    public boolean hasFace = false;
    public Rect faceBounds = new Rect();
    public Point faceCenter = new Point();
    public Point chinPoint = new Point();
    public Point leftJaw = new Point();
    public Point rightJaw = new Point();
    public Point leftCheek = new Point();
    public Point rightCheek = new Point();

    // 紐몃ℓ ?뺣낫
    public boolean hasBody = false;
    public Rect bodyBounds = new Rect();
    public Point leftShoulder = new Point();
    public Point rightShoulder = new Point();
    public Point leftHip = new Point();
    public Point rightHip = new Point();
    public float bodyCenterY;
    public float bodyTopY;
    public float bodyBottomY;

    public BeautyLandmarks(int width, int height) {
        this.imageWidth = width;
        this.imageHeight = height;
    }

    /**
     * 媛먯??섏? ?딆븯?????대?吏 以묒븰 ?곸뿭??湲곗??쇰줈 湲곕낯 ?곸뿭 異붿젙
     */
    public void setupDefaultsIfEmpty() {
        if (!hasFace) {
            // 以묒븰 ?곷떒 1/3???쇨뎬濡?異붿젙
            float fcX = imageWidth * 0.5f;
            float fcY = imageHeight * 0.35f;
            float fw = imageWidth * 0.35f;
            float fh = imageHeight * 0.30f;
            faceBounds.set(fcX - fw * 0.5f, fcY - fh * 0.5f, fcX + fw * 0.5f, fcY + fh * 0.5f);
            faceCenter.set(fcX, fcY);
            chinPoint.set(fcX, fcY + fh * 0.45f);
            leftJaw.set(fcX - fw * 0.38f, fcY + fh * 0.25f);
            rightJaw.set(fcX + fw * 0.38f, fcY + fh * 0.25f);
            leftCheek.set(fcX - fw * 0.35f, fcY);
            rightCheek.set(fcX + fw * 0.35f, fcY);
            hasFace = true;
        }

        if (!hasBody) {
            // 以묒븰 紐명넻 ?곸뿭 異붿젙 (Y異?30% ~ 85%)
            float bcX = imageWidth * 0.5f;
            bodyTopY = imageHeight * 0.30f;
            bodyBottomY = imageHeight * 0.85f;
            bodyCenterY = (bodyTopY + bodyBottomY) * 0.5f;
            float halfWidth = imageWidth * 0.30f;
            leftShoulder.set(bcX - halfWidth, bodyTopY);
            rightShoulder.set(bcX + halfWidth, bodyTopY);
            leftHip.set(bcX - halfWidth * 0.85f, bodyCenterY);
            rightHip.set(bcX + halfWidth * 0.85f, bodyCenterY);
            bodyBounds.set(bcX - halfWidth, bodyTopY, bcX + halfWidth, bodyBottomY);
            hasBody = true;
        }
    }

    /**
     * ?ㅻⅨ ?ш린(?? 誘몃━蹂닿린??鍮꾪듃留?<-> ?먮낯 怨좏빐?곷룄 鍮꾪듃留?濡??ㅼ??쇰쭅 蹂듭궗
     */
    public BeautyLandmarks scaleTo(int targetWidth, int targetHeight) {
        BeautyLandmarks res = new BeautyLandmarks(targetWidth, targetHeight);
        if (imageWidth <= 0 || imageHeight <= 0) return res;

        float sx = (float) targetWidth / imageWidth;
        float sy = (float) targetHeight / imageHeight;

        res.hasFace = this.hasFace;
        res.faceBounds.set(faceBounds.left * sx, faceBounds.top * sy, faceBounds.right * sx, faceBounds.bottom * sy);
        res.faceCenter.set(faceCenter.x * sx, faceCenter.y * sy);
        res.chinPoint.set(chinPoint.x * sx, chinPoint.y * sy);
        res.leftJaw.set(leftJaw.x * sx, leftJaw.y * sy);
        res.rightJaw.set(rightJaw.x * sx, rightJaw.y * sy);
        res.leftCheek.set(leftCheek.x * sx, leftCheek.y * sy);
        res.rightCheek.set(rightCheek.x * sx, rightCheek.y * sy);

        res.hasBody = this.hasBody;
        res.bodyBounds.set(bodyBounds.left * sx, bodyBounds.top * sy, bodyBounds.right * sx, bodyBounds.bottom * sy);
        res.leftShoulder.set(leftShoulder.x * sx, leftShoulder.y * sy);
        res.rightShoulder.set(rightShoulder.x * sx, rightShoulder.y * sy);
        res.leftHip.set(leftHip.x * sx, leftHip.y * sy);
        res.rightHip.set(rightHip.x * sx, rightHip.y * sy);
        res.bodyTopY = this.bodyTopY * sy;
        res.bodyBottomY = this.bodyBottomY * sy;
        res.bodyCenterY = this.bodyCenterY * sy;

        return res;
    }
}