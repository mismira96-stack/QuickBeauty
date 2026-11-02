package com.quickbeauty.app

import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.palette.graphics.Palette

/** The editor and JPEG export share the same caption layout and output-space positions. */
class StoryRenderer(private val photo: Bitmap,
    private val handwriting: Typeface=Typeface.DEFAULT,
    private val journal: Typeface=Typeface.SERIF,
    private val cinematicFocus: StoryPhotoPosition=StoryPhotoPosition(.5f,.25f)) : java.io.Closeable {
    data class CaptionPlan(
        val bounds: StoryLayout.Box, val layout: StaticLayout, val paint: TextPaint,
        val padding: Float, val vertical: Boolean, val caption: String,
        val labelColor: Int?, val date: String?, val datePaint: Paint,
        val contentScale: Float=1f, val inkInset: Float=16f, val readabilityColor: Int?=null,
        val fixedDate: DatePlan?=null
    ) {
        val textBounds get()=StoryLayout.Box(bounds.left+padding,bounds.top+padding,
            bounds.right-padding,bounds.top+padding+(layout.height+inkInset*2)*contentScale)
    }
    data class DatePlan(val bounds: StoryLayout.Box,val text: String,val paint: Paint,val background: Int)
    private val tone: Int=run {
        val fallback=0xFFEAE6E1.toInt()
        val palette=Palette.from(photo).resizeBitmapArea(4096).maximumColorCount(12).generate()
        val total=palette.swatches.sumOf { it.population }
        val swatch=palette.swatches.filter { candidate ->
            val hsv=FloatArray(3);Color.colorToHSV(candidate.rgb,hsv)
            hsv[1]>=.18f && candidate.population>=total*.08f
        }.maxByOrNull { it.population } ?: palette.dominantSwatch ?: palette.mutedSwatch
        if(swatch==null) fallback else FloatArray(3).let { hsv ->
            Color.colorToHSV(swatch.rgb,hsv);hsv[1]=(hsv[1]*.30f).coerceIn(0f,.18f);hsv[2]=(hsv[2]+(1f-hsv[2])*.82f).coerceIn(.90f,.97f)
            Color.HSVToColor(hsv)
        }
    }
    private val labelTone=FloatArray(3).let { hsv ->
        Color.colorToHSV(tone,hsv);hsv[1]=maxOf(.18f,hsv[1]);hsv[2]=.97f;Color.HSVToColor(hsv)
    }

    private fun baseCaptionPlan(template: StoryTemplateType,caption: String,style: StoryTextStyle,
        date: String?,position: StoryTextPosition?=null,vertical: Boolean=template==StoryTemplateType.SIDE_JOURNAL): CaptionPlan {
        val area=StoryLayout.textRegion(if(vertical) StoryTemplateType.SIDE_JOURNAL else template)
        val padding=if(style==StoryTextStyle.LABEL) 16f else 0f
        val light=template==StoryTemplateType.FULL_DRIFT || template==StoryTemplateType.CINEMATIC_16_9
        val paint=TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color=if(light && style!=StoryTextStyle.LABEL) Color.WHITE else 0xFF282D27.toInt()
            typeface=when(style) {
                StoryTextStyle.TITLE -> Typeface.create("sans-serif-condensed",Typeface.BOLD)
                StoryTextStyle.LABEL -> Typeface.create("sans-serif",Typeface.BOLD)
                StoryTextStyle.JOURNAL -> journal
                StoryTextStyle.MEMO -> handwriting
                StoryTextStyle.TEMPLATE -> when(template) {
                    StoryTemplateType.CLEAN_PURE,StoryTemplateType.MUTED_TONE,StoryTemplateType.FULL_DRIFT -> Typeface.create("sans-serif",Typeface.NORMAL)
                    StoryTemplateType.CINEMATIC_16_9 -> Typeface.create("sans-serif-light",Typeface.NORMAL)
                    else -> handwriting
                }
            }
            textSize=when(style) {
                StoryTextStyle.TITLE -> if(vertical) 74f else 100f
                StoryTextStyle.LABEL -> if(vertical) 64f else 74f
                StoryTextStyle.JOURNAL -> 84f
                StoryTextStyle.MEMO -> 100f
                StoryTextStyle.TEMPLATE -> when(template) {
                    StoryTemplateType.CINEMATIC_16_9 -> 52f
                    StoryTemplateType.SIDE_JOURNAL -> 88f
                    StoryTemplateType.FULL_DRIFT -> 96f
                    else -> 88f
                }
            }
            textScaleX=if(template==StoryTemplateType.CINEMATIC_16_9 && style==StoryTextStyle.TEMPLATE) 1.06f else 1f
            if(template==StoryTemplateType.FULL_DRIFT) setShadowLayer(3f,0f,2f,0x66000000)
        }
        val body=if(vertical) StoryCharacters.split(caption.replace("\n","")).joinToString("\n") else caption.replace('\n',' ').replace('\r',' ')
        val dateSpace=if(date.isNullOrBlank()) 0f else if(vertical) 24f else 28f
        val alignment=when {
            vertical || template.centered -> Layout.Alignment.ALIGN_CENTER
            template==StoryTemplateType.CINEMATIC_16_9 -> Layout.Alignment.ALIGN_OPPOSITE
            else -> Layout.Alignment.ALIGN_NORMAL
        }
        // Measure the complete single line before layout; never rewrap or ellipsize a caption.
        // A separate ink gutter preserves handwriting overhangs and font fallback ascenders.
        val inkInset=16f
        val maxWidth=area.width-padding*2-inkInset*2
        val available=area.height-padding*2-dateSpace-inkInset*2
        fun desiredWidth()=maxOf(1f,Layout.getDesiredWidth(body,paint))
        var contentWidth=kotlin.math.ceil(desiredWidth()+2f).toInt()
        fun makeLayout()=StaticLayout.Builder.obtain(body,0,body.length,paint,contentWidth)
            .setAlignment(alignment).setIncludePad(true).setUseLineSpacingFromFallbacks(true)
            .setLineSpacing(0f,1.08f).build()
        var layout=makeLayout()
        while(paint.textSize>12f && (contentWidth>maxWidth || layout.height>available)) {
            paint.textSize-=1f;contentWidth=kotlin.math.ceil(desiredWidth()+2f).toInt();layout=makeLayout()
        }
        val datePaint=Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface=Typeface.create("sans-serif",Typeface.NORMAL)
            textSize=if(vertical) 23f else 28f
            color=if(light) 0xFFE5E5E3.toInt() else 0xFF70736D.toInt()
            textAlign=when(alignment) { Layout.Alignment.ALIGN_CENTER -> Paint.Align.CENTER
                Layout.Alignment.ALIGN_OPPOSITE -> Paint.Align.RIGHT;else -> Paint.Align.LEFT }
        }
        val dateWidth=if(date.isNullOrBlank()) 0f else datePaint.measureText(date)
        val minimumWidth=if(caption.isBlank()) { if(vertical) 130f else 360f } else 40f
        contentWidth=kotlin.math.ceil(maxOf(contentWidth.toFloat(),dateWidth,minimumWidth)).toInt()
        layout=makeLayout()
        val groupWidth=contentWidth+padding*2+inkInset*2
        val height=(layout.height+padding*2+inkInset*2+dateSpace).coerceAtLeast(60f)
        val defaultX=when(alignment) {
            Layout.Alignment.ALIGN_CENTER -> (area.left+area.right)/2
            Layout.Alignment.ALIGN_OPPOSITE -> area.right-groupWidth/2
            else -> area.left+groupWidth/2
        }
        val preferred=position ?: StoryTextPosition(defaultX/1080f,
            (if(vertical) (area.top+area.bottom)/2 else area.bottom-height/2)/1920f)
        val bounds=StoryLayout.block(groupWidth,height,preferred)
        return CaptionPlan(bounds,layout,paint,padding,vertical,caption,
            if(style==StoryTextStyle.LABEL && caption.isNotBlank()) labelTone else null,date,datePaint)
    }

    fun captionPlan(template: StoryTemplateType,caption: String,style: StoryTextStyle,
        date: String?,position: StoryTextPosition?=null,textScale: Float=1f,
        photoPosition: StoryPhotoPosition?=null,verticalText: Boolean=template==StoryTemplateType.SIDE_JOURNAL,
        dateScale: Float=1f,datePosition: StoryTextPosition?=null,
        captionBackground: StoryBackgroundMode=StoryBackgroundMode.NONE,
        dateBackground: StoryBackgroundMode=StoryBackgroundMode.NONE,dateStyle: StoryTextStyle=StoryTextStyle.TEMPLATE): CaptionPlan {
        val detached=template in StoryLayout.availableTemplates
        val base=baseCaptionPlan(template,caption,style,if(detached) null else date,vertical=verticalText)
        var factor=StoryLayout.textScale(base.bounds.width,base.bounds.height,textScale)
        if(verticalText) {
            val column=StoryLayout.textRegion(StoryTemplateType.SIDE_JOURNAL)
            factor=minOf(factor,column.width/base.bounds.width,column.height/base.bounds.height)
        }
        val bounds=StoryLayout.block(base.bounds.width*factor,base.bounds.height*factor,
            position ?: StoryLayout.center(base.bounds))
        val contrast=if(template in StoryLayout.availableTemplates && style!=StoryTextStyle.LABEL)
            photoTextColor(template,bounds,photoPosition) else null
        val autoColor=when(captionBackground) { StoryBackgroundMode.WHITE -> Color.BLACK; StoryBackgroundMode.NONE -> Color.WHITE; StoryBackgroundMode.AUTO -> contrast?.textColor }
        if(autoColor!=null) {
            base.paint.color=autoColor
            base.paint.setShadowLayer(4f,0f,1f,if(autoColor==Color.WHITE) 0x99000000.toInt() else 0x99FFFFFF.toInt())
        }
        return base.copy(bounds=bounds,padding=base.padding*factor,contentScale=factor,readabilityColor=when(captionBackground) { StoryBackgroundMode.AUTO -> contrast?.background;StoryBackgroundMode.WHITE -> Color.WHITE;StoryBackgroundMode.NONE -> null },
            fixedDate=if(detached && !date.isNullOrBlank()) datePlan(template,date,photoPosition,dateScale,datePosition,dateBackground,dateStyle) else null,
            datePaint=Paint(base.datePaint).apply { textSize=base.datePaint.textSize*factor;autoColor?.let { color=it } })
    }
    private fun datePlan(template: StoryTemplateType,date: String,photoPosition: StoryPhotoPosition?,dateScale: Float,position: StoryTextPosition?,mode: StoryBackgroundMode,style: StoryTextStyle): DatePlan {
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface=when(style) { StoryTextStyle.MEMO -> handwriting; StoryTextStyle.JOURNAL -> journal; else -> Typeface.create("sans-serif-medium",Typeface.NORMAL) };textSize=56f*(if(dateScale.isFinite()) dateScale.coerceIn(.5f,2.2f) else 1f);textAlign=Paint.Align.LEFT
        }
        val width=paint.measureText(date)+24f;val height=paint.descent()-paint.ascent()+16f
        val box=if(position==null) StoryLayout.Box(72f,216f,72f+width,216f+height)
            else StoryLayout.block(width,height,position)
        val contrast=photoTextColor(template,box,photoPosition)
        val background=when(mode) {
            StoryBackgroundMode.WHITE -> Color.WHITE
            StoryBackgroundMode.NONE -> Color.TRANSPARENT
            StoryBackgroundMode.AUTO -> contrast.background ?: Color.TRANSPARENT
        }
        paint.color=if(mode==StoryBackgroundMode.WHITE) Color.BLACK else contrast.textColor
        return DatePlan(box,date,paint,background)
    }
    private data class Contrast(val textColor: Int,val background: Int?)
    private fun photoTextColor(template: StoryTemplateType,bounds: StoryLayout.Box,position: StoryPhotoPosition?): Contrast {
        val frame=StoryLayout.frame(template)
        val rect=StoryLayout.photo(photo.width,photo.height,template,position ?: defaultPhotoPosition(template))
        var luminance=0.0
        val samples=mutableListOf<Double>()
        for(row in 0 until 5) for(column in 0 until 9) {
            val x=bounds.left+bounds.width*(column+.5f)/9
            val y=bounds.top+bounds.height*(row+.5f)/5
            val sx=(x-rect.left)/rect.width;val sy=(y-rect.top)/rect.height
            val color=if(sx in 0f..1f && sy in 0f..1f && x in frame.left..frame.right && y in frame.top..frame.bottom)
                photo.getPixel((sx*photo.width).toInt().coerceIn(0,photo.width-1),
                    (sy*photo.height).toInt().coerceIn(0,photo.height-1)) else if(template==StoryTemplateType.MUTED_TONE) tone else template.background
            val value=androidx.core.graphics.ColorUtils.calculateLuminance(color)
            samples.add(value);luminance+=value
        }
        // Pick whichever neutral text color has the higher contrast on this part of the photo.
        val textColor=if(luminance/45>.179) Color.BLACK else Color.WHITE
        val hardToRead=samples.count { if(textColor==Color.WHITE) 1.05/(it+.05)<4.5 else (it+.05)/.05<4.5 }
        return Contrast(textColor,if(hardToRead>9) {
            if(textColor==Color.WHITE) 0x99000000.toInt() else 0xCCFFFFFF.toInt()
        } else null)
    }
    fun defaultPhotoPosition(template: StoryTemplateType)=
        if(template==StoryTemplateType.CINEMATIC_16_9) cinematicFocus else StoryPhotoPosition(.5f,.5f)
    fun panPhoto(template: StoryTemplateType,position: StoryPhotoPosition?,dx: Float,dy: Float): StoryPhotoPosition {
        val current=position ?: defaultPhotoPosition(template)
        return StoryLayout.pan(photo.width,photo.height,template,current,dx,dy)
    }

    fun draw(canvas: Canvas,template: StoryTemplateType,plan: CaptionPlan?,includeText: Boolean=true,
        photoPosition: StoryPhotoPosition?=null) {
        canvas.drawColor(if(template==StoryTemplateType.MUTED_TONE) tone else template.background)
        val frame=StoryLayout.frame(template)
        val dst=StoryLayout.photo(photo.width,photo.height,template,photoPosition ?: defaultPhotoPosition(template))
        canvas.save();canvas.clipRect(frame.left,frame.top,frame.right,frame.bottom)
        canvas.drawBitmap(photo,null,RectF(dst.left,dst.top,dst.right,dst.bottom),Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        canvas.restore()
        if(plan==null) return
        canvas.save()
        canvas.clipRect(24f,StoryLayout.SAFE_TOP,1056f,StoryLayout.SAFE_BOTTOM)
        if(plan.caption.isNotBlank() || !plan.date.isNullOrBlank()) plan.readabilityColor?.let { color ->
            canvas.drawRoundRect(RectF(plan.bounds.left,plan.bounds.top,plan.bounds.right,plan.bounds.bottom),
                minOf(plan.bounds.width,plan.bounds.height)/2,minOf(plan.bounds.width,plan.bounds.height)/2,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color=color })
        }
        plan.labelColor?.let { color ->
            canvas.drawRoundRect(RectF(plan.bounds.left,plan.bounds.top,plan.bounds.right,
                plan.bounds.top+(plan.layout.height+plan.inkInset*2)*plan.contentScale+plan.padding*2),8f,8f,Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color=color })
        }
        if(includeText && plan.caption.isNotBlank()) {
            canvas.save();canvas.translate(plan.textBounds.left+plan.inkInset*plan.contentScale,plan.textBounds.top+plan.inkInset*plan.contentScale)
            canvas.scale(plan.contentScale,plan.contentScale);plan.layout.draw(canvas);canvas.restore()
        }
        if(!plan.date.isNullOrBlank()) {
            val x=when(plan.datePaint.textAlign) {
                Paint.Align.CENTER -> (plan.bounds.left+plan.bounds.right)/2
                Paint.Align.RIGHT -> plan.bounds.right
                else -> plan.bounds.left
            }
            canvas.drawText(plan.date,x,plan.bounds.bottom-8f*plan.contentScale,plan.datePaint)
        }
        canvas.restore()
        plan.fixedDate?.let { stamp ->
            val b=stamp.bounds
            canvas.drawRoundRect(RectF(b.left,b.top,b.right,b.bottom),minOf(b.width,b.height)/2,minOf(b.width,b.height)/2,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color=stamp.background })
            canvas.drawText(stamp.text,b.left+12f,b.top+8f-stamp.paint.ascent(),stamp.paint)
        }
    }
    fun render(template: StoryTemplateType,caption: String,width: Int=1080,
        textStyle: StoryTextStyle=StoryTextStyle.TEMPLATE,date: String?=null,
        position: StoryTextPosition?=null,textScale: Float=1f,photoPosition: StoryPhotoPosition?=null,
        verticalText: Boolean=template==StoryTemplateType.SIDE_JOURNAL,dateScale: Float=1f,
        datePosition: StoryTextPosition?=null,captionBackground: StoryBackgroundMode=StoryBackgroundMode.NONE,
        dateBackground: StoryBackgroundMode=StoryBackgroundMode.NONE,dateStyle: StoryTextStyle=StoryTextStyle.TEMPLATE): Bitmap {
        val bitmap=Bitmap.createBitmap(width,width*16/9,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap);canvas.scale(width/1080f,bitmap.height/1920f)
        val plan=if(caption.isBlank() && date.isNullOrBlank()) null else captionPlan(template,caption,textStyle,date,position,textScale,photoPosition,verticalText,dateScale,datePosition,captionBackground,dateBackground,dateStyle)
        draw(canvas,template,plan,photoPosition=photoPosition)
        return bitmap
    }
    override fun close()=Unit
}
