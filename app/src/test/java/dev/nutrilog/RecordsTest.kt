package dev.nutrilog

import org.junit.Test
import org.junit.Assert.*

class RecordsTest {
    private val milk=blank("Food","2026-11-13T23:59").with("Name","Milk").with("Quantity","250").with("Unit","mL").with("Calories","150").with("Protein","18")
    @Test fun scaleServing(){val next=milk.scaled("500");assertEquals(300.0,next.number("Calories"),0.0);assertEquals(36.0,next.number("Protein"),0.0);assertEquals(150.0,milk.number("Calories"),0.0)}
    @Test fun independentSnapshots(){val copy=milk.fresh("Saved food");assertNotEquals(milk.id,copy.id);assertEquals("150",milk["Calories"]);assertEquals("200",copy.with("Calories","200")["Calories"])}
    @Test fun intakeSeparateFromExercise(){val walk=blank("Exercise").with("Name","Walk").with("Burned","50");val rows=listOf(milk,walk);assertEquals(150.0,total(rows,"Food","Calories"),0.0);assertEquals(50.0,total(rows,"Exercise","Burned"),0.0)}
    @Test fun historicalGoals(){val first=blank("Goals","2026-11-13T00:00").with("Protein","120");val next=first.fresh(time="2026-11-14T00:00").with("Protein","140");assertEquals(first,goalAt(listOf(next,first),milk.day));assertNull(goalAt(listOf(next,first),"2026-10-01"))}
    @Test fun localMidnight(){assertNotEquals(milk.day,milk.copy(stamp="2026-11-14T00:00").day)}
    @Test fun convert(){assertEquals(2.54,metricValue(1.0,"in"),0.0);assertEquals(45.359237,metricValue(100.0,"lb"),0.000001)}
    @Test fun rejectMalformedValues(){for(raw in listOf("-1","NaN","Infinity","bad")) assertTrue(runCatching {milk.with("Calories",raw).validate()}.isFailure)}
    @Test fun quantityRequired(){assertTrue(runCatching {milk.with("Quantity","0").validate()}.isFailure)}
    @Test fun validDraft(){milk.validate();assertTrue(runCatching {milk.copy(stamp="yesterday").validate()}.isFailure)}
}
