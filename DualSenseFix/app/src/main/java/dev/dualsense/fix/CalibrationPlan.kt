package dev.dualsense.fix

data class CalibrationStep(val id:String,val title:String,val instruction:String,val type:Int,val target:Int)
object CalibrationPlan {
    val dpad=listOf(
        CalibrationStep("up","↑  D-pad вверх","Нажмите вверх на крестовине и отпустите.",4,13),
        CalibrationStep("down","↓  D-pad вниз","Нажмите вниз на крестовине и отпустите.",4,14),
        CalibrationStep("left","←  D-pad влево","Нажмите влево на крестовине и отпустите.",4,15),
        CalibrationStep("right","→  D-pad вправо","Нажмите вправо на крестовине и отпустите.",4,16))
    private fun key(id:String,title:String,target:Int)=CalibrationStep(id,title,"Нажмите указанную кнопку один раз и отпустите.",1,target)
    private fun axis(id:String,title:String,target:Int)=CalibrationStep(id,title,"Из исходного положения отклоните только эту ось, затем верните обратно.",3,target)
    private val buttons=listOf(key("cross","✕  Cross",0),key("circle","○  Circle",1),key("square","□  Square",2),key("triangle","△  Triangle",3),key("l1","L1",4),key("r1","R1",5),axis("l2","L2 · нажмите триггер",2),axis("r2","R2 · нажмите триггер",5),key("share","Share / Create",8),key("options","Options",9),key("ps","PS",10),key("l3","L3 · нажмите левый стик",11),key("r3","R3 · нажмите правый стик",12))
    private val sticks=listOf(axis("lx","Левый стик · вправо",0),axis("ly","Левый стик · вниз",1),axis("rx","Правый стик · вправо",3),axis("ry","Правый стик · вниз",4))
    fun steps(mode:String)=when(mode){"dpad"->dpad;"full"->buttons+dpad+sticks;else->buttons+dpad}
    /** Physical binding token; HAT directions sharing an axis remain distinct. */
    fun binding(mapping:String,step:CalibrationStep):String? {
        for(line in mapping.lineSequence()) {
            val p=line.trim().split(Regex("\\s+"))
            if(step.type==3&&p.size==3&&p[0]=="a"&&p[2].toIntOrNull()==step.target)return "a:${p[1]}"
            if(step.type!=3&&p.size==3&&p[0]=="k"&&p[2].toIntOrNull()==step.target)return "k:${p[1]}"
            if(step.type==4&&p.size==5&&p[0]=="h"&&p[4].toIntOrNull()==step.target)return "h:${p[1]}:${p[2]}"
        }
        return null
    }
}
