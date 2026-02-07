package com.rustore.forgetful

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

private const val PREFS_NAME = "forgetful_prefs"
private const val PREFS_TASKS = "tasks"
private const val PREFS_LAST_RESET = "last_reset"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val storage = TaskStorage(this)
        setContent {
            MaterialTheme {
                ForgetfulApp(storage = storage)
            }
        }
    }
}

data class TaskUi(
    val id: String,
    val title: String,
    val iconRes: Int,
    val done: Boolean
)

private sealed class Screen {
    data object List : Screen()
    data object Create : Screen()
    data class Edit(val taskId: String) : Screen()
}

@Composable
private fun ForgetfulApp(storage: TaskStorage) {
    var screen by remember { mutableStateOf<Screen>(Screen.List) }
    val tasks = remember { mutableStateListOf<TaskUi>() }

    LaunchedEffect(Unit) {
        val loaded = storage.loadTasks()
        tasks.clear()
        tasks.addAll(storage.applyDailyResetIfNeeded(loaded))
    }

    val currentScreen = screen
    when (currentScreen) {
        Screen.List -> TaskListScreen(
            tasks = tasks,
            onToggle = { index, checked ->
                tasks[index] = tasks[index].copy(done = checked)
                storage.saveTasks(tasks)
            },
            onCreate = { screen = Screen.Create },
            onEdit = { index -> screen = Screen.Edit(tasks[index].id) },
            onDelete = { index ->
                tasks.removeAt(index)
                storage.saveTasks(tasks)
            },
            onManualReset = {
                for (index in tasks.indices) {
                    tasks[index] = tasks[index].copy(done = false)
                }
                storage.markResetNow()
                storage.saveTasks(tasks)
            }
        )
        Screen.Create -> CreateTaskScreen(
            onBack = { screen = Screen.List },
            onSave = { title, iconRes ->
                tasks.add(
                    TaskUi(
                        id = UUID.randomUUID().toString(),
                        title = title,
                        iconRes = iconRes,
                        done = false
                    )
                )
                storage.saveTasks(tasks)
                screen = Screen.List
            }
        )
        is Screen.Edit -> {
            val task = tasks.firstOrNull { it.id == screen.taskId }
            if (task == null) {
                screen = Screen.List
            } else {
                EditTaskScreen(
                    task = task,
                    onBack = { screen = Screen.List },
                    onSave = { title, iconRes ->
                        val index = tasks.indexOfFirst { it.id == task.id }
                        if (index != -1) {
                            tasks[index] = tasks[index].copy(title = title, iconRes = iconRes)
                            storage.saveTasks(tasks)
                        }
                        screen = Screen.List
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskListScreen(
    tasks: List<TaskUi>,
    onToggle: (Int, Boolean) -> Unit,
    onCreate: () -> Unit,
    onEdit: (Int) -> Unit,
    onDelete: (Int) -> Unit,
    onManualReset: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.app_name)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                actions = {
                    IconButton(onClick = onCreate) {
                        Text(
                            text = stringResource(id = R.string.add_task),
                            style = MaterialTheme.typography.headlineMedium,
                            fontSize = 32.sp
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(id = R.string.daily_reset_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                itemsIndexed(tasks) { index, task ->
                    TaskRow(
                        task = task,
                        onToggle = { checked -> onToggle(index, checked) },
                        onDelete = { onDelete(index) },
                        onEdit = { onEdit(index) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onManualReset,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Text(text = stringResource(id = R.string.reset_today))
            }
        }
    }
}

@Composable
private fun TaskRow(
    task: TaskUi,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onEdit() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(color = MaterialTheme.colorScheme.surface, shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = task.iconRes),
                        contentDescription = null,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(id = R.string.delete_task)
                )
            }
            Switch(checked = task.done, onCheckedChange = onToggle)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateTaskScreen(
    onBack: () -> Unit,
    onSave: (String, Int) -> Unit
) {
    val icons = remember { taskIcons() }
    var selectedIcon by remember { mutableStateOf(icons.first()) }
    var title by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.custom_task)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text(text = "←")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = stringResource(id = R.string.pick_icon),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(12.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.height(260.dp)
            ) {
                items(icons) { icon ->
                    IconOption(
                        iconRes = icon,
                        selected = icon == selectedIcon,
                        onClick = { selectedIcon = icon }
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(text = stringResource(id = R.string.task_name)) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = { if (title.isNotBlank()) onSave(title.trim(), selectedIcon) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(id = R.string.save))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditTaskScreen(
    task: TaskUi,
    onBack: () -> Unit,
    onSave: (String, Int) -> Unit
) {
    val icons = remember { taskIcons() }
    var selectedIcon by remember { mutableStateOf(task.iconRes) }
    var title by remember { mutableStateOf(task.title) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.edit_task)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text(text = "←")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = stringResource(id = R.string.pick_icon),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(12.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.height(260.dp)
            ) {
                items(icons) { icon ->
                    IconOption(
                        iconRes = icon,
                        selected = icon == selectedIcon,
                        onClick = { selectedIcon = icon }
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(text = stringResource(id = R.string.task_name)) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = { if (title.isNotBlank()) onSave(title.trim(), selectedIcon) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(id = R.string.save))
            }
        }
    }
}

@Composable
private fun IconOption(iconRes: Int, selected: Boolean, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(2.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier
            .size(72.dp)
            .clickable { onClick() }
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

private fun taskIcons(): List<Int> {
    return listOf(
        R.drawable.ic_iron,
        R.drawable.ic_door,
        R.drawable.ic_window,
        R.drawable.ic_gas,
        R.drawable.ic_water,
        R.drawable.ic_light,
        R.drawable.ic_pets,
        R.drawable.ic_home
    )
}

private class TaskStorage(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadTasks(): MutableList<TaskUi> {
        val serialized = prefs.getString(PREFS_TASKS, null) ?: return defaultTasks()
        val array = JSONArray(serialized)
        val items = mutableListOf<TaskUi>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            items.add(
                TaskUi(
                    id = obj.getString("id"),
                    title = obj.getString("title"),
                    iconRes = obj.getInt("icon"),
                    done = obj.getBoolean("done")
                )
            )
        }
        return items
    }

    fun saveTasks(tasks: List<TaskUi>) {
        val array = JSONArray()
        tasks.forEach { task ->
            val obj = JSONObject()
            obj.put("id", task.id)
            obj.put("title", task.title)
            obj.put("icon", task.iconRes)
            obj.put("done", task.done)
            array.put(obj)
        }
        prefs.edit().putString(PREFS_TASKS, array.toString()).apply()
    }

    fun applyDailyResetIfNeeded(tasks: MutableList<TaskUi>): MutableList<TaskUi> {
        val today = LocalDate.now().toString()
        val lastReset = prefs.getString(PREFS_LAST_RESET, null)
        if (lastReset == null || lastReset != today) {
            val resetTasks = tasks.map { it.copy(done = false) }.toMutableList()
            saveTasks(resetTasks)
            prefs.edit().putString(PREFS_LAST_RESET, today).apply()
            return resetTasks
        }
        return tasks
    }

    fun markResetNow() {
        val today = LocalDate.now().toString()
        prefs.edit().putString(PREFS_LAST_RESET, today).apply()
    }

    private fun defaultTasks(): MutableList<TaskUi> {
        return mutableListOf(
            TaskUi("iron", "Выключить утюг", R.drawable.ic_iron, false),
            TaskUi("door", "Закрыть дверь", R.drawable.ic_door, false),
            TaskUi("window", "Закрыть окна", R.drawable.ic_window, false),
            TaskUi("gas", "Проверить газ", R.drawable.ic_gas, false),
            TaskUi("water", "Проверить воду", R.drawable.ic_water, false),
            TaskUi("light", "Выключить свет", R.drawable.ic_light, false)
        )
    }
}
