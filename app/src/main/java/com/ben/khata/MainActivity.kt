@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.ben.khata

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.ben.khata.data.*
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private const val Currency = "Rs."
private fun money(value: Long) = if (value < 0) "-$Currency ${NumberFormat.getIntegerInstance(Locale.US).format(-value)}" else "$Currency ${NumberFormat.getIntegerInstance(Locale.US).format(value)}"
private fun date(value: Long) = SimpleDateFormat("dd MMM yyyy, h:mm a", Locale.getDefault()).format(Date(value))
private fun day(value: Long) = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(value))

class KhataViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = KhataDatabase.create(app).dao()
    val people = dao.people().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val total = dao.total().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val peopleCount = dao.peopleCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val recent = dao.recent().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val history = dao.history().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val budget = dao.budget().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    fun add(name: String, amount: Long, note: String, personId: Long? = null) = viewModelScope.launch { dao.addEntry(name, amount, note, personId) }
    fun update(tx: KhataTransaction, amount: Long, note: String) = viewModelScope.launch { dao.updateTransaction(tx.id, amount, note) }
    fun delete(tx: KhataTransaction) = viewModelScope.launch { dao.deleteTransaction(tx.id) }
    fun deletePerson(id: Long) = viewModelScope.launch { dao.deletePerson(id) }
    fun saveBudget(amount: Long) = viewModelScope.launch { dao.saveSettings(AppSettings(totalBudget = amount)) }
    fun detail(id: Long) = dao.person(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    fun entries(id: Long) = dao.transactions(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) = super.onCreate(savedInstanceState).also {
        setContent {
            val preferences = remember { getSharedPreferences("settings", MODE_PRIVATE) }
            var theme by remember { mutableStateOf(preferences.getString("theme", "system") ?: "system") }
            val dark = when (theme) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                KhataApp(
                    theme = theme,
                    setTheme = { value ->
                        theme = value
                        preferences.edit().putString("theme", value).apply()
                    }
                )
            }
        }
    }
}

@Composable private fun KhataApp(theme: String, setTheme: (String) -> Unit, vm: KhataViewModel = viewModel()) {
    val nav = rememberNavController()
    val tabs = listOf("home" to "Home", "people" to "People", "history" to "Daily", "settings" to "Settings")
    val backStackEntry by nav.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    fun goToRoot(index: Int) {
        val route = tabs[index].first
        if (currentRoute == route) return
        // Reuse the already-open root tab when navigating out of a details or add-payment screen.
        if (!nav.popBackStack(route, inclusive = false)) {
            nav.navigate(route) { popUpTo("home") { saveState = true }; launchSingleTop = true; restoreState = true }
        }
    }
    Scaffold(bottomBar = { NavigationBar { tabs.forEach { (route, label) ->
        val icon = when (route) { "home" -> Icons.Default.Home; "people" -> Icons.Default.People; "history" -> Icons.Default.ReceiptLong; else -> Icons.Default.Settings }
        NavigationBarItem(currentRoute == route, { goToRoot(tabs.indexOfFirst { it.first == route }) }, { Icon(icon, null) }, label = { Text(label) })
    } } }) { padding ->
        var horizontalDrag by remember(currentRoute) { mutableFloatStateOf(0f) }
        val rootIndex = tabs.indexOfFirst { it.first == currentRoute }
        val swipeModifier = if (rootIndex >= 0) Modifier.pointerInput(currentRoute) {
            detectHorizontalDragGestures(
                onHorizontalDrag = { _, delta -> horizontalDrag += delta },
                onDragEnd = {
                    if (abs(horizontalDrag) > 120f) {
                        val next = if (horizontalDrag < 0) rootIndex + 1 else rootIndex - 1
                        if (next in tabs.indices) goToRoot(next)
                    }
                    horizontalDrag = 0f
                },
                onDragCancel = { horizontalDrag = 0f }
            )
        } else Modifier
        NavHost(
            navController = nav,
            startDestination = "home",
            modifier = Modifier.padding(padding).then(swipeModifier),
            enterTransition = { slideInHorizontally(animationSpec = tween(220)) { it / 7 } + fadeIn(tween(180)) },
            exitTransition = { slideOutHorizontally(animationSpec = tween(180)) { -it / 10 } + fadeOut(tween(140)) },
            popEnterTransition = { slideInHorizontally(animationSpec = tween(220)) { -it / 7 } + fadeIn(tween(180)) },
            popExitTransition = { slideOutHorizontally(animationSpec = tween(180)) { it / 10 } + fadeOut(tween(140)) }
        ) {
        composable("home") { Home(vm, { nav.navigate("add") }, { nav.navigate("person/$it") }) }
        composable("people") { People(vm) { nav.navigate("person/$it") } }
        composable("history") { History(vm) { nav.navigate("person/$it") } }
        composable("settings") { Settings(vm, theme, setTheme) }
        composable("add") { EntryForm("Add entry", { nav.popBackStack() }) { name, amount, note -> vm.add(name, amount, note); nav.popBackStack() } }
        composable("person/{id}", listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            val id = entry.arguments!!.getLong("id")
            PersonScreen(vm, id, { nav.popBackStack() }, { nav.navigate("add/$id") })
        }
        composable("add/{id}", listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            val id = entry.arguments!!.getLong("id")
            val personFlow = remember(id) { vm.detail(id) }
            val person by personFlow.collectAsState()
            EntryForm("Add amount", { nav.popBackStack() }, person?.name) { _, amount, note -> vm.add("", amount, note, id); nav.popBackStack() }
        }
        }
    }
}

@Composable private fun Home(vm: KhataViewModel, add: () -> Unit, open: (Long) -> Unit) {
    val paid by vm.total.collectAsState(); val budget by vm.budget.collectAsState(); val recent by vm.recent.collectAsState(); val people by vm.people.collectAsState()
    val remaining = (budget ?: 0) - paid
    Scaffold(topBar = { AppHeader("Digital Hisab", "Personal payment tracker") }, floatingActionButton = { FloatingActionButton(add) { Icon(Icons.Default.Add, "Add entry") } }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { Text("Your money", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            item { SummaryCard("Total Budget", money(budget ?: 0), Modifier.fillMaxWidth()) }
            item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { SummaryCard("Total Paid", money(paid), Modifier.weight(1f)); SummaryCard("Remaining", money(remaining), Modifier.weight(1f), if (budget != null && remaining < 0) MaterialTheme.colorScheme.error else null) } }
            item { Text("Recent entries", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
            if (recent.isEmpty()) item { Empty("No entries yet. Tap + to add your first payment.") }
            items(recent, key = { it.id }) { tx -> TransactionCard(tx, people.firstOrNull { p -> p.id == tx.personId }?.name ?: "Person", Modifier.clickable { open(tx.personId) }) }
        }
    }
}

@Composable private fun People(vm: KhataViewModel, open: (Long) -> Unit) {
    val people by vm.people.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    val shown = people.filter { it.name.contains(query.trim(), true) }
    Scaffold(topBar = { AppHeader("People", "All accounts") }) { padding -> Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("Search people") }, singleLine = true)
        Spacer(Modifier.height(14.dp))
        if (shown.isEmpty()) Empty(if (query.isBlank()) "No people yet." else "No matching people.") else LazyColumn(contentPadding = PaddingValues(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(shown, key = { it.id }) { p ->
                GlassCard(Modifier.fillMaxWidth().clickable { open(p.id) }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(p.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); Text("${p.entryCount} ${if (p.entryCount == 1L) "payment" else "payments"}", style = MaterialTheme.typography.bodySmall) }
                        Text(money(p.total), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    } }
}

@Composable private fun History(vm: KhataViewModel, open: (Long) -> Unit) {
    val history by vm.history.collectAsState()
    val groups = history.groupBy { day(it.createdAt) }
    Scaffold(topBar = { AppHeader("Daily history", "Payments grouped by day") }) { padding -> Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(14.dp))
        if (history.isEmpty()) Empty("No payment history yet.") else LazyColumn(contentPadding = PaddingValues(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            groups.forEach { (label, entries) -> item(label) {
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, fontWeight = FontWeight.Bold); Text(money(entries.sumOf { it.amount }), fontWeight = FontWeight.Bold) }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                        entries.forEach { tx -> Row(Modifier.fillMaxWidth().clickable { open(tx.personId) }.padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(tx.personName, fontWeight = FontWeight.Medium); Text(money(tx.amount), fontWeight = FontWeight.SemiBold) } }
                    }
                }
            } }
        }
    } }
}
@Composable private fun PersonScreen(vm: KhataViewModel, id: Long, back: () -> Unit, add: () -> Unit) {
    val personFlow = remember(id) { vm.detail(id) }
    val entriesFlow = remember(id) { vm.entries(id) }
    val person by personFlow.collectAsState()
    val entries by entriesFlow.collectAsState()
    var edit by remember { mutableStateOf<KhataTransaction?>(null) }
    var remove by remember { mutableStateOf<KhataTransaction?>(null) }
    var deletePerson by remember { mutableStateOf(false) }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(person?.name ?: "Khata") },
            navigationIcon = { IconButton(back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = { IconButton({ deletePerson = true }) { Icon(Icons.Default.Delete, "Delete person") } }
        )
    }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { SummaryCard("Total owed", money(person?.total ?: 0), Modifier.fillMaxWidth()) }
            item { Button(onClick = add, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Add amount") } }
            item { Text("Transactions", style = MaterialTheme.typography.titleMedium) }
            if (entries.isEmpty()) item { Empty("No transactions yet.") }
            items(entries, key = { it.id }) { tx -> TransactionCard(tx, null, actions = { IconButton({ edit = tx }) { Icon(Icons.Default.Edit, "Edit") }; IconButton({ remove = tx }) { Icon(Icons.Default.Delete, "Delete") } }) }
        }
    }
    edit?.let { tx -> EditDialog(tx, { edit = null }) { amount, note -> vm.update(tx, amount, note); edit = null } }
    remove?.let { tx -> AlertDialog({ remove = null }, title = { Text("Delete entry?") }, text = { Text("Totals and remaining budget will update automatically.") }, confirmButton = { TextButton({ vm.delete(tx); remove = null }) { Text("Delete") } }, dismissButton = { TextButton({ remove = null }) { Text("Cancel") } }) }
    if (deletePerson) AlertDialog({ deletePerson = false }, title = { Text("Delete ${person?.name ?: "person"}?") }, text = { Text("All of this person's transactions will be deleted. Totals and remaining budget will update automatically.") }, confirmButton = { TextButton({ vm.deletePerson(id); deletePerson = false; back() }) { Text("Delete") } }, dismissButton = { TextButton({ deletePerson = false }) { Text("Cancel") } })
}

@Composable private fun Settings(vm: KhataViewModel, theme: String, setTheme: (String) -> Unit) {
    val budget by vm.budget.collectAsState()
    var editing by remember { mutableStateOf(false) }
    Scaffold(topBar = { AppHeader("Settings", "Personalise Digital Hisab") }) { padding -> Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(16.dp))
        GlassCard(Modifier.fillMaxWidth()) { Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Total budget", fontWeight = FontWeight.SemiBold); Text(money(budget ?: 0), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }; OutlinedButton({ editing = true }) { Text("Edit") } } }
        Spacer(Modifier.height(18.dp))
        Text("Appearance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEach { (value, label) -> FilterChip(theme == value, { setTheme(value) }, label = { Text(label) }) } }
        Spacer(Modifier.height(18.dp))
        GlassCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Text("Currency", fontWeight = FontWeight.SemiBold); Text("$Currency · Pakistani Rupee", color = MaterialTheme.colorScheme.onSurfaceVariant); HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)); Text("About", fontWeight = FontWeight.SemiBold); Text("Private offline payment tracking", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
    } }
    if (editing) BudgetDialog(budget ?: 0, { editing = false }) { vm.saveBudget(it); editing = false }
}

@Composable private fun EntryForm(title: String, back: () -> Unit, fixedName: String? = null, save: (String, Long, String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(fixedName.orEmpty()) }; var amount by rememberSaveable { mutableStateOf("") }; var note by rememberSaveable { mutableStateOf("") }; var error by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(fixedName) { if (!fixedName.isNullOrBlank()) name = fixedName }
    Scaffold(topBar = { TopAppBar(title = { Text(title) }, navigationIcon = { IconButton(back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }) { padding ->
        Column(Modifier.padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (fixedName == null) {
                OutlinedTextField(name, { name = it; error = null }, Modifier.fillMaxWidth(), label = { Text("Person name") }, singleLine = true)
            } else {
                GlassCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Text("Adding payment for", style = MaterialTheme.typography.labelMedium); Text(fixedName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) } }
            }
            OutlinedTextField(amount, { amount = it.filter(Char::isDigit); error = null }, Modifier.fillMaxWidth(), label = { Text("Amount (PKR)") }, prefix = { Text("$Currency ") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text("Note (optional)") }, minLines = 2)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = {
                val value = amount.toLongOrNull()
                val personName = fixedName ?: name
                error = when { personName.trim().isEmpty() -> "Enter a person name."; value == null || value <= 0 -> "Enter an amount greater than zero."; else -> null }
                if (error == null) save(personName, value!!, note)
            }, modifier = Modifier.fillMaxWidth()) { Text(if (fixedName == null) "Save entry" else "Add amount") }
        }
    }
}

@Composable private fun BudgetDialog(current: Long, dismiss: () -> Unit, save: (Long) -> Unit) { var input by remember { mutableStateOf(if (current == 0L) "" else current.toString()) }; var error by remember { mutableStateOf(false) }; AlertDialog({ dismiss() }, title = { Text("Set total budget") }, text = { Column { OutlinedTextField(input, { input = it.filter(Char::isDigit); error = false }, label = { Text("Budget (PKR)") }, prefix = { Text("$Currency ") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)); if (error) Text("Enter a valid budget.", color = MaterialTheme.colorScheme.error) } }, confirmButton = { TextButton({ val value = input.toLongOrNull(); if (value == null) error = true else save(value) }) { Text("Save") } }, dismissButton = { TextButton(dismiss) { Text("Cancel") } }) }
@Composable private fun EditDialog(tx: KhataTransaction, dismiss: () -> Unit, save: (Long, String) -> Unit) { var amount by remember { mutableStateOf(tx.amount.toString()) }; var note by remember { mutableStateOf(tx.note) }; var error by remember { mutableStateOf(false) }; AlertDialog(dismiss, title = { Text("Edit entry") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(amount, { amount = it.filter(Char::isDigit); error = false }, label = { Text("Amount") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)); OutlinedTextField(note, { note = it }, label = { Text("Note") }); if (error) Text("Enter an amount greater than zero.", color = MaterialTheme.colorScheme.error) } }, confirmButton = { TextButton({ val value = amount.toLongOrNull(); if (value == null || value <= 0) error = true else save(value, note) }) { Text("Save") } }, dismissButton = { TextButton(dismiss) { Text("Cancel") } }) }
@Composable private fun AppHeader(title: String, subtitle: String) = Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RectangleShape,
    color = MaterialTheme.colorScheme.surface,
    contentColor = MaterialTheme.colorScheme.onSurface,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    tonalElevation = 0.dp,
    shadowElevation = 0.dp
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
@Composable private fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) = ElevatedCard(modifier, shape = RoundedCornerShape(12.dp)) { Column(content = content) }
@Composable private fun SummaryCard(label: String, value: String, modifier: Modifier, color: Color? = null) = GlassCard(modifier) { Column(Modifier.padding(18.dp)) { Text(label, style = MaterialTheme.typography.labelMedium); Spacer(Modifier.height(6.dp)); Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color ?: LocalContentColor.current, maxLines = 1) } }
@Composable private fun TransactionCard(tx: KhataTransaction, name: String?, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {}) = GlassCard(modifier.fillMaxWidth()) { Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(name ?: money(tx.amount), style = MaterialTheme.typography.titleSmall); if (name != null) Text(money(tx.amount), fontWeight = FontWeight.SemiBold); if (tx.note.isNotBlank()) Text(tx.note, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis); Text(date(tx.createdAt), style = MaterialTheme.typography.labelSmall) }; Row(content = actions) } }
@Composable private fun Empty(text: String) = Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) { Text(text) }
