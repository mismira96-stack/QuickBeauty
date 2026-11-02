package com.quickbeauty.app

import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/** App-owned sheets. System permission and share dialogs remain owned by Android. */
object QuickBeautyDialog {
    data class Choice(val title: String,val detail: String,val typeface: Typeface?=null,val background: StoryBackgroundMode?=null)
    data class Action(val title: String,val run: () -> Unit={})
    private const val PURPLE=0xFFB7A8FF.toInt()
    private fun dp(a: AppCompatActivity,v: Int)=(a.resources.displayMetrics.density*v).toInt()
    private fun shape(a: AppCompatActivity,color: Int,radius: Int,stroke: Int?=null)=GradientDrawable().apply {
        setColor(color);cornerRadius=dp(a,radius).toFloat();stroke?.let { setStroke(dp(a,1),it) }
    }
    private fun text(a: AppCompatActivity,value: String,size: Float,color: Int)=TextView(a).apply {
        text=value;textSize=size;setTextColor(color)
    }
    private fun frame(a: AppCompatActivity,title: String,subtitle: String?): Pair<Dialog,LinearLayout> {
        val dialog=Dialog(a).apply { requestWindowFeature(Window.FEATURE_NO_TITLE);setOwnerActivity(a) }
        val root=LinearLayout(a).apply {
            orientation=LinearLayout.VERTICAL;setPadding(dp(a,18),dp(a,14),dp(a,18),dp(a,20))
            background=shape(a,0xFF191A22.toInt(),24)
        }
        val header=LinearLayout(a).apply { gravity=Gravity.CENTER_VERTICAL }
        header.addView(text(a,title,19f,Color.WHITE).apply { setTypeface(typeface,Typeface.BOLD) },LinearLayout.LayoutParams(0,dp(a,48),1f))
        header.addView(text(a,"×",26f,0xFFB9B7C7.toInt()).apply {
            gravity=Gravity.CENTER;contentDescription=a.getString(R.string.close);setOnClickListener { dialog.dismiss() }
        },LinearLayout.LayoutParams(dp(a,48),dp(a,48)))
        root.addView(header)
        if(!subtitle.isNullOrBlank()) root.addView(text(a,subtitle,13f,0xFFB6B3C5.toInt()).apply { setPadding(0,0,0,dp(a,16)) })
        dialog.setContentView(root)
        val observer=object : DefaultLifecycleObserver { override fun onDestroy(owner: LifecycleOwner) { dialog.dismiss() } }
        a.lifecycle.addObserver(observer);dialog.setOnDismissListener { a.lifecycle.removeObserver(observer) }
        return dialog to root
    }
    private fun show(a: AppCompatActivity,dialog: Dialog) {
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent);setGravity(Gravity.BOTTOM)
            setLayout(minOf(a.resources.displayMetrics.widthPixels-dp(a,24),dp(a,560)),WindowManager.LayoutParams.WRAP_CONTENT)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);setDimAmount(.40f)
        }
    }
    fun choices(a: AppCompatActivity,title: String,subtitle: String,choices: List<Choice>,selected: Int,onSelect: (Int) -> Unit) {
        val (dialog,root)=frame(a,title,subtitle)
        val list=LinearLayout(a).apply { orientation=LinearLayout.VERTICAL }
        val scroll=object : ScrollView(a) {
            override fun onMeasure(w: Int,h: Int) { super.onMeasure(w,View.MeasureSpec.makeMeasureSpec((a.resources.displayMetrics.heightPixels*.55f).toInt(),View.MeasureSpec.AT_MOST)) }
        }.apply { isFillViewport=false;addView(list) }
        root.addView(scroll)
        choices.forEachIndexed { index,choice ->
            val active=index==selected
            val row=LinearLayout(a).apply {
                gravity=Gravity.CENTER_VERTICAL;minimumHeight=dp(a,84);setPadding(dp(a,12),dp(a,12),dp(a,12),dp(a,12))
                background=RippleDrawable(ColorStateList.valueOf(0x337563E7),
                    shape(a,if(active) 0xFF302B44.toInt() else 0xFF23242D.toInt(),16,if(active) 0xFF9580F5.toInt() else 0xFF363743.toInt()),null)
                isClickable=true;isFocusable=true
                setOnClickListener { dialog.dismiss();onSelect(index) }
            }
            row.addView(object : View(a) {
                private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
                override fun onDraw(canvas: Canvas) {
                    val box=RectF(0f,0f,width.toFloat(),height.toFloat())
                    paint.shader=LinearGradient(0f,0f,width.toFloat(),height.toFloat(),0xFF777F90.toInt(),0xFF303340.toInt(),Shader.TileMode.CLAMP)
                    canvas.drawRoundRect(box,dp(a,12).toFloat(),dp(a,12).toFloat(),paint);paint.shader=null
                    val mode=choice.background
                    if(mode!=null && mode!=StoryBackgroundMode.NONE) {
                        paint.color=if(mode==StoryBackgroundMode.WHITE) Color.WHITE else 0x99000000.toInt()
                        canvas.drawRoundRect(RectF(width*.1f,height*.2f,width*.9f,height*.8f),height/2f,height/2f,paint)
                    }
                    paint.color=if(mode==StoryBackgroundMode.WHITE) Color.BLACK else Color.WHITE
                    paint.typeface=choice.typeface ?: Typeface.DEFAULT;paint.textAlign=Paint.Align.CENTER
                    paint.textSize=dp(a,26).toFloat()
                    canvas.drawText(a.getString(R.string.story_preview_sample),width/2f,height/2f-(paint.ascent()+paint.descent())/2,paint)
                }
            },LinearLayout.LayoutParams(dp(a,72),dp(a,54)).apply { marginEnd=dp(a,14) })
            val labels=LinearLayout(a).apply { orientation=LinearLayout.VERTICAL }
            labels.addView(text(a,choice.title,15f,Color.WHITE))
            labels.addView(text(a,choice.detail,12f,0xFFB6B3C5.toInt()).apply { setPadding(0,dp(a,4),0,0) })
            row.addView(labels,LinearLayout.LayoutParams(0,-2,1f))
            row.addView(text(a,if(active) "✓" else "○",20f,if(active) PURPLE else 0xFF747180.toInt()).apply { gravity=Gravity.CENTER },LinearLayout.LayoutParams(dp(a,28),dp(a,36)))
            list.addView(row,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(a,10) })
        }
        show(a,dialog)
    }
    fun message(a: AppCompatActivity,title: String,body: String,actions: List<Action>) {
        val (dialog,root)=frame(a,title,null)
        val scroll=object : ScrollView(a) {
            override fun onMeasure(w: Int,h: Int) { super.onMeasure(w,View.MeasureSpec.makeMeasureSpec((a.resources.displayMetrics.heightPixels*.4f).toInt(),View.MeasureSpec.AT_MOST)) }
        }
        scroll.addView(text(a,body,15f,0xFFD4D1DF.toInt()).apply { setLineSpacing(dp(a,4).toFloat(),1f);setPadding(0,dp(a,4),0,dp(a,20)) })
        root.addView(scroll)
        actions.forEachIndexed { index,action ->
            root.addView(text(a,action.title,14f,if(index==0) Color.WHITE else 0xFFD4CEF0.toInt()).apply {
                gravity=Gravity.CENTER;minimumHeight=dp(a,48)
                background=shape(a,if(index==0) 0xFF7563E7.toInt() else 0xFF292A35.toInt(),14)
                setOnClickListener { dialog.dismiss();action.run() }
            },LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(a,8) })
        }
        show(a,dialog)
    }
}
