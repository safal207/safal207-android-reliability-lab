package com.robys.coffeehouse

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Coffee
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.robys.coffeehouse.ui.theme.Caramel
import com.robys.coffeehouse.ui.theme.Cream
import com.robys.coffeehouse.ui.theme.CreamDeep
import com.robys.coffeehouse.ui.theme.Espresso
import com.robys.coffeehouse.ui.theme.EspressoSoft
import com.robys.coffeehouse.ui.theme.Muted
import com.robys.coffeehouse.ui.theme.RobysCoffeeHouseTheme

private enum class AppScreen(val label: String) { HOME("Ana Sayfa"), MENU("Keşfet"), VISIT("Ziyaret") }
private enum class MenuCategory(val label: String) { ALL("Tümü"), HOT("Sıcak Kahve"), COLD("Soğuk İçecekler"), DESSERT("Tatlı"), FOOD("Atıştırmalık") }
private data class MenuItem(val name: String, val detail: String, val price: String, val category: MenuCategory, val image: Int? = null)

private val menuItems = listOf(
    MenuItem("Latte", "Espresso · kadifemsi süt köpüğü", "₺180", MenuCategory.HOT, R.drawable.latte),
    MenuItem("Iced Latte", "Soğuk espresso · süt · buz", "₺180", MenuCategory.COLD, R.drawable.iced_latte),
    MenuItem("Americano", "Çift espresso · sıcak su", "₺150", MenuCategory.HOT),
    MenuItem("San Sebastian", "Günün cheesecake’i", "₺190", MenuCategory.DESSERT, R.drawable.san_sebastian),
    MenuItem("Kruvasan", "Tereyağlı · fırından taze", "₺170", MenuCategory.FOOD),
    MenuItem("Cool Lime", "Lime · nane · ferahlatıcı", "₺160", MenuCategory.COLD)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent { RobysCoffeeHouseTheme { RobysApp() } }
    }
}

@Composable
private fun RobysApp() {
    var selectedScreen by rememberSaveable { mutableStateOf(AppScreen.HOME) }
    var category by rememberSaveable { mutableStateOf(MenuCategory.ALL) }
    BackHandler(enabled = selectedScreen != AppScreen.HOME) { selectedScreen = AppScreen.HOME }
    Scaffold(
        containerColor = Cream,
        bottomBar = {
            NavigationBar(containerColor = Espresso, tonalElevation = 0.dp) {
                AppScreen.entries.forEach { screen ->
                    NavigationBarItem(
                        modifier = Modifier.testTag("tab_${screen.name}"),
                        selected = selectedScreen == screen,
                        onClick = { selectedScreen = screen },
                        icon = {
                            Icon(
                                imageVector = when (screen) {
                                    AppScreen.HOME -> Icons.Outlined.Home
                                    AppScreen.MENU -> Icons.Outlined.MenuBook
                                    AppScreen.VISIT -> Icons.Outlined.LocationOn
                                },
                                contentDescription = screen.label
                            )
                        },
                        label = { Text(screen.label, fontSize = 11.sp) },
                        colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                            selectedIconColor = Caramel,
                            selectedTextColor = Cream,
                            unselectedIconColor = CreamDeep,
                            unselectedTextColor = CreamDeep,
                            indicatorColor = EspressoSoft
                        )
                    )
                }
            }
        }
    ) { padding ->
        when (selectedScreen) {
            AppScreen.HOME -> HomeScreen(padding, onMenu = { selectedScreen = AppScreen.MENU }, onVisit = { selectedScreen = AppScreen.VISIT })
            AppScreen.MENU -> DiscoverScreen(padding, category, onCategory = { category = it })
            AppScreen.VISIT -> VisitScreen(padding)
        }
    }
}

@Composable
private fun HomeScreen(padding: PaddingValues, onMenu: () -> Unit, onVisit: () -> Unit) {
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding).testTag("homeScroll"),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Box(modifier = Modifier.fillMaxWidth().heightIn(min = 425.dp)) {
                Image(
                    painter = painterResource(R.drawable.hero_coffee),
                    contentDescription = "Roby's Coffee House",
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop
                )
                Box(modifier = Modifier.matchParentSize().background(Color(0x990F0B0A)))
                Column(modifier = Modifier.align(Alignment.BottomStart).padding(start = 24.dp, end = 24.dp, top = 70.dp, bottom = 24.dp)) {
                    Text("GAZİPAŞA · FRESH COFFEE POINT", color = Caramel, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("İyi kahve.", color = Color.White, fontSize = 42.sp, fontWeight = FontWeight.Bold, lineHeight = 44.sp)
                    Text("Sakin anlar.", color = Cream, fontSize = 42.sp, fontWeight = FontWeight.Light, lineHeight = 44.sp)
                    Spacer(Modifier.height(10.dp))
                    Text("Taze kahveler, lezzetli tatlılar ve günün hızından uzaklaşabileceğiniz keyifli bir atmosfer.", color = Color.White.copy(alpha = .86f), fontSize = 15.sp, lineHeight = 22.sp)
                    Spacer(Modifier.height(18.dp))
                    Column {
                        Button(onClick = onMenu, colors = ButtonDefaults.buttonColors(containerColor = Caramel, contentColor = Espresso), shape = RoundedCornerShape(14.dp)) { Text("Tatları keşfet", fontWeight = FontWeight.Bold) }
                        TextButton(onClick = onVisit) { Text("Bizi bul →", color = Cream, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
        item { InfoStrip() }
        item { SectionTitle(eyebrow = "ÖRNEK TATLAR", title = "Roby's Favorileri", subtitle = "Demo seçkisi · gösterilen fiyatlar güncel değildir.") }
        item {
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                menuItems.take(3).forEach { item -> FavoriteCard(item) }
            }
        }
        item { SectionTitle(eyebrow = "ROBY'S HİSSİ", title = "Kahveden fazlası.", subtitle = "Kendinize ayırdığınız zaman.") }
        item { FeatureList() }
        item {
            Card(modifier = Modifier.padding(20.dp).fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Espresso), shape = RoundedCornerShape(24.dp)) {
                Column(modifier = Modifier.padding(22.dp)) {
                    Text("Bir kahve molası için hazır mısın?", color = Cream, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(7.dp))
                    Text("Pazarcı, Uğur Mumcu Cd. · Gazipaşa / Antalya", color = CreamDeep, fontSize = 14.sp)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { openMaps(context) }, colors = ButtonDefaults.buttonColors(containerColor = Caramel, contentColor = Espresso), shape = RoundedCornerShape(12.dp)) {
                        Icon(Icons.Outlined.Navigation, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(8.dp)); Text("Rota al")
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoStrip() {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        InfoCell("09:00 — 00:00", "Her gün", Modifier.weight(1f))
        InfoCell("Gazipaşa", "Antalya", Modifier.weight(1f))
        InfoCell("Wi‑Fi · 220V", "Laptop friendly", Modifier.weight(1f))
    }
}

@Composable
private fun InfoCell(value: String, caption: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(end = 6.dp)) { Text(value, color = Espresso, fontWeight = FontWeight.Bold, fontSize = 13.sp); Text(caption, color = Muted, fontSize = 12.sp) }
}

@Composable
private fun SectionTitle(eyebrow: String, title: String, subtitle: String) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
        Text(eyebrow, color = EspressoSoft, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.7.sp)
        Spacer(Modifier.height(7.dp)); Text(title, color = Espresso, fontSize = 28.sp, fontWeight = FontWeight.Bold); Text(subtitle, color = Muted, fontSize = 14.sp)
    }
}

@Composable
private fun FavoriteCard(item: MenuItem) {
    Card(modifier = Modifier.width(210.dp), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column {
            item.image?.let { Image(painterResource(it), item.name, Modifier.fillMaxWidth().height(160.dp), contentScale = ContentScale.Crop) }
            Column(Modifier.padding(14.dp)) { Text(item.name, color = Espresso, fontSize = 18.sp, fontWeight = FontWeight.Bold); Text(item.detail, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis); Spacer(Modifier.height(8.dp)); Text(item.price, color = Espresso, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun FeatureList() {
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        listOf("Taze kahve" to "Her fincanda dengeli tat, özenli hazırlık ve iyi çekirdeklerin sıcak aroması.", "Tatlı molalar" to "Kahvenize eşlik eden tatlılar, kurabiyeler ve günün küçük mutlulukları.", "Sakin atmosfer" to "Arkadaşlarınızla buluşmak, çalışmak ya da sadece yavaşlamak için rahat bir köşe.").forEachIndexed { index, pair ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 13.dp), verticalAlignment = Alignment.Top) {
                Text("0${index + 1}", color = Caramel, fontWeight = FontWeight.Bold, modifier = Modifier.width(32.dp)); Column { Text(pair.first, color = Espresso, fontSize = 17.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(3.dp)); Text(pair.second, color = Muted, fontSize = 13.sp, lineHeight = 19.sp) }
            }
            if (index < 2) Divider(color = CreamDeep)
        }
    }
}

@Composable
private fun DiscoverScreen(padding: PaddingValues, category: MenuCategory, onCategory: (MenuCategory) -> Unit) {
    val context = LocalContext.current
    val visibleItems = menuItems.filter { category == MenuCategory.ALL || it.category == category }
    LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).testTag("discoverScroll"), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 24.dp)) {
                Text("ROBY'S TASTE JOURNEY", color = EspressoSoft, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.7.sp)
                Spacer(Modifier.height(8.dp))
                Text("Kendi tadını keşfet.", color = Espresso, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("Bugün tek bir iyi eşleşme. Yarın başka bir tat. Baskı yok, sadece merak.", color = Muted, fontSize = 15.sp, lineHeight = 22.sp)
                Spacer(Modifier.height(18.dp))
                Button(onClick = { openDiscover(context) }, colors = ButtonDefaults.buttonColors(containerColor = Espresso, contentColor = Cream), shape = RoundedCornerShape(14.dp)) {
                    Text("Tatları keşfet", fontWeight = FontWeight.Bold)
                }
                Text("Roby's Taste Journey tarayıcıda açılır.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                Spacer(Modifier.height(24.dp))
                Text("Örnek tatlar", color = Espresso, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Demo seçkisi · gösterilen fiyatlar güncel değildir.", color = Muted, fontSize = 13.sp)
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MenuCategory.entries.forEach { cat -> CategoryChip(cat.label, category == cat, onClick = { onCategory(cat) }) }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
        items(visibleItems, key = { it.name }) { MenuRow(it) }
    }
}

@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontWeight = FontWeight.Bold) },
        modifier = Modifier.heightIn(min = 48.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = CreamDeep,
            labelColor = Espresso,
            selectedContainerColor = Espresso,
            selectedLabelColor = Cream
        )
    )
}

@Composable
private fun MenuRow(item: MenuItem) {
    Card(modifier = Modifier.padding(horizontal = 20.dp, vertical = 7.dp).fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            item.image?.let { Image(painterResource(it), item.name, Modifier.size(76.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop) } ?: Box(Modifier.size(76.dp).clip(RoundedCornerShape(14.dp)).background(CreamDeep), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Coffee, null, tint = Espresso, modifier = Modifier.size(30.dp)) }
            Column(modifier = Modifier.padding(horizontal = 14.dp).weight(1f)) { Text(item.name, color = Espresso, fontSize = 17.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(3.dp)); Text(item.detail, color = Muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            Text(item.price, color = Espresso, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

@Composable
private fun VisitScreen(padding: PaddingValues) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).testTag("visitScroll").padding(horizontal = 20.dp, vertical = 24.dp)) {
        Text("BİZE UĞRAYIN", color = EspressoSoft, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.7.sp)
        Spacer(Modifier.height(8.dp)); Text("Sıradaki kahveniz", color = Espresso, fontSize = 32.sp, fontWeight = FontWeight.Bold); Text("burada bekliyor.", color = Espresso, fontSize = 32.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.height(26.dp))
        Card(colors = CardDefaults.cardColors(containerColor = Espresso), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(22.dp)) {
                ContactLine(Icons.Outlined.LocationOn, "Adres", "Pazarcı, Uğur Mumcu Cd.\nGazipaşa / Antalya")
                Divider(color = Color.White.copy(alpha = .12f), modifier = Modifier.padding(vertical = 17.dp))
                ContactLine(Icons.Outlined.Home, "Çalışma saatleri", "09:00 — 00:00 · Her gün")
                Divider(color = Color.White.copy(alpha = .12f), modifier = Modifier.padding(vertical = 17.dp))
                ContactLine(Icons.Outlined.PhotoCamera, "Instagram", "@robyscoffeehouse")
            }
        }
        Spacer(Modifier.height(20.dp))
        Button(onClick = { openMaps(context) }, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp), colors = ButtonDefaults.buttonColors(containerColor = Caramel, contentColor = Espresso), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Outlined.Navigation, null); Spacer(Modifier.width(8.dp)); Text("Haritada aç", fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(10.dp))
        Button(onClick = { openInstagram(context) }, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp), colors = ButtonDefaults.buttonColors(containerColor = CreamDeep, contentColor = Espresso), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Outlined.PhotoCamera, null); Spacer(Modifier.width(8.dp)); Text("Instagram topluluğuna katıl", fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(26.dp))
        Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.Wifi, null, tint = Caramel); Spacer(Modifier.width(10.dp)); Text("Wi‑Fi · 220V · Klima · Çalışmaya uygun", color = Muted, fontSize = 13.sp) }
    }
}

@Composable
private fun ContactLine(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.Top) { Icon(icon, null, tint = Caramel, modifier = Modifier.size(22.dp)); Spacer(Modifier.width(14.dp)); Column { Text(label.uppercase(), color = CreamDeep, fontSize = 10.sp, letterSpacing = 1.3.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(4.dp)); Text(value, color = Color.White, fontSize = 16.sp, lineHeight = 22.sp) } }
}

internal const val DISCOVER_URL = "https://safal207.github.io/robys-coffee-house-demo/discover.html"

private fun openExternal(context: android.content.Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "Bu bağlantıyı açacak bir uygulama bulunamadı.", Toast.LENGTH_LONG).show()
    }
}

private fun openDiscover(context: android.content.Context) = openExternal(context, DISCOVER_URL)
private fun openMaps(context: android.content.Context) = openExternal(context, "https://www.google.com/maps/dir/?api=1&destination=Roby%27s+Coffee+House+Gazipasa&travelmode=driving")
private fun openInstagram(context: android.content.Context) = openExternal(context, "https://www.instagram.com/robyscoffeehouse/")
