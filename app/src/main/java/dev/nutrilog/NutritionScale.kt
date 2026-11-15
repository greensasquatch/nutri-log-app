package dev.nutrilog

enum class Nutrient(val key:String, val title:String, val defaultTarget:Int) {
    Calories("Calories","Calories",2400),
    Protein("Protein","Protein",180),
    Carbs("Carbs","Carbohydrates",240),
    Fat("Fat","Fat",80)
}

object NutritionScale {
    const val cells=20
    const val goalCells=15
    const val goalPosition=goalCells.toFloat()/cells
    const val warningBoundary=17.25f // 115% of the first 15 slots, inside cell 18.
    fun ratio(current:Double,target:Double?) =
        target?.takeIf { it.isFinite() && it>0 }?.let { current/it }?.takeIf { it.isFinite() }
    fun filled(ratio:Double?)=((ratio ?: 0.0)*goalCells).coerceIn(0.0,cells.toDouble()).toFloat()
    fun fraction(boundary:Float,cell:Int)=(boundary-cell).coerceIn(0f,1f)
    fun target(nutrient:Nutrient,goals:Record?) =
        if(goals==null) nutrient.defaultTarget.toDouble() else goals[nutrient.key].toDoubleOrNull()
}
