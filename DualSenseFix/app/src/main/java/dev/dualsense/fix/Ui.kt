package dev.dualsense.fix

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.widget.*

object Ui {
    val bg=Color.rgb(12,16,27)
    val surface=Color.rgb(23,30,46)
    val ink=Color.rgb(240,244,255)
    val muted=Color.rgb(164,177,200)
    val accent=Color.rgb(135,226,214)
    val violet=Color.rgb(193,179,255)
    fun dp(c: Context,n: Int)=(n*c.resources.displayMetrics.density).toInt()
    fun shape(color: Int,stroke: Int=Color.TRANSPARENT)=GradientDrawable().apply { setColor(color);cornerRadius=48f;setStroke(1,stroke) }
    fun column(c:Context)=LinearLayout(c).apply { orientation=LinearLayout.VERTICAL }
    fun text(c:Context,s:String,size:Float=14f,color:Int=ink)=TextView(c).apply { text=s;textSize=size;setTextColor(color);setLineSpacing(dp(c,3).toFloat(),1f) }
    fun gap(box:LinearLayout,n:Int=12){box.addView(View(box.context),LinearLayout.LayoutParams(1,dp(box.context,n)))}
    fun card(box:LinearLayout):LinearLayout {
        val c=box.context
        val child=column(c).apply { background=shape(surface);setPadding(dp(c,20),dp(c,20),dp(c,20),dp(c,20)) }
        box.addView(child,LinearLayout.LayoutParams(-1,-2));gap(box,16);return child
    }
    fun title(box:LinearLayout,s:String,sub:String="") {
        box.addView(text(box.context,s,20f).apply { typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL) })
        if(sub.isNotEmpty()){gap(box,6);box.addView(text(box.context,sub,14f,muted))};gap(box,16)
    }
    fun button(box:LinearLayout,s:String,primary:Boolean=false,danger:Boolean=false,action:()->Unit):Button {
        val c=box.context
        return Button(c).apply {
            text=s;isAllCaps=false;textSize=15f;typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)
            setTextColor(if(primary)bg else if(danger)Color.rgb(255,174,184) else ink)
            val color=if(primary)accent else if(danger)Color.rgb(58,31,44) else Color.rgb(37,47,66)
            background=RippleDrawable(ColorStateList.valueOf(0x2287E2D6),shape(color),null)
            minHeight=dp(c,52);gravity=Gravity.CENTER;setPadding(dp(c,12),dp(c,8),dp(c,12),dp(c,8))
            setOnClickListener { action() }
            box.addView(this,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(c,10) })
        }
    }
    fun spinner(box:LinearLayout,labels:Array<String>):Spinner {
        val c=box.context
        return Spinner(c).apply {
            adapter=ArrayAdapter(c,android.R.layout.simple_spinner_dropdown_item,labels)
            background=shape(Color.rgb(37,47,66));setPadding(dp(c,12),0,dp(c,12),0)
            box.addView(this,LinearLayout.LayoutParams(-1,dp(c,56)).apply { bottomMargin=dp(c,14) })
        }
    }
}

class ControllerArt(c:Context):View(c) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(canvas:Canvas) {
        super.onDraw(canvas)
        val save=canvas.save();canvas.translate(width/2f,height/2f)
        // Density-independent controller silhouette, centered on every screen width.
        canvas.scale(resources.displayMetrics.density,resources.displayMetrics.density)
        paint.color=0xFF172437.toInt();canvas.drawOval(-120f,-47f,120f,51f,paint)
        val p=Path().apply { moveTo(-65f,-33f);cubicTo(-91f,-30f,-110f,50f,-83f,48f);lineTo(-43f,22f);lineTo(43f,22f);lineTo(83f,48f);cubicTo(110f,50f,91f,-30f,65f,-33f);close() }
        paint.color=0xFF34425A.toInt();canvas.drawPath(p,paint)
        paint.color=0xFF0C101B.toInt();canvas.drawRoundRect(-34f,-28f,34f,-4f,7f,7f,paint)
        paint.color=Ui.accent;canvas.drawRoundRect(-25f,-25f,25f,-23f,1f,1f,paint)
        paint.color=0xFF131C2D.toInt();canvas.drawCircle(-29f,15f,13f,paint);canvas.drawCircle(29f,15f,13f,paint)
        paint.color=0xFF65758E.toInt();canvas.drawCircle(-29f,15f,8f,paint);canvas.drawCircle(29f,15f,8f,paint)
        paint.color=Ui.muted;canvas.drawRoundRect(-67f,-19f,-59f,5f,2f,2f,paint);canvas.drawRoundRect(-75f,-11f,-51f,-3f,2f,2f,paint)
        paint.color=Ui.violet;canvas.drawCircle(63f,-19f,4f,paint);canvas.drawCircle(63f,1f,4f,paint)
        paint.color=Ui.accent;canvas.drawCircle(53f,-9f,4f,paint);canvas.drawCircle(73f,-9f,4f,paint)
        canvas.restoreToCount(save)
    }
}
