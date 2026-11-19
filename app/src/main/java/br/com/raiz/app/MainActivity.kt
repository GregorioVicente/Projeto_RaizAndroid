package br.com.raiz.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import android.os.Bundle
import org.json.JSONArray
import org.json.JSONObject
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.KeyboardType
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private val Forest = Color(0xFF315C43)
private val ForestDark = Color(0xFF203D2C)
private val Cream = Color(0xFFF8F7F1)
private val CardGreen = Color(0xFFE8EFE3)
private val Ink = Color(0xFF25352B)
private val Muted = Color(0xFF788178)
private val Line = Color(0xFFE4E6DD)
private val White = Color(0xFFFFFFFF)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { RaizApp() }
    }
}

private enum class Screen(val title: String) {
    Welcome("Boas-vindas"), Home("Início"), Crops("Meus cultivos"), NewPlanting("Novo plantio"),
    CropDetail("Alface crespa"), Costs("Custos do cultivo"), Harvest("Registrar colheita"),
    Market("Preparar a feira"), Sale("Registrar venda"), Results("Como foi a feira?"), History("Histórico de vendas")
}

private enum class VoiceTarget { Welcome, Home, Form }

private data class CropRecord(
    val id: String,
    val name: String,
    val bed: String,
    val plantedAt: String,
    val quantity: Int,
    val status: String,
    val seedCost: Double = 0.0,
    val waterCost: Double = 0.0,
    val fertilizerCost: Double = 0.0
)

private data class SaleRecord(
    val id: String,
    val product: String,
    val quantity: Int,
    val amount: Double,
    val costs: Double,
    val date: String
)

private data class MarketCosts(val transport: Double = 25.0, val stall: Double = 20.0, val other: Double = 0.0) {
    val total: Double get() = transport + stall + other
}

private data class HarvestGuide(val minDays: Int, val maxDays: Int, val basis: String, val note: String)

private object LocalData {
    private const val PREFS = "raiz_local_data"
    private const val CROPS_KEY = "crops"
    private const val SALES_KEY = "sales"
    private const val USER_NAME_KEY = "user_name"
    private const val MARKET_COSTS_KEY = "market_costs"

    fun loadUserName(context: Context): String? = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(USER_NAME_KEY, null)

    fun saveUserName(context: Context, name: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(USER_NAME_KEY, name).apply()
    }

    fun loadCrops(context: Context): List<CropRecord> {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(CROPS_KEY, null)
            ?: return initialCrops()
        return runCatching {
            val array = JSONArray(saved)
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                CropRecord(
                    id = item.getString("id"), name = item.getString("name"),
                    bed = item.optString("bed"), plantedAt = item.optString("plantedAt"),
                    quantity = item.optInt("quantity"), status = item.optString("status", "GROWING"),
                    seedCost = item.optDouble("seedCost"), waterCost = item.optDouble("waterCost"),
                    fertilizerCost = item.optDouble("fertilizerCost")
                )
            }
        }.getOrElse { initialCrops() }
    }

    fun saveCrops(context: Context, crops: List<CropRecord>) {
        val array = JSONArray()
        crops.forEach { crop ->
            array.put(JSONObject().apply {
                put("id", crop.id); put("name", crop.name); put("bed", crop.bed)
                put("plantedAt", crop.plantedAt); put("quantity", crop.quantity); put("status", crop.status)
                put("seedCost", crop.seedCost); put("waterCost", crop.waterCost); put("fertilizerCost", crop.fertilizerCost)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(CROPS_KEY, array.toString()).apply()
    }

    fun loadSales(context: Context): List<SaleRecord> {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(SALES_KEY, null)
            ?: return listOf(SaleRecord("demo-sale", "Feira do bairro", 100, 320.0, 0.0, "24/10/2026"))
        return runCatching {
            val array = JSONArray(saved)
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                SaleRecord(
                    id = item.getString("id"), product = item.getString("product"),
                    quantity = item.optInt("quantity"), amount = item.optDouble("amount"),
                    costs = item.optDouble("costs"), date = item.optString("date")
                )
            }
        }.getOrElse { emptyList() }
    }

    fun saveSales(context: Context, sales: List<SaleRecord>) {
        val array = JSONArray()
        sales.forEach { sale ->
            array.put(JSONObject().apply {
                put("id", sale.id); put("product", sale.product); put("quantity", sale.quantity)
                put("amount", sale.amount); put("costs", sale.costs); put("date", sale.date)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(SALES_KEY, array.toString()).apply()
    }

    fun loadMarketCosts(context: Context): MarketCosts {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(MARKET_COSTS_KEY, null)
            ?: return MarketCosts()
        return runCatching {
            val item = JSONObject(saved)
            MarketCosts(item.optDouble("transport"), item.optDouble("stall"), item.optDouble("other"))
        }.getOrDefault(MarketCosts())
    }

    fun saveMarketCosts(context: Context, costs: MarketCosts) {
        val json = JSONObject().apply {
            put("transport", costs.transport); put("stall", costs.stall); put("other", costs.other)
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(MARKET_COSTS_KEY, json.toString()).apply()
    }

    private fun initialCrops() = listOf(
        CropRecord("lettuce", "Alface crespa", "Canteiro 02", dateDaysAgo(45), 120, "READY", 22.0, 15.0, 35.0),
        CropRecord("carrot", "Cenoura", "Canteiro 01", dateDaysAgo(100), 80, "READY", 5.0, 2.0, 3.0),
        CropRecord("kale", "Couve", "Canteiro 03", dateDaysAgo(15), 30, "GROWING"),
        CropRecord("tomato", "Tomate", "Canteiro 04", dateDaysAgo(20), 24, "GROWING"),
        CropRecord("pepper", "Pimentão", "Canteiro 05", dateDaysAgo(10), 18, "GROWING"),
        CropRecord("zucchini", "Abobrinha", "Canteiro 06", dateDaysAgo(15), 12, "GROWING"),
        CropRecord("basil", "Manjericão", "Canteiro 07", dateDaysAgo(20), 20, "GROWING"),
        CropRecord("arugula", "Rúcula", "Canteiro 08", dateDaysAgo(15), 35, "GROWING")
    )
}

private fun todayLabel(): String = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).format(Date())
private fun dateDaysAgo(days: Int): String {
    val calendar = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, -days) }
    return SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).format(calendar.time)
}
private fun moneyLabel(value: Double): String = String.format(Locale("pt", "BR"), "R$ %.2f", value)
private fun parseMoney(value: String): Double {
    val cleaned = value.filter { it.isDigit() || it == ',' || it == '.' }
    val normalized = if (cleaned.contains(',') && cleaned.contains('.')) {
        cleaned.replace(".", "").replace(',', '.')
    } else if (cleaned.contains(',')) cleaned.replace(',', '.') else cleaned
    return normalized.toDoubleOrNull() ?: 0.0
}

private fun editableMoney(value: Double): String = String.format(Locale("pt", "BR"), "%.2f", value)

private fun normalizedCropName(name: String): String = Normalizer.normalize(name.trim().lowercase(), Normalizer.Form.NFD)
    .replace("\\p{Mn}+".toRegex(), "")

private fun harvestGuide(name: String): HarvestGuide? = when (normalizedCropName(name)) {
    "alface", "alface crespa", "alface lisa", "alface americana" -> HarvestGuide(35, 90, "após transplante", "Cultivares precoces podem iniciar em 35–45 dias; recomendações regionais chegam a 60–90 dias.")
    "cenoura" -> HarvestGuide(90, 110, "após semeadura", "A janela muda conforme cultivar e condições de solo e clima.")
    "couve", "couve manteiga", "couve folha" -> HarvestGuide(40, 90, "após transplante", "As folhas permitem cortes sucessivos; o início varia conforme região e manejo.")
    "tomate", "tomate cereja", "tomate italiano" -> HarvestGuide(75, 100, "após transplante", "A Embrapa relata cerca de 90–100 dias para tomate industrial; cultivares e regiões podem antecipar o início.")
    "pimentao" -> HarvestGuide(60, 110, "após transplante", "A faixa ampla reflete diferenças entre regiões, cultivares e sistemas de cultivo.")
    "abobrinha", "abobrinha italiana" -> HarvestGuide(45, 90, "após plantio", "As fontes consultadas variam entre 45–60 dias após transplante e 60–90 dias após semeadura.")
    "manjericao" -> HarvestGuide(48, 90, "após plantio/semeadura", "A primeira colheita pode começar antes; o manejo e o ponto de floração influenciam os cortes.")
    "rucula" -> HarvestGuide(40, 60, "após semeadura", "A janela depende do clima e do tamanho desejado para as folhas.")
    else -> null
}

private fun estimatedHarvestWindow(crop: CropRecord): String? = estimatedHarvestWindow(crop.name, crop.plantedAt)

private fun estimatedHarvestWindow(name: String, plantedAt: String): String? {
    val guide = harvestGuide(name) ?: return null
    val parser = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).apply { isLenient = false }
    val planted = runCatching { parser.parse(plantedAt) }.getOrNull() ?: return null
    fun plusDays(days: Int): String {
        val calendar = java.util.Calendar.getInstance().apply { time = planted; add(java.util.Calendar.DAY_OF_YEAR, days) }
        return parser.format(calendar.time)
    }
    return "${plusDays(guide.minDays)} a ${plusDays(guide.maxDays)}"
}

private fun effectiveCropStatus(crop: CropRecord): String {
    if (crop.status == "HARVESTED" || crop.status == "READY") return crop.status
    val guide = harvestGuide(crop.name) ?: return crop.status
    val parser = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).apply { isLenient = false }
    val planted = runCatching { parser.parse(crop.plantedAt) }.getOrNull() ?: return crop.status
    val calendar = java.util.Calendar.getInstance().apply { time = planted; add(java.util.Calendar.DAY_OF_YEAR, guide.minDays) }
    return if (!Date().before(calendar.time)) "READY" else crop.status
}

@Composable
private fun RaizApp() {
    val context = LocalContext.current
    val savedName = remember { LocalData.loadUserName(context).orEmpty() }
    var screen by remember { mutableStateOf(if (savedName.isBlank()) Screen.Welcome else Screen.Home) }
    var name by remember { mutableStateOf(savedName) }
    var crops by remember { mutableStateOf(LocalData.loadCrops(context)) }
    var sales by remember { mutableStateOf(LocalData.loadSales(context)) }
    var marketCosts by remember { mutableStateOf(LocalData.loadMarketCosts(context)) }
    var selectedCropId by remember { mutableStateOf(crops.firstOrNull()?.id.orEmpty()) }
    var toast by remember { mutableStateOf("") }
    var voiceTarget by remember { mutableStateOf(VoiceTarget.Home) }
    var voiceFieldIndex by remember { mutableStateOf(0) }
    var voiceText by remember { mutableStateOf("") }

    fun updateCrops(updated: List<CropRecord>) {
        crops = updated
        LocalData.saveCrops(context, updated)
    }

    fun updateSales(updated: List<SaleRecord>) {
        sales = updated
        LocalData.saveSales(context, updated)
    }

    fun updateMarketCosts(updated: MarketCosts) {
        marketCosts = updated
        LocalData.saveMarketCosts(context, updated)
    }

    val cropsForUi = crops.map { crop -> crop.copy(status = effectiveCropStatus(crop)) }
    LaunchedEffect(cropsForUi) {
        if (cropsForUi != crops) updateCrops(cropsForUi)
    }
    val selectedCrop = cropsForUi.firstOrNull { it.id == selectedCropId } ?: cropsForUi.firstOrNull()
    val recordedCosts = cropsForUi.sumOf { it.seedCost + it.waterCost + it.fertilizerCost } + marketCosts.total

    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val transcript = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.trim()

            if (!transcript.isNullOrBlank()) {
                voiceText = transcript
                when (voiceTarget) {
                    VoiceTarget.Welcome -> {
                        name = transcript
                        LocalData.saveUserName(context, transcript)
                        screen = Screen.Home
                        toast = "Olá, $transcript!"
                    }
                    VoiceTarget.Form -> toast = "Transcrição adicionada ao formulário"
                    VoiceTarget.Home -> {
                        val phrase = transcript.lowercase()
                        screen = when {
                            "colh" in phrase -> Screen.Harvest
                            "vend" in phrase -> Screen.Sale
                            "cust" in phrase -> Screen.Costs
                            "resultado" in phrase || "feira" in phrase -> Screen.Market
                            else -> Screen.NewPlanting
                        }
                        toast = "Entendi: $transcript"
                    }
                }
            } else {
                toast = "Não consegui reconhecer a fala. Tente novamente."
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val prompt = when (voiceTarget) {
                VoiceTarget.Welcome -> "Diga seu nome"
                VoiceTarget.Form -> "Diga o que deseja registrar"
                VoiceTarget.Home -> "Descreva o que deseja registrar"
            }
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "pt-BR")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
            }
            try {
                speechLauncher.launch(intent)
            } catch (_: Exception) {
                toast = "Não há um serviço de reconhecimento de voz disponível neste aparelho."
            }
        } else {
            toast = "Permita o acesso ao microfone para registrar por voz."
        }
    }

    val startVoice: (VoiceTarget, Int) -> Unit = { target, fieldIndex ->
        voiceTarget = target
        voiceFieldIndex = fieldIndex
        voiceText = ""
        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    MaterialTheme(colorScheme = lightColorScheme(
        primary = Forest, onPrimary = White, background = Cream, onBackground = Ink,
        surface = White, onSurface = Ink, secondary = Color(0xFF82966E)
    )) {
        Surface(modifier = Modifier.fillMaxSize(), color = Cream) {
            Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
                if (screen != Screen.Welcome) {
                    TopHeader(
                        screen = screen,
                        onBack = {
                            screen = when (screen) {
                                Screen.Costs -> Screen.CropDetail
                                Screen.Harvest -> Screen.CropDetail
                                Screen.Sale -> Screen.Market
                                Screen.History -> Screen.Results
                                else -> Screen.Crops
                            }
                        },
                        name = name,
                        titleOverride = if (screen == Screen.CropDetail) selectedCrop?.name else null
                    )
                }
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (screen) {
                        Screen.Welcome -> WelcomeScreen(
                            name = name,
                            onNameChange = { name = it },
                            onContinue = {
                                val cleanedName = name.trim()
                                if (cleanedName.isNotBlank()) {
                                    name = cleanedName
                                    LocalData.saveUserName(context, cleanedName)
                                    screen = Screen.Home
                                } else {
                                    toast = "Digite seu nome para continuar."
                                }
                            },
                            onVoice = { startVoice(VoiceTarget.Welcome, 0) }
                        )
                        Screen.Home -> HomeScreen(name, cropsForUi, onOpenCrops = { screen = Screen.Crops }, onPlant = { screen = Screen.NewPlanting }, onVoice = { startVoice(VoiceTarget.Home, 0) }, onOpenCrop = { crop -> selectedCropId = crop.id; screen = Screen.CropDetail })
                        Screen.Crops -> CropsScreen(cropsForUi, onPlant = { screen = Screen.NewPlanting }, onOpenCrop = { crop -> selectedCropId = crop.id; screen = Screen.CropDetail })
                        Screen.NewPlanting -> FormScreen(
                            fields = listOf("Nome do cultivo" to "", "Canteiro" to "Canteiro 02", "Data de semeadura/transplante" to todayLabel(), "Quantidade plantada" to ""),
                            primary = "Salvar plantio", secondary = "Preencher por voz",
                            onDone = { values ->
                                val crop = CropRecord(
                                    id = UUID.randomUUID().toString(),
                                    name = values.getOrNull(0).orEmpty().ifBlank { "Novo cultivo" },
                                    bed = values.getOrNull(1).orEmpty().ifBlank { "Canteiro" },
                                    plantedAt = values.getOrNull(2).orEmpty().ifBlank { todayLabel() },
                                    quantity = values.getOrNull(3)?.toIntOrNull() ?: 0,
                                    status = "GROWING"
                                )
                                updateCrops(listOf(crop) + crops)
                                selectedCropId = crop.id
                                screen = Screen.Crops
                                toast = "${crop.name} adicionado aos cultivos"
                            },
                            onSecondary = { index -> startVoice(VoiceTarget.Form, index) },
                            voiceText = voiceText,
                            voiceFieldIndex = voiceFieldIndex
                        )
                        Screen.CropDetail -> selectedCrop?.let { crop -> CropDetailScreen(crop, onHarvest = { screen = Screen.Harvest }, onCosts = { screen = Screen.Costs }, onVoice = { toast = "Avisos por voz em breve" }) }
                        Screen.Costs -> selectedCrop?.let { crop ->
                            CostsScreen(crop, onSave = { seed, water, fertilizer ->
                                updateCrops(cropsForUi.map {
                                    if (it.id == crop.id) it.copy(seedCost = parseMoney(seed), waterCost = parseMoney(water), fertilizerCost = parseMoney(fertilizer)) else it
                                })
                                toast = "Custos de ${crop.name} salvos"
                            })
                        }
                        Screen.Harvest -> FormScreen(
                            fields = listOf("Quantidade colhida" to (selectedCrop?.quantity?.takeIf { it > 0 }?.toString() ?: "110"), "Data da colheita" to todayLabel(), "Destino" to "Estoque da feira"),
                            primary = "Confirmar colheita", secondary = "Registrar por voz",
                            onDone = { values ->
                                selectedCrop?.let { crop ->
                                    updateCrops(crops.map { if (it.id == crop.id) it.copy(quantity = values.getOrNull(0)?.toIntOrNull() ?: it.quantity, status = "HARVESTED") else it })
                                }
                                screen = Screen.Market
                                toast = "Colheita registrada no estoque"
                            },
                            onSecondary = { index -> startVoice(VoiceTarget.Form, index) },
                            voiceText = voiceText,
                            voiceFieldIndex = voiceFieldIndex
                        )
                        Screen.Market -> MarketScreen(marketCosts, onSaveCosts = { transport, stall, other ->
                            updateMarketCosts(MarketCosts(parseMoney(transport), parseMoney(stall), parseMoney(other)))
                            toast = "Custos da feira salvos"
                        }, onStart = { screen = Screen.Sale })
                        Screen.Sale -> FormScreen(
                            fields = listOf("Produto vendido" to (selectedCrop?.name ?: "Alface crespa"), "Quantidade" to "5", "Valor recebido" to "R$ 15,00"),
                            primary = "Confirmar venda", secondary = "Registrar por voz",
                            onDone = { values ->
                                val product = values.getOrNull(0).orEmpty().ifBlank { selectedCrop?.name ?: "Produto" }
                                val quantity = values.getOrNull(1)?.toIntOrNull() ?: 0
                                val sale = SaleRecord(UUID.randomUUID().toString(), product, quantity, parseMoney(values.getOrNull(2).orEmpty()), 0.0, todayLabel())
                                updateSales(listOf(sale) + sales)
                                val soldCrop = crops.firstOrNull { it.name.equals(product, ignoreCase = true) }
                                if (soldCrop != null && quantity > 0) {
                                    updateCrops(crops.map { if (it.id == soldCrop.id) it.copy(quantity = (it.quantity - quantity).coerceAtLeast(0)) else it })
                                }
                                screen = Screen.Results
                                toast = "Venda salva no histórico"
                            },
                            onSecondary = { index -> startVoice(VoiceTarget.Form, index) },
                            voiceText = voiceText,
                            voiceFieldIndex = voiceFieldIndex
                        )
                        Screen.Results -> ResultsScreen(sales, recordedCosts, onNext = { screen = Screen.NewPlanting }, onHistory = { screen = Screen.History })
                        Screen.History -> HistoryScreen(sales)
                    }
                    if (toast.isNotEmpty()) {
                        Card(
                            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp).clickable { toast = "" },
                            colors = CardDefaults.cardColors(containerColor = ForestDark),
                            shape = RoundedCornerShape(14.dp)
                        ) { Text(toast, color = White, modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp), fontSize = 14.sp) }
                    }
                }
                if (screen !in listOf(Screen.Welcome, Screen.NewPlanting, Screen.Harvest, Screen.Sale)) {
                    BottomNavigation(screen = screen, onSelect = { selected -> screen = selected })
                }
            }
        }
    }
}

@Composable
private fun TopHeader(screen: Screen, onBack: () -> Unit, name: String, titleOverride: String? = null) {
    Column(modifier = Modifier.fillMaxWidth().background(Cream).padding(horizontal = 24.dp, vertical = 14.dp)) {
        if (screen == Screen.Home) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🌱", fontSize = 27.sp)
                Spacer(Modifier.width(8.dp))
                Text("raiz", fontSize = 27.sp, fontWeight = FontWeight.Bold, color = Forest)
            }
            Spacer(Modifier.height(13.dp))
            Text("Bom dia, $name", fontSize = 25.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            Text("Quinta, 22 de outubro • Sítio Boa Terra", fontSize = 13.sp, color = Muted)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("‹", fontSize = 30.sp, color = Forest, modifier = Modifier.clickable { onBack() }.padding(end = 12.dp))
                Text(titleOverride ?: screen.title, fontSize = 25.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            }
            if (screen == Screen.Crops) Text("Acompanhe cada etapa da sua horta.", fontSize = 13.sp, color = Muted, modifier = Modifier.padding(start = 40.dp))
        }
    }
}

@Composable
private fun BottomNavigation(screen: Screen, onSelect: (Screen) -> Unit) {
    val selected = when (screen) {
        Screen.Crops, Screen.CropDetail, Screen.Costs, Screen.NewPlanting, Screen.Harvest -> Screen.Crops
        Screen.Market, Screen.Sale -> Screen.Market
        Screen.Results, Screen.History -> Screen.Results
        else -> Screen.Home
    }
    Row(
        modifier = Modifier.fillMaxWidth().background(White).windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 8.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        NavItem("⌂", "Início", selected == Screen.Home) { onSelect(Screen.Home) }
        NavItem("♧", "Cultivos", selected == Screen.Crops) { onSelect(Screen.Crops) }
        NavItem("▱", "Feira", selected == Screen.Market) { onSelect(Screen.Market) }
        NavItem("⌁", "Resultados", selected == Screen.Results) { onSelect(Screen.Results) }
    }
}

@Composable
private fun NavItem(icon: String, label: String, active: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onClick() }.padding(horizontal = 8.dp, vertical = 2.dp)) {
        Text(icon, fontSize = 21.sp, color = if (active) Forest else Muted)
        Text(label, fontSize = 11.sp, color = if (active) Forest else Muted, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun WelcomeScreen(name: String, onNameChange: (String) -> Unit, onContinue: () -> Unit, onVoice: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(24.dp))
        Brand()
        Spacer(Modifier.height(58.dp))
        Text("Sua horta,\nmais perto de você.", fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.SemiBold, color = ForestDark)
        Spacer(Modifier.height(12.dp))
        Text("Organize seus plantios e acompanhe cada colheita.", fontSize = 16.sp, lineHeight = 23.sp, color = Muted)
        Spacer(Modifier.height(62.dp))
        Text("Como podemos chamar você?", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Ink)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(value = name, onValueChange = onNameChange, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Seu nome") })
        Spacer(Modifier.height(14.dp))
        SecondaryButton("🎙  Continuar por voz", onVoice)
        Spacer(Modifier.height(12.dp))
        PrimaryButton("Continuar", onContinue)
        Text("Você pode atualizar seus dados depois.", fontSize = 12.sp, color = Muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 16.dp))
    }
}

@Composable
private fun HomeScreen(name: String, crops: List<CropRecord>, onOpenCrops: () -> Unit, onPlant: () -> Unit, onVoice: () -> Unit, onOpenCrop: (CropRecord) -> Unit) {
    val ready = crops.filter { it.status == "READY" }
    val growing = crops.filter { it.status == "GROWING" }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 12.dp)) {
        SectionCard("SUA HORTA HOJE", "${crops.count { it.status != "HARVESTED" }} cultivos ativos", "${ready.size} prontos para colher", icon = "☀️")
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("O que precisa de você", fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            Spacer(Modifier.weight(1f)); TextButton(onClick = onOpenCrops) { Text("Ver tudo") }
        }
        (ready.ifEmpty { growing }).take(3).forEach { crop ->
            CropRow(cropEmoji(crop.name), crop.name, crop.bed + " • " + if (crop.status == "READY") "pronta para colher" else "em crescimento", if (crop.status == "READY") "Colher" else "Crescendo") { onOpenCrop(crop) }
        }
        if (crops.isEmpty()) Text("Cadastre o primeiro cultivo para acompanhar sua horta.", color = Muted, modifier = Modifier.padding(vertical = 20.dp))
        Spacer(Modifier.height(14.dp))
        PrimaryButton("＋  Registrar plantio", onPlant)
        Spacer(Modifier.height(10.dp))
        SecondaryButton("🎙  Registrar por voz", onVoice)
    }
}

@Composable
private fun CropsScreen(crops: List<CropRecord>, onPlant: () -> Unit, onOpenCrop: (CropRecord) -> Unit) {
    var filter by remember { mutableStateOf("Todos") }
    val growingCount = crops.count { it.status == "GROWING" }
    val readyCount = crops.count { it.status == "READY" }
    val visible = when (filter) {
        "Em crescimento" -> crops.filter { it.status == "GROWING" }
        "Colheita" -> crops.filter { it.status == "READY" }
        else -> crops
    }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterPill("Todos (${crops.size})", filter == "Todos") { filter = "Todos" }
            FilterPill("Em crescimento ($growingCount)", filter == "Em crescimento") { filter = "Em crescimento" }
            FilterPill("Colheita ($readyCount)", filter == "Colheita") { filter = "Colheita" }
        }
        Spacer(Modifier.height(12.dp))
        visible.forEach { crop ->
            val statusLabel = when (crop.status) {
                "READY" -> "Colher"
                "HARVESTED" -> "Colhido"
                else -> "Crescendo"
            }
            val guide = harvestGuide(crop.name)
            val forecast = estimatedHarvestWindow(crop)
            val detail = if (guide != null && forecast != null) {
                "${crop.bed} • colher em ${forecast} (${guide.minDays}–${guide.maxDays} dias)"
            } else {
                "${crop.bed} • plantada em ${crop.plantedAt}"
            }
            CropRow(cropEmoji(crop.name), crop.name, detail, statusLabel) { onOpenCrop(crop) }
        }
        if (visible.isEmpty()) Text(if (filter == "Colheita") "Nenhum cultivo pronto para colher." else "Nenhum cultivo em crescimento.", color = Muted, modifier = Modifier.padding(vertical = 20.dp))
        Spacer(Modifier.height(16.dp)); PrimaryButton("＋  Novo plantio", onPlant)
    }
}

@Composable
private fun CropDetailScreen(crop: CropRecord, onHarvest: () -> Unit, onCosts: () -> Unit, onVoice: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp)) {
        val guide = harvestGuide(crop.name)
        val forecast = estimatedHarvestWindow(crop)
        SectionCard("STATUS DO CULTIVO", when (crop.status) { "READY" -> "Na janela de colheita"; "HARVESTED" -> "Colheita registrada"; else -> "Em crescimento" }, "${crop.quantity} unidades • ${crop.bed}", icon = cropEmoji(crop.name))
        if (guide != null && forecast != null) {
            Spacer(Modifier.height(10.dp))
            SectionCard("PREVISÃO ESTIMADA", forecast, "${guide.minDays}–${guide.maxDays} dias ${guide.basis}. ${guide.note}", icon = "◷")
        } else {
            Spacer(Modifier.height(10.dp))
            Text("Ainda não há uma faixa de colheita cadastrada para esse alimento. O prazo varia conforme cultivar e região.", color = Muted, fontSize = 12.sp)
        }
        Spacer(Modifier.height(20.dp)); Text("Seu cultivo em dia", fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = Ink)
        InfoRow("✓", "Plantio registrado", crop.plantedAt, "Concluído")
        InfoRow("◷", "Última rega", "Hoje, às 7h30", "Em dia")
        val cropCosts = crop.seedCost + crop.waterCost + crop.fertilizerCost
        InfoRow("R$", "Custos do cultivo", "Sementes, adubo e água", moneyLabel(cropCosts), onCosts)
        if (crop.status != "HARVESTED") {
            Spacer(Modifier.height(16.dp)); PrimaryButton("Registrar colheita", onHarvest)
        }
        Spacer(Modifier.height(10.dp)); SecondaryButton("Ver e adicionar custos", onCosts)
        Spacer(Modifier.height(10.dp)); SecondaryButton("🔈  Ouvir avisos", onVoice)
    }
}

@Composable
private fun CostsScreen(crop: CropRecord, onSave: (String, String, String) -> Unit) {
    var seeds by remember(crop.id, crop.seedCost) { mutableStateOf(editableMoney(crop.seedCost)) }
    var water by remember(crop.id, crop.waterCost) { mutableStateOf(editableMoney(crop.waterCost)) }
    var fertilizer by remember(crop.id, crop.fertilizerCost) { mutableStateOf(editableMoney(crop.fertilizerCost)) }
    val total = parseMoney(seeds) + parseMoney(water) + parseMoney(fertilizer)
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp)) {
        Text(crop.name, color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(10.dp)); SectionCard("TOTAL INVESTIDO", moneyLabel(total), "Custos registrados para este cultivo", icon = "R$")
        Spacer(Modifier.height(18.dp)); Text("Editar custos", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        CostField("Sementes", seeds, { seeds = it })
        CostField("Água", water, { water = it })
        CostField("Adubo", fertilizer, { fertilizer = it })
        Spacer(Modifier.height(14.dp)); PrimaryButton("Salvar custos") { onSave(seeds, water, fertilizer) }
    }
}

@Composable
private fun MarketScreen(costs: MarketCosts, onSaveCosts: (String, String, String) -> Unit, onStart: () -> Unit) {
    var transport by remember(costs.transport) { mutableStateOf(editableMoney(costs.transport)) }
    var stall by remember(costs.stall) { mutableStateOf(editableMoney(costs.stall)) }
    var other by remember(costs.other) { mutableStateOf(editableMoney(costs.other)) }
    val editableTotal = parseMoney(transport) + parseMoney(stall) + parseMoney(other)
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp)) {
        Text("Sábado, 24/10 • Feira do bairro", color = Muted, fontSize = 13.sp)
        InfoRow("🥬", "Alface crespa", "80 un. • R$ 3,00 por unidade", "R$ 240")
        InfoRow("🌿", "Couve", "30 maços • R$ 4,00 por maço", "R$ 120")
        Spacer(Modifier.height(12.dp)); Text("Editar custos da feira", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        CostField("Transporte", transport, { transport = it })
        CostField("Banca e taxas", stall, { stall = it })
        CostField("Outros custos", other, { other = it })
        TextButton(onClick = { onSaveCosts(transport, stall, other) }, modifier = Modifier.align(Alignment.End)) { Text("Salvar custos") }
        Spacer(Modifier.height(8.dp)); SectionCard("CUSTOS DA FEIRA", moneyLabel(editableTotal), "Valores salvos quando você tocar em Salvar custos", icon = "R$")
        Spacer(Modifier.height(16.dp)); SectionCard("RECEITA PREVISTA", "R$ 360,00", "Produtos R$ 82 • custos da feira ${moneyLabel(editableTotal)}", icon = "↗")
        Spacer(Modifier.height(16.dp)); PrimaryButton("Começar a vender", onStart)
    }
}

@Composable
private fun CostField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { input -> onValueChange(input.filter { it.isDigit() || it == ',' || it == '.' }) },
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        label = { Text(label) },
        prefix = { Text("R$ ") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    )
}

@Composable
private fun ResultsScreen(sales: List<SaleRecord>, recordedCosts: Double, onNext: () -> Unit, onHistory: () -> Unit) {
    val revenue = sales.sumOf { it.amount }
    val costs = sales.sumOf { it.costs } + recordedCosts
    val quantity = sales.sumOf { it.quantity }
    val topProduct = sales.groupBy { it.product }.maxByOrNull { (_, entries) -> entries.sumOf { it.amount } }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp)) {
        Text("Resumo das vendas registradas", color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(10.dp)); SectionCard("RESULTADO LÍQUIDO", moneyLabel(revenue - costs), "Receita ${moneyLabel(revenue)} • custos ${moneyLabel(costs)}", icon = "✦")
        Spacer(Modifier.height(14.dp)); InfoRow("↗", "Total vendido", "$quantity itens em ${sales.size} vendas", moneyLabel(revenue))
        InfoRow("▱", "Feiras registradas", "Vendas salvas no dispositivo", sales.size.toString())
        InfoRow("♧", "Produto em destaque", topProduct?.key ?: "Nenhuma venda", topProduct?.value?.let { moneyLabel(it.sumOf(SaleRecord::amount)) } ?: "—")
        if (sales.isEmpty()) Text("As vendas que você registrar aparecerão aqui.", color = Muted, modifier = Modifier.padding(vertical = 8.dp))
        Spacer(Modifier.height(14.dp)); PrimaryButton("Planejar próximo ciclo", onNext)
        Spacer(Modifier.height(8.dp)); SecondaryButton("Consultar histórico", onHistory)
    }
}

@Composable
private fun HistoryScreen(sales: List<SaleRecord>) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp)) {
        Text("${sales.size} registro(s) salvo(s) neste aparelho", color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
        if (sales.isEmpty()) {
            SectionCard("SEU HISTÓRICO", "Ainda sem vendas", "Quando registrar uma venda, ela ficará salva aqui.", icon = "▱")
        } else {
            sales.forEach { sale ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                    colors = CardDefaults.cardColors(containerColor = White),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(42.dp).background(CardGreen, CircleShape), contentAlignment = Alignment.Center) {
                            Text("▱", fontSize = 20.sp, color = Forest)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(sale.product, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                            Text("${sale.quantity} itens • ${sale.date}", fontSize = 12.sp, color = Muted)
                        }
                        Text(moneyLabel(sale.amount), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Forest)
                    }
                }
            }
        }
    }
}

@Composable
private fun FormScreen(
    fields: List<Pair<String, String>>,
    primary: String,
    secondary: String,
    onDone: (List<String>) -> Unit,
    onSecondary: (Int) -> Unit,
    voiceText: String,
    voiceFieldIndex: Int
) {
    var values by remember(fields) { mutableStateOf(fields.map { it.second }) }
    var focusedIndex by remember { mutableStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(voiceText, fields) {
        if (voiceText.isNotBlank() && values.isNotEmpty()) {
            val destination = voiceFieldIndex.coerceIn(0, values.lastIndex)
            values = values.toMutableList().also { it[destination] = voiceText }
        }
    }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp)) {
        fields.forEachIndexed { index, field ->
            OutlinedTextField(
                value = values[index],
                onValueChange = { value -> values = values.toMutableList().also { it[index] = value } },
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).onFocusChanged { if (it.isFocused) focusedIndex = index },
                label = { Text(field.first) }, singleLine = true,
            )
        }
        if (primary == "Salvar plantio") {
            val guide = harvestGuide(values.getOrNull(0).orEmpty())
            val forecast = estimatedHarvestWindow(values.getOrNull(0).orEmpty(), values.getOrNull(2).orEmpty())
            if (guide != null && forecast != null) {
                SectionCard("PREVISÃO ESTIMADA", forecast, "${guide.minDays}–${guide.maxDays} dias ${guide.basis}. ${guide.note}", icon = "🌱")
            } else {
                SectionCard("PRAZO DE COLHEITA", "Sem referência cadastrada", "Prazo disponível para alface, cenoura, couve, tomate, pimentão, abobrinha, manjericão e rúcula.", icon = "🌱")
            }
        }
        if (primary == "Confirmar colheita") {
            SectionCard("CUSTO POR UNIDADE COLHIDA", "R$ 0,65", "R$ 72,00 divididos por 110 unidades", icon = "R$")
        }
        if (primary == "Confirmar venda") {
            SectionCard("APÓS ESTA VENDA", "75 unidades", "Disponíveis na sua barraca", icon = "▱")
        }
        Spacer(Modifier.height(16.dp)); PrimaryButton(primary) { onDone(values) }
        Spacer(Modifier.height(10.dp)); SecondaryButton("🎙  $secondary", { onSecondary(focusedIndex) })
    }
}

@Composable
private fun SectionCard(label: String, value: String, detail: String, icon: String) {
    Card(colors = CardDefaults.cardColors(containerColor = CardGreen), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(42.dp).background(Color(0xFFD7E4D1), CircleShape), contentAlignment = Alignment.Center) {
                Text(icon, fontSize = 19.sp, color = Forest)
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, color = Forest)
                Spacer(Modifier.height(5.dp)); Text(value, fontSize = 23.sp, fontWeight = FontWeight.SemiBold, color = ForestDark)
                Spacer(Modifier.height(3.dp)); Text(detail, fontSize = 12.sp, color = Muted)
            }
        }
    }
}

@Composable
private fun CropRow(icon: String, title: String, detail: String, badge: String, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp).clickable { onClick() }, colors = CardDefaults.cardColors(containerColor = White), shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(48.dp).background(Color(0xFFF0F2E9), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) { Text(icon, fontSize = 25.sp) }
            Spacer(Modifier.width(12.dp)); Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Ink)
            Text(detail, fontSize = 11.sp, color = Muted, lineHeight = 15.sp)
        }
            Text(badge, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Forest, modifier = Modifier.background(CardGreen, RoundedCornerShape(50)).padding(horizontal = 9.dp, vertical = 6.dp))
        }
    }
}

@Composable
private fun InfoRow(icon: String, title: String, detail: String, value: String, onClick: () -> Unit = {}) {
    Row(modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(38.dp).background(CardGreen, CircleShape), contentAlignment = Alignment.Center) { Text(icon, fontSize = 15.sp, color = Forest) }
        Spacer(Modifier.width(12.dp)); Column(modifier = Modifier.weight(1f)) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink)
        Text(detail, fontSize = 12.sp, color = Muted)
    }
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Forest)
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
}

@Composable
private fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(label, fontSize = 12.sp, color = if (selected) White else Muted, fontWeight = FontWeight.Medium,
        modifier = Modifier.clickable { onClick() }.background(if (selected) Forest else White, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 8.dp))
}

private fun cropEmoji(name: String): String = when (name.lowercase()) {
    "alface", "alface crespa", "rúcula" -> "🥬"
    "cenoura" -> "🥕"
    "couve", "manjericão" -> "🌿"
    "tomate" -> "🍅"
    "pimentão" -> "🫑"
    "abobrinha" -> "🥒"
    else -> "🌱"
}

@Composable
private fun Brand() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("🌱", fontSize = 29.sp); Spacer(Modifier.width(9.dp)); Text("raiz", fontSize = 29.sp, fontWeight = FontWeight.Bold, color = Forest)
    }
}

@Composable
private fun PrimaryButton(label: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Forest)) {
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SecondaryButton(label: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = White, contentColor = Forest)) {
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}
