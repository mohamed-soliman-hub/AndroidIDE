package com.androidide.core.editor

import com.androidide.data.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CodeCompletionEngine @Inject constructor() {

    suspend fun getCompletions(code: String, cursorPosition: Int, language: EditorLanguage): List<CompletionItem> =
        withContext(Dispatchers.Default) {
            if (cursorPosition <= 0 || cursorPosition > code.length) return@withContext emptyList()
            val prefix = extractPrefix(code, cursorPosition)
            val ctx    = extractContext(code, cursorPosition)
            when (language) {
                EditorLanguage.KOTLIN, EditorLanguage.JAVA -> getKotlinCompletions(code, prefix, ctx)
                EditorLanguage.XML    -> getXmlCompletions(prefix)
                EditorLanguage.GRADLE -> gradleItems.filter { it.label.startsWith(prefix, true) }
                EditorLanguage.JSON   -> jsonItems.filter { it.label.startsWith(prefix, true) }
                else -> emptyList()
            }
        }

    private fun getKotlinCompletions(code: String, prefix: String, ctx: CompletionContext): List<CompletionItem> {
        val results = mutableListOf<CompletionItem>()
        if (ctx.isDotCompletion()) {
            val obj  = ctx.extractObjectName()
            val type = inferType(code, obj)
            val members = resolveMembers(obj, type).filter { it.label.startsWith(prefix, true) }
            if (members.isNotEmpty()) return members.take(80)
        }
        if (ctx.isImportStatement()) return importItems.filter { it.label.contains(prefix, true) }.take(60)
        if (ctx.isModifierChain())   return modifierItems.filter { it.label.startsWith(prefix, true) }.take(60)
        if (ctx.isComposableContext()) results += composeItems.filter { it.label.startsWith(prefix, true) }
        results += kotlinKeywords.filter { it.startsWith(prefix, true) }.map { ci(it,"keyword",CompletionKind.KEYWORD,it) }
        results += androidClassItems.filter { it.label.startsWith(prefix, true) }
        results += extractUserSymbols(code, prefix)
        return results.distinctBy { it.label }
            .sortedWith(compareBy({ !it.label.startsWith(prefix) }, { it.label.length }, { it.label })).take(80)
    }

    private fun inferType(code: String, varName: String): String {
        listOf(
            Regex("""(?:val|var)\s+${Regex.escape(varName)}\s*:\s*([A-Za-z][A-Za-z0-9<>?,\s]*)"""),
            Regex("""(?:private|protected)\s+(?:val|var)\s+${Regex.escape(varName)}\s*:\s*([A-Za-z][A-Za-z0-9<>]*)"""),
            Regex("""(?:val|var)\s+${Regex.escape(varName)}\s*=\s*([A-Z][A-Za-z0-9]*)"""),
            Regex("""fun\s+\w+[^)]*${Regex.escape(varName)}\s*:\s*([A-Za-z][A-Za-z0-9<>]*)"""),
        ).forEach { r -> r.find(code)?.let { return it.groupValues[1].trim().substringBefore("<").trim() } }
        return when {
            varName in listOf("context","ctx","appContext") || varName.endsWith("Context") -> "Context"
            varName in listOf("intent")  || varName.endsWith("Intent")     -> "Intent"
            varName in listOf("bundle")  || varName.endsWith("Bundle")     -> "Bundle"
            varName in listOf("viewModel","vm") || varName.endsWith("ViewModel") -> "ViewModel"
            varName.endsWith("Flow")     || varName.endsWith("State")       -> "Flow"
            varName.endsWith("List")     || varName.endsWith("Items")       -> "List"
            varName.endsWith("Map")                                          -> "Map"
            varName == "modifier"                                            -> "Modifier"
            varName == "navController"   || varName.endsWith("Controller")  -> "NavController"
            varName.endsWith("Scope")    || varName == "scope"              -> "CoroutineScope"
            varName.endsWith("Dao")      || varName == "db"                 -> "Dao"
            varName.endsWith("Prefs")    || varName == "prefs"              -> "SharedPreferences"
            varName in listOf("gson","json")  -> "Gson"
            varName == "retrofit"             -> "Retrofit"
            varName in listOf("client","okClient") -> "OkHttp"
            varName == "file"            || varName.endsWith("File")        -> "File"
            varName.endsWith("String")   || varName.endsWith("Text")        -> "String"
            else -> varName.replaceFirstChar { it.uppercase() }
        }
    }

    private fun resolveMembers(objName: String, type: String): List<CompletionItem> {
        val k = type.lowercase(); val n = objName.lowercase()
        return typeMembers[k] ?: typeMembers[n]
            ?: typeMembers.entries.find { (key,_) -> k.contains(key) || n.contains(key) }?.value
            ?: when { n.contains("list") || n.endsWith("s") -> typeMembers["list"]!!
                      n.contains("map") -> typeMembers["map"]!!
                      n.contains("flow") -> typeMembers["flow"]!!
                      n.contains("string") || n.contains("text") || n.contains("name") -> typeMembers["string"]!!
                      else -> typeMembers["any"]!! }
    }

    private fun extractUserSymbols(code: String, prefix: String) = buildList<CompletionItem> {
        Regex("""(?:data\s+)?class\s+([A-Z][A-Za-z0-9]*)""").findAll(code).forEach {
            if (it.groupValues[1].startsWith(prefix,true)) add(ci(it.groupValues[1],"class (local)",CompletionKind.CLASS))
        }
        Regex("""fun\s+([a-z][A-Za-z0-9]*)""").findAll(code).forEach {
            if (it.groupValues[1].startsWith(prefix,true)) add(ci(it.groupValues[1],"fun (local)",CompletionKind.METHOD,"${it.groupValues[1]}()"))
        }
        Regex("""(?:val|var)\s+([a-z][A-Za-z0-9]*)""").findAll(code).forEach {
            if (it.groupValues[1].startsWith(prefix,true)) add(ci(it.groupValues[1],"var (local)",CompletionKind.VARIABLE))
        }
    }

    private fun getXmlCompletions(prefix: String) =
        (xmlTagItems + xmlAttrItems + xmlValueItems).filter { it.label.contains(prefix, true) }.take(80)

    private fun extractPrefix(code: String, cursor: Int): String {
        var s = cursor - 1
        while (s >= 0 && (code[s].isLetterOrDigit() || code[s] == '_')) s--
        return code.substring(s + 1, cursor)
    }
    private fun extractContext(code: String, cursor: Int): CompletionContext {
        val ls = code.lastIndexOf('\n', cursor - 1) + 1
        return CompletionContext(code.substring(ls, cursor), code.substring(maxOf(0,cursor-2000),cursor), code, cursor)
    }
    private fun ci(l:String,d:String,k:CompletionKind,i:String=l) =
        CompletionItem(l,d,"",k,i,l)
    private fun snip(l:String,d:String,i:String) = CompletionItem(l,d,"",CompletionKind.SNIPPET,i,l)
    private fun meth(l:String,d:String,i:String="$l()") = CompletionItem(l,d,"",CompletionKind.METHOD,i,l)
    private fun prop(l:String,d:String)                 = CompletionItem(l,d,"",CompletionKind.PROPERTY,l,l)

    // ════════════════════════════════════════════════════════════════════════
    // KEYWORDS
    // ════════════════════════════════════════════════════════════════════════
    private val kotlinKeywords = listOf(
        "abstract","actual","annotation","as","as?","break","by","catch","class","companion",
        "const","constructor","continue","crossinline","data","delegate","do","dynamic",
        "else","enum","expect","external","false","field","file","final","finally","for",
        "fun","get","if","import","in","infix","init","inline","inner","interface","internal",
        "is","it","lateinit","noinline","null","object","open","operator","out","override",
        "package","private","protected","public","reified","return","sealed","set","super",
        "suspend","tailrec","this","throw","true","try","typealias","typeof","val","var",
        "vararg","when","where","while"
    )

    // ════════════════════════════════════════════════════════════════════════
    // TYPE MEMBERS DATABASE
    // ════════════════════════════════════════════════════════════════════════
    private val typeMembers: Map<String,List<CompletionItem>> = buildMap {
        put("string", listOf(prop("length","Int"),meth("isEmpty","Boolean","isEmpty()"),meth("isNotEmpty","Boolean","isNotEmpty()"),meth("isBlank","Boolean","isBlank()"),meth("isNotBlank","Boolean","isNotBlank()"),meth("uppercase","String","uppercase()"),meth("lowercase","String","lowercase()"),meth("trim","String","trim()"),meth("trimStart","String","trimStart()"),meth("trimEnd","String","trimEnd()"),meth("contains","Boolean","contains(\"\${1:value}\")"),meth("startsWith","Boolean","startsWith(\"\${1:prefix}\")"),meth("endsWith","Boolean","endsWith(\"\${1:suffix}\")"),meth("replace","String","replace(\"\${1:old}\", \"\${2:new}\")"),meth("replaceFirst","String","replaceFirst(\"\${1:old}\", \"\${2:new}\")"),meth("split","List<String>","split(\"\${1:,}\")"),meth("substring","String","substring(\${1:start}, \${2:end})"),meth("indexOf","Int","indexOf(\"\${1:value}\")"),meth("lastIndexOf","Int","lastIndexOf(\"\${1:value}\")"),meth("toInt","Int","toInt()"),meth("toIntOrNull","Int?","toIntOrNull()"),meth("toLong","Long","toLong()"),meth("toDouble","Double","toDouble()"),meth("toFloat","Float","toFloat()"),meth("toBoolean","Boolean","toBoolean()"),meth("toCharArray","CharArray","toCharArray()"),meth("toByteArray","ByteArray","toByteArray()"),meth("lines","List<String>","lines()"),meth("first","Char","first()"),meth("last","Char","last()"),meth("reversed","String","reversed()"),meth("repeat","String","repeat(\${1:n})"),meth("padStart","String","padStart(\${1:n},' ')"),meth("padEnd","String","padEnd(\${1:n},' ')"),meth("matches","Boolean","matches(Regex(\"\${1:pattern}\"))"),meth("ifEmpty","String","ifEmpty { \"\${1:default}\" }"),meth("ifBlank","String","ifBlank { \"\${1:default}\" }"),meth("orEmpty","String","orEmpty()"),meth("count","Int","count()"),snip("filter","String","filter { it.isLetter() }"),snip("forEach","Unit","forEach { c -> \${1:} }"),meth("chunked","List<String>","chunked(\${1:size})"),meth("drop","String","drop(\${1:n})"),meth("take","String","take(\${1:n})"),meth("removePrefix","String","removePrefix(\"\${1:prefix}\")"),meth("removeSuffix","String","removeSuffix(\"\${1:suffix}\")"),meth("compareTo","Int","compareTo(\"\${1:other}\")"),meth("format","String","format(\${1:args})")))
        put("list", listOf(prop("size","Int"),prop("indices","IntRange"),prop("lastIndex","Int"),meth("isEmpty","Boolean","isEmpty()"),meth("isNotEmpty","Boolean","isNotEmpty()"),meth("first","T","first()"),meth("firstOrNull","T?","firstOrNull()"),meth("last","T","last()"),meth("lastOrNull","T?","lastOrNull()"),meth("get","T","get(\${1:index})"),meth("getOrNull","T?","getOrNull(\${1:index})"),meth("getOrElse","T","getOrElse(\${1:index}) { \${2:default} }"),meth("contains","Boolean","contains(\${1:element})"),meth("containsAll","Boolean","containsAll(\${1:list})"),meth("indexOf","Int","indexOf(\${1:element})"),meth("lastIndexOf","Int","lastIndexOf(\${1:element})"),snip("filter","List<T>","filter { \${1:condition} }"),snip("filterNot","List<T>","filterNot { \${1:condition} }"),meth("filterNotNull","List<T>","filterNotNull()"),meth("filterIsInstance","List<R>","filterIsInstance<\${1:Type}>()"),snip("map","List<R>","map { it -> \${1:transform} }"),snip("mapNotNull","List<R>","mapNotNull { it -> \${1:transform} }"),snip("flatMap","List<R>","flatMap { it -> \${1:list} }"),meth("flatten","List<T>","flatten()"),snip("forEach","Unit","forEach { item ->\n    \${1:}\n}"),snip("forEachIndexed","Unit","forEachIndexed { index, item ->\n    \${1:}\n}"),snip("find","T?","find { \${1:condition} }"),snip("findLast","T?","findLast { \${1:condition} }"),snip("any","Boolean","any { \${1:condition} }"),snip("all","Boolean","all { \${1:condition} }"),snip("none","Boolean","none { \${1:condition} }"),snip("count","Int","count { \${1:condition} }"),meth("sum","T","sum()"),snip("sumOf","R","sumOf { it.\${1:value} }"),meth("maxOrNull","T?","maxOrNull()"),meth("minOrNull","T?","minOrNull()"),snip("maxByOrNull","T?","maxByOrNull { it.\${1:value} }"),snip("minByOrNull","T?","minByOrNull { it.\${1:value} }"),snip("sortedBy","List<T>","sortedBy { it.\${1:value} }"),snip("sortedByDescending","List<T>","sortedByDescending { it.\${1:value} }"),snip("sortedWith","List<T>","sortedWith(compareBy { it.\${1:value} })"),snip("groupBy","Map","groupBy { it.\${1:key} }"),snip("associateBy","Map","associateBy { it.\${1:key} }"),snip("associate","Map","associate { it -> \${1:key} to \${2:value} }"),meth("zip","List<Pair>","zip(\${1:other})"),meth("chunked","List<List<T>>","chunked(\${1:size})"),meth("windowed","List<List<T>>","windowed(\${1:size})"),meth("distinct","List<T>","distinct()"),snip("distinctBy","List<T>","distinctBy { it.\${1:key} }"),meth("take","List<T>","take(\${1:n})"),meth("takeLast","List<T>","takeLast(\${1:n})"),meth("drop","List<T>","drop(\${1:n})"),meth("dropLast","List<T>","dropLast(\${1:n})"),meth("reversed","List<T>","reversed()"),meth("shuffled","List<T>","shuffled()"),meth("plus","List<T>","plus(\${1:other})"),meth("minus","List<T>","minus(\${1:element})"),meth("toMutableList","MutableList<T>","toMutableList()"),meth("toSet","Set<T>","toSet()"),meth("toMap","Map","toMap()"),meth("joinToString","String","joinToString(\"\${1:, }\")"),snip("reduce","T","reduce { acc, it -> \${1:acc + it} }"),snip("fold","R","fold(\${1:initial}) { acc, it -> \${2:} }"),snip("partition","Pair","partition { \${1:condition} }"),meth("iterator","Iterator","iterator()"),meth("subList","List<T>","subList(\${1:from}, \${2:to})"),meth("add","Unit","add(\${1:element})"),meth("addAll","Unit","addAll(\${1:elements})"),meth("remove","Boolean","remove(\${1:element})"),meth("removeAt","T","removeAt(\${1:index})"),meth("clear","Unit","clear()"),meth("set","T","set(\${1:index}, \${2:element})"),snip("retainAll","Boolean","retainAll { \${1:condition} }"),snip("removeAll","Boolean","removeAll { \${1:condition} }"),snip("sortBy","Unit","sortBy { it.\${1:value} }"),meth("shuffle","Unit","shuffle()"),meth("fill","Unit","fill(\${1:value})")))
        put("map", listOf(prop("size","Int"),prop("keys","Set<K>"),prop("values","Collection<V>"),prop("entries","Set<Entry>"),meth("isEmpty","Boolean","isEmpty()"),meth("isNotEmpty","Boolean","isNotEmpty()"),meth("get","V?","get(\${1:key})"),meth("getOrDefault","V","getOrDefault(\${1:key}, \${2:default})"),meth("getOrElse","V","getOrElse(\${1:key}) { \${2:default} }"),meth("containsKey","Boolean","containsKey(\${1:key})"),meth("containsValue","Boolean","containsValue(\${1:value})"),snip("forEach","Unit","forEach { (key, value) ->\n    \${1:}\n}"),snip("map","Map","map { (k, v) -> \${1:} }"),snip("filter","Map","filter { (k, v) -> \${1:} }"),snip("mapKeys","Map","mapKeys { (k, _) -> \${1:k} }"),snip("mapValues","Map","mapValues { (_, v) -> \${1:v} }"),snip("any","Boolean","any { (k, v) -> \${1:} }"),snip("all","Boolean","all { (k, v) -> \${1:} }"),snip("count","Int","count { (k, v) -> \${1:} }"),meth("plus","Map","plus(\${1:other})"),meth("minus","Map","minus(\${1:key})"),meth("toMutableMap","MutableMap","toMutableMap()"),meth("toList","List<Pair>","toList()"),meth("put","V?","put(\${1:key}, \${2:value})"),meth("putAll","Unit","putAll(\${1:map})"),meth("remove","V?","remove(\${1:key})"),meth("clear","Unit","clear()"),meth("putIfAbsent","V?","putIfAbsent(\${1:key}, \${2:value})")))
        put("flow", listOf(snip("collect","Unit","collect { value ->\n    \${1:}\n}"),snip("collectLatest","Unit","collectLatest { value ->\n    \${1:}\n}"),meth("collectAsState","State<T>","collectAsState()"),meth("collectAsStateWithLifecycle","State<T>","collectAsStateWithLifecycle()"),snip("map","Flow<R>","map { value -> \${1:transform} }"),snip("filter","Flow<T>","filter { value -> \${1:condition} }"),snip("filterNot","Flow<T>","filterNot { value -> \${1:condition} }"),meth("filterNotNull","Flow<T>","filterNotNull()"),meth("take","Flow<T>","take(\${1:n})"),meth("drop","Flow<T>","drop(\${1:n})"),snip("onEach","Flow<T>","onEach { value ->\n    \${1:}\n}"),snip("onStart","Flow<T>","onStart {\n    \${1:}\n}"),snip("onCompletion","Flow<T>","onCompletion {\n    \${1:}\n}"),snip("catch","Flow<T>","catch { e ->\n    \${1:}\n}"),meth("retry","Flow<T>","retry(\${1:3}) { true }"),meth("debounce","Flow<T>","debounce(\${1:300L})"),meth("sample","Flow<T>","sample(\${1:300L})"),meth("distinctUntilChanged","Flow<T>","distinctUntilChanged()"),snip("flatMapLatest","Flow<R>","flatMapLatest { value -> \${1:flow} }"),snip("flatMapConcat","Flow<R>","flatMapConcat { value -> \${1:flow} }"),snip("flatMapMerge","Flow<R>","flatMapMerge { value -> \${1:flow} }"),meth("zip","Flow<R>","zip(\${1:other}) { a, b -> \${2:a to b} }"),meth("combine","Flow<R>","combine(\${1:other}) { a, b -> \${2:a to b} }"),meth("first","T","first()"),meth("firstOrNull","T?","firstOrNull()"),meth("toList","List<T>","toList()"),meth("flowOn","Flow<T>","flowOn(Dispatchers.\${1:IO})"),meth("shareIn","SharedFlow<T>","shareIn(scope, SharingStarted.Lazily)"),meth("stateIn","StateFlow<T>","stateIn(scope, SharingStarted.Eagerly, \${1:initial})"),prop("value","T (StateFlow current)"),meth("emit","Unit","emit(\${1:value})"),meth("tryEmit","Boolean","tryEmit(\${1:value})"),snip("update","Unit","update { state -> \${1:newState} }"),snip("updateAndGet","T","updateAndGet { state -> \${1:newState} }")))
        put("viewmodel", listOf(prop("viewModelScope","CoroutineScope"),snip("onCleared","Unit","override fun onCleared() {\n    super.onCleared()\n    \${1:}\n}")))
        put("context", listOf(meth("startActivity","Unit","startActivity(Intent(this, \${1:Activity}::class.java))"),meth("startService","ComponentName?","startService(Intent(this, \${1:Service}::class.java))"),meth("stopService","Boolean","stopService(Intent(this, \${1:Service}::class.java))"),meth("sendBroadcast","Unit","sendBroadcast(Intent(\"\${1:action}\"))"),meth("registerReceiver","Intent?","registerReceiver(\${1:receiver}, IntentFilter(\"\${2:action}\"))"),meth("unregisterReceiver","Unit","unregisterReceiver(\${1:receiver})"),meth("getSystemService","T?","getSystemService(\${1:Context.NOTIFICATION_SERVICE})"),meth("getSharedPreferences","SharedPreferences","getSharedPreferences(\"\${1:name}\", Context.MODE_PRIVATE)"),meth("getExternalFilesDir","File?","getExternalFilesDir(\${1:null})"),meth("getFilesDir","File","getFilesDir()"),meth("getCacheDir","File","getCacheDir()"),prop("packageName","String"),prop("applicationContext","Context"),prop("resources","Resources"),prop("assets","AssetManager"),prop("contentResolver","ContentResolver"),meth("getString","String","getString(R.string.\${1:name})"),meth("getColor","Int","getColor(R.color.\${1:name})"),meth("getDrawable","Drawable?","getDrawable(R.drawable.\${1:name})"),meth("checkSelfPermission","Int","checkSelfPermission(android.Manifest.permission.\${1:CAMERA})")))
        put("intent", listOf(meth("putExtra","Intent","putExtra(\"\${1:key}\", \${2:value})"),meth("getStringExtra","String?","getStringExtra(\"\${1:key}\")"),meth("getIntExtra","Int","getIntExtra(\"\${1:key}\", \${2:0})"),meth("getBooleanExtra","Boolean","getBooleanExtra(\"\${1:key}\", \${2:false})"),meth("getLongExtra","Long","getLongExtra(\"\${1:key}\", \${2:0L})"),meth("getParcelableExtra","T?","getParcelableExtra<\${1:Type}>(\"\${2:key}\")"),prop("action","String?"),prop("data","Uri?"),prop("type","String?"),prop("flags","Int"),prop("extras","Bundle?"),meth("addFlags","Intent","addFlags(Intent.FLAG_ACTIVITY_\${1:NEW_TASK})"),meth("setFlags","Intent","setFlags(Intent.FLAG_ACTIVITY_\${1:CLEAR_TOP})"),meth("setAction","Intent","setAction(\"\${1:action}\")"),meth("setData","Intent","setData(Uri.parse(\"\${1:uri}\"))"),meth("hasExtra","Boolean","hasExtra(\"\${1:key}\")"),meth("removeExtra","Unit","removeExtra(\"\${1:key}\")")))
        put("navcontroller", listOf(meth("navigate","Unit","navigate(\"\${1:route}\")"),meth("navigateUp","Boolean","navigateUp()"),meth("popBackStack","Boolean","popBackStack()"),meth("clearBackStack","Boolean","clearBackStack(\"\${1:route}\")"),meth("getBackStackEntry","NavBackStackEntry","getBackStackEntry(\"\${1:route}\")"),prop("currentDestination","NavDestination?"),prop("currentBackStackEntry","NavBackStackEntry?"),prop("graph","NavGraph")))
        put("coroutinescope", listOf(snip("launch","Job","launch {\n    \${1:}\n}"),snip("async","Deferred<T>","async {\n    \${1:}\n}"),meth("cancel","Unit","cancel()"),prop("coroutineContext","CoroutineContext")))
        put("sharedpreferences", listOf(meth("getString","String?","getString(\"\${1:key}\", \"\${2:default}\")"),meth("getInt","Int","getInt(\"\${1:key}\", \${2:0})"),meth("getBoolean","Boolean","getBoolean(\"\${1:key}\", \${2:false})"),meth("getLong","Long","getLong(\"\${1:key}\", \${2:0L})"),meth("getFloat","Float","getFloat(\"\${1:key}\", \${2:0f})"),meth("contains","Boolean","contains(\"\${1:key}\")"),prop("all","Map<String,*>"),snip("edit","Editor","edit() {\n    putString(\"\${1:key}\", \${2:value})\n}")))
        put("retrofit", listOf(meth("create","T","create(\${1:ApiService}::class.java)"),meth("baseUrl","Retrofit.Builder","baseUrl(\"\${1:https://api.example.com/}\")"),meth("addConverterFactory","Retrofit.Builder","addConverterFactory(GsonConverterFactory.create())"),meth("client","Retrofit.Builder","client(\${1:okHttpClient})"),meth("build","Retrofit","build()")))
        put("gson", listOf(meth("toJson","String","toJson(\${1:object})"),meth("fromJson","T","fromJson(\${1:json}, \${2:Type}::class.java)"),meth("toJsonTree","JsonElement","toJsonTree(\${1:object})"),meth("newBuilder","GsonBuilder","newBuilder()")))
        put("dao", listOf(meth("getAll","List<T>","getAll()"),meth("getById","T?","getById(\${1:id})"),meth("insert","Long","insert(\${1:entity})"),meth("insertAll","List<Long>","insertAll(\${1:entities})"),meth("update","Int","update(\${1:entity})"),meth("delete","Int","delete(\${1:entity})"),meth("deleteAll","Unit","deleteAll()")))
        put("file", listOf(meth("exists","Boolean","exists()"),prop("isDirectory","Boolean"),prop("isFile","Boolean"),prop("name","String"),prop("nameWithoutExtension","String"),prop("extension","String"),prop("absolutePath","String"),prop("path","String"),prop("parent","String?"),prop("parentFile","File?"),meth("length","Long","length()"),meth("readText","String","readText()"),meth("writeText","Unit","writeText(\"\${1:content}\")"),meth("appendText","Unit","appendText(\"\${1:content}\")"),meth("readLines","List<String>","readLines()"),meth("readBytes","ByteArray","readBytes()"),meth("writeBytes","Unit","writeBytes(\${1:bytes})"),meth("listFiles","Array<File>?","listFiles()"),snip("listFiles","Array<File>?","listFiles { f -> \${1:filter} }"),meth("walkTopDown","FileTreeWalk","walkTopDown()"),meth("mkdir","Boolean","mkdir()"),meth("mkdirs","Boolean","mkdirs()"),meth("createNewFile","Boolean","createNewFile()"),meth("delete","Boolean","delete()"),meth("deleteRecursively","Boolean","deleteRecursively()"),meth("renameTo","Boolean","renameTo(File(\"\${1:path}\"))"),meth("copyTo","File","copyTo(File(\"\${1:dest}\"))"),meth("lastModified","Long","lastModified()"),meth("canRead","Boolean","canRead()"),meth("canWrite","Boolean","canWrite()"),snip("forEachLine","Unit","forEachLine { line ->\n    \${1:}\n}"),snip("useLines","T","useLines { lines ->\n    \${1:}\n}")))
        put("any", listOf(meth("toString","String","toString()"),meth("hashCode","Int","hashCode()"),meth("equals","Boolean","equals(\${1:other})"),snip("also","T","also {\n    \${1:}\n}"),snip("apply","T","apply {\n    \${1:}\n}"),snip("let","R","let { it ->\n    \${1:}\n}"),snip("run","R","run {\n    \${1:}\n}"),snip("takeIf","T?","takeIf { \${1:condition} }"),snip("takeUnless","T?","takeUnless { \${1:condition} }")))
    }

    // ════════════════════════════════════════════════════════════════════════
    // JETPACK COMPOSE — 100+ items
    // ════════════════════════════════════════════════════════════════════════
    private val composeItems = listOf(
        snip("remember","State","remember { mutableStateOf(\${1:value}) }"),
        snip("rememberSaveable","State","rememberSaveable { mutableStateOf(\${1:value}) }"),
        snip("mutableStateOf","State<T>","mutableStateOf(\${1:value})"),
        snip("mutableStateListOf","SnapshotStateList","mutableStateListOf(\${1:})"),
        snip("mutableStateMapOf","SnapshotStateMap","mutableStateMapOf()"),
        snip("derivedStateOf","State<T>","derivedStateOf { \${1:expression} }"),
        snip("produceState","State<T>","produceState(initialValue = \${1:value}) {\n    \${2:}\n}"),
        snip("snapshotFlow","Flow","snapshotFlow { \${1:state.value} }"),
        snip("collectAsState","State<T>","collectAsState()"),
        snip("collectAsStateWithLifecycle","State<T>","collectAsStateWithLifecycle()"),
        snip("LaunchedEffect","Unit","LaunchedEffect(\${1:key1}) {\n    \${2:// suspending code}\n}"),
        snip("DisposableEffect","Unit","DisposableEffect(\${1:key1}) {\n    onDispose {\n        \${2:// cleanup}\n    }\n}"),
        snip("SideEffect","Unit","SideEffect {\n    \${1:}\n}"),
        snip("rememberCoroutineScope","CoroutineScope","rememberCoroutineScope()"),
        snip("rememberUpdatedState","State<T>","rememberUpdatedState(\${1:value})"),
        snip("rememberScrollState","ScrollState","rememberScrollState()"),
        snip("rememberLazyListState","LazyListState","rememberLazyListState()"),
        snip("rememberLazyGridState","LazyGridState","rememberLazyGridState()"),
        snip("rememberPagerState","PagerState","rememberPagerState(pageCount = { \${1:pages} })"),
        snip("rememberNavController","NavHostController","rememberNavController()"),
        snip("rememberModalBottomSheetState","ModalBottomSheetState","rememberModalBottomSheetState()"),
        snip("rememberInfiniteTransition","InfiniteTransition","rememberInfiniteTransition()"),
        snip("Column","Composable","Column(\n    modifier = Modifier\${1:.fillMaxWidth()},\n    verticalArrangement = Arrangement.\${2:Top},\n    horizontalAlignment = Alignment.\${3:Start}\n) {\n    \${4:}\n}"),
        snip("Row","Composable","Row(\n    modifier = Modifier\${1:.fillMaxWidth()},\n    horizontalArrangement = Arrangement.\${2:Start},\n    verticalAlignment = Alignment.\${3:CenterVertically}\n) {\n    \${4:}\n}"),
        snip("Box","Composable","Box(\n    modifier = Modifier\${1:.fillMaxSize()},\n    contentAlignment = Alignment.\${2:TopStart}\n) {\n    \${3:}\n}"),
        snip("BoxWithConstraints","Composable","BoxWithConstraints(modifier = Modifier\${1:.fillMaxSize()}) {\n    if (maxWidth < 600.dp) { \${2:} } else { \${3:} }\n}"),
        snip("FlowRow","Composable","FlowRow(\n    modifier = Modifier\${1:.fillMaxWidth()},\n    horizontalArrangement = Arrangement.spacedBy(8.dp)\n) {\n    \${2:}\n}"),
        snip("Scaffold","Composable","Scaffold(\n    topBar = { \${1:} },\n    bottomBar = { \${2:} },\n    floatingActionButton = { \${3:} },\n    snackbarHost = { SnackbarHost(it) }\n) { innerPadding ->\n    \${4:}\n}"),
        snip("TopAppBar","Composable","TopAppBar(\n    title = { Text(\"\${1:Title}\") },\n    navigationIcon = {\n        IconButton(onClick = { \${2:} }) {\n            Icon(Icons.Default.ArrowBack, \"Back\")\n        }\n    },\n    actions = { \${3:} },\n    colors = TopAppBarDefaults.topAppBarColors()\n)"),
        snip("LargeTopAppBar","Composable","LargeTopAppBar(\n    title = { Text(\"\${1:Title}\") },\n    scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()\n)"),
        snip("BottomAppBar","Composable","BottomAppBar {\n    \${1:}\n}"),
        snip("NavigationBar","Composable","NavigationBar {\n    var selected by remember { mutableIntStateOf(0) }\n    listOf(\${1:\"Home\",\"Search\"}).forEachIndexed { i, label ->\n        NavigationBarItem(\n            selected = selected == i, onClick = { selected = i },\n            icon = { Icon(Icons.Default.Home, label) }, label = { Text(label) }\n        )\n    }\n}"),
        snip("NavigationRail","Composable","NavigationRail {\n    NavigationRailItem(selected = true, onClick = {},\n        icon = { Icon(Icons.Default.Home, \"Home\") }, label = { Text(\"Home\") })\n}"),
        snip("NavigationDrawer","Composable","ModalNavigationDrawer(\n    drawerState = rememberDrawerState(DrawerValue.Closed),\n    drawerContent = { ModalDrawerSheet { \${1:} } }\n) {\n    \${2:// screen content}\n}"),
        snip("Text","Composable","Text(\n    text = \"\${1:Hello}\",\n    style = MaterialTheme.typography.\${2:bodyMedium},\n    color = MaterialTheme.colorScheme.\${3:onBackground},\n    maxLines = \${4:Int.MAX_VALUE},\n    overflow = TextOverflow.\${5:Clip}\n)"),
        snip("Button","Composable","Button(\n    onClick = { \${1:} },\n    modifier = Modifier\${2:},\n    enabled = \${3:true}\n) {\n    Text(\"\${4:Button}\")\n}"),
        snip("OutlinedButton","Composable","OutlinedButton(onClick = { \${1:} }) {\n    Text(\"\${2:Button}\")\n}"),
        snip("TextButton","Composable","TextButton(onClick = { \${1:} }) {\n    Text(\"\${2:Button}\")\n}"),
        snip("FilledTonalButton","Composable","FilledTonalButton(onClick = { \${1:} }) {\n    Text(\"\${2:Button}\")\n}"),
        snip("ElevatedButton","Composable","ElevatedButton(onClick = { \${1:} }) {\n    Text(\"\${2:Button}\")\n}"),
        snip("FloatingActionButton","Composable","FloatingActionButton(\n    onClick = { \${1:} },\n    containerColor = MaterialTheme.colorScheme.primary\n) {\n    Icon(Icons.Default.\${2:Add}, null)\n}"),
        snip("ExtendedFloatingActionButton","Composable","ExtendedFloatingActionButton(\n    text = { Text(\"\${1:Label}\") },\n    icon = { Icon(Icons.Default.\${2:Add}, null) },\n    onClick = { \${3:} }\n)"),
        snip("SmallFloatingActionButton","Composable","SmallFloatingActionButton(onClick = { \${1:} }) {\n    Icon(Icons.Default.\${2:Add}, null)\n}"),
        snip("IconButton","Composable","IconButton(onClick = { \${1:} }) {\n    Icon(Icons.Default.\${2:MoreVert}, contentDescription = \"\${3:}\")\n}"),
        snip("FilledIconButton","Composable","FilledIconButton(onClick = { \${1:} }) {\n    Icon(Icons.Default.\${2:Add}, null)\n}"),
        snip("OutlinedIconButton","Composable","OutlinedIconButton(onClick = { \${1:} }) {\n    Icon(Icons.Default.\${2:Add}, null)\n}"),
        snip("TextField","Composable","var \${1:text} by remember { mutableStateOf(\"\") }\nTextField(\n    value = \${1:text},\n    onValueChange = { \${1:text} = it },\n    label = { Text(\"\${2:Label}\") },\n    modifier = Modifier\${3:.fillMaxWidth()},\n    singleLine = \${4:true}\n)"),
        snip("OutlinedTextField","Composable","var \${1:text} by remember { mutableStateOf(\"\") }\nOutlinedTextField(\n    value = \${1:text},\n    onValueChange = { \${1:text} = it },\n    label = { Text(\"\${2:Label}\") },\n    modifier = Modifier\${3:.fillMaxWidth()},\n    singleLine = \${4:true},\n    keyboardOptions = KeyboardOptions(imeAction = ImeAction.\${5:Done}),\n    keyboardActions = KeyboardActions(onDone = { \${6:} })\n)"),
        snip("BasicTextField","Composable","BasicTextField(\n    value = \${1:text},\n    onValueChange = { \${1:text} = it },\n    modifier = Modifier\${2:}\n)"),
        snip("SearchBar","Composable","var \${1:query} by remember { mutableStateOf(\"\") }\nvar \${2:active} by remember { mutableStateOf(false) }\nSearchBar(\n    query = \${1:query}, onQueryChange = { \${1:query} = it },\n    onSearch = { \${3:} }, active = \${2:active}, onActiveChange = { \${2:active} = it }\n) { \${4:} }"),
        snip("Image","Composable","Image(\n    painter = painterResource(id = R.drawable.\${1:ic_launcher}),\n    contentDescription = \"\${2:}\",\n    modifier = Modifier\${3:},\n    contentScale = ContentScale.\${4:Fit}\n)"),
        snip("AsyncImage","Composable","AsyncImage(\n    model = \"\${1:https://example.com/img.jpg}\",\n    contentDescription = \"\${2:}\",\n    modifier = Modifier\${3:.size(100.dp)},\n    contentScale = ContentScale.\${4:Crop}\n)"),
        snip("Icon","Composable","Icon(\n    imageVector = Icons.Default.\${1:Favorite},\n    contentDescription = \"\${2:}\",\n    tint = MaterialTheme.colorScheme.\${3:primary},\n    modifier = Modifier\${4:.size(24.dp)}\n)"),
        snip("Card","Composable","Card(\n    modifier = Modifier\${1:.fillMaxWidth()},\n    shape = RoundedCornerShape(\${2:8}.dp),\n    elevation = CardDefaults.cardElevation(\${3:2}.dp)\n) {\n    \${4:}\n}"),
        snip("ElevatedCard","Composable","ElevatedCard(\n    modifier = Modifier\${1:.fillMaxWidth()},\n    elevation = CardDefaults.cardElevation(\${2:4}.dp)\n) { \${3:} }"),
        snip("OutlinedCard","Composable","OutlinedCard(modifier = Modifier\${1:.fillMaxWidth()}) { \${2:} }"),
        snip("Surface","Composable","Surface(\n    modifier = Modifier\${1:.fillMaxSize()},\n    color = MaterialTheme.colorScheme.\${2:background},\n    tonalElevation = \${3:0}.dp\n) {\n    \${4:}\n}"),
        snip("Spacer","Composable","Spacer(modifier = Modifier.\${1:height(16.dp)})"),
        snip("HorizontalDivider","Composable","HorizontalDivider(\n    modifier = Modifier\${1:},\n    thickness = \${2:1}.dp,\n    color = MaterialTheme.colorScheme.outline\n)"),
        snip("VerticalDivider","Composable","VerticalDivider(modifier = Modifier\${1:}, thickness = \${2:1}.dp)"),
        snip("CircularProgressIndicator","Composable","CircularProgressIndicator(\n    modifier = Modifier\${1:.size(48.dp)},\n    color = MaterialTheme.colorScheme.primary,\n    strokeWidth = \${2:4}.dp\n)"),
        snip("LinearProgressIndicator","Composable","LinearProgressIndicator(\n    progress = { \${1:0.7f} },\n    modifier = Modifier\${2:.fillMaxWidth()},\n    color = MaterialTheme.colorScheme.primary\n)"),
        snip("Checkbox","Composable","var \${1:checked} by remember { mutableStateOf(false) }\nCheckbox(checked = \${1:checked}, onCheckedChange = { \${1:checked} = it })"),
        snip("RadioButton","Composable","RadioButton(selected = \${1:selected}, onClick = { \${2:} })"),
        snip("Switch","Composable","var \${1:checked} by remember { mutableStateOf(false) }\nSwitch(checked = \${1:checked}, onCheckedChange = { \${1:checked} = it })"),
        snip("Slider","Composable","var \${1:value} by remember { mutableFloatStateOf(0.5f) }\nSlider(\n    value = \${1:value},\n    onValueChange = { \${1:value} = it },\n    valueRange = \${2:0f}..\${3:1f}\n)"),
        snip("RangeSlider","Composable","var \${1:range} by remember { mutableStateOf(0.2f..0.8f) }\nRangeSlider(value = \${1:range}, onValueChange = { \${1:range} = it })"),
        snip("AlertDialog","Composable","if (\${1:showDialog}) {\n    AlertDialog(\n        onDismissRequest = { \${1:showDialog} = false },\n        title = { Text(\"\${2:Title}\") },\n        text = { Text(\"\${3:Message}\") },\n        confirmButton = { TextButton(onClick = { \${1:showDialog} = false }) { Text(\"OK\") } },\n        dismissButton = { TextButton(onClick = { \${1:showDialog} = false }) { Text(\"Cancel\") } }\n    )\n}"),
        snip("ModalBottomSheet","Composable","val \${1:sheetState} = rememberModalBottomSheetState()\nif (\${2:show}) {\n    ModalBottomSheet(\n        onDismissRequest = { \${2:show} = false },\n        sheetState = \${1:sheetState}\n    ) {\n        \${3:// sheet content}\n        Spacer(Modifier.height(64.dp))\n    }\n}"),
        snip("Snackbar","Composable","val \${1:snackbarHostState} = remember { SnackbarHostState() }\nLaunchedEffect(\${2:key}) {\n    \${1:snackbarHostState}.showSnackbar(\"\${3:Message}\")\n}"),
        snip("Badge","Composable","BadgedBox(badge = { Badge { Text(\"\${1:5}\") } }) {\n    Icon(Icons.Default.\${2:Notifications}, null)\n}"),
        snip("AssistChip","Composable","AssistChip(\n    onClick = { \${1:} },\n    label = { Text(\"\${2:Label}\") },\n    leadingIcon = { Icon(Icons.Default.\${3:Add}, null, modifier = Modifier.size(18.dp)) }\n)"),
        snip("FilterChip","Composable","var \${1:selected} by remember { mutableStateOf(false) }\nFilterChip(\n    selected = \${1:selected}, onClick = { \${1:selected} = !\${1:selected} },\n    label = { Text(\"\${2:Filter}\") }\n)"),
        snip("InputChip","Composable","InputChip(\n    selected = \${1:false}, onClick = { \${2:} },\n    label = { Text(\"\${3:Label}\") },\n    trailingIcon = { Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp)) }\n)"),
        snip("TabRow","Composable","var \${1:selectedTab} by remember { mutableIntStateOf(0) }\nval \${2:tabs} = listOf(\"\${3:Tab1}\", \"\${4:Tab2}\")\nTabRow(selectedTabIndex = \${1:selectedTab}) {\n    \${2:tabs}.forEachIndexed { i, tab ->\n        Tab(selected = \${1:selectedTab} == i, onClick = { \${1:selectedTab} = i }, text = { Text(tab) })\n    }\n}"),
        snip("ScrollableTabRow","Composable","ScrollableTabRow(\n    selectedTabIndex = \${1:selectedTab},\n    edgePadding = 0.dp\n) { \${2:} }"),
        snip("LazyColumn","Composable","LazyColumn(\n    modifier = Modifier\${1:.fillMaxSize()},\n    contentPadding = PaddingValues(\${2:16}.dp),\n    verticalArrangement = Arrangement.spacedBy(\${3:8}.dp),\n    state = rememberLazyListState()\n) {\n    items(\${4:list}) { item ->\n        \${5:}\n    }\n}"),
        snip("LazyRow","Composable","LazyRow(\n    modifier = Modifier\${1:.fillMaxWidth()},\n    contentPadding = PaddingValues(horizontal = \${2:16}.dp),\n    horizontalArrangement = Arrangement.spacedBy(\${3:8}.dp)\n) {\n    items(\${4:list}) { item ->\n        \${5:}\n    }\n}"),
        snip("LazyVerticalGrid","Composable","LazyVerticalGrid(\n    columns = GridCells.\${1:Adaptive(minSize = 120.dp)},\n    modifier = Modifier\${2:.fillMaxSize()},\n    contentPadding = PaddingValues(\${3:16}.dp),\n    verticalArrangement = Arrangement.spacedBy(\${4:8}.dp),\n    horizontalArrangement = Arrangement.spacedBy(\${4:8}.dp)\n) {\n    items(\${5:list}) { item ->\n        \${6:}\n    }\n}"),
        snip("LazyHorizontalGrid","Composable","LazyHorizontalGrid(\n    rows = GridCells.Fixed(\${1:2}),\n    modifier = Modifier\${2:.height(200.dp)}\n) {\n    items(\${3:list}) { item ->\n        \${4:}\n    }\n}"),
        snip("LazyVerticalStaggeredGrid","Composable","LazyVerticalStaggeredGrid(\n    columns = StaggeredGridCells.Adaptive(\${1:150}.dp),\n    modifier = Modifier\${2:.fillMaxSize()}\n) {\n    items(\${3:list}) { item ->\n        \${4:}\n    }\n}"),
        snip("HorizontalPager","Composable","val \${1:pagerState} = rememberPagerState(pageCount = { \${2:list}.size })\nHorizontalPager(\n    state = \${1:pagerState},\n    modifier = Modifier\${3:.fillMaxWidth()}\n) { page ->\n    \${4:}\n}"),
        snip("VerticalPager","Composable","VerticalPager(\n    state = rememberPagerState(pageCount = { \${1:count} })\n) { page ->\n    \${2:}\n}"),
        snip("AnimatedVisibility","Composable","AnimatedVisibility(\n    visible = \${1:visible},\n    enter = fadeIn() + slideInVertically(),\n    exit  = fadeOut() + slideOutVertically()\n) {\n    \${2:}\n}"),
        snip("AnimatedContent","Composable","AnimatedContent(\n    targetState = \${1:state},\n    transitionSpec = { fadeIn() togetherWith fadeOut() },\n    label = \"\${2:animation}\"\n) { target ->\n    \${3:}\n}"),
        snip("Crossfade","Composable","Crossfade(\n    targetState = \${1:state},\n    animationSpec = tween(\${2:300}),\n    label = \"\${3:crossfade}\"\n) { target ->\n    \${4:}\n}"),
        snip("NavHost","Composable","val \${1:navController} = rememberNavController()\nNavHost(\n    navController = \${1:navController},\n    startDestination = \"\${2:home}\"\n) {\n    composable(\"\${2:home}\") {\n        \${3:HomeScreen}(\${1:navController})\n    }\n    composable(\"\${4:detail}/{id}\") { entry ->\n        \${5:DetailScreen}(entry.arguments?.getString(\"id\"))\n    }\n}"),
        snip("Canvas","Composable","Canvas(modifier = Modifier\${1:.size(200.dp)}) {\n    drawCircle(color = Color.\${2:Blue}, radius = \${3:size.minDimension / 2})\n    drawRect(color = Color.\${4:Red}, size = Size(\${5:100.dp.toPx()}, \${6:50.dp.toPx()}))\n}"),
        snip("AndroidView","Composable","AndroidView(\n    factory = { ctx ->\n        \${1:android.widget.TextView(ctx)}.apply {\n            \${2:text = \"Hello\"}\n        }\n    },\n    modifier = Modifier\${3:},\n    update = { view -> \${4:} }\n)"),
        snip("DropdownMenu","Composable","var \${1:expanded} by remember { mutableStateOf(false) }\nBox {\n    Button(onClick = { \${1:expanded} = true }) { Text(\"\${2:Menu}\") }\n    DropdownMenu(expanded = \${1:expanded}, onDismissRequest = { \${1:expanded} = false }) {\n        DropdownMenuItem(text = { Text(\"\${3:Item}\") }, onClick = { \${4:}; \${1:expanded} = false })\n    }\n}"),
        snip("ExposedDropdownMenuBox","Composable","var \${1:expanded} by remember { mutableStateOf(false) }\nvar \${2:selected} by remember { mutableStateOf(\"\") }\nExposedDropdownMenuBox(expanded = \${1:expanded}, onExpandedChange = { \${1:expanded} = !it }) {\n    OutlinedTextField(\n        value = \${2:selected}, onValueChange = {}, readOnly = true,\n        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(\${1:expanded}) },\n        modifier = Modifier.menuAnchor()\n    )\n    ExposedDropdownMenu(expanded = \${1:expanded}, onDismissRequest = { \${1:expanded} = false }) {\n        listOf(\"\${3:Option 1}\", \"\${4:Option 2}\").forEach { option ->\n            DropdownMenuItem(text = { Text(option) }, onClick = { \${2:selected} = option; \${1:expanded} = false })\n        }\n    }\n}"),
        snip("SwipeToDismissBox","Composable","val \${1:dismissState} = rememberSwipeToDismissBoxState()\nSwipeToDismissBox(\n    state = \${1:dismissState},\n    backgroundContent = { \${2:// background during swipe} }\n) {\n    \${3:// main content}\n}"),
        snip("ListItem","Composable","ListItem(\n    headlineContent = { Text(\"\${1:Title}\") },\n    supportingContent = { Text(\"\${2:Description}\") },\n    leadingContent = { Icon(Icons.Default.\${3:Folder}, null) },\n    trailingContent = { Icon(Icons.Default.\${4:MoreVert}, null) }\n)"),
        snip("DatePickerDialog","Composable","DatePickerDialog(\n    onDismissRequest = { \${1:show} = false },\n    confirmButton = { TextButton(onClick = { \${1:show} = false }) { Text(\"OK\") } }\n) {\n    DatePicker(state = rememberDatePickerState())\n}"),
        snip("TimePicker","Composable","TimePicker(\n    state = rememberTimePickerState(initialHour = 12, initialMinute = 0)\n)"),
        snip("Dialog","Composable","Dialog(\n    onDismissRequest = { \${1:show} = false },\n    properties = DialogProperties(dismissOnClickOutside = true)\n) {\n    Surface(shape = RoundedCornerShape(16.dp)) {\n        \${2:}\n    }\n}"),
        snip("TooltipBox","Composable","TooltipBox(\n    positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),\n    tooltip = { PlainTooltip { Text(\"\${1:Tooltip}\") } },\n    state = rememberTooltipState()\n) {\n    \${2:}\n}"),
    )

    // ════════════════════════════════════════════════════════════════════════
    // MODIFIER COMPLETIONS — 80+ items
    // ════════════════════════════════════════════════════════════════════════
    private val modifierItems = listOf(
        meth("fillMaxSize","Modifier","fillMaxSize()"), meth("fillMaxWidth","Modifier","fillMaxWidth()"), meth("fillMaxHeight","Modifier","fillMaxHeight()"),
        meth("fillMaxSize","Modifier","fillMaxSize(\${1:0.5f})"), meth("fillMaxWidth","Modifier","fillMaxWidth(\${1:0.8f})"),
        meth("wrapContentSize","Modifier","wrapContentSize()"), meth("wrapContentWidth","Modifier","wrapContentWidth()"), meth("wrapContentHeight","Modifier","wrapContentHeight()"),
        meth("size","Modifier","size(\${1:48}.dp)"), meth("size","Modifier","size(width = \${1:100}.dp, height = \${2:48}.dp)"),
        meth("width","Modifier","width(\${1:200}.dp)"), meth("widthIn","Modifier","widthIn(min = \${1:0}.dp, max = \${2:300}.dp)"),
        meth("height","Modifier","height(\${1:48}.dp)"), meth("heightIn","Modifier","heightIn(min = \${1:0}.dp, max = \${2:300}.dp)"),
        meth("requiredSize","Modifier","requiredSize(\${1:48}.dp)"), meth("aspectRatio","Modifier","aspectRatio(\${1:16f/9f})"),
        meth("defaultMinSize","Modifier","defaultMinSize(minWidth = \${1:48}.dp, minHeight = \${2:48}.dp)"),
        meth("padding","Modifier","padding(\${1:16}.dp)"), meth("padding","Modifier","padding(horizontal = \${1:16}.dp, vertical = \${2:8}.dp)"),
        meth("padding","Modifier","padding(start = \${1:}, top = \${2:}, end = \${3:}, bottom = \${4:})"),
        meth("absolutePadding","Modifier","absolutePadding(left = \${1:}, top = \${2:}, right = \${3:}, bottom = \${4:})"),
        meth("offset","Modifier","offset(x = \${1:0}.dp, y = \${2:0}.dp)"), meth("absoluteOffset","Modifier","absoluteOffset(x = \${1:0}.dp, y = \${2:0}.dp)"),
        meth("background","Modifier","background(\${1:Color.Blue})"), meth("background","Modifier","background(\${1:Color.Blue}, \${2:RoundedCornerShape(8.dp)})"),
        meth("clip","Modifier","clip(\${1:RoundedCornerShape(8.dp)})"), meth("clipToBounds","Modifier","clipToBounds()"),
        meth("border","Modifier","border(\${1:1}.dp, \${2:Color.Gray})"), meth("border","Modifier","border(\${1:1}.dp, \${2:Color.Gray}, \${3:RoundedCornerShape(8.dp)})"),
        meth("shadow","Modifier","shadow(\${1:4}.dp, \${2:RoundedCornerShape(8.dp)})"),
        meth("alpha","Modifier","alpha(\${1:0.5f})"),
        snip("drawBehind","Modifier","drawBehind {\n    \${1:drawRect(Color.Blue)}\n}"),
        snip("drawWithContent","Modifier","drawWithContent {\n    drawContent()\n    \${1:}\n}"),
        meth("clickable","Modifier","clickable { \${1:} }"),
        snip("clickable","Modifier","clickable(\n    interactionSource = remember { MutableInteractionSource() },\n    indication = ripple()\n) { \${1:} }"),
        snip("combinedClickable","Modifier","combinedClickable(\n    onClick = { \${1:} },\n    onLongClick = { \${2:} },\n    onDoubleClick = { \${3:} }\n)"),
        snip("selectable","Modifier","selectable(\n    selected = \${1:selected},\n    onClick = { \${2:} }\n)"),
        snip("toggleable","Modifier","toggleable(\n    value = \${1:checked},\n    onValueChange = { \${1:checked} = it }\n)"),
        meth("verticalScroll","Modifier","verticalScroll(rememberScrollState())"),
        meth("horizontalScroll","Modifier","horizontalScroll(rememberScrollState())"),
        meth("nestedScroll","Modifier","nestedScroll(\${1:scrollBehavior}.nestedScrollConnection)"),
        snip("draggable","Modifier","draggable(\n    state = rememberDraggableState { delta ->\n        offset += delta.toInt()\n    },\n    orientation = Orientation.\${1:Horizontal}\n)"),
        snip("transformable","Modifier","transformable(\n    state = rememberTransformableState { zoom, pan, rotation ->\n        scale *= zoom\n        offset += pan\n        rotationAngle += rotation\n    }\n)"),
        snip("pointerInput","Modifier","pointerInput(Unit) {\n    detectTapGestures(\n        onTap = { offset -> \${1:} },\n        onLongPress = { offset -> \${2:} },\n        onDoubleTap = { offset -> \${3:} }\n    )\n}"),
        snip("detectDragGestures","Modifier","pointerInput(Unit) {\n    detectDragGestures { change, dragAmount ->\n        change.consume()\n        offset += dragAmount\n    }\n}"),
        meth("weight","Modifier","weight(\${1:1f})"), meth("align","Modifier","align(\${1:Alignment.Center})"),
        meth("alignBy","Modifier","alignBy(LastBaseline)"), meth("alignByBaseline","Modifier","alignByBaseline()"),
        meth("rotate","Modifier","rotate(\${1:45f})"), meth("scale","Modifier","scale(\${1:1.5f})"),
        meth("scale","Modifier","scale(scaleX = \${1:1.5f}, scaleY = \${2:0.8f})"),
        snip("graphicsLayer","Modifier","graphicsLayer {\n    translationX = \${1:0f}\n    translationY = \${2:0f}\n    rotationZ    = \${3:0f}\n    scaleX       = \${4:1f}\n    scaleY       = \${5:1f}\n    alpha        = \${6:1f}\n}"),
        meth("zIndex","Modifier","zIndex(\${1:1f})"),
        meth("statusBarsPadding","Modifier","statusBarsPadding()"),
        meth("navigationBarsPadding","Modifier","navigationBarsPadding()"),
        meth("systemBarsPadding","Modifier","systemBarsPadding()"),
        meth("imePadding","Modifier","imePadding()"),
        meth("windowInsetsPadding","Modifier","windowInsetsPadding(\${1:WindowInsets.statusBars})"),
        snip("clearAndSetSemantics","Modifier","clearAndSetSemantics {\n    contentDescription = \"\${1:}\"\n}"),
        snip("semantics","Modifier","semantics {\n    contentDescription = \"\${1:}\"\n}"),
        meth("testTag","Modifier","testTag(\"\${1:tag}\")"),
        snip("onGloballyPositioned","Modifier","onGloballyPositioned { coordinates ->\n    \${1:}\n}"),
        snip("onSizeChanged","Modifier","onSizeChanged { size ->\n    \${1:}\n}"),
        snip("layout","Modifier","layout { measurable, constraints ->\n    val placeable = measurable.measure(constraints)\n    layout(placeable.width, placeable.height) {\n        placeable.placeRelative(\${1:0}, \${2:0})\n    }\n}"),
        snip("animateContentSize","Modifier","animateContentSize(\n    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)\n)"),
        meth("blur","Modifier","blur(\${1:8}.dp)"),
        meth("hoverable","Modifier","hoverable(interactionSource = remember { MutableInteractionSource() })"),
        meth("focusable","Modifier","focusable()"),
        meth("focusRequester","Modifier","focusRequester(\${1:focusRequester})"),
        snip("onFocusChanged","Modifier","onFocusChanged { state ->\n    \${1:}\n}"),
        meth("indication","Modifier","indication(remember { MutableInteractionSource() }, ripple())"),
        meth("layoutId","Modifier","layoutId(\"\${1:id}\")")
    )

    // ════════════════════════════════════════════════════════════════════════
    // ANDROID CLASSES
    // ════════════════════════════════════════════════════════════════════════
    private val androidClassItems = listOf(
        ci("Intent","android.content",CompletionKind.CLASS),ci("Bundle","android.os",CompletionKind.CLASS),
        ci("Handler","android.os",CompletionKind.CLASS),ci("Looper","android.os",CompletionKind.CLASS),
        ci("Message","android.os",CompletionKind.CLASS),ci("Parcel","android.os",CompletionKind.CLASS),
        ci("Uri","android.net",CompletionKind.CLASS),ci("Log","android.util",CompletionKind.CLASS),
        ci("Build","android.os",CompletionKind.CLASS),ci("Environment","android.os",CompletionKind.CLASS),
        ci("PowerManager","android.os",CompletionKind.CLASS),ci("ConnectivityManager","android.net",CompletionKind.CLASS),
        ci("NotificationManager","android.app",CompletionKind.CLASS),ci("NotificationCompat","androidx.core.app",CompletionKind.CLASS),
        ci("NotificationChannel","android.app",CompletionKind.CLASS),ci("PendingIntent","android.app",CompletionKind.CLASS),
        ci("AlarmManager","android.app",CompletionKind.CLASS),ci("WorkManager","androidx.work",CompletionKind.CLASS),
        ci("OneTimeWorkRequest","androidx.work",CompletionKind.CLASS),ci("PeriodicWorkRequest","androidx.work",CompletionKind.CLASS),
        ci("CoroutineWorker","androidx.work",CompletionKind.CLASS),ci("Worker","androidx.work",CompletionKind.CLASS),
        ci("SharedPreferences","android.content",CompletionKind.CLASS),ci("ContentResolver","android.content",CompletionKind.CLASS),
        ci("ContentValues","android.content",CompletionKind.CLASS),ci("Context","android.content",CompletionKind.CLASS),
        ci("Resources","android.content.res",CompletionKind.CLASS),ci("Configuration","android.content.res",CompletionKind.CLASS),
        ci("ViewModel","androidx.lifecycle",CompletionKind.CLASS),ci("AndroidViewModel","androidx.lifecycle",CompletionKind.CLASS),
        ci("SavedStateHandle","androidx.lifecycle",CompletionKind.CLASS),ci("LiveData","androidx.lifecycle",CompletionKind.CLASS),
        ci("MutableLiveData","androidx.lifecycle",CompletionKind.CLASS),ci("MediatorLiveData","androidx.lifecycle",CompletionKind.CLASS),
        ci("LifecycleOwner","androidx.lifecycle",CompletionKind.INTERFACE),
        ci("StateFlow","kotlinx.coroutines.flow",CompletionKind.INTERFACE),ci("MutableStateFlow","kotlinx.coroutines.flow",CompletionKind.CLASS),
        ci("SharedFlow","kotlinx.coroutines.flow",CompletionKind.INTERFACE),ci("MutableSharedFlow","kotlinx.coroutines.flow",CompletionKind.CLASS),
        ci("Flow","kotlinx.coroutines.flow",CompletionKind.INTERFACE),ci("CoroutineScope","kotlinx.coroutines",CompletionKind.INTERFACE),
        ci("Dispatchers","kotlinx.coroutines",CompletionKind.CLASS),ci("withContext","kotlinx.coroutines",CompletionKind.METHOD,"withContext(Dispatchers.\${1:IO}) {\n    \${2:}\n}"),
        ci("launch","kotlinx.coroutines",CompletionKind.METHOD,"launch {\n    \${1:}\n}"),ci("async","kotlinx.coroutines",CompletionKind.METHOD,"async {\n    \${1:}\n}"),
        ci("delay","kotlinx.coroutines",CompletionKind.METHOD,"delay(\${1:1000L})"),
        ci("flow","kotlinx.coroutines.flow",CompletionKind.METHOD,"flow {\n    emit(\${1:value})\n}"),
        ci("callbackFlow","kotlinx.coroutines.flow",CompletionKind.METHOD,"callbackFlow {\n    \${1:}\n    awaitClose { \${2:} }\n}"),
        ci("combine","kotlinx.coroutines.flow",CompletionKind.METHOD,"combine(\${1:flow1}, \${2:flow2}) { a, b -> \${3:a to b} }"),
        ci("merge","kotlinx.coroutines.flow",CompletionKind.METHOD,"merge(\${1:flow1}, \${2:flow2})"),
        ci("RoomDatabase","androidx.room",CompletionKind.CLASS),ci("Entity","androidx.room",CompletionKind.CLASS),
        ci("Dao","androidx.room",CompletionKind.INTERFACE),ci("Query","androidx.room",CompletionKind.CLASS),
        ci("Insert","androidx.room",CompletionKind.CLASS),ci("Update","androidx.room",CompletionKind.CLASS),
        ci("Delete","androidx.room",CompletionKind.CLASS),ci("ForeignKey","androidx.room",CompletionKind.CLASS),
        ci("TypeConverter","androidx.room",CompletionKind.CLASS),
        ci("Retrofit","retrofit2",CompletionKind.CLASS),ci("OkHttpClient","okhttp3",CompletionKind.CLASS),
        ci("Request","okhttp3",CompletionKind.CLASS),ci("Response","okhttp3",CompletionKind.CLASS),
        ci("Interceptor","okhttp3",CompletionKind.INTERFACE),ci("HttpLoggingInterceptor","okhttp3.logging",CompletionKind.CLASS),
        ci("GsonConverterFactory","retrofit2.converter.gson",CompletionKind.CLASS),
        ci("Gson","com.google.gson",CompletionKind.CLASS),ci("JsonObject","com.google.gson",CompletionKind.CLASS),
        ci("HiltViewModel","dagger.hilt.android.lifecycle",CompletionKind.CLASS),ci("AndroidEntryPoint","dagger.hilt.android",CompletionKind.CLASS),
        ci("HiltAndroidApp","dagger.hilt.android",CompletionKind.CLASS),ci("Inject","javax.inject",CompletionKind.CLASS),
        ci("Singleton","javax.inject",CompletionKind.CLASS),ci("Module","dagger",CompletionKind.CLASS),
        ci("Provides","dagger",CompletionKind.CLASS),ci("Binds","dagger",CompletionKind.CLASS),
        ci("NavController","androidx.navigation",CompletionKind.CLASS),ci("NavHost","androidx.navigation.compose",CompletionKind.METHOD,"NavHost(\n    navController = \${1:navController},\n    startDestination = \"\${2:home}\"\n) {\n    composable(\"\${2:home}\") { \${3:HomeScreen}() }\n}"),
        ci("composable","androidx.navigation.compose",CompletionKind.METHOD,"composable(\"\${1:route}\") {\n    \${2:Screen}()\n}"),
        ci("navArgument","androidx.navigation.compose",CompletionKind.METHOD,"navArgument(\"\${1:arg}\") { type = NavType.StringType }"),
        ci("hiltViewModel","androidx.hilt.navigation.compose",CompletionKind.METHOD,"hiltViewModel<\${1:ViewModel}>()"),
        ci("DataStore","androidx.datastore",CompletionKind.INTERFACE),
        ci("preferencesDataStore","androidx.datastore.preferences",CompletionKind.METHOD,"preferencesDataStore(name = \"\${1:prefs}\")"),
    )

    // ════════════════════════════════════════════════════════════════════════
    // IMPORTS
    // ════════════════════════════════════════════════════════════════════════
    private val importItems = listOf(
        "androidx.compose.runtime.*","androidx.compose.runtime.remember","androidx.compose.runtime.mutableStateOf",
        "androidx.compose.runtime.getValue","androidx.compose.runtime.setValue","androidx.compose.runtime.collectAsState",
        "androidx.compose.runtime.LaunchedEffect","androidx.compose.runtime.DisposableEffect",
        "androidx.compose.runtime.SideEffect","androidx.compose.runtime.derivedStateOf",
        "androidx.compose.runtime.rememberCoroutineScope","androidx.compose.runtime.snapshotFlow",
        "androidx.compose.runtime.produceState","androidx.compose.runtime.rememberUpdatedState",
        "androidx.compose.ui.*","androidx.compose.ui.Modifier","androidx.compose.ui.Alignment",
        "androidx.compose.ui.draw.clip","androidx.compose.ui.draw.shadow","androidx.compose.ui.draw.alpha",
        "androidx.compose.ui.draw.blur","androidx.compose.ui.draw.rotate","androidx.compose.ui.draw.scale",
        "androidx.compose.ui.text.TextStyle","androidx.compose.ui.text.font.FontWeight",
        "androidx.compose.ui.text.font.FontFamily","androidx.compose.ui.text.style.TextOverflow",
        "androidx.compose.ui.text.style.TextAlign","androidx.compose.ui.text.input.KeyboardType",
        "androidx.compose.ui.text.input.ImeAction","androidx.compose.ui.text.input.PasswordVisualTransformation",
        "androidx.compose.ui.graphics.Color","androidx.compose.ui.graphics.Brush",
        "androidx.compose.ui.graphics.vector.ImageVector","androidx.compose.ui.graphics.Shape",
        "androidx.compose.ui.unit.dp","androidx.compose.ui.unit.sp","androidx.compose.ui.unit.Dp",
        "androidx.compose.foundation.layout.*","androidx.compose.foundation.layout.Column",
        "androidx.compose.foundation.layout.Row","androidx.compose.foundation.layout.Box",
        "androidx.compose.foundation.layout.fillMaxSize","androidx.compose.foundation.layout.fillMaxWidth",
        "androidx.compose.foundation.layout.padding","androidx.compose.foundation.layout.Arrangement",
        "androidx.compose.foundation.layout.PaddingValues","androidx.compose.foundation.layout.WindowInsets",
        "androidx.compose.foundation.*","androidx.compose.foundation.shape.RoundedCornerShape",
        "androidx.compose.foundation.shape.CircleShape","androidx.compose.foundation.background",
        "androidx.compose.foundation.border","androidx.compose.foundation.clickable",
        "androidx.compose.foundation.lazy.LazyColumn","androidx.compose.foundation.lazy.LazyRow",
        "androidx.compose.foundation.lazy.items","androidx.compose.foundation.lazy.itemsIndexed",
        "androidx.compose.foundation.lazy.rememberLazyListState","androidx.compose.foundation.lazy.grid.*",
        "androidx.compose.foundation.lazy.staggeredgrid.*",
        "androidx.compose.material3.*","androidx.compose.material3.MaterialTheme",
        "androidx.compose.material3.Surface","androidx.compose.material3.Text","androidx.compose.material3.Button",
        "androidx.compose.material3.Card","androidx.compose.material3.Scaffold","androidx.compose.material3.TopAppBar",
        "androidx.compose.material3.ExperimentalMaterial3Api","androidx.compose.material3.SnackbarHost",
        "androidx.compose.material.icons.Icons","androidx.compose.material.icons.filled.*",
        "androidx.compose.material.icons.outlined.*","androidx.compose.material.icons.rounded.*",
        "androidx.compose.animation.*","androidx.compose.animation.core.*","androidx.compose.animation.fadeIn",
        "androidx.compose.animation.fadeOut","androidx.compose.animation.slideInHorizontally",
        "androidx.compose.animation.slideOutHorizontally","androidx.compose.animation.AnimatedVisibility",
        "androidx.compose.animation.AnimatedContent","androidx.compose.animation.Crossfade",
        "androidx.lifecycle.ViewModel","androidx.lifecycle.viewModelScope",
        "androidx.lifecycle.compose.collectAsStateWithLifecycle","androidx.lifecycle.lifecycleScope",
        "kotlinx.coroutines.flow.StateFlow","kotlinx.coroutines.flow.MutableStateFlow",
        "kotlinx.coroutines.flow.SharedFlow","kotlinx.coroutines.flow.MutableSharedFlow",
        "kotlinx.coroutines.flow.Flow","kotlinx.coroutines.flow.flow","kotlinx.coroutines.flow.callbackFlow",
        "kotlinx.coroutines.flow.combine","kotlinx.coroutines.flow.map","kotlinx.coroutines.flow.filter",
        "kotlinx.coroutines.launch","kotlinx.coroutines.async","kotlinx.coroutines.delay",
        "kotlinx.coroutines.Dispatchers","kotlinx.coroutines.withContext",
        "android.content.Context","android.content.Intent","android.os.Bundle","android.util.Log",
        "androidx.navigation.compose.NavHost","androidx.navigation.compose.composable",
        "androidx.navigation.compose.rememberNavController","androidx.hilt.navigation.compose.hiltViewModel",
        "dagger.hilt.android.lifecycle.HiltViewModel","dagger.hilt.android.AndroidEntryPoint",
        "javax.inject.Inject","javax.inject.Singleton","androidx.room.*","androidx.room.Room",
        "androidx.work.WorkManager","androidx.work.OneTimeWorkRequestBuilder","androidx.work.CoroutineWorker",
    ).map { ci(it,"import",CompletionKind.CLASS,it) }

    // ════════════════════════════════════════════════════════════════════════
    // XML TAGS — 60+ items
    // ════════════════════════════════════════════════════════════════════════
    private val xmlTagItems = listOf(
        snip("TextView","View","<TextView\n    android:id=\"@+id/\${1:textView}\"\n    android:layout_width=\"wrap_content\"\n    android:layout_height=\"wrap_content\"\n    android:text=\"\${2:Hello World}\"\n    android:textSize=\"\${3:16}sp\" />"),
        snip("EditText","View","<EditText\n    android:id=\"@+id/\${1:editText}\"\n    android:layout_width=\"match_parent\"\n    android:layout_height=\"wrap_content\"\n    android:hint=\"\${2:Enter text}\"\n    android:inputType=\"\${3:text}\"\n    android:imeOptions=\"\${4:actionDone}\" />"),
        snip("Button","View","<Button\n    android:id=\"@+id/\${1:button}\"\n    android:layout_width=\"wrap_content\"\n    android:layout_height=\"wrap_content\"\n    android:text=\"\${2:Click Me}\" />"),
        snip("MaterialButton","View","<com.google.android.material.button.MaterialButton\n    android:id=\"@+id/\${1:btn}\"\n    android:layout_width=\"wrap_content\"\n    android:layout_height=\"wrap_content\"\n    android:text=\"\${2:Button}\"\n    app:cornerRadius=\"\${3:8}dp\" />"),
        snip("ImageView","View","<ImageView\n    android:id=\"@+id/\${1:imageView}\"\n    android:layout_width=\"\${2:match_parent}\"\n    android:layout_height=\"\${3:200dp}\"\n    android:src=\"@drawable/\${4:ic_launcher}\"\n    android:contentDescription=\"\${5:}\"\n    android:scaleType=\"\${6:centerCrop}\" />"),
        snip("RecyclerView","View","<androidx.recyclerview.widget.RecyclerView\n    android:id=\"@+id/\${1:recyclerView}\"\n    android:layout_width=\"match_parent\"\n    android:layout_height=\"match_parent\"\n    app:layoutManager=\"androidx.recyclerview.widget.LinearLayoutManager\"\n    tools:listitem=\"@layout/\${2:item_layout}\" />"),
        snip("LinearLayout","View","<LinearLayout\n    android:layout_width=\"match_parent\"\n    android:layout_height=\"wrap_content\"\n    android:orientation=\"\${1:vertical}\"\n    android:padding=\"\${2:16}dp\">\n\n    \${3:<!-- children -->}\n\n</LinearLayout>"),
        snip("ConstraintLayout","View","<androidx.constraintlayout.widget.ConstraintLayout\n    android:layout_width=\"match_parent\"\n    android:layout_height=\"match_parent\">\n\n    \${1:<!-- views with constraints -->}\n\n</androidx.constraintlayout.widget.ConstraintLayout>"),
        snip("FrameLayout","View","<FrameLayout\n    android:layout_width=\"match_parent\"\n    android:layout_height=\"match_parent\">\n\n    \${1:<!-- children -->}\n\n</FrameLayout>"),
        snip("RelativeLayout","View","<RelativeLayout\n    android:layout_width=\"match_parent\"\n    android:layout_height=\"wrap_content\"\n    android:padding=\"\${1:16}dp\">\n\n    \${2:<!-- children -->}\n\n</RelativeLayout>"),
        snip("CoordinatorLayout","View","<androidx.coordinatorlayout.widget.CoordinatorLayout\n    android:layout_width=\"match_parent\"\n    android:layout_height=\"match_parent\">\n\n    \${1:<!-- children -->}\n\n</androidx.coordinatorlayout.widget.CoordinatorLayout>"),
        snip("AppBarLayout","View","<com.google.android.material.appbar.AppBarLayout\n    android:layout_width=\"match_parent\"\n    android:layout_height=\"wrap_content\">\n\n    <com.google.android.material.appbar.MaterialToolbar\n        android:id=\"@+id/toolbar\"\n        android:layout_width=\"match_parent\"\n        android:layout_height=\"?attr/actionBarSize\" />\n\n</com.google.android.material.appbar.AppBarLayout>"),
        snip("CollapsingToolbarLayout","View","<com.google.android.material.appbar.CollapsingToolbarLayout\n    android:layout_width=\"match_parent\"\n    android:layout_height=\"match_parent\"\n    app:layout_scrollFlags=\"scroll|exitUntilCollapsed\">\n\n    \${1:<!-- content -->}\n\n</com.google.android.material.appbar.CollapsingToolbarLayout>"),
        snip("DrawerLayout","View","<androidx.drawerlayout.widget.DrawerLayout\n    android:id=\"@+id/drawerLayout\"\n    android:layout_width=\"match_parent\"\n    android:layout_height=\"match_parent\">\n\n    \${1:<!-- main content -->}\n\n    <com.google.android.material.navigation.NavigationView\n        android:id=\"@+id/navView\"\n        android:layout_width=\"wrap_content\"\n        android:layout_height=\"match_parent\"\n        android:layout_gravity=\"start\"\n        app:menu=\"@menu/\${2:nav_menu}\" />\n\n</androidx.drawerlayout.widget.DrawerLayout>"),
        snip("BottomNavigationView","View","<com.google.android.material.bottomnavigation.BottomNavigationView\n    android:id=\"@+id/bottomNavView}\"\n    android:layout_width=\"match_parent\"\n    android:layout_height=\"wrap_content\"\n    android:layout_gravity=\"bottom\"\n    app:menu=\"@menu/\${1:bottom_nav}\" />"),
        snip("TabLayout","View","<com.google.android.material.tabs.TabLayout\n    android:id=\"@+id/tabLayout\"\n    android:layout_width=\"match_parent\"\n    android:layout_height=\"wrap_content\"\n    app:tabMode=\"\${1:fixed}\"\n    app:tabGravity=\"\${2:fill}\" />"),
        snip("ViewPager2","View","<androidx.viewpager2.widget.ViewPager2\n    android:id=\"@+id/viewPager\"\n    android:layout_width=\"match_parent\"\n    android:layout_height=\"match_parent\"\n    android:orientation=\"\${1:horizontal}\" />"),
        snip("ScrollView","View","<ScrollView\n    android:layout_width=\"match_parent\"\n    android:layout_height=\"match_parent\"\n    android:fillViewport=\"true\">\n\n    <LinearLayout\n        android:layout_width=\"match_parent\"\n        android:layout_height=\"wrap_content\"\n        android:orientation=\"vertical\"\n        android:padding=\"16dp\">\n\n        \${1:<!-- children -->}\n\n    </LinearLayout>\n\n</ScrollView>"),
        snip("NestedScrollView","View","<androidx.core.widget.NestedScrollView\n    android:layout_width=\"match_parent\"\n    android:layout_height=\"match_parent\">\n\n    \${1:<!-- content -->}\n\n</androidx.core.widget.NestedScrollView>"),
        snip("CardView","View","<com.google.android.material.card.MaterialCardView\n    android:layout_width=\"match_parent\"\n    android:layout_height=\"wrap_content\"\n    android:layout_margin=\"8dp\"\n    app:cardCornerRadius=\"\${1:8}dp\"\n    app:cardElevation=\"\${2:2}dp\">\n\n    \${3:<!-- content -->}\n\n</com.google.android.material.card.MaterialCardView>"),
        snip("CheckBox","View","<CheckBox\n    android:id=\"@+id/\${1:checkBox}\"\n    android:layout_width=\"wrap_content\"\n    android:layout_height=\"wrap_content\"\n    android:text=\"\${2:Label}\" />"),
        snip("RadioButton","View","<RadioButton\n    android:id=\"@+id/\${1:radioButton}\"\n    android:layout_width=\"wrap_content\"\n    android:layout_height=\"wrap_content\"\n    android:text=\"\${2:Option}\" />"),
        snip("RadioGroup","View","<RadioGroup\n    android:id=\"@+id/\${1:radioGroup}\"\n    android:layout_width=\"wrap_content\"\n    android:layout_height=\"wrap_content\"\n    android:orientation=\"\${2:vertical}\">\n\n    \${3:<!-- RadioButton items -->}\n\n</RadioGroup>"),
        snip("Switch","View","<com.google.android.material.switchmaterial.SwitchMaterial\n    android:id=\"@+id/\${1:switchView}\"\n    android:layout_width=\"wrap_content\"\n    android:layout_height=\"wrap_content\"\n    android:text=\"\${2:Label}\" />"),
        snip("SeekBar","View","<SeekBar\n    android:id=\"@+id/\${1:seekBar}\"\n    android:layout_width=\"match_parent\"\n    android:layout_height=\"wrap_content\"\n    android:max=\"\${2:100}\"\n    android:progress=\"\${3:50}\" />"),
        snip("Slider","View","<com.google.android.material.slider.Slider\n    android:id=\"@+id/\${1:slider}\"\n    android:layout_width=\"match_parent\"\n    android:layout_height=\"wrap_content\"\n    android:valueFrom=\"0\"\n    android:valueTo=\"100\"\n    android:value=\"50\" />"),
        snip("ProgressBar","View","<ProgressBar\n    android:id=\"@+id/\${1:progressBar}\"\n    android:layout_width=\"wrap_content\"\n    android:layout_height=\"wrap_content\"\n    android:indeterminate=\"true\" />"),
        snip("LinearProgressIndicator","View","<com.google.android.material.progressindicator.LinearProgressIndicator\n    android:id=\"@+id/\${1:linearProgress}\"\n    android:layout_width=\"match_parent\"\n    android:layout_height=\"wrap_content\"\n    android:indeterminate=\"true\" />"),
        snip("CircularProgressIndicator","View","<com.google.android.material.progressindicator.CircularProgressIndicator\n    android:id=\"@+id/\${1:circularProgress}\"\n    android:layout_width=\"wrap_content\"\n    android:layout_height=\"wrap_content\"\n    android:indeterminate=\"true\" />"),
        snip("Spinner","View","<Spinner\n    android:id=\"@+id/\${1:spinner}\"\n    android:layout_width=\"wrap_content\"\n    android:layout_height=\"wrap_content\"\n    android:entries=\"@array/\${2:items}\" />"),
        snip("TextInputLayout","View","<com.google.android.material.textfield.TextInputLayout\n    android:layout_width=\"match_parent\"\n    android:layout_height=\"wrap_content\"\n    android:hint=\"\${1:Label}\"\n    style=\"@style/Widget.MaterialComponents.TextInputLayout.OutlinedBox\">\n\n    <com.google.android.material.textfield.TextInputEditText\n        android:layout_width=\"match_parent\"\n        android:layout_height=\"wrap_content\" />\n\n</com.google.android.material.textfield.TextInputLayout>"),
        snip("Chip","View","<com.google.android.material.chip.Chip\n    android:id=\"@+id/\${1:chip}\"\n    android:layout_width=\"wrap_content\"\n    android:layout_height=\"wrap_content\"\n    android:text=\"\${2:Label}\" />"),
        snip("ChipGroup","View","<com.google.android.material.chip.ChipGroup\n    android:id=\"@+id/\${1:chipGroup}\"\n    android:layout_width=\"match_parent\"\n    android:layout_height=\"wrap_content\">\n\n    \${2:<!-- Chip items -->}\n\n</com.google.android.material.chip.ChipGroup>"),
        snip("FloatingActionButton","View","<com.google.android.material.floatingactionbutton.FloatingActionButton\n    android:id=\"@+id/\${1:fab}\"\n    android:layout_width=\"wrap_content\"\n    android:layout_height=\"wrap_content\"\n    android:layout_gravity=\"bottom|end\"\n    android:layout_margin=\"16dp\"\n    android:src=\"@drawable/\${2:ic_add}\"\n    android:contentDescription=\"\${3:Add}\" />"),
        snip("WebView","View","<WebView\n    android:id=\"@+id/\${1:webView}\"\n    android:layout_width=\"match_parent\"\n    android:layout_height=\"match_parent\" />"),
        snip("ViewStub","View","<ViewStub\n    android:id=\"@+id/\${1:viewStub}\"\n    android:layout_width=\"match_parent\"\n    android:layout_height=\"wrap_content\"\n    android:layout=\"@layout/\${2:stub_layout}\" />"),
        snip("include","XML","<include\n    layout=\"@layout/\${1:layout_name}\"\n    android:layout_width=\"match_parent\"\n    android:layout_height=\"wrap_content\" />"),
        snip("merge","XML","<merge xmlns:android=\"http://schemas.android.com/apk/res/android\">\n    \${1:<!-- merged views -->}\n</merge>"),
        snip("Space","View","<Space\n    android:layout_width=\"\${1:0dp}\"\n    android:layout_height=\"\${2:16dp}\" />"),
        snip("View","View","<View\n    android:layout_width=\"match_parent\"\n    android:layout_height=\"\${1:1dp}\"\n    android:background=\"?attr/colorOutline\" />"),
        snip("ComposeView","View","<androidx.compose.ui.platform.ComposeView\n    android:id=\"@+id/\${1:composeView}\"\n    android:layout_width=\"match_parent\"\n    android:layout_height=\"match_parent\" />"),
        snip("GridLayout","View","<GridLayout\n    android:layout_width=\"match_parent\"\n    android:layout_height=\"wrap_content\"\n    android:rowCount=\"\${1:2}\"\n    android:columnCount=\"\${2:2}\">\n\n    \${3:<!-- children -->}\n\n</GridLayout>"),
        snip("TableLayout","View","<TableLayout\n    android:layout_width=\"match_parent\"\n    android:layout_height=\"wrap_content\"\n    android:stretchColumns=\"*\">\n\n    <TableRow>\n        \${1:<!-- cells -->}\n    </TableRow>\n\n</TableLayout>"),
    )

    // ════════════════════════════════════════════════════════════════════════
    // XML ATTRIBUTES — 80+ items
    // ════════════════════════════════════════════════════════════════════════
    private val xmlAttrItems = listOf(
        snip("android:id","attr","android:id=\"@+id/\${1:viewId}\""),
        snip("android:layout_width","attr","android:layout_width=\"\${1:match_parent}\""),
        snip("android:layout_height","attr","android:layout_height=\"\${1:wrap_content}\""),
        snip("android:layout_margin","attr","android:layout_margin=\"\${1:16}dp\""),
        snip("android:layout_marginTop","attr","android:layout_marginTop=\"\${1:16}dp\""),
        snip("android:layout_marginBottom","attr","android:layout_marginBottom=\"\${1:16}dp\""),
        snip("android:layout_marginStart","attr","android:layout_marginStart=\"\${1:16}dp\""),
        snip("android:layout_marginEnd","attr","android:layout_marginEnd=\"\${1:16}dp\""),
        snip("android:padding","attr","android:padding=\"\${1:16}dp\""),
        snip("android:paddingTop","attr","android:paddingTop=\"\${1:8}dp\""),
        snip("android:paddingBottom","attr","android:paddingBottom=\"\${1:8}dp\""),
        snip("android:paddingStart","attr","android:paddingStart=\"\${1:16}dp\""),
        snip("android:paddingEnd","attr","android:paddingEnd=\"\${1:16}dp\""),
        snip("android:paddingHorizontal","attr","android:paddingHorizontal=\"\${1:16}dp\""),
        snip("android:paddingVertical","attr","android:paddingVertical=\"\${1:8}dp\""),
        snip("android:text","attr","android:text=\"\${1:@string/text}\""),
        snip("android:textSize","attr","android:textSize=\"\${1:16}sp\""),
        snip("android:textColor","attr","android:textColor=\"\${1:@color/text_primary}\""),
        snip("android:textStyle","attr","android:textStyle=\"\${1|bold,italic,bold|italic|}\""),
        snip("android:fontFamily","attr","android:fontFamily=\"\${1:@font/roboto}\""),
        snip("android:letterSpacing","attr","android:letterSpacing=\"\${1:0.05}\""),
        snip("android:lineSpacingExtra","attr","android:lineSpacingExtra=\"\${1:4}dp\""),
        snip("android:maxLines","attr","android:maxLines=\"\${1:1}\""),
        snip("android:minLines","attr","android:minLines=\"\${1:1}\""),
        snip("android:ellipsize","attr","android:ellipsize=\"\${1|end,start,middle,marquee|}\""),
        snip("android:hint","attr","android:hint=\"\${1:@string/hint}\""),
        snip("android:inputType","attr","android:inputType=\"\${1|text,textPassword,textEmailAddress,number,phone,date|}\""),
        snip("android:imeOptions","attr","android:imeOptions=\"\${1|actionDone,actionNext,actionSearch,actionSend|}\""),
        snip("android:background","attr","android:background=\"\${1:@drawable/bg}\""),
        snip("android:backgroundTint","attr","android:backgroundTint=\"\${1:@color/primary}\""),
        snip("android:foreground","attr","android:foreground=\"\${1:?attr/selectableItemBackground}\""),
        snip("android:src","attr","android:src=\"@drawable/\${1:image}\""),
        snip("android:scaleType","attr","android:scaleType=\"\${1|centerCrop,fitCenter,fitXY,centerInside,center|}\""),
        snip("android:contentDescription","attr","android:contentDescription=\"\${1:description}\""),
        snip("android:gravity","attr","android:gravity=\"\${1|center,center_horizontal,center_vertical,start,end,top,bottom|}\""),
        snip("android:layout_gravity","attr","android:layout_gravity=\"\${1|center,start,end,top,bottom|}\""),
        snip("android:orientation","attr","android:orientation=\"\${1|vertical,horizontal|}\""),
        snip("android:visibility","attr","android:visibility=\"\${1|visible,invisible,gone|}\""),
        snip("android:enabled","attr","android:enabled=\"\${1|true,false|}\""),
        snip("android:clickable","attr","android:clickable=\"\${1|true,false|}\""),
        snip("android:focusable","attr","android:focusable=\"\${1|true,false|}\""),
        snip("android:alpha","attr","android:alpha=\"\${1:1.0}\""),
        snip("android:elevation","attr","android:elevation=\"\${1:4}dp\""),
        snip("android:translationX","attr","android:translationX=\"\${1:0}dp\""),
        snip("android:translationY","attr","android:translationY=\"\${1:0}dp\""),
        snip("android:rotation","attr","android:rotation=\"\${1:0}\""),
        snip("android:scaleX","attr","android:scaleX=\"\${1:1.0}\""),
        snip("android:scaleY","attr","android:scaleY=\"\${1:1.0}\""),
        snip("android:minWidth","attr","android:minWidth=\"\${1:48}dp\""),
        snip("android:minHeight","attr","android:minHeight=\"\${1:48}dp\""),
        snip("android:maxWidth","attr","android:maxWidth=\"\${1:200}dp\""),
        snip("android:maxHeight","attr","android:maxHeight=\"\${1:200}dp\""),
        snip("android:layout_weight","attr","android:layout_weight=\"\${1:1}\""),
        snip("android:checked","attr","android:checked=\"\${1|true,false|}\""),
        snip("android:singleLine","attr","android:singleLine=\"\${1|true,false|}\""),
        snip("android:scrollbars","attr","android:scrollbars=\"\${1|vertical,horizontal,none|}\""),
        snip("android:dividerHeight","attr","android:dividerHeight=\"\${1:1}dp\""),
        snip("android:weightSum","attr","android:weightSum=\"\${1:1}\""),
        snip("app:cardCornerRadius","attr","app:cardCornerRadius=\"\${1:8}dp\""),
        snip("app:cardElevation","attr","app:cardElevation=\"\${1:2}dp\""),
        snip("app:cardBackgroundColor","attr","app:cardBackgroundColor=\"\${1:@color/surface}\""),
        snip("app:strokeColor","attr","app:strokeColor=\"\${1:@color/outline}\""),
        snip("app:strokeWidth","attr","app:strokeWidth=\"\${1:1}dp\""),
        snip("app:layout_constraintTop_toTopOf","attr","app:layout_constraintTop_toTopOf=\"\${1:parent}\""),
        snip("app:layout_constraintTop_toBottomOf","attr","app:layout_constraintTop_toBottomOf=\"@+id/\${1:view}\""),
        snip("app:layout_constraintBottom_toBottomOf","attr","app:layout_constraintBottom_toBottomOf=\"\${1:parent}\""),
        snip("app:layout_constraintBottom_toTopOf","attr","app:layout_constraintBottom_toTopOf=\"@+id/\${1:view}\""),
        snip("app:layout_constraintStart_toStartOf","attr","app:layout_constraintStart_toStartOf=\"\${1:parent}\""),
        snip("app:layout_constraintStart_toEndOf","attr","app:layout_constraintStart_toEndOf=\"@+id/\${1:view}\""),
        snip("app:layout_constraintEnd_toEndOf","attr","app:layout_constraintEnd_toEndOf=\"\${1:parent}\""),
        snip("app:layout_constraintEnd_toStartOf","attr","app:layout_constraintEnd_toStartOf=\"@+id/\${1:view}\""),
        snip("app:layout_constraintWidth_percent","attr","app:layout_constraintWidth_percent=\"\${1:0.5}\""),
        snip("app:layout_constraintHorizontal_bias","attr","app:layout_constraintHorizontal_bias=\"\${1:0.5}\""),
        snip("app:layout_constraintVertical_bias","attr","app:layout_constraintVertical_bias=\"\${1:0.5}\""),
        snip("app:layout_constraintDimensionRatio","attr","app:layout_constraintDimensionRatio=\"\${1:16:9}\""),
        snip("tools:text","attr","tools:text=\"\${1:Preview text}\""),
        snip("tools:visibility","attr","tools:visibility=\"\${1|visible,invisible,gone|}\""),
        snip("tools:src","attr","tools:src=\"@tools:sample/avatars\""),
        snip("tools:listitem","attr","tools:listitem=\"@layout/\${1:item_layout}\""),
        snip("tools:ignore","attr","tools:ignore=\"\${1:HardcodedText}\""),
    )

    // ════════════════════════════════════════════════════════════════════════
    // XML VALUES
    // ════════════════════════════════════════════════════════════════════════
    private val xmlValueItems = listOf(
        snip("match_parent","value","match_parent"), snip("wrap_content","value","wrap_content"),
        snip("0dp","value","0dp"), snip("@color/primary","value","@color/primary"),
        snip("@color/secondary","value","@color/secondary"), snip("?attr/colorPrimary","value","?attr/colorPrimary"),
        snip("?attr/colorSecondary","value","?attr/colorSecondary"), snip("?attr/colorSurface","value","?attr/colorSurface"),
        snip("?attr/colorBackground","value","?attr/colorBackground"), snip("?attr/colorOnPrimary","value","?attr/colorOnPrimary"),
        snip("?attr/textAppearanceBodyMedium","value","?attr/textAppearanceBodyMedium"),
        snip("?attr/textAppearanceTitleLarge","value","?attr/textAppearanceTitleLarge"),
        snip("?attr/actionBarSize","value","?attr/actionBarSize"),
        snip("?attr/selectableItemBackground","value","?attr/selectableItemBackground"),
        snip("@android:color/transparent","value","@android:color/transparent"),
        snip("@android:color/white","value","@android:color/white"),
        snip("@android:color/black","value","@android:color/black"),
        snip("@tools:sample/avatars","value","@tools:sample/avatars"),
        snip("@tools:sample/names","value","@tools:sample/names"),
        snip("@tools:sample/lorem","value","@tools:sample/lorem"),
    )

    // ════════════════════════════════════════════════════════════════════════
    // GRADLE COMPLETIONS
    // ════════════════════════════════════════════════════════════════════════
    private val gradleItems = listOf(
        snip("implementation","dep","implementation(\"\${1:group}:\${2:artifact}:\${3:version}\")"),
        snip("debugImplementation","dep","debugImplementation(\"\${1:group}:\${2:artifact}:\${3:version}\")"),
        snip("testImplementation","dep","testImplementation(\"\${1:group}:\${2:artifact}:\${3:version}\")"),
        snip("androidTestImplementation","dep","androidTestImplementation(\"\${1:group}:\${2:artifact}:\${3:version}\")"),
        snip("ksp","dep","ksp(\"\${1:group}:\${2:artifact}:\${3:version}\")"),
        snip("kapt","dep","kapt(\"\${1:group}:\${2:artifact}:\${3:version}\")"),
        snip("api","dep","api(\"\${1:group}:\${2:artifact}:\${3:version}\")"),
        snip("compileOnly","dep","compileOnly(\"\${1:group}:\${2:artifact}:\${3:version}\")"),
        snip("runtimeOnly","dep","runtimeOnly(\"\${1:group}:\${2:artifact}:\${3:version}\")"),
        snip("platform","BOM","implementation(platform(\"\${1:group}:\${2:artifact}:\${3:version}\"))"),
        snip("dependencies","block","dependencies {\n    \${1:}\n}"),
        snip("android","block","android {\n    namespace = \"\${1:com.example.app}\"\n    compileSdk = \${2:35}\n    defaultConfig {\n        applicationId = \"\${1:com.example.app}\"\n        minSdk = \${3:26}\n        targetSdk = \${2:35}\n        versionCode = \${4:1}\n        versionName = \"\${5:1.0}\"\n    }\n    buildTypes {\n        release {\n            isMinifyEnabled = true\n            proguardFiles(getDefaultProguardFile(\"proguard-android-optimize.txt\"), \"proguard-rules.pro\")\n        }\n    }\n    compileOptions {\n        sourceCompatibility = JavaVersion.VERSION_17\n        targetCompatibility = JavaVersion.VERSION_17\n    }\n    kotlinOptions { jvmTarget = \"17\" }\n}"),
        snip("buildFeatures","block","buildFeatures {\n    compose = \${1:true}\n    buildConfig = \${2:true}\n    viewBinding = \${3:false}\n}"),
        snip("defaultConfig","block","defaultConfig {\n    applicationId = \"\${1:com.example.app}\"\n    minSdk = \${2:26}\n    targetSdk = \${3:35}\n    versionCode = \${4:1}\n    versionName = \"\${5:1.0}\"\n}"),
        snip("compileSdk","int","compileSdk = \${1:35}"), snip("minSdk","int","minSdk = \${1:26}"),
        snip("targetSdk","int","targetSdk = \${1:35}"), snip("versionCode","int","versionCode = \${1:1}"),
        snip("versionName","string","versionName = \"\${1:1.0}\""),
        snip("applicationId","string","applicationId = \"\${1:com.example.app}\""),
        snip("namespace","string","namespace = \"\${1:com.example.app}\""),
        snip("buildTypes","block","buildTypes {\n    release {\n        isMinifyEnabled = true\n        proguardFiles(getDefaultProguardFile(\"proguard-android-optimize.txt\"), \"proguard-rules.pro\")\n    }\n    debug { applicationIdSuffix = \".debug\"; isDebuggable = true }\n}"),
        snip("signingConfigs","block","signingConfigs {\n    create(\"release\") {\n        keyAlias = \"\${1:key_alias}\"\n        keyPassword = \"\${2:key_password}\"\n        storeFile = file(\"\${3:keystore.jks}\")\n        storePassword = \"\${4:store_password}\"\n    }\n}"),
        snip("productFlavors","block","productFlavors {\n    create(\"\${1:free}\") { applicationIdSuffix = \".\${1:free}\" }\n    create(\"\${2:paid}\") {}\n}"),
        snip("compileOptions","block","compileOptions {\n    sourceCompatibility = JavaVersion.VERSION_17\n    targetCompatibility = JavaVersion.VERSION_17\n}"),
        snip("kotlinOptions","block","kotlinOptions { jvmTarget = \"17\" }"),
        snip("repositories","block","repositories {\n    google()\n    mavenCentral()\n    maven { url = uri(\"https://jitpack.io\") }\n}"),
        snip("plugins","block","plugins {\n    id(\"\${1:com.android.application}\")\n    id(\"\${2:org.jetbrains.kotlin.android}\")\n}"),
    )

    // ════════════════════════════════════════════════════════════════════════
    // JSON
    // ════════════════════════════════════════════════════════════════════════
    private val jsonItems = listOf(
        snip("object","JSON","{\n  \"\${1:key}\": \"\${2:value}\"\n}"),
        snip("array","JSON","[\n  \${1:}\n]"),
        ci("null","JSON",CompletionKind.KEYWORD,"null"),
        ci("true","JSON",CompletionKind.KEYWORD,"true"),
        ci("false","JSON",CompletionKind.KEYWORD,"false"),
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Context helpers
// ─────────────────────────────────────────────────────────────────────────────
data class CompletionContext(
    val lineText: String,
    val precedingCode: String,
    val fullCode: String,
    val cursorPos: Int
) {
    fun isModifierChain() =
        lineText.matches(Regex(".*Modifier\\.[a-zA-Z_]*$")) ||
        (lineText.contains(".") && precedingCode.contains("Modifier"))

    fun isImportStatement() = lineText.trimStart().startsWith("import")

    fun isComposableContext() =
        precedingCode.contains("@Composable") || precedingCode.contains("setContent") ||
        precedingCode.contains("composable {") || precedingCode.contains("content: @Composable")

    fun isDotCompletion() = lineText.matches(Regex(".*[a-zA-Z_][a-zA-Z0-9_]*\\.[a-zA-Z_]*$"))

    fun extractObjectName(): String =
        Regex("([a-zA-Z_][a-zA-Z0-9_]*)\\.[a-zA-Z_]*$").find(lineText)?.groupValues?.get(1) ?: ""
}
