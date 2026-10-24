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
    private val dead = Shizuku.OnBinderDeadListener { handler.post { bridge = null; BridgeConnection.bound = false; deviceState.text="○  DualSense · состояние неизвестно";virtualState.text="○  Виртуальный контроллер · связь потеряна";status.text = "Shizuku остановлен. Мост освободит устройство по watchdog." } }
    private val permission = Shizuku.OnRequestPermissionResultListener { _, result ->
        if (result == PackageManager.PERMISSION_GRANTED) bind() else status.text = "Shizuku: доступ отклонён"
    }
    private val ticker = object : Runnable {
        override fun run() {
            if (!visible) return
            val b = bridge
            if (b != null) {
                try {
                    b.heartbeat()
                    // Keep action feedback visible; raw shell details remain in diagnostics.
                    latest = b.snapshot()
                    log.text = b.info() + "\n" + latest
                    val physical=latest.lineSequence().firstOrNull { it.startsWith("DualSense:") }?.substringAfter(": ")
                    deviceState.text=if(physical!=null&&physical!="Disconnected") "●  DualSense · подключён" else "○  DualSense · ожидает подключения"
                    val active=latest.startsWith("Bridge active")||latest.lineSequence().firstOrNull()?.contains("virtual=ready")==true
                    virtualState.text=if(active) "●  Виртуальный контроллер · запущен" else "○  Виртуальный контроллер · не запущен"
                    deviceState.setTextColor(if(physical!=null&&physical!="Disconnected")Ui.accent else Ui.muted)
                    virtualState.setTextColor(if(active)Ui.accent else Ui.muted)
                } catch (e: Exception) { status.text = e.toString(); bridge = null }
            }
            handler.postDelayed(this, 400)
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
        content.addView(status);Ui.gap(content,14)
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
        Ui.button(hero,"Подключить Shizuku",true) { authorize() }
        val controls=Ui.card(home)
        Ui.title(controls,"Запуск моста","Сначала обучите кнопки во вкладке «Кнопки». После запуска можно перейти в игру.")
        controls.addView(Ui.text(this,"ПРОФИЛЬ КОНТРОЛЛЕРА",11f,Ui.muted));Ui.gap(controls,8)
        profile=Ui.spinner(controls,arrayOf("Xbox · рекомендуется","DualSense"))
        capture=CheckBox(this).apply { text="Разрешить захват DualSense";setTextColor(Ui.ink);textSize=14f;buttonTintList=android.content.res.ColorStateList.valueOf(Ui.accent);minHeight=Ui.dp(context,48) };controls.addView(capture)
        controls.addView(Ui.text(this,"Захват исключает двойной ввод. Stop вернёт контроллер в исходный режим.",12f,Ui.muted));Ui.gap(controls,16)
        Ui.button(controls,"Запустить мост",true) {
            if (!capture.isChecked) { status.text="Разрешите захват DualSense или откройте диагностику без захвата." }
            else AlertDialog.Builder(this).setTitle("Запустить мост?").setMessage("Физический ввод DualSense будет перенаправлен в виртуальный контроллер. Нажмите Stop в приложении или уведомлении, чтобы освободить его. При отказе EVIOCGRAB останется только диагностика.")
                .setPositiveButton("Запустить") { _, _ -> start(false) }.setNegativeButton("Отмена",null).show()
        }
        Ui.button(controls,"Остановить мост",danger=true) { remote { it.stop() };stopService(Intent(this,BridgeKeeper::class.java)) }
        val learn=Ui.card(training)
        Ui.title(learn,"Знакомство с DualSense","01  Включите диагностику\n02  Выберите элемент и нажмите «Обучить»\n03  Нажмите кнопку или подвигайте только выбранную ось")
        Ui.button(learn,"Начать диагностику без захвата",true) { start(true) }
        val labels=arrayOf("✕  Cross / A","○  Circle / B","□  Square / X","△  Triangle / Y","L1","R1","L2 · кнопка","R2 · кнопка","Share","Options","PS","L3","R3","D-pad · вверх","D-pad · вниз","D-pad · влево","D-pad · вправо","Левый стик · X","Левый стик · Y","Правый стик · X","Правый стик · Y","L2 · ось","R2 · ось","D-pad · ось X","D-pad · ось Y")
        targets=Ui.spinner(learn,labels)
        Ui.button(learn,"Обучить выбранный элемент") {
            val pos=targets.selectedItemPosition
            remote { it.learn(if(pos<17)1 else 3,if(pos<17)pos else intArrayOf(0,1,3,4,2,5,16,17)[pos-17]);status.text="Ожидаю ввод: ${labels[pos]}. После обучения получите и сохраните карту." }
        }
        learn.addView(Ui.text(this,"Перед обучением оси верните стик в центр или отпустите триггер. Остальные элементы не трогайте.",13f,Ui.muted))
        val saved=Ui.card(training)
        Ui.title(saved,"Ваша раскладка","Получите выученные назначения и сохраните их. Карта применяется при следующем запуске моста.")
        map=EditText(this).apply {
            setText(getPreferences(0).getString("mapping","k 306 1\na 0 0\na 1 1\na 2 3\na 5 4\na 3 2\na 4 5\na 16 16\na 17 17\n"))
            minLines=4;maxLines=8;gravity=android.view.Gravity.TOP;setTextColor(Ui.ink);textSize=13f;typeface=android.graphics.Typeface.MONOSPACE
            background=Ui.shape(Ui.bg);setPadding(Ui.dp(context,14),Ui.dp(context,12),Ui.dp(context,14),Ui.dp(context,12));visibility=android.view.View.GONE
        }
        Ui.button(saved,"Получить выученную карту") { remote { map.setText(it.mappings());status.text="Карта получена. Нажмите «Сохранить раскладку»." } }
        Ui.button(saved,"Сохранить раскладку",true) { getPreferences(0).edit().putString("mapping",map.text.toString()).apply();status.text="Раскладка сохранена. Применится при следующем запуске." }
        Ui.button(saved,"Редактировать коды вручную") { map.visibility=if(map.visibility==android.view.View.VISIBLE)android.view.View.GONE else android.view.View.VISIBLE }
        saved.addView(map)
        val tests=Ui.card(diagnostics)
        Ui.title(tests,"Проверка ввода","Смотрите, какие кнопки и оси получает Android от виртуального контроллера.")
        Ui.button(tests,"Открыть Input Tester",true) { startActivity(Intent(this,InputTestActivity::class.java)) }
        Ui.button(tests,"Отправить тестовое нажатие ✕ / A") { remote { it.testButton() } }
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
        select(0)
        Shizuku.addBinderReceivedListenerSticky(received)
        Shizuku.addBinderDeadListener(dead)
        Shizuku.addRequestPermissionResultListener(permission)
        updateStatus()
    }
    private fun remote(action: (IBridge) -> Unit) {
        val b = bridge ?: run { status.text = "Сначала подключите Shizuku"; return }
        try { action(b) } catch (e: Exception) { status.text = e.toString() }
    }
    private fun updateStatus() {
        try {
            status.text = if (Shizuku.pingBinder()) if(bridge!=null) "Shizuku подключён · можно начать диагностику" else "Shizuku готов · нажмите «Подключить Shizuku»"
            else {
                val installed = try { packageManager.getPackageInfo("moe.shizuku.privileged.api", 0); true } catch (_: PackageManager.NameNotFoundException) { false }
                if(installed) "Shizuku установлен, служба не запущена. Запустите через ADB." else "Установите Shizuku v13+ и запустите через ADB."
            }
        } catch(e: Exception) { status.text=e.toString() }
    }
    private fun authorize() {
        try {
            if (!Shizuku.pingBinder()) { updateStatus(); return }
            if (Shizuku.getVersion() < 13) { status.text = "Требуется Shizuku v13+"; return }
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) bind() else Shizuku.requestPermission(1)
        } catch (e: Exception) { status.text = e.toString() }
    }
    private fun bind() { try { BridgeConnection.bind(applicationContext);handler.postDelayed({ updateStatus() },600) } catch (e: Exception) { status.text = e.toString() } }
    private fun start(diagnostic: Boolean) {
        remote {
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 9)
            it.start(profile.selectedItemPosition, !diagnostic && capture.isChecked, diagnostic, map.text.toString())
            try { startForegroundService(Intent(this, BridgeKeeper::class.java)) }
            catch(e: Exception) { it.stop(); throw e }
        }
    }
    override fun onStart() { super.onStart(); visible = true; handler.post(ticker) }
    override fun onStop() { visible = false; handler.removeCallbacks(ticker); super.onStop() }
    override fun onDestroy() {
        if (!BridgeConnection.keeping) BridgeConnection.close(applicationContext)
        Shizuku.removeBinderReceivedListener(received); Shizuku.removeBinderDeadListener(dead); Shizuku.removeRequestPermissionResultListener(permission)
        io.shutdownNow(); super.onDestroy()
    }
    @Deprecated("Activity API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if (requestCode==42 && resultCode==RESULT_OK) data?.data?.let { uri ->
            try { contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write("DualSense Fix\n$latest\nMapping:\n${map.text}\nInput Report:\n$report\nAndroid events:\n${BridgeConnection.androidEvents}") } } catch(e: Exception) { status.text=e.toString() }
        }
    }
}
