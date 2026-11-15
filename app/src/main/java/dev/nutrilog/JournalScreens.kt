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

@Composable fun WeeklyHistory(rows:List<Record>,prefs:Map<String,String>,pref:(String,String)->Unit,edit:(Record)->Unit,copy:(List<Record>)->Unit,save:(List<Record>)->Unit) {
    var anchor by rememberSaveable { mutableStateOf(LocalDate.now(zone).toString()) }
    var expanded by rememberSaveable { mutableStateOf("") }
    val complete=prefs["History complete"]=="On"
    val mode=prefs["History view"] ?: "Numbers"
    val week=remember(rows,anchor,complete){ FoodWeek(rows,LocalDate.parse(anchor),complete) }
    val fmt=DateTimeFormatter.ofPattern("MMM d")
    Row(Modifier.fillMaxWidth(),Arrangement.SpaceBetween,Alignment.CenterVertically) {
        TextButton({anchor=week.start.minusWeeks(1).toString();expanded=""}){Text("‹ Prev")}
        Text(week.start.format(fmt)+" – "+week.start.plusDays(6).format(DateTimeFormatter.ofPattern("MMM d, yyyy")),style=MaterialTheme.typography.labelLarge)
        TextButton({anchor=week.start.plusWeeks(1).toString();expanded=""}){Text("Next ›")}
    }
    Text("Daily averages",style=MaterialTheme.typography.titleLarge)
    Choices("View",mode,listOf("Numbers","Battery meters")){pref("History view",it)}
    CheckOption("Average only days with at least 1,000 kcal",complete){pref("History complete",if(it)"On" else "Off")}
    Text("${week.logged.size} of 7 days included in averages")
    Nutrient.values().forEach { n ->
        val avg=week.average(n.key)
        val unit=if(n==Nutrient.Calories)"kcal" else "g"
        if(mode=="Battery meters" && avg!=null){
            val included=if(n==Nutrient.Fibre)week.fibreDays else week.logged
            val targets=week.days.filterValues {it in included}.keys.map { NutritionScale.target(n,goalAt(rows,it.toString())) }
            NutritionBattery(n,avg,targets.filterNotNull().takeIf {it.size==targets.size && it.isNotEmpty()}?.average(),unit)
        } else Text(n.title+"   "+(avg?.let {display(it)+" "+unit} ?: "—")+
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
        if(selected && mode=="Battery meters") Summary(entries,goalAt(rows,date.toString()),false)
        if(selected) EntryList(entries,edit,copy,save)
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
        for(k in listOf("Minutes","Burned","Plan age","Plan sex","Plan activity","Plan enabled","Plan auto calories")) values[k]=r[k]
        values["Weekly change"]=r["Plan weekly kg"].toDoubleOrNull()?.let {display(it/metricValue(1.0,weightUnit))}.orEmpty()
        values
    }
    fun put(k:String,v:String){draft[k]=v;notice=""}
    val planning=draft["Plan enabled"]=="On"
    val recorded=latestWeight(rows,effective)
    val startKg=recorded ?: draft["Starting kg"]?.toDoubleOrNull()?.let {metricValue(it,weightUnit)}
    val height=latestHeight(rows,effective)
    val target=draft["Target kg"]?.toDoubleOrNull()?.let {metricValue(it,weightUnit)}
    val rate=draft["Weekly change"]?.toDoubleOrNull()?.let {metricValue(it,weightUnit)}
    val estimate=calorieEstimate(startKg ?: 0.0,height ?: 0.0,draft["Plan age"]?.toIntOrNull() ?: 0,
        draft["Plan sex"].orEmpty(),draft["Plan activity"].orEmpty(),target ?: 0.0,rate ?: 0.0)
    val auto=planning && draft["Plan auto calories"]=="On"
    val calories=if(auto) estimate?.let {Math.round(it).toDouble()} else draft["Calories"]?.toDoubleOrNull()
    CheckOption("Calculate a daily weight plan",planning){put("Plan enabled",if(it)"On" else "Off")}
    if(planning) CheckOption("Calculate calories from my weekly weight goal",auto){put("Plan auto calories",if(it)"On" else "Off")}
    if(auto) Text("Estimated daily target   "+(calories?.let {display(it)+" kcal"} ?: "Complete the weight plan below"))
    else Field("Daily calories (kcal)",draft["Calories"].orEmpty()){put("Calories",it)}
    Text("Macro targets",style=MaterialTheme.typography.titleMedium)
    energyFactors.forEach { (name,factor)->
        val grams=(calories ?: 0.0)*(draft[name]?.toDoubleOrNull() ?: 0.0)/100/factor
        Field(name+" (%)"+if(grams.isFinite()) " · "+display(grams)+" g" else "",draft[name].orEmpty()){put(name,it)}
    }
    Field("Daily fibre minimum (g)",draft["Fibre"].orEmpty()){put("Fibre",it)}
    Field("Starting weight ("+weightUnit+")",draft["Starting kg"].orEmpty()){put("Starting kg",it)}
    Field("Target weight ("+weightUnit+")",draft["Target kg"].orEmpty()){put("Target kg",it)}
    if(planning){
        Text("Starting from "+(startKg?.let {display(it/metricValue(1.0,weightUnit))+" "+weightUnit} ?: "a weight entered above or logged in Body"))
        Field("Age (years)",draft["Plan age"].orEmpty()){put("Plan age",it)}
        Choices("Sex for calorie estimate",draft["Plan sex"].orEmpty().ifBlank {"Choose"},listOf("Male","Female")){put("Plan sex",it)}
        Choices("Activity level",draft["Plan activity"].orEmpty().ifBlank {"Choose"},activityFactors.keys.toList()){put("Plan activity",it)}
        Field("Weekly weight change ("+weightUnit+"/week)",draft["Weekly change"].orEmpty()){put("Weekly change",it)}
        if(auto && height==null)Text("Log height in Body to estimate calories.")
        else if(auto && estimate==null)Text("An estimate requires adult age, sex, activity, weights and a weekly rate. Estimates below 1,000 kcal/day are unavailable.")
    }
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
            val kcal=requireNotNull(calories?.takeIf {it.isFinite()}) {"Complete the calorie estimate or enter a manual target."}
            require(kcal>0) {"Enter a positive calorie target."}
            require(abs(energyFactors.keys.sumOf {numeric(it)}-100)<0.001) {"Macro ratios must total 100%."}
            // Editing an effective day reuses its most recent goal ID and time.
            var record=if(prior?.day==effective) prior else (prior ?: blank("Goals")).fresh("Goals",effective+"T00:00")
            if(planning){
                require(startKg!=null && startKg>0 && numeric("Target kg")>0 && numeric("Weekly change")>0){"Enter starting and target weights and choose a weekly change."}
                record=record.with("Plan start kg",startKg.toString()).with("Plan weekly kg",metricValue(numeric("Weekly change"),weightUnit).toString())
            }
            draft.filterKeys {it.startsWith("Plan ")}.forEach { (k,v)->record=record.with(k,v) }
            record=record.with("Calories",kcal.toString())
            energyFactors.forEach { (name,factor)->record=record.with("Ratio "+name,numeric(name).toString()).with(name,(kcal*numeric(name)/100/factor).toString()) }
            draft.keys.filter { it !in energyFactors.keys && it!="Calories" && it!="Weekly change" && !it.startsWith("Plan ") }.forEach { k->
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
