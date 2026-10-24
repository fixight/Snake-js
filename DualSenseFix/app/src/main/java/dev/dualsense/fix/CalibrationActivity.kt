package dev.dualsense.fix

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.*
import android.view.InputDevice
import android.view.KeyEvent
import android.widget.*

/** Transactional calibration: native changes stay in diagnostics until explicit Save. */
class CalibrationActivity:Activity() {
    private val handler=Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("MainActivity",0) }
    private val mode by lazy { intent.getStringExtra("mode") ?: "buttons" }
    private val steps by lazy { CalibrationPlan.steps(mode) }
    private var index=0
    private var stage="ready"
    private var revision=0
    private var neutralSince=0L
    private var beforeStep=""
    private var visible=false
    private var closed=false
    private val completed=linkedMapOf<String,String>()
    private lateinit var title:TextView
    private lateinit var instruction:TextView
    private lateinit var feedback:TextView
    private lateinit var summary:TextView
    private lateinit var progress:ProgressBar
    private lateinit var back:Button
    private lateinit var skip:Button
    private lateinit var retry:Button
    private lateinit var save:Button
    private var lastPhysical=""
    override fun onCreate(saved:Bundle?) {
        super.onCreate(saved)
        index=saved?.getInt("index") ?: 0
        if(saved!=null)saved.getStringArrayList("ids")?.zip(saved.getStringArrayList("bindings") ?: arrayListOf())?.forEach { completed[it.first]=it.second }
        val box=Ui.column(this).apply { setPadding(Ui.dp(context,20),Ui.dp(context,20),Ui.dp(context,20),Ui.dp(context,24)) }
        setContentView(ScrollView(this).apply { fitsSystemWindows=true;addView(box) })
        box.addView(Ui.text(this,if(mode=="dpad")"Настройка D-pad" else "Мастер раскладки",27f));Ui.gap(box,8)
        box.addView(Ui.text(this,"Нажимайте только то, что показано на экране. Приложение дождётся отпускания и само перейдёт дальше.",14f,Ui.muted));Ui.gap(box,20)
        val card=Ui.card(box)
        progress=ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply { max=steps.size };card.addView(progress,LinearLayout.LayoutParams(-1,Ui.dp(this,8)));Ui.gap(card,24)
        title=Ui.text(this,"Подготовка…",28f);card.addView(title);Ui.gap(card,12)
        instruction=Ui.text(this,"",16f);card.addView(instruction);Ui.gap(card,18)
        feedback=Ui.text(this,"",14f,Ui.accent).apply { accessibilityLiveRegion=android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE };card.addView(feedback);Ui.gap(card,20)
        back=Ui.button(card,"← Предыдущий шаг") { move(-1) }
        skip=Ui.button(card,"Пропустить этот элемент") { move(1) }
        retry=Ui.button(card,"Повторить / возобновить") { stage="ready";neutralSince=0;safe { it.learn(0,0) };ensureDiagnostics(true) }
        save=Ui.button(card,"Сохранить и завершить",true) { save() }
        summary=Ui.text(this,"",13f,Ui.muted);box.addView(summary);Ui.gap(box,16)
        Ui.button(box,"Отменить без сохранения",danger=true) { discard() }
        ensureDiagnostics(saved!=null)
        render()
    }
    private fun safe(action:(IBridge)->Unit) {
        try { val b=BridgeConnection.bridge ?: throw IllegalStateException("Нет связи с Shizuku. Вернитесь на главный экран и подключитесь заново.");action(b) }
        catch(e:Exception){stage="error";feedback.text=e.message ?: e.toString();feedback.setTextColor(android.graphics.Color.rgb(255,174,184))}
    }
    private fun ensureDiagnostics(reuse:Boolean) {
        safe { b ->
            b.learn(0,0)
            val state=RuntimeState.parse(b.state())
            if(!reuse||!state.busy||!state.diagnostic) {
                // Keep partial assignments on Retry; a fresh wizard starts from the user's saved mapping.
                val mapping=if(reuse&&beforeStep.isNotEmpty())beforeStep else if(reuse&&state.diagnostic)b.mappings() else prefs.getString("mapping","") ?: ""
                b.start(0,false,true,mapping)
            }
            startForegroundService(Intent(this,BridgeKeeper::class.java))
        }
    }
    private val tick=object:Runnable {
        override fun run() {
            if(!visible||closed)return
            safe { b ->
                b.heartbeat()
                val state=RuntimeState.parse(b.state());BridgeConnection.lastState=state
                if(state.phase=="error"||!state.busy) {stage="error";feedback.text=state.error.ifEmpty { "Диагностика остановлена. Нажмите «Возобновить»." };render();return@safe}
                if(state.phase!="diagnostics") {
                    if(stage=="armed") {b.learn(0,0);if(beforeStep.isNotEmpty())b.replaceMapping(beforeStep);stage="ready"}
                    neutralSince=0
                    feedback.text=if(state.phase=="waiting")"Подключите DualSense. Ожидаем физический контроллер…" else "Подготавливаем диагностику…"
                    render();return@safe
                }
                if(lastPhysical.isNotEmpty()&&lastPhysical!=state.physical){b.learn(0,0);stage="ready";neutralSince=0}
                lastPhysical=state.physical
                if(index>=steps.size){stage="complete";render();return@safe}
                val step=steps[index]
                if(stage=="ready") {
                    if(state.neutral) {
                        if(neutralSince==0L)neutralSince=SystemClock.elapsedRealtime()
                        if(SystemClock.elapsedRealtime()-neutralSince>=450) {
                            beforeStep=b.mappings();revision=state.learned
                            b.learn(step.type,step.target);stage="armed";feedback.text="Ждём: ${step.title}"
                            feedback.setTextColor(Ui.accent)
                        }
                    } else {neutralSince=0;feedback.text="Отпустите все кнопки и триггеры. Верните стики в центр."}
                } else if(stage=="armed"&&state.learned>revision) {
                    val mapping=b.mappings();val binding=CalibrationPlan.binding(mapping,step)
                    val duplicate=completed.entries.firstOrNull { it.key!=step.id&&it.value==binding }
                    if(binding==null||duplicate!=null) {
                        b.replaceMapping(beforeStep);stage="ready";neutralSince=0
                        val other=steps.firstOrNull { it.id==duplicate?.key }?.title ?: "другой элемент"
                        feedback.text=if(duplicate!=null)"Это уже назначено на $other. Отпустите и нажмите ${step.title}." else "Назначение не подтверждено. Повторите этот шаг."
                        feedback.setTextColor(Ui.violet)
                    } else {
                        completed[step.id]=binding;stage="release";neutralSince=0
                        feedback.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                        feedback.text="✓ Распознано: ${step.title}. Отпустите и верните всё в исходное положение."
                        feedback.setTextColor(Ui.accent)
                    }
                } else if(stage=="release") {
                    if(state.neutral) {
                        if(neutralSince==0L)neutralSince=SystemClock.elapsedRealtime()
                        if(SystemClock.elapsedRealtime()-neutralSince>=450){index++;stage=if(index>=steps.size)"complete" else "ready";neutralSince=0;beforeStep=""}
                    } else neutralSince=0
                }
                render()
            }
            handler.postDelayed(this,100)
        }
    }
    private fun move(delta:Int) {
        safe { b ->
            b.learn(0,0)
            if(stage=="armed"&&beforeStep.isNotEmpty())b.replaceMapping(beforeStep)
            index=(index+delta).coerceIn(0,steps.size);stage=if(index==steps.size)"complete" else "ready";neutralSince=0;beforeStep=""
            feedback.text="Отпустите элементы перед следующим шагом."
        }
        render()
    }
    private fun render() {
        val complete=index>=steps.size
        title.text=if(complete)"Готово · ${completed.size} назначений" else steps[index].title
        instruction.text=if(complete)"Проверьте список ниже. Пропущенные шаги не считались обученными. После сохранения запустите мост на главном экране." else "Шаг ${index+1} из ${steps.size}\n${steps[index].instruction}"
        progress.progress=index
        back.isEnabled=index>0;back.alpha=if(back.isEnabled)1f else 0.4f
        skip.visibility=if(complete)android.view.View.GONE else android.view.View.VISIBLE
        skip.isEnabled=stage!="release"
        save.visibility=if(complete)android.view.View.VISIBLE else android.view.View.GONE
        save.isEnabled=stage=="complete"&&completed.isNotEmpty()
        summary.text=steps.mapIndexed { i,s -> "${if(completed.containsKey(s.id))"✓" else if(i<index)"—" else "○"} ${s.title}" }.joinToString("\n")
    }
    private fun save() {
        safe { b ->
            b.learn(0,0);val mapping=b.mappings()
            val missing=completed.keys.filter { id -> CalibrationPlan.binding(mapping,steps.first { it.id==id })!=completed[id] }
            if(missing.isNotEmpty()){feedback.text="Некоторые назначения изменились. Вернитесь к шагам: "+missing.joinToString();return@safe}
            check(prefs.edit().putString("mapping",mapping).commit()) { "Не удалось сохранить раскладку. Повторите попытку." }
            setResult(RESULT_OK);close();finish()
        }
    }
    private fun close() {
        if(closed)return
        closed=true;handler.removeCallbacks(tick)
        try { BridgeConnection.bridge?.let { it.learn(0,0);it.stop();BridgeConnection.lastState=RuntimeState.parse(it.state()) } }catch(_:Exception){}
        stopService(Intent(this,BridgeKeeper::class.java))
    }
    private fun discard(){AlertDialog.Builder(this).setTitle("Отменить настройку?").setMessage("Сохранённая раскладка останется прежней.").setPositiveButton("Отменить") { _, _ -> close();finish() }.setNegativeButton("Продолжить",null).show()}
    override fun onStart(){super.onStart();visible=true;handler.post(tick)}
    override fun onStop() {
        visible=false;handler.removeCallbacks(tick)
        try { BridgeConnection.bridge?.let { b ->
            b.learn(0,0)
            if(stage=="armed"&&index<steps.size) {
                // PS may open system UI before the normal polling tick sees the accepted event.
                val current=RuntimeState.parse(b.state())
                val binding=if(current.learned>revision)CalibrationPlan.binding(b.mappings(),steps[index]) else null
                if(binding!=null&&completed.none { it.key!=steps[index].id&&it.value==binding }) {
                    completed[steps[index].id]=binding;stage="release"
                } else {if(beforeStep.isNotEmpty())b.replaceMapping(beforeStep);stage="ready"}
            }
        } }catch(_:Exception){}
        neutralSince=0;super.onStop()
    }
    override fun onDestroy(){if(isFinishing)close();super.onDestroy()}
    override fun onSaveInstanceState(out:Bundle){out.putInt("index",index);out.putStringArrayList("ids",ArrayList(completed.keys));out.putStringArrayList("bindings",ArrayList(completed.values));super.onSaveInstanceState(out)}
    @Deprecated("Activity API") override fun onBackPressed(){discard()}
    override fun dispatchKeyEvent(e:KeyEvent):Boolean {
        val device=e.device
        if(e.isFromSource(InputDevice.SOURCE_GAMEPAD)||e.isFromSource(InputDevice.SOURCE_JOYSTICK)||(device?.vendorId==0x054c&&device.productId==0x0ce6))return true
        return super.dispatchKeyEvent(e)
    }
}
