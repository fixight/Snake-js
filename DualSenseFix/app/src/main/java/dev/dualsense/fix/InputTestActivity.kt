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
        val box=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(20,20,20,20) }
        setContentView(ScrollView(this).apply { addView(box) })
        box.addView(TextView(this).apply { text="Android Input Tester: нажмите кнопки и подвигайте оси. Смотрите имя устройства DSFIX и Android AXIS_*, а не только сырые evdev коды." })
        box.addView(Button(this).apply { text="Тест A/Cross через uinput";setOnClickListener { try { BridgeConnection.bridge?.testButton() }catch(e:Exception){show(e.toString())} } })
        box.addView(Button(this).apply { text="Обновить список Android устройств";setOnClickListener { devices() } })
        box.addView(Button(this).apply { text="Назад";setOnClickListener { finish() } })
        output=TextView(this).apply { setTextIsSelectable(true) };box.addView(output);devices()
    }
    private fun devices() {
        for(id in InputDevice.getDeviceIds()) InputDevice.getDevice(id)?.let { d ->
            if(d.name.startsWith("DSFIX")) {
                show("${d.name} id=${d.id} sources=0x${d.sources.toString(16)} VID=${d.vendorId} PID=${d.productId}")
                d.motionRanges.forEach { show("${MotionEvent.axisToString(it.axis)} min=${it.min} max=${it.max} flat=${it.flat}") }
            }
        }
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
