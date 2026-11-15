package dev.nutrilog

import kotlin.math.log10

// Classic Navy circumference equations take inches. The journal stores centimetres.
fun estimatedBodyFat(female:Boolean,height:Double,waist:Double,neck:Double,hip:Double?=null):Double? {
    if(listOf(height,waist,neck).any { !it.isFinite() || it<=0 }) return null
    if(female && (hip==null || !hip.isFinite() || hip<=0)) return null
    val girth=(waist-neck+if(female) hip!! else 0.0)/2.54
    if(girth<=0) return null
    val h=log10(height/2.54)
    val estimate=if(female) 163.205*log10(girth)-97.684*h-78.387 else 86.010*log10(girth)-70.041*h+36.76
    return estimate.takeIf { it>0 && it<100 }
}
