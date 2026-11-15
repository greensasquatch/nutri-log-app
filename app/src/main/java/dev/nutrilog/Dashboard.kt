package dev.nutrilog

import android.app.DatePickerDialog
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable fun DayPicker(day:LocalDate,change:(LocalDate)->Unit) {
    val ctx=LocalContext.current
    Row(Modifier.fillMaxWidth(),Arrangement.SpaceBetween,Alignment.CenterVertically) {
        TextButton({change(day.minusDays(1))}){Text("‹ Prev")}
        TextButton({DatePickerDialog(ctx,{_,y,m,d->change(LocalDate.of(y,m+1,d))},day.year,day.monthValue-1,day.dayOfMonth).show()}){Text(day.format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")))}
        TextButton({change(day.plusDays(1))}){Text("Next ›")}
    }
}

@Composable fun Summary(rows:List<Record>,goals:Record?,net:Boolean) {
    Nutrient.values().forEach { nutrient ->
        NutritionBattery(
            nutrientType=nutrient,
            currentValue=total(rows,"Food",nutrient.key),
            targetValue=NutritionScale.target(nutrient,goals),
            unit=if(nutrient==Nutrient.Calories) "kcal" else "g",
            unknownItems=if(nutrient==Nutrient.Fibre) FoodDay(rows).unknownFibre else 0
        )
    }
    val burned=total(rows,"Exercise","Burned")
    Text("Exercise · "+display(total(rows,"Exercise","Minutes"))+" min   /   "+display(burned)+" kcal estimated")
    if(net) Text("Net energy · "+display(total(rows,"Food","Calories")-burned)+" kcal")
}

@Composable fun EntryList(rows:List<Record>,edit:(Record)->Unit,copy:(List<Record>)->Unit,save:(List<Record>)->Unit) {
    val items=rows.filter { it.kind=="Food" || it.kind=="Exercise" }
    if(items.isEmpty()) Text("No meals or activity recorded for this day.")
    items.groupBy { if(it.kind=="Food") it["Meal"] else "Exercise" }.forEach { (group,entries)->
        Row(Modifier.fillMaxWidth(),Arrangement.SpaceBetween,Alignment.CenterVertically) {
            Text(group,style=MaterialTheme.typography.titleMedium)
            if(group!="Exercise") TextButton({copy(entries)}) { Text("Copy to today") }
        }
        var grouping by remember(entries) { mutableStateOf(false) }
        if(group!="Exercise" && entries.size>1) {
            TextButton({grouping=true}){Text("Group ingredients")}
            if(grouping) IngredientPicker(entries,{grouping=false},save)
        }
        entries.groupBy { it["Group id"].ifBlank { it.id } }.forEach { (_,members)->
            val grouped=members.first()["Group id"].isNotBlank()
            var expanded by remember(members.first().id){mutableStateOf(false)}
            if(grouped){
                TextButton({expanded=!expanded}){Text((if(expanded)"▾ " else "▸ ")+members.first()["Group name"]+" · "+display(members.sumOf {it.number("Calories")})+" kcal")}
                if(expanded){
                    Choices("Move group",group,mealTypes){meal->save(members.map {it.with("Meal",meal)})}
                    TextButton({save(members.map {it.with("Group id","").with("Group name","")})}){Text("Ungroup ingredients")}
                }
            }
            if(!grouped || expanded) members.forEach { r ->
            ListItem(headlineContent={Text(r.name)},supportingContent={Text(r.stamp.substringAfter("T").take(5)+"  ·  "+if(r.kind=="Food") r["Quantity"]+" "+r["Unit"]+"  ·  "+display(r.number("Calories"))+" kcal" else display(r.number("Minutes"))+" min  ·  ~"+display(r.number("Burned"))+" kcal")},
                trailingContent={TextButton({copy(listOf(r))}){Text("Copy")}},modifier=Modifier.clickable { edit(r) })
            }
        }
    }
}

@Composable private fun IngredientPicker(entries:List<Record>,close:()->Unit,save:(List<Record>)->Unit) {
    var selected by remember {mutableStateOf(emptySet<String>())}
    var name by remember {mutableStateOf("")}
    Sheet("Group ingredients",close,action={
        Button({
            val id=java.util.UUID.randomUUID().toString()
            save(entries.filter {it.id in selected}.map {it.with("Group id",id).with("Group name",name.trim())})
            close()
        },enabled=selected.size>=2 && name.isNotBlank()){Text("Save group")}
    }){
        Field("Group name",name){name=it}
        entries.forEach {r->CheckOption(r.name,r.id in selected){on->selected=if(on)selected+r.id else selected-r.id}}
    }
}

@Composable fun Trends(rows:List<Record>,weightUnit:String,lengthUnit:String) {
    var period by remember { mutableStateOf("30 days") }
    val options=macros+listOf("Exercise minutes","Exercise kcal","Weight")+rows.filter { it.kind=="Measurement" }.map { "Measure · "+it.name }.distinct()
    var metric by remember { mutableStateOf("Calories") }
    Choices("Range",period,listOf("7 days","30 days","90 days","180 days","365 days","All time")){period=it}
    Choices("Metric",metric,options){metric=it}
    val today=LocalDate.now(zone)
    val days=period.substringBefore(" ").toLongOrNull()
    val relevant=rows.filter { row ->
        row.day<=today.toString() && (days==null || row.day>=today.minusDays(days-1).toString()) && when(metric) {
            in macros -> row.kind=="Food"
            "Weight" -> row.kind=="Weight"
            "Exercise minutes","Exercise kcal" -> row.kind=="Exercise"
            else->row.kind=="Measurement" && row.name==metric.substringAfter(" · ")
        }
    }
    // Average repeated body measurements per day. Missing days have no data point.
    val points=relevant.groupBy { it.day }.toSortedMap().mapValues { (_,items) ->
        when(metric) { "Weight"->items.map { it.number("Value") }.average()/metricValue(1.0,weightUnit)
            "Exercise minutes"->items.sumOf { it.number("Minutes") }
            "Exercise kcal"->items.sumOf { it.number("Burned") }
            in macros->items.sumOf { it.number(metric) }
            else->items.map { it.number("Value") }.average()/metricValue(1.0,lengthUnit) }
    }
    if(points.isEmpty()){Text("No recorded days in this range.");return}
    val unit=when(metric) {"Weight"->weightUnit;"Calories","Exercise kcal"->"kcal";"Exercise minutes"->"min";in macros->"g";else->lengthUnit}
    Text(display(points.values.average())+" "+unit,style=MaterialTheme.typography.headlineLarge)
    Text("Average across "+points.size+" recorded days")
    val color=MaterialTheme.colorScheme.primary
    val first=LocalDate.parse(points.keys.first()).toEpochDay(); val last=LocalDate.parse(points.keys.last()).toEpochDay()
    val target=if(metric=="Weight") goalAt(rows,today.toString())?.number("Target kg")?.takeIf { it>0 }?.div(metricValue(1.0,weightUnit)) else null
    val low=minOf(points.values.min(),target ?: Double.MAX_VALUE);val high=maxOf(points.values.max(),target ?: -Double.MAX_VALUE)
    Canvas(Modifier.fillMaxWidth().height(160.dp).padding(8.dp)) {
        fun y(value:Double)=if(high==low) size.height/2 else (size.height*(1-(value-low)/(high-low))).toFloat()
        target?.let { drawLine(color.copy(alpha=.45f),Offset(0f,y(it)),Offset(size.width,y(it)),2f) }
        var previous:Offset?=null
        points.forEach { (date,value)->
            val x=if(first==last) size.width/2 else size.width*(LocalDate.parse(date).toEpochDay()-first).toFloat()/(last-first)
            val pos=Offset(x,y(value));previous?.let {drawLine(color,it,pos,3f)};drawCircle(color,5f,pos);previous=pos
        }
    }
    if(target!=null) Text("Goal line · "+display(target)+" "+unit)
    Text("Daily records",style=MaterialTheme.typography.titleMedium)
    points.entries.reversed().forEach { (date,value)->Text(date+"     "+display(value)+" "+unit) }
}
