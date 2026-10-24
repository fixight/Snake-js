package dev.dualsense.fix

/** Versioned service fields, independent of localized UI and diagnostic log contents. */
data class RuntimeState(
    val phase: String = "stopped", val physical: String = "Disconnected",
    val virtual: Boolean = false, val grabbed: Boolean = false,
    val diagnostic: Boolean = false, val error: String = "",
    val learning: Int = 0, val learned: Int = 0, val tested: Int = 0
) {
    val busy get() = phase in setOf("starting", "waiting", "diagnostics", "running")
    val working get() = phase == "running" && virtual && grabbed && physical != "Disconnected"
    val title get() = when {
        working -> "Мост работает"
        phase == "starting" -> "Запускаем мост…"
        phase == "waiting" -> if(diagnostic) "Диагностика · ждём DualSense" else "Ожидаем DualSense"
        phase == "diagnostics" -> "Диагностика · без перехвата"
        phase == "error" -> "Мост остановлен с ошибкой"
        phase == "lost" -> "Связь с мостом потеряна"
        phase == "running" -> "Состояние моста не подтверждено"
        else -> "Мост остановлен"
    }
    val detail get() = when {
        working -> "Ввод перенаправляется. Можно открыть игру."
        phase == "starting" -> "Проверяем раскладку и создаём виртуальное устройство."
        phase == "waiting" -> if(virtual) "Виртуальный контроллер готов, физического ввода пока нет." else "Подключите DualSense по Bluetooth. Перехват выключен."
        phase == "diagnostics" -> "События только читаются. Физический контроллер не захвачен."
        phase == "lost" -> "Работу подтвердить нельзя. Watchdog освободит устройство при потере heartbeat."
        phase == "error" -> "Откройте журнал для подробностей. Устройство освобождено."
        else -> "Перенаправление выключено. Физический контроллер свободен."
    }
    companion object {
        fun parse(raw: String): RuntimeState {
            val f=raw.lineSequence().filter { '=' in it }.associate { it.substringBefore('=') to it.substringAfter('=') }
            val phase=f["phase"]?.takeIf { it in setOf("stopped","starting","waiting","diagnostics","running","error","lost") } ?: "lost"
            return RuntimeState(phase,f["physical"] ?: "Disconnected",f["virtual"]=="1",f["grabbed"]=="1",f["diagnostic"]=="1",f["error"] ?: "",f["learning"]?.toIntOrNull() ?: 0,f["learned"]?.toIntOrNull() ?: 0,f["tested"]?.toIntOrNull() ?: 0)
        }
    }
}
