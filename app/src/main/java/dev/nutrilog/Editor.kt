package dev.nutrilog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable fun Field(label:String,value:String,change:(String)->Unit) {
    OutlinedTextField(value,change,label={ Text(label) },singleLine=true,modifier=Modifier.fillMaxWidth())
}
@Composable fun Choices(label:String,value:String,options:List<String>,change:(String)->Unit) {
    var menu by remember { mutableStateOf(false) }
    Box { OutlinedButton({menu=true}) { Text(label+" · "+value) }
        DropdownMenu(menu,{menu=false}) { options.forEach { DropdownMenuItem({Text(it)},{change(it);menu=false}) } }
    }
}
@Composable fun Sheet(title:String,close:()->Unit,action:@Composable ()->Unit = {},content:@Composable ColumnScope.()->Unit) {
    Dialog(close,properties=DialogProperties(usePlatformDefaultWidth=false, decorFitsSystemWindows=false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.safeDrawingPadding().imePadding().padding(horizontal=18.dp)) {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                    Text(title,style=MaterialTheme.typography.headlineSmall); TextButton(close) { Text("Close") }
                }
                action()
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom=28.dp),verticalArrangement=Arrangement.spacedBy(8.dp),content=content)
            }
        }
    }
}

@Composable fun Editor(initial:List<Record>,existing:Boolean,unitFor:(String)->String,onClose:()->Unit,onSave:(List<Record>)->Unit,onDelete:(Record)->Unit,onCorrect:((List<Record>)->Unit)?=null) {
    var rows by remember(initial) { mutableStateOf(initial) }
    var warning by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }
    var recipe by remember { mutableStateOf("") }
    var favorite by remember { mutableStateOf(false) }
    Sheet(if(existing) "Edit entry" else "Review entries",onClose,action = {
        if(warning.isNotBlank()) Text(warning,color=MaterialTheme.colorScheme.error)
        Button({
            runCatching {
                val foods=rows.filter { it.kind=="Food" }
                val extras=if(existing) emptyList() else buildList {
                    if(favorite) addAll(foods.map { it.fresh("Saved food",it.stamp) })
                    if(recipe.isNotBlank()) addAll(foods.map { it.fresh("Saved meal item",it.stamp).with("Recipe",recipe.trim()) })
                }
                val all=rows+extras
                all.forEach { it.validate() }; onSave(all)
            }.onFailure {warning=it.message ?: "Check the entry fields"}
        },modifier=Modifier.fillMaxWidth()){Text(if(existing) "Save changes" else "Confirm and save")}
    }) {
        rows.forEachIndexed { i,r -> key(r.id) {
            fun update(next:Record) { rows=rows.toMutableList().also { it[i]=next } }
            fun set(k:String,v:String) = update(r.with(k,v))
            Text(r.kind,style=MaterialTheme.typography.titleMedium)
            if(r.kind!="Goals") Field("Name",r.name){set("Name",it)}
            Field("Local date and time · YYYY-MM-DDTHH:mm",r.stamp){update(r.copy(stamp=it))}
            if(r.kind in listOf("Food","Saved food","Saved meal item")) {
                Choices("Meal",r["Meal"],mealTypes){set("Meal",it)}
                var base by remember(r.id) { mutableStateOf(r) }
                Field("Quantity",r["Quantity"]){v ->
                    val ref=if(r.number("Quantity")>0) r else base
                    if(r.number("Quantity")>0) base=r
                    update(ref.scaled(v).copy(stamp=r.stamp))
                }
                Field("Serving unit",r["Unit"]){set("Unit",it)}
                if(r.kind=="Saved meal item") Field("Saved meal name",r["Recipe"]){set("Recipe",it)}
            }
            val keys=when(r.kind) {
                "Food","Saved food","Saved meal item" -> macros
                "Exercise" -> listOf("Minutes","Distance","Intensity","Sets","Reps","Burned")
                "Goals" -> macros+listOf("Starting kg","Target kg","Minutes","Burned")
                else -> emptyList()
            }
            keys.forEach { k -> Field(when(k) { "Burned"->"Estimated exercise kcal"; "Distance"->"Distance (km)"; in macros.drop(1)->k+" (g)"; else->k },r[k]){set(k,it)} }
            if(r.kind in listOf("Measurement","Weight")) {
                val unit=unitFor(r.kind)
                var raw by remember(r.id) { mutableStateOf(if(r["Value"].isBlank()) "" else display(r.number("Value")/metricValue(1.0,unit))) }
                Field("Measurement ("+unit+")",raw){raw=it;set("Value",it.toDoubleOrNull()?.let { n -> metricValue(n,unit).toString() } ?: "")}
            }
            Field("Notes",r["Notes"]){set("Notes",it)}
            if(!existing && rows.size>1) TextButton({rows=rows.filter { it.id!=r.id }}) { Text("Remove item") }
            HorizontalDivider()
        } }
        if(!existing && rows.all { it.kind in listOf("Food","Exercise") }) {
            Row { TextButton({rows=rows+blank("Food",rows.first().stamp)}){Text("+ Food")}
                TextButton({rows=rows+blank("Exercise",rows.first().stamp)}){Text("+ Exercise")} }
            if(onCorrect!=null) TextButton({onCorrect(rows)}){Text("Describe a draft correction")}
            if(rows.any { it.kind=="Food" }) {
                Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) { Checkbox(favorite,{favorite=it});Text("Favorite individual foods") }
                Field("Save as reusable meal (optional)",recipe){recipe=it}
            }
        }
        if(existing) TextButton({confirm=true}) {Text("Delete entry",color=MaterialTheme.colorScheme.error)}
    }
    if(confirm) AlertDialog({confirm=false},title={Text("Delete this entry?")},text={Text(initial.first().name.ifBlank { initial.first().kind })},
        confirmButton={TextButton({confirm=false;onDelete(initial.first())}){Text("Delete")}},dismissButton={TextButton({confirm=false}){Text("Cancel")}})
}
