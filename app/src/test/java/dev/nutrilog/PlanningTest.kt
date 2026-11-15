package dev.nutrilog

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class PlanningTest {
    private val day=LocalDate.parse("2024-01-01")
    @Test fun trajectoryStopsAtGoalInBothDirections() {
        assertEquals(79.5,scheduledWeight(80.0,75.0,0.5,day,day.plusWeeks(1))!!,0.0001)
        assertEquals(75.0,scheduledWeight(80.0,75.0,0.5,day,day.plusYears(1))!!,0.0001)
        assertEquals(81.0,scheduledWeight(80.0,81.0,0.5,day,day.plusYears(1))!!,0.0001)
    }
    @Test fun activityAndDirectionAffectEstimate() {
        fun kcal(level:String,target:Double)=calorieEstimate(80.0,180.0,40,"Male",level,target,0.25)!!
        assertEquals(2076.0,kcal("Sedentary",80.0),0.001)
        assertEquals(275.0,kcal("Active",80.0)-kcal("Active",75.0),0.001)
        assertTrue(kcal("Active",75.0)>kcal("Light activity",75.0))
        assertNull(calorieEstimate(80.0,180.0,0,"Male","Active",75.0,0.25))
    }
    @Test fun dailyTargetsUseOnlyMeasurementsAvailableOnTheSelectedDay() {
        val goal=blank("Goals","2024-01-01T08:00").with("Plan enabled","On").with("Plan auto calories","On")
            .with("Plan start kg","80").with("Target kg","75").with("Plan weekly kg",metricValue(0.5,"lb").toString())
            .with("Plan age","40").with("Plan sex","Male").with("Plan activity","Sedentary").with("Ratio Protein","30")
        val height=blank("Measurement",goal.stamp).with("Name","Height").with("Value","180")
        val weight=blank("Weight","2024-01-03T09:00").with("Value","79")
        val rows=listOf(goal,height,weight)
        val earlier=goalAt(rows,"2024-01-02")!!
        assertEquals(1827.0,earlier.number("Calories"),0.0)
        assertEquals(1815.0,goalAt(rows,"2024-01-03")!!.number("Calories"),0.0)
        assertEquals(1827.0*0.3/4,earlier.number("Protein"),0.0001)
        assertEquals(goal.number("Calories"),goalAt(listOf(goal.with("Plan auto calories","Off"),height,weight),"2024-01-03")!!.number("Calories"),0.0)
    }
    @Test fun bodyFatAndMissingMeasurements() {
        fun inches(n:Int)=2.54*n
        assertEquals(21.26,estimatedBodyFat(false,inches(70),inches(36),inches(15))!!,0.1)
        assertNotNull(estimatedBodyFat(true,inches(65),inches(30),inches(13),inches(38)))
        assertNull(estimatedBodyFat(true,inches(65),inches(30),inches(13)))
        assertNull(estimatedBodyFat(false,inches(70),inches(15),inches(16)))
    }
    @Test fun completeDayThresholdAndIndependentGroupCopies() {
        val food=blank("Food","2024-01-01T10:00").with("Group id","group").with("Calories","1000")
        val low=food.fresh(time="2024-01-02T10:00").with("Calories","999.9")
        assertEquals(1000.0,FoodWeek(listOf(food,low),day,true).average("Calories")!!,0.0)
        val copies=copyToToday(listOf(food,food.copy(id="second")))
        assertEquals(copies.first()["Group id"],copies.last()["Group id"])
        assertNotEquals(food["Group id"],copies.first()["Group id"])
    }
}
