package dev.nutrilog

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.sign

val activityFactors = linkedMapOf("Sedentary" to 1.2, "Light activity" to 1.375, "Active" to 1.55)

// Linear goal schedule. Stop at the target even when reviewing a later date.
fun scheduledWeight(startKg:Double,targetKg:Double,weeklyKg:Double,start:LocalDate,date:LocalDate):Double? {
    if(listOf(startKg,targetKg,weeklyKg).any { !it.isFinite() || it<=0 }) return null
    val delta=targetKg-startKg
    val days=ChronoUnit.DAYS.between(start,date).coerceAtLeast(0)
    return startKg+sign(delta)*minOf(abs(delta),days*weeklyKg/7)
}

// Mifflin-St Jeor estimates resting expenditure. Activity includes habitual exercise.
fun calorieEstimate(kg:Double,heightCm:Double,age:Int,sex:String,activity:String,targetKg:Double,weeklyKg:Double):Double? {
    if(age !in 18..120 || listOf(kg,heightCm,targetKg,weeklyKg).any { !it.isFinite() || it<=0 }) return null
    val offset=when(sex){"Male"->5;"Female"->-161;else->return null}
    val factor=activityFactors[activity] ?: return null
    val maintenance=(10*kg+6.25*heightCm-5*age+offset)*factor
    val adjustment=sign(targetKg-kg)*minOf(abs(targetKg-kg),weeklyKg)*7700/7
    return (maintenance+adjustment).takeIf {it>=1000}
}
