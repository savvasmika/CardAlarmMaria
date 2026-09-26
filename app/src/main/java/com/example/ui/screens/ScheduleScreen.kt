package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.BeachAccess
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.SentimentSatisfiedAlt
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ShiftDay

@Composable
fun ScheduleScreen(
    allShifts: List<ShiftDay>,
    availableMonths: List<String>,
    selectedMonth: String,
    onSelectMonth: (String) -> Unit,
    onEditShift: (ShiftDay) -> Unit,
    modifier: Modifier = Modifier
) {
    // Φιλτράρισμα των βαρδιών για τον επιλεγμένο μήνα
    val filteredShifts = remember(allShifts, selectedMonth) {
        if (selectedMonth.isEmpty()) {
            allShifts
        } else {
            allShifts.filter { it.monthKey == selectedMonth }
        }
    }

    val workingDaysCount = remember(filteredShifts) { filteredShifts.count { !it.isOff } }
    val offDaysCount = remember(filteredShifts) { filteredShifts.count { it.isOff } }
    val totalHours = remember(filteredShifts) { filteredShifts.sumOf { it.hoursWorked } }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Επικεφαλίδα
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    Text(
                        text = "ΠΡΟΓΡΑΜΜΑ",
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }

                Text(
                    text = "Μηνιαίο Ωράριο Μαρίας",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Text(
                    text = "Πατήστε σε οποιαδήποτε ημέρα για χειροκίνητη διόρθωση ωραρίου & alarms.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Επιλογέας Μήνα (Horizontal Scroll)
        if (availableMonths.isNotEmpty()) {
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    items(availableMonths) { monthKey ->
                        val isSelected = monthKey == selectedMonth
                        FilterChip(
                            selected = isSelected,
                            onClick = { onSelectMonth(monthKey) },
                            label = {
                                Text(
                                    text = formatMonthKey(monthKey),
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }
            }
        }

        // Κάρτα Στατιστικών Μήνα
        if (filteredShifts.isNotEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatItem(label = "Εργάσιμες", value = "$workingDaysCount ημέρες")
                        StatItem(label = "Ρεπό/Άδειες", value = "$offDaysCount ημέρες")
                        StatItem(label = "Σύνολο Ωρών", value = "${totalHours.toInt()} ώρες")
                    }
                }
            }
        }

        // Λίστα Ημερών
        if (filteredShifts.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Δεν βρέθηκαν καταχωρήσεις για τον επιλεγμένο μήνα.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 15.sp
                    )
                }
            }
        } else {
            items(filteredShifts, key = { it.dateKey }) { shift ->
                ScheduleDayCard(
                    shift = shift,
                    onClick = { onEditShift(shift) }
                )
            }
        }
    }
}

@Composable
fun ScheduleDayCard(
    shift: ShiftDay,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (shift.isOff) {
                if (shift.offType == "ΑΔΕΙΑ") Color(0xFFFEF3C7).copy(alpha = 0.7f) else Color(0xFFF1F5F9)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("shift_item_${shift.dateKey}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Ημερομηνία & Ημέρα
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    color = if (shift.isOff) Color(0xFFE2E8F0) else MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = shift.dayOfWeek,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (shift.isOff) Color(0xFF475569) else MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = shift.displayDate.replace(".", "/"),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            color = if (shift.isOff) Color(0xFF1E293B) else MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                // Ωράριο
                Column {
                    Text(
                        text = if (shift.isOff) (shift.offType ?: "ΡΕΠΟ") else formatScheduleDisplay(shift.rawSchedule),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (shift.isOff) Color(0xFF0369A1) else MaterialTheme.colorScheme.onSurface
                    )

                    if (!shift.isOff) {
                        Text(
                            text = "Alarms: ${shift.firstStartTime} (δουλειά) • ${shift.lastEndTime} (σχόλασμα)",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "Κανένα alarm για αυτή την ημέρα",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }
            }

            // Δεξιά: Εικονίδιο Επεξεργασίας
            IconButton(
                onClick = onClick,
                modifier = Modifier.testTag("edit_button_${shift.dateKey}")
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Επεξεργασία",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}

fun formatMonthKey(monthKey: String): String {
    val parts = monthKey.split('-')
    if (parts.size == 2) {
        val y = parts[0]
        val m = parts[1].toIntOrNull() ?: 1
        val names = listOf("Ιανουάριος", "Φεβρουάριος", "Μάρτιος", "Απρίλιος", "Μάιος", "Ιούνιος", "Ιούλιος", "Αύγουστος", "Σεπτέμβριος", "Οκτώβριος", "Νοέμβριος", "Δεκέμβριος")
        val name = names.getOrNull(m - 1) ?: monthKey
        return "$name $y"
    }
    return monthKey
}
