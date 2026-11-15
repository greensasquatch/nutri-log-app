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
