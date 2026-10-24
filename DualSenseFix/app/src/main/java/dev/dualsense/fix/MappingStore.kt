package dev.dualsense.fix

import android.content.Context

object MappingStore {
    const val DEFAULT="k 306 1\na 0 0\na 1 1\na 2 3\na 5 4\na 3 2\na 4 5\na 16 16\na 17 17\n"
    private fun prefs(c:Context)=c.getSharedPreferences("MainActivity",0)
    fun load(c:Context)=prefs(c).getString("mapping",DEFAULT) ?: DEFAULT
    fun backup(c:Context,raw:String) {
        val p=prefs(c)
        if(p.all.any { it.key.startsWith("mapping_backup_")&&it.value==raw })return
        check(p.edit().putString("mapping_backup_${System.currentTimeMillis()}_${System.nanoTime()}",raw).commit()) { "Не удалось сохранить резервную копию раскладки" }
    }
    fun remember(c:Context,canonical:String) {
        val p=prefs(c);val old=p.getString("mapping_last_valid",null)
        val edit=p.edit().putString("mapping_last_valid",canonical)
        if(old!=null&&old!=canonical)edit.putString("mapping_previous_valid",old)
        check(edit.commit()) { "Не удалось сохранить проверенную раскладку" }
    }
    /** Call only with the native validator's canonical result. Archive before replacing anything. */
    fun save(c:Context,canonical:String,editorRaw:String) {
        backup(c,editorRaw)
        val old=load(c);if(old!=canonical)backup(c,old)
        check(prefs(c).edit().putString("mapping",canonical).commit()) { "Не удалось сохранить раскладку" }
        remember(c,canonical)
    }
    fun previous(c:Context):String? {
        val p=prefs(c)
        return p.getString("mapping_previous_valid",null) ?: p.getString("mapping_last_valid",null)
    }
    fun diagnostic(c:Context):String=prefs(c).all.filterKeys { it=="mapping"||it.startsWith("mapping_backup_")||it.endsWith("_valid") }.entries.joinToString("\n\n") { "${it.key}:\n${it.value}" }
}
