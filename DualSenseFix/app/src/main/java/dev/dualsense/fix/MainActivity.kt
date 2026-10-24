package dev.dualsense.fix

import android.app.Activity
import android.app.AlertDialog
import android.os.*
import android.content.*
import android.content.pm.PackageManager
import android.view.WindowManager
import android.widget.*
import rikka.shizuku.Shizuku
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private val control = Executors.newSingleThreadExecutor()
    private var connectionWasReady = false
    private var renderedPhase = "stopped"
    private var renderedConnectionError = ""
    private var operation = ""
    private var permissionPending = false
    private var permissionDenied = false
    private var lastLearned = 0
    private var lastTested = 0
    private lateinit var shizukuState: TextView
    private lateinit var bridgeState: TextView
    private lateinit var bridgeDetail: TextView
    private lateinit var errorState: TextView
    private lateinit var recoveryButton: Button
    private lateinit var progress: ProgressBar
    private lateinit var connectButton: Button
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var diagnosticButton: Button
    private lateinit var learnButton: Button
    private lateinit var testButton: Button
    private val io = Executors.newSingleThreadExecutor()
    private var bridge: IBridge?
        get() = BridgeConnection.bridge
        set(value) { BridgeConnection.bridge = value }
    private var visible = false
    private lateinit var status: TextView
    private lateinit var deviceState: TextView
    private lateinit var virtualState: TextView
    private lateinit var log: TextView
    private lateinit var profile: Spinner
    private lateinit var targets: Spinner
    private lateinit var capture: CheckBox
    private lateinit var map: EditText
    private var latest = ""
    private var report = ""
    private val received = Shizuku.OnBinderReceivedListener { handler.post { updateStatus() } }
    private val dead = Shizuku.OnBinderDeadListener { handler.post {
        BridgeConnection.lost("Shizuku остановлен. Проверьте службу и подключитесь заново.")
        permissionPending=false;notice("Связь с Shizuku потеряна",true);renderState()
    } }
    private val permission = Shizuku.OnRequestPermissionResultListener { _, result -> handler.post {
        permissionPending=false;permissionDenied=result!=PackageManager.PERMISSION_GRANTED
        if(!permissionDenied)bind() else { notice("Доступ Shizuku отклонён. Разрешите его в Shizuku и повторите.",true);renderState() }
    } }
    private val ticker = object : Runnable {
        override fun run() {
            if (!visible) return
            val b=bridge
            if(b!=null)try {
                b.heartbeat()
                latest=b.snapshot()
                val state=RuntimeState.parse(b.state());BridgeConnection.lastState=state
                log.text=b.info()+"\n"+latest
                if(state.learned>lastLearned)notice("Назначение распознано. Получите карту и сохраните раскладку.")
                if(state.tested>lastTested)notice("Тестовое нажатие отправлено в uinput. Проверьте получение в Input Tester.")
                lastLearned=state.learned;lastTested=state.tested
            }catch(e:Exception){BridgeConnection.lost(e.message ?: "Shell-служба недоступна")}
            renderState()
            handler.postDelayed(this,400)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val root = Ui.column(this).apply { fitsSystemWindows=true;setPadding(Ui.dp(context,20),Ui.dp(context,12),Ui.dp(context,20),0) }
        setContentView(root)
        val brand=Ui.text(this,"DUALSENSE  /  FIX",13f,Ui.accent).apply { letterSpacing=0.16f; typeface=android.graphics.Typeface.DEFAULT_BOLD }
        root.addView(brand);Ui.gap(root,8)
        root.addView(Ui.text(this,"Играйте по своим правилам.",24f))
        root.addView(Ui.text(this,"Правильная раскладка. Без root.",14f,Ui.muted));Ui.gap(root,14)
        val navigation=LinearLayout(this).apply { background=Ui.shape(Ui.surface);setPadding(4,4,4,4) };root.addView(navigation)
        val tabs=mutableListOf<Button>()
        val pages=mutableListOf<LinearLayout>()
        val scroll=ScrollView(this).apply { isFillViewport=true;clipToPadding=false;setPadding(0,Ui.dp(context,18),0,Ui.dp(context,24)) }
        root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        val content=Ui.column(this);scroll.addView(content)
        status=Ui.text(this,"Подключите Shizuku, чтобы начать.",13f,Ui.muted)
        status.accessibilityLiveRegion=android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE
        val health=Ui.card(content)
        bridgeState=Ui.text(this,"Мост остановлен",22f);health.addView(bridgeState);Ui.gap(health,6)
        bridgeState.accessibilityLiveRegion=android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE
        bridgeDetail=Ui.text(this,"",13f,Ui.muted);health.addView(bridgeDetail);Ui.gap(health,12)
        shizukuState=Ui.text(this,"Проверяем Shizuku…",14f,Ui.violet);shizukuState.accessibilityLiveRegion=android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE;health.addView(shizukuState)
        progress=ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply { isIndeterminate=true;visibility=android.view.View.GONE };health.addView(progress,LinearLayout.LayoutParams(-1,Ui.dp(this,6)))
        errorState=Ui.text(this,"",13f,android.graphics.Color.rgb(255,174,184));health.addView(errorState)
        health.addView(status);Ui.gap(health,6)
        recoveryButton=Ui.button(health,"Восстановить раскладку") { recoverMapping() }
        repeat(3) { pages.add(Ui.column(this).also { content.addView(it) }) }
        fun select(index:Int) {
            pages.forEachIndexed { i,v -> v.visibility=if(i==index)android.view.View.VISIBLE else android.view.View.GONE }
            tabs.forEachIndexed { i,b -> b.background=Ui.shape(if(i==index)Ui.accent else Ui.surface);b.setTextColor(if(i==index)Ui.bg else Ui.muted) }
            scroll.scrollTo(0,0)
        }
        arrayOf("Мост","Кнопки","Журнал").forEachIndexed { i,label ->
            tabs.add(Button(this).apply { text=label;isAllCaps=false;textSize=14f;minWidth=0;minimumWidth=0;minHeight=Ui.dp(context,48);setPadding(0,0,0,0);setOnClickListener { select(i) };navigation.addView(this,LinearLayout.LayoutParams(0,-2,1f)) })
        }
        val home=pages[0];val training=pages[1];val diagnostics=pages[2]
        val hero=Ui.card(home)
        hero.addView(ControllerArt(this),LinearLayout.LayoutParams(-1,Ui.dp(this,122)))
        Ui.title(hero,"Ваш контроллер","Подключите DualSense по Bluetooth, затем разрешите доступ через Shizuku.")
        deviceState=Ui.text(this,"○  DualSense · ожидает подключения",14f,Ui.muted);hero.addView(deviceState);Ui.gap(hero,8)
        virtualState=Ui.text(this,"○  Виртуальный контроллер · не запущен",14f,Ui.muted);hero.addView(virtualState);Ui.gap(hero,18)
        connectButton=Ui.button(hero,"Подключить Shizuku",true) { authorize() }
        val controls=Ui.card(home)
        Ui.title(controls,"Запуск моста","Сначала обучите кнопки во вкладке «Кнопки». После запуска можно перейти в игру.")
        controls.addView(Ui.text(this,"ПРОФИЛЬ КОНТРОЛЛЕРА",11f,Ui.muted));Ui.gap(controls,8)
        profile=Ui.spinner(controls,arrayOf("Xbox · рекомендуется","DualSense"))
        capture=CheckBox(this).apply { text="Разрешить захват DualSense";setTextColor(Ui.ink);textSize=14f;buttonTintList=android.content.res.ColorStateList.valueOf(Ui.accent);minHeight=Ui.dp(context,48) };controls.addView(capture)
        controls.addView(Ui.text(this,"Захват исключает двойной ввод. Stop вернёт контроллер в исходный режим.",12f,Ui.muted));Ui.gap(controls,16)
        startButton=Ui.button(controls,"Запустить мост",true) {
            if (!capture.isChecked) { status.text="Разрешите захват DualSense или откройте диагностику без захвата." }
            else AlertDialog.Builder(this).setTitle("Запустить мост?").setMessage("Физический ввод DualSense будет перенаправлен в виртуальный контроллер. Нажмите Stop в приложении или уведомлении, чтобы освободить его. При отказе EVIOCGRAB останется только диагностика.")
                .setPositiveButton("Запустить") { _, _ -> start(false) }.setNegativeButton("Отмена",null).show()
        }
        stopButton=Ui.button(controls,"Остановить мост",danger=true) { stopBridge() }
        val guided=Ui.card(training)
        Ui.title(guided,"Пошаговая настройка","Приложение покажет нужную кнопку, дождётся нажатия и отпускания, затем перейдёт дальше. Сохранение — в конце.")
        Ui.button(guided,"Настроить только D-pad",true) { openCalibration("dpad") }
        Ui.button(guided,"Пройти кнопки и триггеры") { openCalibration("buttons") }
        Ui.button(guided,"Полная настройка: кнопки + стики") { openCalibration("full") }
        guided.addView(Ui.text(this,"Настройка D-pad не меняет стики и остальные кнопки. Во время мастера мост останавливается, захват отключён.",13f,Ui.muted))
        val learn=Ui.card(training)
        Ui.title(learn,"Знакомство с DualSense","01  Включите диагностику\n02  Выберите элемент и нажмите «Обучить»\n03  Нажмите кнопку или подвигайте только выбранную ось")
        diagnosticButton=Ui.button(learn,"Начать диагностику без захвата",true) { start(true) }
        val labels=arrayOf("✕  Cross / A","○  Circle / B","□  Square / X","△  Triangle / Y","L1","R1","L2 · кнопка","R2 · кнопка","Share","Options","PS","L3","R3","D-pad · вверх","D-pad · вниз","D-pad · влево","D-pad · вправо","Левый стик · X","Левый стик · Y","Правый стик · X","Правый стик · Y","L2 · ось","R2 · ось","D-pad · ось X","D-pad · ось Y")
        targets=Ui.spinner(learn,labels)
        learnButton=Ui.button(learn,"Обучить выбранный элемент") {
            val pos=targets.selectedItemPosition
            remote { it.learn(if(pos in 13..16)4 else if(pos<17)1 else 3,if(pos<17)pos else intArrayOf(0,1,3,4,2,5,16,17)[pos-17]);notice("Ожидаю ввод: ${labels[pos]}. Нажмите кнопку или переместите ось.") }
        }
        learn.addView(Ui.text(this,"Перед обучением оси верните стик в центр или отпустите триггер. Остальные элементы не трогайте.",13f,Ui.muted))
        val saved=Ui.card(training)
        Ui.title(saved,"Ваша раскладка","Получите выученные назначения и сохраните их. Карта применяется при следующем запуске моста.")
        map=EditText(this).apply {
            inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setSingleLine(false);setHorizontallyScrolling(false)
            importantForAutofill=android.view.View.IMPORTANT_FOR_AUTOFILL_NO
            setText(MappingStore.load(this@MainActivity))
            minLines=4;maxLines=8;gravity=android.view.Gravity.TOP;setTextColor(Ui.ink);textSize=13f;typeface=android.graphics.Typeface.MONOSPACE
            background=Ui.shape(Ui.bg);setPadding(Ui.dp(context,14),Ui.dp(context,12),Ui.dp(context,14),Ui.dp(context,12));visibility=android.view.View.GONE
        }
        Ui.button(saved,"Получить выученную карту") { remote { map.setText(it.mappings());notice("Карта получена. Нажмите «Сохранить раскладку».") } }
        Ui.button(saved,"Сохранить раскладку",true) { saveEditorMapping() }
        Ui.button(saved,"Проверить и исправить формат") { formatMapping() }
        Ui.button(saved,"Восстановление раскладки") { recoverMapping() }
        Ui.button(saved,"Редактировать коды вручную") { map.visibility=if(map.visibility==android.view.View.VISIBLE)android.view.View.GONE else android.view.View.VISIBLE }
        saved.addView(map)
        val tests=Ui.card(diagnostics)
        Ui.title(tests,"Проверка ввода","Смотрите, какие кнопки и оси получает Android от виртуального контроллера.")
        Ui.button(tests,"Открыть Input Tester",true) { startActivity(Intent(this,InputTestActivity::class.java)) }
        testButton=Ui.button(tests,"Отправить тестовое нажатие ✕ / A") { remote { it.testButton() } }
        Ui.button(tests,"Системный отчёт Android") {
            val b=bridge
            if(b==null)status.text="Сначала подключите Shizuku" else io.execute {
                try { report=b.inputReport();handler.post { if(!isFinishing&&!isDestroyed)AlertDialog.Builder(this).setTitle("dumpsys input").setMessage(report).setPositiveButton("Закрыть",null).show() } }
                catch(e:Exception){handler.post { status.text=e.toString() }}
            }
        }
        Ui.button(tests,"Экспортировать журнал") { startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply { type="text/plain";addCategory(Intent.CATEGORY_OPENABLE);putExtra(Intent.EXTRA_TITLE,"dualsense-fix.txt") },42) }
        val journal=Ui.card(diagnostics)
        Ui.title(journal,"Журнал событий","Сырые события, состояния устройств и ошибки доступа.")
        log=Ui.text(this,"Событий пока нет. Подключите Shizuku и включите диагностику.",12f,Ui.muted).apply { typeface=android.graphics.Typeface.MONOSPACE;setTextIsSelectable(true) };journal.addView(log)
        capture.setOnCheckedChangeListener { _, _ -> renderState() }
        select(0)
        Shizuku.addBinderReceivedListenerSticky(received)
        Shizuku.addBinderDeadListener(dead)
        Shizuku.addRequestPermissionResultListener(permission)
        updateStatus()
    }
    private fun saveEditorMapping() {
        remote { b ->
            val raw=map.text.toString()
            if(raw.isBlank()){notice("Карта пуста. Для базового профиля используйте «Восстановление раскладки».",true);return@remote}
            try {
                val canonical=b.validateMapping(raw)
                MappingStore.save(this,canonical,raw);BridgeConnection.error="";map.setText(canonical)
                notice("Раскладка проверена и сохранена. Предыдущий текст оставлен в резервной копии.")
            }catch(e:Exception){notice(e.message ?: e.toString(),true)}
        }
    }
    private fun formatMapping() {
        remote { b ->
            val raw=map.text.toString()
            if(raw.isBlank()){notice("Карта пуста. Выберите базовый профиль через восстановление.",true);return@remote}
            try {
                MappingStore.backup(this,raw)
                val canonical=b.validateMapping(raw)
                map.setText(canonical)
                notice("Формат проверен. Числовые назначения сохранены. Нажмите «Сохранить раскладку».")
            }catch(e:Exception){notice(e.message ?: e.toString(),true);AlertDialog.Builder(this).setTitle("Не удалось исправить карту без догадок").setMessage((e.message ?: e.toString())+"\n\nИсходный текст сохранён в резервной копии и попадёт в экспорт журнала. Исправьте указанное место или выберите базовый профиль через восстановление.").setPositiveButton("Понятно",null).show()}
        }
    }
    private fun recoverMapping() {
        val previous=MappingStore.previous(this)
        val options=mutableListOf("Исправить формат, сохранив назначения")
        if(previous!=null)options.add("Вернуть предыдущую проверенную по формату карту")
        options.add("Вернуть базовый профиль с исправлением стиков")
        AlertDialog.Builder(this).setTitle("Восстановление раскладки").setItems(options.toTypedArray()) { _, index ->
            if(index==0){formatMapping();return@setItems}
            val base=index==options.lastIndex
            AlertDialog.Builder(this).setTitle(if(base)"Восстановить базовый профиль?" else "Вернуть сохранённую карту?")
                .setMessage(if(base)"Назначения кнопок потребуется обучить заново. Базовая перестановка осей стиков и триггеров останется. Текущий текст будет сохранён в резервной копии." else "Текущий текст будет сохранён в резервной копии. После восстановления потребуется запустить мост заново.")
                .setPositiveButton("Восстановить") { _, _ -> remote { b ->
                    try { val canonical=b.validateMapping(if(base)MappingStore.DEFAULT else previous!!);MappingStore.save(this,canonical,map.text.toString());BridgeConnection.error="";map.setText(canonical);notice("Раскладка восстановлена. Запустите мост заново.") }
                    catch(e:Exception){notice(e.message ?: e.toString(),true)}
                } }.setNegativeButton("Отмена",null).show()
        }.setNegativeButton("Закрыть",null).show()
    }
    private fun openCalibration(mode:String) {
        if(bridge==null){notice("Сначала подключите Shizuku",true);return}
        AlertDialog.Builder(this).setTitle("Открыть мастер?").setMessage("Текущий мост будет остановлен. Мастер читает физические события без захвата. Отпускайте каждую кнопку перед следующим шагом.")
            .setPositiveButton("Начать") { _, _ -> startActivityForResult(Intent(this,CalibrationActivity::class.java).putExtra("mode",mode),43) }.setNegativeButton("Отмена",null).show()
    }
    private fun remote(action: (IBridge) -> Unit) {
        val b = bridge ?: run { status.text = "Сначала подключите Shizuku"; return }
        try { action(b) } catch (e: Exception) { status.text = e.toString() }
    }
    private fun notice(message:String, error:Boolean=false) {
        if(isDestroyed)return
        status.text=message
        status.setTextColor(if(error)android.graphics.Color.rgb(255,174,184) else Ui.accent)
        Toast.makeText(this,message,Toast.LENGTH_SHORT).show()
    }
    private fun updateStatus() { renderState() }
    private fun renderState() {
        val connected=bridge!=null
        val saved=BridgeConnection.lastState
        val state=if(connected&&BridgeConnection.error.isNotEmpty()&&!saved.busy)saved.copy(phase="error",error=BridgeConnection.error) else if(!connected) saved.copy(phase=if(saved.busy)"lost" else saved.phase,virtual=false,grabbed=false,physical="Disconnected") else saved
        var available=false
        var permitted=false
        try { available=Shizuku.pingBinder();permitted=available&&Shizuku.checkSelfPermission()==PackageManager.PERMISSION_GRANTED }catch(_:Exception){}
        val installed=if(available)true else try { packageManager.getPackageInfo("moe.shizuku.privileged.api",0);true }catch(_:Exception){false}
        shizukuState.text=when {
            connected -> "●  Shizuku подключён · shell подтверждён"
            BridgeConnection.connecting -> "◌  Shizuku · подключаем shell-службу…"
            permissionPending -> "◌  Shizuku · ожидаем ваше разрешение"
            BridgeConnection.error.isNotEmpty() -> "!  Shizuku · ошибка подключения"
            !available -> if(installed) "○  Shizuku установлен, но не запущен" else "○  Shizuku не установлен"
            permissionDenied||!permitted -> "○  Shizuku запущен · нужен доступ"
            else -> "○  Shizuku готов · подключите shell-службу"
        }
        shizukuState.setTextColor(if(connected)Ui.accent else Ui.violet)
        bridgeState.text=if(operation.isNotEmpty())operation else state.title
        bridgeState.setTextColor(if(state.working)Ui.accent else if(state.phase in setOf("error","lost"))android.graphics.Color.rgb(255,174,184) else Ui.ink)
        bridgeDetail.text=state.detail
        val error=listOf(BridgeConnection.error,state.error).filter { it.isNotEmpty() }.distinct().joinToString("\n")
        recoveryButton.visibility=if(error.contains("mapping",true)||error.contains("Строка")||error.contains("раскладк",true))android.view.View.VISIBLE else android.view.View.GONE
        errorState.text=error;errorState.visibility=if(error.isEmpty())android.view.View.GONE else android.view.View.VISIBLE
        deviceState.text=if(!connected) "○  DualSense · сначала подключите Shizuku" else if(state.physical!="Disconnected") "●  DualSense · подключён" else if(state.busy) "○  DualSense · поиск устройства…" else "○  DualSense · проверяется при запуске"
        virtualState.text=if(state.virtual) "●  Виртуальный контроллер · создан" else "○  Виртуальный контроллер · выключен"
        deviceState.setTextColor(if(connected&&state.physical!="Disconnected")Ui.accent else Ui.muted)
        virtualState.setTextColor(if(state.virtual)Ui.accent else Ui.muted)
        if(connected&&!connectionWasReady)notice("Shizuku подключён. Shell-доступ подтверждён.")
        connectionWasReady=connected
        if(state.phase!=renderedPhase&&operation.isEmpty())notice(if(state.phase=="error")"Причина указана выше. Для ошибки карты доступно восстановление раскладки." else state.title+". "+state.detail,state.phase in setOf("error","lost"))
        if(operation.isEmpty())renderedPhase=state.phase
        if(BridgeConnection.error.isNotEmpty()&&BridgeConnection.error!=renderedConnectionError)notice(BridgeConnection.error,true)
        renderedConnectionError=BridgeConnection.error
        val pending=operation.isNotEmpty()||BridgeConnection.connecting||permissionPending||state.phase=="starting"
        progress.visibility=if(pending||state.learning!=0)android.view.View.VISIBLE else android.view.View.GONE
        fun enabled(button:Button,value:Boolean){button.isEnabled=value;button.alpha=if(value)1f else 0.4f}
        enabled(connectButton,!connected&&!BridgeConnection.connecting&&!permissionPending)
        connectButton.text=if(connected)"Shizuku подключён ✓" else if(BridgeConnection.connecting)"Подключение…" else if(permissionPending)"Ожидание разрешения…" else "Подключить Shizuku"
        enabled(startButton,connected&&!pending&&!state.busy&&capture.isChecked)
        startButton.text=if(state.working)"Мост работает ✓" else if(state.phase=="waiting"&&!state.diagnostic)"Ожидание контроллера…" else "Запустить мост"
        enabled(stopButton,connected&&!pending&&state.busy)
        enabled(diagnosticButton,connected&&!pending&&!state.busy)
        diagnosticButton.text=if(state.busy&&state.diagnostic)"Диагностика запущена ✓" else "Начать диагностику без захвата"
        enabled(learnButton,connected&&state.phase=="diagnostics"&&!pending)
        learnButton.text=if(state.learning!=0)"Ожидаем движение или нажатие…" else "Обучить выбранный элемент"
        enabled(testButton,connected&&state.virtual&&!pending)
        profile.isEnabled=!state.busy&&!pending;capture.isEnabled=!state.busy&&!pending
    }
    private fun authorize() {
        try {
            if(!Shizuku.pingBinder()){notice("Запустите Shizuku через ADB, затем повторите подключение.",true);renderState();return}
            if(Shizuku.getVersion()<13){notice("Требуется Shizuku v13+",true);return}
            permissionDenied=false;BridgeConnection.error=""
            if(Shizuku.checkSelfPermission()==PackageManager.PERMISSION_GRANTED)bind() else {
                permissionPending=true;renderState();notice("Подтвердите разрешение в окне Shizuku.");Shizuku.requestPermission(1)
            }
        }catch(e:Exception){permissionPending=false;notice(e.message ?: e.toString(),true);renderState()}
    }
    private fun bind() { BridgeConnection.bind(applicationContext);renderState();if(BridgeConnection.error.isNotEmpty())notice(BridgeConnection.error,true) else notice("Подключаем shell-службу Shizuku…") }
    private fun start(diagnostic:Boolean) {
        val b=bridge ?: return
        val chosenProfile=profile.selectedItemPosition
        val grab=!diagnostic&&capture.isChecked
        val config=map.text.toString()
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),9)
        BridgeConnection.error=""
        operation=if(diagnostic)"Включаем диагностику…" else "Запускаем мост…";renderState()
        lastLearned=0;lastTested=0
        control.execute {
            try {
                val canonical=b.validateMapping(config)
                MappingStore.remember(applicationContext,canonical)
                b.start(chosenProfile,grab,diagnostic,canonical)
                handler.post { if(!isDestroyed&&map.text.toString()==config)map.setText(canonical) }
                val state=RuntimeState.parse(b.state());BridgeConnection.lastState=state
                if(state.busy)applicationContext.startForegroundService(Intent(applicationContext,BridgeKeeper::class.java))
            }catch(e:Exception){try{b.stop()}catch(_:Exception){};BridgeConnection.error="Не удалось запустить мост: ${e.message ?: e.toString()}";BridgeConnection.lastState=RuntimeState(phase="error",error=e.message ?: e.toString())}
            finally { handler.post { operation="";if(!isDestroyed)renderState() } }
        }
    }
    private fun stopBridge() {
        val b=bridge ?: return
        operation="Останавливаем мост…";renderState()
        control.execute {
            try { b.stop();BridgeConnection.lastState=RuntimeState.parse(b.state());applicationContext.stopService(Intent(applicationContext,BridgeKeeper::class.java));handler.post { notice("Мост остановлен. Контроллер освобождён.") } }
            catch(e:Exception){BridgeConnection.lost(e.message ?: e.toString());handler.post { notice("Не удалось подтвердить остановку. Проверьте связь с Shizuku.",true) }}
            finally { handler.post { operation="";if(!isDestroyed){renderState()} } }
        }
    }
    override fun onStart() { super.onStart(); visible = true; handler.post(ticker) }
    override fun onStop() { visible = false; handler.removeCallbacks(ticker); super.onStop() }
    override fun onDestroy() {
        if (!BridgeConnection.keeping && !isChangingConfigurations && operation.isEmpty()) BridgeConnection.close(applicationContext)
        Shizuku.removeBinderReceivedListener(received); Shizuku.removeBinderDeadListener(dead); Shizuku.removeRequestPermissionResultListener(permission)
        control.shutdown();io.shutdownNow(); super.onDestroy()
    }
    @Deprecated("Activity API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode==43) {
            try { bridge?.let { val state=RuntimeState.parse(it.state());lastLearned=state.learned;lastTested=state.tested } }catch(_:Exception){}
        }
        if(requestCode==43&&resultCode==RESULT_OK) {
            map.setText(getSharedPreferences("MainActivity",0).getString("mapping",map.text.toString()))
            notice("Настройка сохранена. Запустите мост, чтобы проверить её в игре.")
        }
        if (requestCode==42 && resultCode==RESULT_OK) data?.data?.let { uri ->
            try { contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write("DualSense Fix\n$latest\nMapping:\n${map.text}\nStored mappings / backups:\n${MappingStore.diagnostic(this)}\nInput Report:\n$report\nAndroid events:\n${BridgeConnection.androidEvents}") } } catch(e: Exception) { status.text=e.toString() }
        }
    }
}
