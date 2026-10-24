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
    private lateinit var log: TextView
    private lateinit var profile: Spinner
    private lateinit var targets: Spinner
    private lateinit var capture: CheckBox
    private lateinit var map: EditText
    private var latest = ""
    private var report = ""
    private val received = Shizuku.OnBinderReceivedListener { handler.post { updateStatus() } }
    private val dead = Shizuku.OnBinderDeadListener { handler.post { bridge = null; BridgeConnection.bound = false; status.text = "Shizuku остановлен. Мост освободит устройство по watchdog." } }
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
                    status.text = "Shizuku: " + b.info()
                    latest = b.snapshot()
                    log.text = latest
                } catch (e: Exception) { status.text = e.toString(); bridge = null }
            }
            handler.postDelayed(this, 400)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 12, 20, 12) }
        val scroll = ScrollView(this).apply { addView(box) }; setContentView(scroll)
        fun text(s: String) = TextView(this).also { it.text = s; box.addView(it) }
        fun button(s: String, action: () -> Unit) { box.addView(Button(this).apply { text = s; setOnClickListener { action() } }) }
        text("DualSense Fix · shell/uinput · без root")
        status = text("Shizuku: проверка…")
        text("Первый запуск: Test Inputs → обучить кнопки/оси → сохранить. Оси из ТЗ — предварительный профиль. Мост продолжает работу в PPSSPP с уведомлением Stop.")
        button("Подключить Shizuku") { authorize() }
        profile = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, arrayOf("Xbox (по умолчанию)", "DualSense")) }; box.addView(profile)
        capture = CheckBox(this).apply { text = "Разрешить эксклюзивный EVIOCGRAB только DualSense" }; box.addView(capture)
        button("Start Bridge") {
            if (!capture.isChecked) { status.text = "Для перенаправления явно разрешите EVIOCGRAB. Test Inputs работает без захвата." }
            else AlertDialog.Builder(this).setTitle("Захватить DualSense?").setMessage("Физический ввод будет скрыт до Stop, закрытия задачи приложения или потери heartbeat. При отказе захвата останется только диагностика.")
                .setPositiveButton("Start") { _, _ -> start(false) }.setNegativeButton("Отмена", null).show()
        }
        button("Stop Bridge") { remote { it.stop() }; stopService(Intent(this, BridgeKeeper::class.java)) }
        button("Test Inputs — диагностика без захвата") { start(true) }
        text("Обучение: выберите элемент, нажмите Learn и двигайте только его. Для стика сначала верните его в центр; для триггера отпустите. Для D-pad используйте ось HAT либо обучите кнопки отдельно (см. README).")
        val labels = arrayOf("A / Cross", "B / Circle", "X / Square", "Y / Triangle", "L1", "R1", "L2 button", "R2 button", "Share", "Options", "PS", "L3", "R3", "D-pad Up (button)", "D-pad Down (button)", "D-pad Left (button)", "D-pad Right (button)", "Left X", "Left Y", "Right X", "Right Y", "L2 axis", "R2 axis", "Hat X", "Hat Y")
        targets = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, labels) }; box.addView(targets)
        button("Learn выбранный элемент (в диагностике)") {
            val pos = targets.selectedItemPosition
            remote { it.learn(if (pos < 17) 1 else 3, if (pos < 17) pos else intArrayOf(0,1,3,4,2,5,16,17)[pos-17]) }
        }
        button("Получить выученную карту") { remote { map.setText(it.mappings()) } }
        map = EditText(this).apply {
            setText(getPreferences(0).getString("mapping", "k 306 1\na 0 0\na 1 1\na 2 3\na 5 4\na 3 2\na 4 5\na 16 16\na 17 17\n"))
            minLines = 4; maxLines = 8; gravity = android.view.Gravity.TOP
        }; box.addView(map)
        button("Сохранить карту") { getPreferences(0).edit().putString("mapping", map.text.toString()).apply(); status.text = "Карта сохранена; применяется при следующем Start" }
        button("Отправить тест A/Cross в виртуальный геймпад") { remote { it.testButton() } }
        button("Android Input Tester — проверка полученных событий") { startActivity(Intent(this, InputTestActivity::class.java)) }
        button("Input Report: dumpsys input") {
            val b = bridge ?: return@button
            io.execute { try { report = b.inputReport(); handler.post { AlertDialog.Builder(this).setTitle("dumpsys input").setMessage(report).setPositiveButton("OK", null).show() } } catch (e: Exception) { handler.post { status.text = e.toString() } } }
        }
        button("Экспорт диагностического лога") { startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply { type="text/plain"; addCategory(Intent.CATEGORY_OPENABLE); putExtra(Intent.EXTRA_TITLE,"dualsense-fix.txt") }, 42) }
        log = text(""); log.setTextIsSelectable(true)
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
            status.text = if (Shizuku.pingBinder()) "Shizuku доступен; " + (bridge?.info() ?: "нажмите Подключить")
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
    private fun bind() { try { BridgeConnection.bind(applicationContext) } catch (e: Exception) { status.text = e.toString() } }
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
