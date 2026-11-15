package dev.nutrilog

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.util.Locale

@Composable fun CheckOption(label:String,checked:Boolean,change:(Boolean)->Unit) {
    Row(Modifier.fillMaxWidth().toggleable(checked,role=Role.Checkbox,onValueChange=change).heightIn(min=48.dp),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        Checkbox(checked,null);Text(label)
    }
}

@Composable fun BodyFatPanel(rows:List<Record>,unit:String,prefs:Map<String,String>,pref:(String,String)->Unit,add:(String)->Unit) {
    val enabled=prefs["Body fat estimate"]=="On"
    CheckOption("Calculate body fat percentage",enabled){pref("Body fat estimate",if(it)"On" else "Off")}
    if(!enabled)return
    val method=prefs["Tape formula"].orEmpty()
    Choices("Navy formula",method.ifBlank {"Choose"},listOf("Men","Women")){pref("Tape formula",it)}
    if(method !in listOf("Men","Women"))return
    val female=method=="Women"
    val names=if(female) listOf("Height","Waist","Neck","Hips") else listOf("Height","Waist","Neck")
    val today=LocalDate.now(zone).toString()
    val readings=names.associateWith { name ->
        rows.filter { it.kind=="Measurement" && it.day<=today && (it.name.trim().equals(name,true) ||
            (name=="Hips" && it.name.trim().equals("hip",true)) ||
            (!female && name=="Waist" && it.name.trim().equals("abdomen",true))) }.maxByOrNull {it.stamp}
    }
    Text(if(female) "Use the natural waist, hips at the widest point, and neck just below the larynx."
        else "Use the relaxed abdomen at the navel and neck just below the larynx.")
    names.forEach { name->
        val r=readings[name]
        TextButton({add(name)}){Text(if(r==null) "+ Enter "+name.lowercase() else name+"   "+display(r.number("Value")/metricValue(1.0,unit))+" "+unit+"   ·   "+r.day)}
    }
    if(readings.values.all {it!=null}) {
        fun cm(k:String)=readings[k]!!.number("Value")
        val result=estimatedBodyFat(female,cm("Height"),cm("Waist"),cm("Neck"),readings["Hips"]?.number("Value"))
        Text(result?.let {String.format(Locale.getDefault(),"Estimated body fat  %.1f%%",it)} ?: "Check measurement values and units.",
            style=MaterialTheme.typography.titleLarge)
    }
}
