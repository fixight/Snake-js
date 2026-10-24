package dev.dualsense.fix

import android.app.Activity
import android.os.Bundle
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.*

class InputTestActivity: Activity() {
    private lateinit var output: TextView
    private val axes = intArrayOf(MotionEvent.AXIS_X, MotionEvent.AXIS_Y, MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ, MotionEvent.AXIS_LTRIGGER, MotionEvent.AXIS_RTRIGGER, MotionEvent.AXIS_HAT_X, MotionEvent.AXIS_HAT_Y)
    private fun show(s: String) {
        BridgeConnection.androidEvents = (BridgeConnection.androidEvents + "\n" + s).takeLast(16000)
        output.text = BridgeConnection.androidEvents
    }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val box=Ui.column(this).apply { setPadding(Ui.dp(context,20),Ui.dp(context,24),Ui.dp(context,20),Ui.dp(context,24)) }
        setContentView(ScrollView(this).apply { fitsSystemWindows=true;addView(box) })
        box.addView(Ui.text(this,"Проверка ввода",28f));Ui.gap(box,8)
        box.addView(Ui.text(this,"Нажимайте кнопки и двигайте стики. Здесь отображаются события, которые получает Android.",14f,Ui.muted));Ui.gap(box,24)
        val controls=Ui.card(box)
        Ui.button(controls,"Тестовое нажатие ✕ / A",true) { try { val b=BridgeConnection.bridge;if(b==null)show("Сначала подключите Shizuku и запустите мост.") else b.testButton() }catch(e:Exception){show(e.toString())} }
        Ui.button(controls,"Обновить устройства") { devices() }
        Ui.button(controls,"Вернуться к мосту") { finish() }
        val events=Ui.card(box)
        Ui.title(events,"События Android","Кнопки KEYCODE, оси AXIS и диапазоны виртуального устройства.")
        output=Ui.text(this,BridgeConnection.androidEvents.ifEmpty { "Ожидание событий…" },13f,Ui.muted).apply { setTextIsSelectable(true);typeface=android.graphics.Typeface.MONOSPACE };events.addView(output);devices()
    }
    private fun devices() {
        var found=false
        for(id in InputDevice.getDeviceIds()) InputDevice.getDevice(id)?.let { d ->
            if(d.name.startsWith("DSFIX")) {
                found=true
                show("${d.name} id=${d.id} sources=0x${d.sources.toString(16)} VID=${d.vendorId} PID=${d.productId}")
                d.motionRanges.forEach { show("${MotionEvent.axisToString(it.axis)} min=${it.min} max=${it.max} flat=${it.flat}") }
            }
        }
        if(!found)show("Виртуальный контроллер пока не найден. Запустите мост на главном экране.")
    }
    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        if(e.isFromSource(InputDevice.SOURCE_GAMEPAD)||e.isFromSource(InputDevice.SOURCE_JOYSTICK)) {
            show("${e.device?.name} ${KeyEvent.keyCodeToString(e.keyCode)} action=${e.action} scan=${e.scanCode}")
            return true
        }
        return super.dispatchKeyEvent(e)
    }
    override fun dispatchGenericMotionEvent(e: MotionEvent): Boolean {
        if(e.isFromSource(InputDevice.SOURCE_JOYSTICK)) {
            show("${e.device?.name}: "+axes.joinToString { "${MotionEvent.axisToString(it)}=${"%.3f".format(e.getAxisValue(it))}" })
            return true
        }
        return super.dispatchGenericMotionEvent(e)
    }
}
