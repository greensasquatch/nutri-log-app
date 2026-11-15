package dev.nutrilog

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class JournalStatsTest {
    private val sunday=LocalDate.parse("2024-01-07")
    private fun food(day:String,kcal:String,fibre:String="")=blank("Food",day+"T12:00")
        .with("Name","Sample").with("Calories",kcal).with("Fibre",fibre)

    @Test fun weeklyAveragesCountFoodDaysOnly() {
        val records=listOf(food("2024-01-01","50"),food("2024-01-01","150","5"),
            food("2024-01-07","100","0"),food("2024-01-08","500","20"),
            blank("Exercise","2024-01-02T09:00"))
        val week=FoodWeek(records,sunday)
        assertEquals("2024-01-01",week.start.toString())
        assertEquals(7,week.days.size)
        assertEquals(2,week.logged.size)
        assertEquals(150.0,week.average("Calories")!!,0.0)
        assertEquals(1,week.fibreDays.size)
        assertEquals(0.0,week.average("Fibre")!!,0.0)
    }
    @Test fun emptyWeekAndUnknownFibre() {
        assertNull(FoodWeek(emptyList(),sunday).average("Calories"))
        val legacy=food(sunday.toString(),"80").copy(fields=mapOf("Calories" to "80"))
        assertNull(FoodWeek(listOf(legacy),sunday).average("Fibre"))
        assertEquals("",legacy.scaled("2")["Fibre"])
    }
    @Test fun portionScalingAndInvalidFibre() {
        val r=food(sunday.toString(),"120","3.5")
        assertEquals(7.0,r.scaled("2").number("Fibre"),0.0)
        assertTrue(runCatching { r.with("Fibre","-1").validate() }.isFailure)
    }
    @Test fun pieUsesMacroEnergyOnly() {
        val grams=mapOf("Protein" to 180.0,"Carbs" to 240.0,"Fat" to 80.0,"Fibre" to 20.0)
        assertEquals(mapOf("Protein" to 30.0,"Carbs" to 40.0,"Fat" to 30.0),macroPercents {grams.getValue(it)})
        assertTrue(macroPercents { 0.0 }.isEmpty())
    }
    @Test fun effectiveGoalsAndMeasurementNames() {
        val old=blank("Goals","2024-01-01T00:00").with("Fibre","15")
        val newer=old.fresh(time="2024-01-08T00:00").with("Fibre","25")
        assertEquals(15.0,goalAt(listOf(newer,old),sunday.toString())!!.number("Fibre"),0.0)
        assertEquals(measurementGoal("Waist"),measurementGoal(" waist "))
    }
}
