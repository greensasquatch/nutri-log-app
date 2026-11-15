package dev.nutrilog

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs

@Composable fun MacroPie(rows:List<Record>, goals:Record?) {
    val actual=macroPercents { total(rows,"Food",it) }
    val goal=macroPercents { name -> NutritionScale.target(Nutrient.values().first { it.key==name },goals) ?: 0.0 }
    val palette=listOf(Color(0xFF47BAA0),Color(0xFF669FEF),Color(0xFFE8AB54))
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Macro split so far",style=MaterialTheme.typography.titleMedium)
            if(actual.isEmpty()) Text("Log food with macros to see your split.")
            else Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                Canvas(Modifier.size(80.dp)) {
                    var start=-90f
                    actual.values.forEachIndexed { i,pct ->
                        val sweep=(pct*3.6).toFloat()
                        if(sweep>0) drawArc(palette[i],start,sweep,true)
                        start+=sweep
                    }
                }
                Column {
                    Text("Actual / goal",style=MaterialTheme.typography.labelMedium)
                    actual.entries.forEachIndexed { i,(name,pct) ->
                        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                            Spacer(Modifier.size(8.dp).background(palette[i]))
                            Text(name+"  "+display(pct)+"% / "+(goal[name]?.let {display(it)+"%"} ?: "—"),style=MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable fun WeeklyHistory(rows:List<Record>,edit:(Record)->Unit,copy:(List<Record>)->Unit) {
    var anchor by rememberSaveable { mutableStateOf(LocalDate.now(zone).toString()) }
    var expanded by rememberSaveable { mutableStateOf("") }
    val week=remember(rows,anchor){ FoodWeek(rows,LocalDate.parse(anchor)) }
    val fmt=DateTimeFormatter.ofPattern("MMM d")
    Row(Modifier.fillMaxWidth(),Arrangement.SpaceBetween,Alignment.CenterVertically) {
        TextButton({anchor=week.start.minusWeeks(1).toString();expanded=""}){Text("‹ Prev")}
        Text(week.start.format(fmt)+" – "+week.start.plusDays(6).format(DateTimeFormatter.ofPattern("MMM d, yyyy")),style=MaterialTheme.typography.labelLarge)
        TextButton({anchor=week.start.plusWeeks(1).toString();expanded=""}){Text("Next ›")}
    }
    Text("Daily averages",style=MaterialTheme.typography.titleLarge)
    Text("${week.logged.size} of 7 days with food logged")
    Nutrient.values().forEach { n ->
        val avg=week.average(n.key)
        val unit=if(n==Nutrient.Calories)"kcal" else "g"
        Text(n.title+"   "+(avg?.let {display(it)+" "+unit} ?: "—")+
            if(n==Nutrient.Fibre) "   ·   ${week.fibreDays.size} days with complete fibre" else "")
    }
    HorizontalDivider()
    week.days.forEach { (date,day) ->
        val entries=rows.filter { it.day==date.toString() }
        val selected=expanded==date.toString()
        ListItem(headlineContent={Text(date.format(DateTimeFormatter.ofPattern("EEEE, MMM d")))},
            supportingContent={Text(if(day.foods.isEmpty()) "No food logged" else
                display(day.sum("Calories"))+" kcal  ·  "+listOf("Protein","Carbs","Fat").joinToString("   ") {it.take(1)+" "+display(day.sum(it))+" g"}+
                    "   ·   Fibre "+if(day.unknownFibre>0)"unknown" else display(day.sum("Fibre"))+" g")},
            trailingContent={Text(if(selected)"−" else "+")},modifier=Modifier.clickable { expanded=if(selected) "" else date.toString() })
        if(selected) EntryList(entries,edit,copy)
    }
}

@Composable fun GoalForm(rows:List<Record>,weightUnit:String,lengthUnit:String,save:(Record)->Unit) {
    var effective by rememberSaveable { mutableStateOf(LocalDate.now(zone).toString()) }
    val prior=goalAt(rows,effective)
    var notice by remember(effective) { mutableStateOf("") }
    Text("Effective from",style=MaterialTheme.typography.titleMedium)
    DayPicker(LocalDate.parse(effective)){effective=it.toString();notice=""}
    val draft=remember(prior,effective,weightUnit,lengthUnit) {
        val r=prior ?: blank("Goals")
        val values=mutableStateMapOf<String,String>()
        val ratio=macroPercents { r.number(it) }
        values["Calories"]=r["Calories"]
        values["Fibre"]=r["Fibre"]
        val names=energyFactors.keys.toList()
        val firstTwo=names.take(2).map { display(ratio[it] ?: 0.0) }
        names.forEachIndexed { i,k -> values[k]=r["Ratio "+k].ifBlank {
            if(ratio.isEmpty()) "" else if(i<2) firstTwo[i] else display(100-firstTwo.sumOf { it.toDouble() }) } }
        for(k in listOf("Starting kg","Target kg")) values[k]=r[k].toDoubleOrNull()?.let {display(it/metricValue(1.0,weightUnit))}.orEmpty()
        r.fields.filterKeys { it.startsWith("Measure cm ") }.forEach { (k,v)->values[k]=v.toDoubleOrNull()?.let {display(it/metricValue(1.0,lengthUnit))}.orEmpty() }
        for(k in listOf("Minutes","Burned")) values[k]=r[k]
        values
    }
    fun put(k:String,v:String){draft[k]=v;notice=""}
    Field("Daily calories (kcal)",draft["Calories"].orEmpty()){put("Calories",it)}
    Text("Macro targets",style=MaterialTheme.typography.titleMedium)
    energyFactors.forEach { (name,factor)->
        val grams=(draft["Calories"]?.toDoubleOrNull() ?: 0.0)*(draft[name]?.toDoubleOrNull() ?: 0.0)/100/factor
        Field(name+" (%)"+if(grams.isFinite()) " · "+display(grams)+" g" else "",draft[name].orEmpty()){put(name,it)}
    }
    Field("Daily fibre minimum (g)",draft["Fibre"].orEmpty()){put("Fibre",it)}
    Field("Starting weight ("+weightUnit+")",draft["Starting kg"].orEmpty()){put("Starting kg",it)}
    Field("Target weight ("+weightUnit+")",draft["Target kg"].orEmpty()){put("Target kg",it)}
    val options=(listOf("Waist","Chest","Hips","Neck","Upper arm","Forearm","Thigh","Calf","Shoulders")+
        rows.filter { it.kind=="Measurement" }.map { it.name }+draft.keys.filter {it.startsWith("Measure cm ")}.map {it.removePrefix("Measure cm ")}).distinctBy { measurementGoal(it) }
    var measure by rememberSaveable { mutableStateOf("Waist") }
    Choices("Measurement",measure,options){measure=it}
    Field("Target "+measure.lowercase()+" ("+lengthUnit+")",draft[measurementGoal(measure)].orEmpty()){put(measurementGoal(measure),it)}
    Field("Exercise duration (min)",draft["Minutes"].orEmpty()){put("Minutes",it)}
    Field("Exercise energy (kcal)",draft["Burned"].orEmpty()){put("Burned",it)}
    if(notice.isNotBlank()) Text(notice)
    Button({
        runCatching {
            fun numeric(key:String):Double = requireNotNull(draft[key]?.toDoubleOrNull()?.takeIf {it.isFinite() && it>=0}){"Check "+key}
            val kcal=numeric("Calories")
            require(kcal>0) {"Enter a positive calorie target."}
            require(abs(energyFactors.keys.sumOf {numeric(it)}-100)<0.001) {"Macro ratios must total 100%."}
            // Editing an effective day reuses its most recent goal ID and time.
            var record=if(prior?.day==effective) prior else (prior ?: blank("Goals")).fresh("Goals",effective+"T00:00")
            record=record.with("Calories",kcal.toString())
            energyFactors.forEach { (name,factor)->record=record.with("Ratio "+name,numeric(name).toString()).with(name,(kcal*numeric(name)/100/factor).toString()) }
            draft.keys.filter { it !in energyFactors.keys && it!="Calories" }.forEach { k->
                val value=if(draft[k].isNullOrBlank()) "" else {
                    val n=numeric(k)
                    when { k.endsWith(" kg")->metricValue(n,weightUnit); k.startsWith("Measure cm ")->metricValue(n,lengthUnit); else->n }.toString()
                }
                record=record.with(k,value)
            }
            record.validate();save(record);notice="Goals saved for "+effective
        }.onFailure {notice=it.message ?: "Check your goals."}
    }){Text("Save goals")}
    rows.filter {it.kind=="Goals"}.map {it.day}.distinct().sortedDescending().forEach { day->
        TextButton({effective=day}){Text("Goals from "+day)}
    }
}
