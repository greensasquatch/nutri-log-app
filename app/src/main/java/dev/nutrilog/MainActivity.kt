package dev.nutrilog

import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import kotlinx.coroutines.*
import java.time.LocalDate

class MainActivity:ComponentActivity(){
    private val db by lazy { Journal(applicationContext) }
    private val settings by lazy {getSharedPreferences("preferences",MODE_PRIVATE)}
    private val keys by lazy {Keys(this)}
    private val plan by lazy { PlanSession(this,keys) }
    override fun onCreate(savedInstanceState:Bundle?) {super.onCreate(savedInstanceState);keys.save("OpenAI","");keys.save("Claude","");setContent { App() }}
    @Composable private fun App(){
        var rows by remember { mutableStateOf(db.all()) }
        var prefs by remember { mutableStateOf(settings.all.filterKeys {it in settingKeys}.mapValues {it.value.toString()}) }
        fun pref(k:String,v:String){settings.edit().putString(k,v).apply();prefs=prefs+(k to v)}
        var signedIn by remember { mutableStateOf(plan.signedIn()) }
        var models by remember { mutableStateOf(emptyList<String>()) }
        val model=prefs["Plan model"].orEmpty()
        val weightUnit=prefs["Weight unit"] ?: "lb";val lengthUnit=prefs["Length unit"] ?: "in"
        val dark=when(prefs["Theme"]){"Light"->false;"Dark"->true;else->isSystemInDarkTheme()}
        val scope=rememberCoroutineScope()
        var date by remember { mutableStateOf(LocalDate.now(zone)) }
        val stamp=date.toString()+"T"+now().substringAfter("T")
        val dayRows=rows.filter {it.day==date.toString()}
        var edit by remember {mutableStateOf(false)}
        var drafts by remember {mutableStateOf<List<Record>?>(null)}
        var correction by remember {mutableStateOf<List<Record>?>(null)}
        // Manual editing retains the saved ID. Duplicates, suggestions and AI drafts get new IDs.
        fun open(records:List<Record>,existing:Boolean=false){edit=existing;drafts=records;correction=null}
        var modal by rememberSaveable {mutableStateOf("")}
        var prompt by rememberSaveable {mutableStateOf("")}
        var busy by remember {mutableStateOf(false)}
        var message by remember {mutableStateOf("")}
        var query by rememberSaveable {mutableStateOf("")}
        var restoreData by remember {mutableStateOf<Pair<List<Record>,Map<String,String>>?>(null)}
        var job by remember {mutableStateOf<Job?>(null)}
        val voice=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()){res->
            res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let {prompt=it;modal="input"}
        }
        fun speak(){modal="input";runCatching {voice.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM))}.onFailure {message="Speech recognition unavailable. Type your entry here."}}
        val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){uri->if(uri!=null)scope.launch {
            runCatching {withContext(Dispatchers.IO){requireNotNull(contentResolver.openOutputStream(uri,"wt")).bufferedWriter().use {it.write(backup(db.all(),prefs))}}}.onSuccess {message="Backup exported."}.onFailure { message="Export failed: "+it.message }
        }}
        val import=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)scope.launch {
            runCatching {withContext(Dispatchers.IO){requireNotNull(contentResolver.openInputStream(uri)).bufferedReader().use {restore(it.readText())}}}.onSuccess {restoreData=it}.onFailure {message="Backup rejected: "+it.message}
        }}
        fun request(test:Boolean=false){
            if(busy)return
            busy=true
            val requestText=if(test) "One medium apple" else prompt
            val context=if(test) "" else buildString {
                correction?.let {draft->append("Current UNSAVED draft: "+draft.map { it.json().removeId() }+"\n")}
            }
            job=scope.launch {
                try {
                    val result=withContext(Dispatchers.IO){val selected=model.ifBlank { plan.models().firstOrNull() ?: error("No eligible models available.") }; interpret(selected,plan.access(),requestText,stamp,context)}
                    if(test) message="Connection verified." else {modal="";open(result)}
                }catch(e:CancellationException){throw e}catch(e:Exception){message=e.message ?: "Could not interpret entry. Retry."}
                finally {busy=false}
            }
        }
        val tabs=listOf("Today","History","Goals","Body","Trends")
        val pager=rememberPagerState {tabs.size}
        MaterialTheme(colorScheme=if(dark) darkColorScheme(primary=Color(0xFF93D5BA)) else lightColorScheme(primary=Color(0xFF22644F),primaryContainer=Color(0xFFD0EFDB))){
            Scaffold(topBar={Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal=16.dp),Arrangement.SpaceBetween,androidx.compose.ui.Alignment.CenterVertically){
                Text("NutriLog",style=MaterialTheme.typography.titleLarge);TextButton({modal="settings"}){Text("Settings")}
            }},bottomBar={NavigationBar {tabs.forEachIndexed {i,t->NavigationBarItem(pager.currentPage==i,{scope.launch {pager.animateScrollToPage(i)}},icon={Text(listOf("◉","◷","◎","◇","∿")[i])},label={Text(t)})}}}){padding->
                HorizontalPager(pager,Modifier.padding(padding).fillMaxSize()){i->
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                        Text(tabs[i],style=MaterialTheme.typography.headlineLarge)
                        when(i){
                            0,1->{
                                DayPicker(date){date=it}
                                Summary(dayRows,goalAt(rows,date.toString()),prefs["Net"]=="On")
                                if(i==0){
                                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                                        Button({correction=null;modal="input"}){Text("New entry")}
                                        OutlinedButton({correction=null;speak()}){Text("Voice entry")}
                                    }
                                    val period=blank("Food",stamp)["Meal"]
                                    val suggestions=rows.filter {it.kind=="Food" || it.kind=="Saved food"}.groupBy {it.name.lowercase()}.values.sortedByDescending {items->
                                        items.count {it.kind=="Saved food"}*20+items.count {it["Meal"]==period}*3+items.size-if(items.any {it.day==date.toString()}) 3 else 0
                                    }.take(3)
                                    if(suggestions.isNotEmpty()) Text("Suggested for "+period.lowercase(),style=MaterialTheme.typography.titleMedium)
                                    suggestions.forEach {items->val r=items.first();TextButton({open(listOf(r.fresh("Food",stamp).with("Meal",period)))}){Text("+ "+r.name+"   ·   "+display(r.number("Calories"))+" kcal")}}
                                    OutlinedButton({modal="library"}){Text("Saved foods and meals")}
                                }
                                EntryList(dayRows,{open(listOf(it),true)},{items->open(items.map {it.fresh(time=now())})})
                            }
                            2->{
                                val goals=goalAt(rows,date.toString())
                                DayPicker(date){date=it}
                                Summary(dayRows,goals,false)
                                Button({open(listOf((goals ?: blank("Goals")).fresh("Goals",stamp)))}){Text("Set goals from this date")}
                                val target=goals?.number("Target kg") ?: 0.0
                                if(target>0){
                                    val current=rows.firstOrNull {it.kind=="Weight" && it.day<=date.toString()}?.number("Value")
                                    Text("Weight goal · "+display(target/metricValue(1.0,weightUnit))+" "+weightUnit)
                                    if(current!=null){Text(display(kotlin.math.abs(current-target)/metricValue(1.0,weightUnit))+" "+weightUnit+" from target")
                                        val start=goals!!.number("Starting kg")
                                        if(start>0 && start!=target)Text(display(100*(current-start)/(target-start))+"% of weight change goal")}
                                }
                                Text("Goal history",style=MaterialTheme.typography.titleMedium)
                                rows.filter {it.kind=="Goals"}.forEach {r->TextButton({open(listOf(r),true)}){Text(r.day+"   ·   "+r["Calories"]+" kcal")}}
                            }
                            3->{
                                Row {Button({open(listOf(blank("Weight")))}){Text("+ Weight")};TextButton({open(listOf(blank("Measurement")))}){Text("+ Measurement")}}
                                val body=rows.filter {it.kind in listOf("Weight","Measurement")}
                                if(body.isEmpty())Text("Record your first weight or measurement.")
                                body.forEach {r->val unit=if(r.kind=="Weight")weightUnit else lengthUnit
                                    ListItem(headlineContent={Text(r.name+"   "+display(r.number("Value")/metricValue(1.0,unit))+" "+unit)},supportingContent={Text(r.stamp.replace("T"," "))},modifier=Modifier.clickable {open(listOf(r),true)})}
                            }
                            4->Trends(rows,weightUnit,lengthUnit)
                        }
                    }
                }
            }
            if(modal=="input") Sheet(if(correction==null)"New entry" else "Correct draft",{job?.cancel();modal="";correction?.let {drafts=it};correction=null}){
                OutlinedTextField(prompt,{prompt=it},label={Text("Describe food or exercise")},minLines=3,modifier=Modifier.fillMaxWidth())
                TextButton({speak()},enabled=!busy){Text("Dictate")}
                if(!signedIn) TextButton({modal="settings"}){Text("Connect ChatGPT in Settings")}
                Button({request()},enabled=signedIn && !busy && prompt.isNotBlank()){Text(if(busy)"Interpreting…" else "Review AI estimate")}
                if(correction==null)Row {TextButton({modal="";open(listOf(blank("Food",stamp)))}){Text("Manual food")};TextButton({modal="";open(listOf(blank("Exercise",stamp)))}){Text("Manual exercise")}}
            }
            if(modal=="library") Sheet("Saved foods and meals",{modal=""}){
                Field("Search",query){query=it}
                val foods=rows.filter {it.kind=="Saved food" && it.name.contains(query,true)}
                val meals=rows.filter {it.kind=="Saved meal item" && it["Recipe"].contains(query,true)}.groupBy {it["Recipe"]}
                if(meals.isEmpty() && foods.isEmpty())Text("Save favorites or name a reusable meal when reviewing a food entry.")
                meals.forEach {(name,items)->TextButton({modal="";open(items.map {it.fresh("Food",stamp)})}){Text("+ "+name+" · "+items.size+" ingredients")}
                    items.forEach {r->TextButton({modal="";open(listOf(r),true)}){Text("Edit "+r.name)}} }
                foods.forEach {r->Row {TextButton({modal="";open(listOf(r.fresh("Food",stamp)))}){Text("+ "+r.name)};TextButton({modal="";open(listOf(r),true)}){Text("Edit")}}}
            }
            if(modal=="settings") Sheet("Settings",{modal="";job?.cancel()}){
                Text("ChatGPT plan",style=MaterialTheme.typography.titleMedium)
                Text("Requests use your ChatGPT plan. Check your app limit and credit settings in ChatGPT before sending an estimate.")
                if(!signedIn) Button(enabled=!busy,onClick={
                    busy=true
                    job=scope.launch {
                        try {
                            withContext(Dispatchers.IO) { plan.signIn { uri -> startActivity(Intent(Intent.ACTION_VIEW,uri)) } }
                            signedIn=true
                            models=withContext(Dispatchers.IO){plan.models()}
                            if(models.isNotEmpty()) pref("Plan model",models.first())
                        } catch(e:Exception) { message=e.message ?: "Sign-in failed." }
                        finally { busy=false }
                    }
                }){Text(if(busy)"Waiting for ChatGPT…" else if(plan.canRetry()) "Retry connection" else "Continue with ChatGPT")}
                else {
                    Text("Connected to ChatGPT")
                    if(models.isNotEmpty()) Choices("Model",model.ifBlank {models.first()},models){pref("Plan model",it)}
                    TextButton(enabled=!busy,onClick={scope.launch {
                        busy=true
                        try {models=withContext(Dispatchers.IO){plan.models()};if(model !in models && models.isNotEmpty())pref("Plan model",models.first())}
                        catch(e:Exception){message=e.message ?: "Could not load models."}
                        finally {busy=false}
                    }}){Text("Load available models")}
                    TextButton(enabled=!busy,onClick={scope.launch {
                        busy=true
                        try {val revoked=withContext(Dispatchers.IO){plan.signOut()};signedIn=false;models=emptyList();pref("Plan model","")
                            if(!revoked)message="Signed out locally. Check connected apps in ChatGPT Settings to revoke access."
                        } finally {busy=false}
                    }}){Text("Sign out")}
                }
                Text("Only the submitted text, entry time and any unsaved draft being corrected go to OpenAI. Manual entries stay on this device.")
                Choices("Weight",weightUnit,listOf("lb","kg")){pref("Weight unit",it)}
                Choices("Length",lengthUnit,listOf("in","cm")){pref("Length unit",it)}
                Choices("Theme",prefs["Theme"] ?: "System",listOf("System","Light","Dark")){pref("Theme",it)}
                Choices("Show net calories",prefs["Net"] ?: "Off",listOf("Off","On")){pref("Net",it)}
                Text("Backups",style=MaterialTheme.typography.titleMedium)
                Text("Choose a local folder or Google Drive in the Android file picker. The exported file contains your journal in readable form.")
                Row {OutlinedButton({export.launch("NutriLog-"+LocalDate.now(zone)+".json")}){Text("Export")}
                    TextButton({import.launch(arrayOf("application/json","text/plain","application/octet-stream"))}){Text("Restore")}}
                TextButton({modal="wipe"}){Text("Erase local data")}
                Text("NutriLog 0.2.1   ·   Database 1")
            }
            if(modal=="wipe") AlertDialog({modal="settings"},title={Text("Erase all local entries and settings?")},text={Text("Export a backup first if you want to keep your journal.")},confirmButton={TextButton({db.save(emptyList(),true);rows=emptyList();settings.edit().clear().apply();prefs=emptyMap();plan.forget();signedIn=false;keys.save("OpenAI","");keys.save("Claude","");prompt="";modal=""}){Text("Erase all")}},dismissButton={TextButton({modal="settings"}){Text("Cancel")}})
            restoreData?.let {(items,values)->AlertDialog({restoreData=null},title={Text("Replace local journal?")},text={Text("Verified backup contains "+items.size+" records. Restoring replaces current entries and settings.")},
                confirmButton={TextButton({runCatching {db.save(items,true);rows=db.all();val e=settings.edit().clear();values.forEach {(k,v)->e.putString(k,v)};e.apply();prefs=values;message="Restore complete."}.onFailure {message="Restore failed: "+it.message};restoreData=null}){Text("Restore")}},dismissButton={TextButton({restoreData=null}){Text("Cancel")}})}
            // Passing no correction callback removes the AI/voice path for every previously saved entry.
            drafts?.let {initial->Editor(initial,edit,{if(it=="Weight")weightUnit else lengthUnit},{drafts=null},{items->db.save(items);rows=db.all();drafts=null;prompt=""},{r->db.delete(r.id);rows=db.all();drafts=null},
                if(edit || initial.any {it.kind !in listOf("Food","Exercise")})null else {items->correction=items;drafts=null;prompt="";modal="input"})}
            if(message.isNotBlank())AlertDialog({message=""},text={Text(message)},confirmButton={TextButton({message=""}){Text("OK")}})
        }
    }
}
private fun org.json.JSONObject.removeId():String { remove("id");return toString() }
