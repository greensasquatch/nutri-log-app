package dev.nutrilog

import java.time.LocalDate

val energyFactors = linkedMapOf("Protein" to 4.0, "Carbs" to 4.0, "Fat" to 9.0)
fun macroPercents(grams: (String) -> Double): Map<String, Double> {
    val energy = energyFactors.mapValues { (name, factor) -> grams(name) * factor }
    val sum = energy.values.sum()
    return if (sum > 0) energy.mapValues { it.value / sum * 100 } else emptyMap()
}

fun monday(date:LocalDate):LocalDate = date.minusDays((date.dayOfWeek.value - 1).toLong())
class FoodDay(entries:List<Record>) {
    val foods=entries.filter { it.kind=="Food" }
    val unknownFibre=foods.count { it["Fibre"].toDoubleOrNull()==null }
    fun sum(key:String)=foods.sumOf { it.number(key) }
}
class FoodWeek(rows:List<Record>, date:LocalDate, completeOnly:Boolean=false) {
    val start=monday(date)
    val days=(0L..6L).associate { offset ->
        val day=start.plusDays(offset)
        day to FoodDay(rows.filter { it.day==day.toString() })
    }
    val logged=days.values.filter { it.foods.isNotEmpty() && (!completeOnly || it.sum("Calories")>=1000.0) }
    val fibreDays=logged.filter { it.unknownFibre==0 }
    fun average(key:String):Double? {
        val available=if(key=="Fibre") fibreDays else logged
        return available.takeIf { it.isNotEmpty() }?.sumOf { it.sum(key) }?.div(available.size)
    }
}
fun measurementGoal(name:String)="Measure cm "+name.trim().lowercase(java.util.Locale.ROOT)
