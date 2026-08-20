# Drink

Osobní Android appka na sledování pitného režimu — velké „vodní“ kolečko, tlačítka na přidání,
7denní graf, a homescreen widget 1×2 s tlačítkem `+`. Není určená pro Play Store, jen sideload
na vlastní telefon. Komunikace s uživatelem probíhá česky.

## Build

Mimo Android Studio není `JAVA_HOME` nastavené a Gradle spadne hned na startu. Použij JBR z
Android Studia:

```bash
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew assembleDebug
```

```bash
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew installDebug
```

`installDebug` data v appce zachovává. adb je v `%LOCALAPPDATA%/Android/Sdk/platform-tools/`.

### Sideload z jiného PC než minule

Debug klíč si každý stroj generuje vlastní (`~/.android/debug.keystore`) a Android bere podpis jako
identitu appky, takže update ze druhého počítače spadne na `INSTALL_FAILED_UPDATE_INCOMPATIBLE`.
Obejít to nejde — buď zkopíruj `debug.keystore` z původního stroje (pak `installDebug` funguje
normálně a data zůstanou), nebo appku odinstaluj a data přenes ručně:

```bash
adb exec-out run-as com.oj.drink cat shared_prefs/water_prefs.xml > water_prefs.backup.xml
adb uninstall com.oj.drink
```

Po `installDebug` je potřeba appku **jednou spustit a zase zastavit**, aby vznikl adresář
`shared_prefs`, teprve pak zálohu vrátit:

```bash
adb push water_prefs.backup.xml /data/local/tmp/water_prefs.xml
adb shell run-as com.oj.drink cp /data/local/tmp/water_prefs.xml shared_prefs/water_prefs.xml
```

V Git Bashi je u obou adb příkazů nutné `MSYS_NO_PATHCONV=1`, jinak se `/data/local/tmp` přepíše
na windowsovou cestu.

Testy (`ExampleUnitTest`, `ExampleInstrumentedTest`) jsou jen scaffold z Android Studia — nic reálného
netestují.

## Stack

Compose (BOM 2024.09.00) + Material3, AGP 9.1.0, Kotlin 2.2.10, minSdk 26 / targetSdk 36,
`namespace = com.oj.drink`. Verze se přidávají do `gradle/libs.versions.toml`, ne přímo do
`build.gradle.kts`.

Widget **nepoužívá Glance** a záměrně u toho zůstává — je to klasický `AppWidgetProvider` +
`RemoteViews`. Kolečko se do widgetu kreslí jako `Bitmap` přes `android.graphics.Canvas` a nastavuje
přes `setImageViewBitmap`.

## Soubory

| Soubor | Obsah |
|---|---|
| `app/src/main/java/com/oj/drink/WaterRepository.kt` | Veškerá persistence. Jediný zdroj pravdy. |
| `app/src/main/java/com/oj/drink/MainActivity.kt` | Celá UI appky včetně `WaterChart` — jeden soubor. |
| `app/src/main/java/com/oj/drink/WaterWidget.kt` | Widget provider + vykreslení bitmapy. |
| `app/src/main/res/layout/widget_water.xml` | Layout widgetu (50/50 split kolečko / tlačítko). |
| `app/src/main/res/xml/water_widget_info.xml` | Rozměry widgetu — 1×2, `resizeMode="none"`. |

## Data

`SharedPreferences` (`water_prefs`), žádná databáze. Klíče:

- `water_ml` — dnešní součet
- `date` — `LocalDate.now().toEpochDay()` dne, ke kterému `water_ml` patří
- `history` — JSON pole `{day, ml, fake}`, seřazené podle dne a ořezané na posledních
  **400** dnů (`HISTORY_LIMIT`)
- `max_streak_floor` — jediný odvozený údaj, který se ukládá; viz níže

Denní cíl je konstanta `WaterRepository.DAILY_GOAL_ML = 2000`. Streak se **neukládá**, počítá se.

### Věci, které se dají snadno rozbít

- **Rollover dne je lazy.** Neběží žádný alarm ani WorkManager — `checkAndResetIfNeeded()` se volá
  na začátku každého čtení/zápisu a teprve tam se včerejšek odloží do historie. Každá nová veřejná
  funkce, která sahá na `water_ml`, ho musí zavolat taky.
- **Dny bez záznamu v historii nejsou.** Rollover ani `resetDay` nezapisují nulu — den prostě
  chybí. Klouzavý průměr proto počítá jen ze skutečně zaznamenaných dnů a nespadne, když se pár
  dní nezapisuje.
- **Max streak přežívá ořez historie přes podlahu.** `getMaxStreak()` se uvnitř okna počítá
  stejně jako `getStreak()`, ale dny, které z okna vypadnou, by rekord vzaly s sebou. `writeEntries`
  proto těsně před ořezem spočítá rekord z ještě needitovaného seznamu (ať série přes hranici zůstane
  celá), jen z `fake: false` dnů, a uloží ho do `max_streak_floor`. `getMaxStreak()` pak vrací
  maximum z obou. Podlaha jde dolů jedině přes `clearAllData`.
- **Streak je čistá funkce dat**, počítaná v `getStreak()` odzadu od dneška. Dnešek se započítá až
  po splnění cíle, do té doby se počítá série končící včerejškem. Nikde se neinkrementuje — zápis
  do minulého dne se do streaku promítne sám.
- **`fake` flag v historii.** Testovací data (dev menu) se zapisují s `fake: true`, aby je šlo
  smazat bez ztráty reálných dnů. `generateTestData` nikdy nepřepisuje den, který už má reálný
  záznam, a zpětně doplněné dny jsou vždycky `fake: false`.

## Známé chování (ne bugy)

- Widget zobrazuje `%.1fL`, appka `%.2fL` — proto widget u 0,75 L ukazuje 0.8L.
- Synchronizace jde jen jedním směrem: appka po zápisu pošle `ACTION_APPWIDGET_UPDATE` broadcast.
  Přidání z widgetu neaktualizuje běžící appku, dorovná se to až v `onResume()`.
- Vlny ve widgetu jsou statické (jen fázový posun mezi vrstvami). Animované jsou pouze v appce
  přes `rememberInfiniteTransition`.

## Zpětné doplňování dnů

Klik na svislý sloupec grafu (celý sloupec, ne jen popisek) přepne, do kterého dne se zapisuje.
Kolečko, tlačítka i reset pak míří na vybraný den; widget vždycky ukazuje dnešek. Vybraný den je
v grafu bílý a tučný + má podkladový pruh, a pod kolečkem je proužek `DayIndicator` s datem a ✕
pro návrat na dnešek. Výběr se resetuje na dnešek v `onResume()`, takže nemůže přežít odchod
z appky. Rozsah je posledních `VISIBLE_DAYS` (7) dnů.

## Dev menu

7× rychlé ťuknutí na text `/ 2.0L` pod hlavní hodnotou otevře dialog s generováním testovacích dat,
smazáním fake dat a smazáním všeho.
