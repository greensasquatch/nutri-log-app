package dev.nutrilog

import org.junit.Assert.*
import org.junit.Test

class NutritionScaleTest {
    private fun fill(value:Double,target:Double)=NutritionScale.filled(NutritionScale.ratio(value,target)).toDouble()

    @Test fun referenceFractions() {
        for((ratio,count) in listOf(0.76 to 11.4, 127.0/180 to 10.583333, 178.0/240 to 11.125, 0.8625 to 12.9375)) {
            assertEquals(count,fill(ratio,1.0),0.00001)
            assertTrue(fill(ratio,1.0)<15)
        }
    }
    @Test fun goalAndSaturation() {
        assertEquals(0.75f,NutritionScale.goalPosition,0f)
        assertEquals(0.0,fill(0.0,100.0),0.0)
        assertEquals(7.5,fill(50.0,100.0),0.0)
        assertEquals(15.0,fill(100.0,100.0),0.0)
        assertEquals(20.0,fill(4.0,3.0),0.0)
        assertEquals(20.0,fill(175.0,100.0),0.0)
        assertEquals(1.75,NutritionScale.ratio(175.0,100.0)!!,0.0)
    }
    @Test fun partialFillAndSplitWarningCell() {
        assertEquals(.9f,NutritionScale.fraction(fill(86.0,100.0).toFloat(),12),0.00001f)
        assertEquals(.25f,NutritionScale.fraction(NutritionScale.warningBoundary,17),0f)
        assertEquals(18.0,fill(120.0,100.0),0.0)
    }
    @Test fun targetsAndStoredTotals() {
        val day="2020-01-01"
        val first=blank("Food",day+"T12:00").with("Fat","10")
        val other=first.fresh(time="2020-01-02T00:00")
        val exercise=blank("Exercise",day+"T13:00").with("Fat","99")
        fun dayFill(rows:List<Record>,goal:Record?)=NutritionScale.filled(NutritionScale.ratio(
            total(rows.filter { it.day==day },"Food","Fat"),NutritionScale.target(Nutrient.Fat,goal)))
        assertEquals(1.875f,dayFill(listOf(first,other,exercise),null),0f)
        assertEquals(0f,dayFill(listOf(other,exercise),null),0f)
        val goals=blank("Goals").with("Fat","100")
        assertEquals(3f,dayFill(listOf(first.with("Fat","20")),goals),0f)
        for(target in listOf(null,0.0,-1.0)) assertNull(NutritionScale.ratio(10.0,target))
        assertNull(NutritionScale.target(Nutrient.Fat,Record("Goals")))
    }
}
