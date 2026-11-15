package dev.nutrilog

import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

val zone: ZoneId get() = ZoneId.systemDefault()
fun now(): String = LocalDateTime.now(zone).withSecond(0).withNano(0).toString()
val kinds = listOf("Food","Exercise","Weight","Measurement","Goals","Saved food","Saved meal item")
val macros = listOf("Calories","Protein","Carbs","Fat")
val mealTypes = listOf("Breakfast","Lunch","Dinner","Snack","Other")

data class Record(val kind: String, val fields: Map<String,String> = emptyMap(), val id: String = UUID.randomUUID().toString(), val stamp: String = now()) {
    operator fun get(key: String) = fields[key].orEmpty()
    fun number(key: String) = get(key).toDoubleOrNull() ?: 0.0
    val day get() = stamp.substringBefore("T")
    val name get() = get("Name")
    fun with(key: String, value: String) = copy(fields = fields + (key to value))
    fun fresh(type: String = kind, time: String = now()) = copy(kind=type,id=UUID.randomUUID().toString(),stamp=time)
    // Nutrition fields contain totals for the entered quantity, including any manual label corrections.
    fun scaled(quantity: String): Record {
        val old = number("Quantity")
        val next = quantity.toDoubleOrNull()
        var row = with("Quantity",quantity)
        if (next != null && next > 0 && old > 0) for (key in macros) row = row.with(key, (number(key)*next/old).toString())
        if(next!=null && next>0 && old>0 && get("Fibre").isNotBlank()) row=row.with("Fibre",(number("Fibre")*next/old).toString())
        return row
    }
    fun validate() {
        require(kind in kinds && id.isNotBlank())
        LocalDateTime.parse(stamp)
        fun positive(key:String) = require(get(key).toDoubleOrNull()?.let { it.isFinite() && it>0 } == true) { "Enter a positive "+key.lowercase() }
        if(kind != "Goals") require(name.isNotBlank()) { "Enter a name" }
        if(kind in listOf("Food","Saved food","Saved meal item")) {
            positive("Quantity")
            require(get("Unit").isNotBlank() && get("Meal") in mealTypes) { "Check serving unit and meal" }
        }
        if(kind == "Saved meal item") require(get("Recipe").isNotBlank())
        if(kind in listOf("Weight","Measurement")) positive("Value")
        if(kind == "Weight") require(get("Unit") == "kg")
        if(kind == "Measurement") require(get("Unit") == "cm")
        for (key in macros + listOf("Fibre","Burned","Minutes","Distance","Sets","Reps","Target kg","Starting kg")) {
            val raw = get(key)
            if (raw.isNotBlank() || (kind=="Food" && key in macros)) require(raw.toDoubleOrNull()?.let { it.isFinite() && it >= 0 } == true) { "Check "+key }
        }
    }
}
fun blank(kind:String,time:String=now()):Record {
    val meal = when(time.substringAfter("T").take(2).toInt()) { in 5..10 -> "Breakfast"; in 11..14 -> "Lunch"; in 17..21 -> "Dinner"; else -> "Snack" }
    val base = when(kind) {
        "Food" -> macros.associateWith { "0" } + mapOf("Meal" to meal,"Quantity" to "1","Unit" to "serving")
        "Goals" -> Nutrient.values().associate { it.key to it.defaultTarget.toString() }
        "Weight" -> mapOf("Name" to "Weight","Unit" to "kg")
        "Measurement" -> mapOf("Name" to "Waist","Unit" to "cm")
        else -> emptyMap()
    }
    return Record(kind,base,stamp=time)
}
fun total(rows:List<Record>,kind:String,key:String) = rows.filter { it.kind==kind }.sumOf { it.number(key) }
fun goalAt(rows:List<Record>,day:String) = rows.filter { it.kind=="Goals" && it.day<=day }.maxByOrNull { it.stamp }
fun display(n:Double) = java.math.BigDecimal.valueOf(n).setScale(2,java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
fun metricValue(value:Double,unit:String) = when(unit) { "lb" -> value*0.45359237; "in" -> value*2.54; else -> value }
