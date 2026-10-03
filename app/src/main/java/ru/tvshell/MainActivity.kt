package ru.tvshell

import android.app.Activity
import android.app.AlertDialog
import android.app.SearchManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import java.util.Collections
import java.util.concurrent.Executors
import kotlin.math.max

/**
 * Главный экран по макету А «Новинка кино в превью»:
 * сверху — карусель новинок, окрашенная в цвет приложения-источника,
 * ниже — ряд избранных приложений, затем источники, все приложения, настройки.
 *
 * Все размеры задаются в пикселях макета 1280×720 и масштабируются под экран.
 */
class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private var s = 1f // масштаб макета

    // корневые
    private lateinit var scroll: ScrollView
    private lateinit var signature: TextView

    // превью
    private lateinit var hero: FrameLayout
    private lateinit var heroBg: GradientDrawable
    private lateinit var softCircle: View
    private lateinit var accentCircle: View
    private lateinit var poster: FrameLayout
    private lateinit var posterCircle: View
    private lateinit var posterTitle: TextView
    private lateinit var sourceMark: View
    private lateinit var sourceLabel: TextView
    private lateinit var ageBadge: TextView
    private lateinit var ratingText: TextView
    private lateinit var titleText: TextView
    private lateinit var metaText: TextView
    private lateinit var descText: TextView
    private lateinit var watchButton: TextView
    private lateinit var moreButton: TextView
    private lateinit var chips: FlowLayout
    private lateinit var dots: LinearLayout

    // ряды
    private lateinit var dockRow: LinearLayout
    private lateinit var inputsTitle: TextView
    private lateinit var inputsScroll: View
    private lateinit var inputsRow: LinearLayout
    private lateinit var appsTitle: TextView
    private lateinit var grid: GridLayout
    private lateinit var actionsRow: LinearLayout

    private var apps: List<AppEntry> = emptyList()
    private val releases = Catalog.demo
    private var current = 0

    private val main = Handler(Looper.getMainLooper())
    private val loader = Executors.newSingleThreadExecutor()
    private var loadToken = 0

    private val autoAdvance = object : Runnable {
        override fun run() {
            if (!heroHasFocus()) showRelease(current + 1)
            main.postDelayed(this, AUTO_ADVANCE_MS)
        }
    }

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = reload()
    }

    // ───────────────────────── жизненный цикл ─────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        s = resources.displayMetrics.widthPixels / 1280f

        setContentView(buildUi())
        buildActions()
        buildInputs()
        showRelease(0)
        watchButton.requestFocus()

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(packageReceiver, filter, RECEIVER_EXPORTED_FLAG)
        } else {
            registerReceiver(packageReceiver, filter)
        }
        reload()
    }

    override fun onResume() {
        super.onResume()
        main.removeCallbacks(autoAdvance)
        main.postDelayed(autoAdvance, AUTO_ADVANCE_MS)
    }

    override fun onPause() {
        main.removeCallbacks(autoAdvance)
        super.onPause()
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(packageReceiver)
        } catch (e: IllegalArgumentException) {
        }
        loader.shutdownNow()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        goHome()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP) goHome()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun goHome() {
        scroll.smoothScrollTo(0, 0)
        watchButton.requestFocus()
    }

    // ───────────────────────── построение экрана ─────────────────────────

    private fun buildUi(): View {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            setPadding(0, 0, 0, u(48))
        }

        // Верхняя часть — ровно один экран, как на макете
        val top = FrameLayout(this).apply {
            clipChildren = false
            clipToPadding = false
        }
        column.addView(top, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, u(720)))

        signature = text(13, 500, Color.parseColor("#6a7080")).apply {
            alpha = 0.75f
            letterSpacing = 0.04f
            text = prefs.signature
        }
        place(top, signature, 9, 8, WRAP, WRAP)

        buildHero(top)

        // Ряд-док: полупрозрачная подложка и плитки избранного
        val dockBand = View(this).apply { background = rounded(Color.argb(153, 255, 255, 255), 28) }
        place(top, dockBand, 2, 474, 1277, 155)
        val dockScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            clipChildren = false
            clipToPadding = false
        }
        dockRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            clipToPadding = false
            setPadding(u(54), u(16), u(64), 0)
        }
        dockScroll.addView(dockRow)
        place(top, dockScroll, 2, 474, 1277, 200)

        inputsTitle = sectionTitle("Источники")
        place(top, inputsTitle, 72, 676, WRAP, WRAP)

        // Ниже первого экрана — источники, все приложения, настройки
        val (inScroll, inRow) = horizontalRow()
        inputsScroll = inScroll
        inputsRow = inRow
        column.addView(inScroll)

        appsTitle = sectionTitle("Все приложения").apply { setPadding(u(72), u(28), 0, 0) }
        column.addView(appsTitle)
        val columns = max(3, (1280 - 128) / (168 + 28))
        grid = GridLayout(this).apply {
            columnCount = columns
            clipChildren = false
            clipToPadding = false
            setPadding(u(64 - 14), u(12), 0, 0)
        }
        column.addView(grid)

        column.addView(sectionTitle("Настройки").apply { setPadding(u(72), u(28), 0, 0) })
        val (actScroll, actRow) = horizontalRow()
        actionsRow = actRow
        column.addView(actScroll)

        scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            isFillViewport = true
            clipChildren = false
            clipToPadding = false
            setBackgroundColor(Color.parseColor("#e7e9ee"))
            addView(column)
        }
        return scroll
    }

    private fun buildHero(top: FrameLayout) {
        heroBg = GradientDrawable().apply { cornerRadius = u(28).toFloat() }
        hero = FrameLayout(this).apply {
            background = heroBg
            outlineProvider = ViewOutlineProvider.BACKGROUND
            clipToOutline = true
            clipChildren = true
        }
        place(top, hero, 0, 33, 1280, 427)

        softCircle = circle()
        place(hero, softCircle, 600, -140, 520, 520)
        accentCircle = circle()
        place(hero, accentCircle, 640, 220, 300, 300)

        // Постер (в тестовой сборке — абстрактная демо-обложка)
        poster = FrameLayout(this).apply {
            outlineProvider = ViewOutlineProvider.BACKGROUND
            clipToOutline = true
            elevation = u(14).toFloat()
        }
        place(hero, poster, 912, 40, 232, 348)
        posterCircle = circle()
        place(poster, posterCircle, -30, 150, 220, 220)
        val light = circle().apply { (background as GradientDrawable).setColor(Color.parseColor("#e7efe4")) }
        place(poster, light, 120, 40, 120, 120)
        posterTitle = text(22, 700, Color.WHITE).apply {
            maxLines = 3
            ellipsize = TextUtils.TruncateAt.END
        }
        place(poster, posterTitle, 16, 230, 200, WRAP)
        place(poster, text(11, 600, Color.argb(200, 255, 255, 255)).apply { text = "демо-обложка" }, 16, 320, WRAP, WRAP)

        // Карточка с описанием
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Color.argb(189, 255, 255, 255), 22)
            setPadding(u(32), u(30), u(32), u(30))
        }
        place(hero, card, 32, 40, 564, WRAP)

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        sourceMark = View(this)
        header.addView(sourceMark, LinearLayout.LayoutParams(u(10), u(10)).apply { rightMargin = u(10) })
        sourceLabel = text(13, 600, Color.parseColor("#5a6070")).apply { letterSpacing = 0.06f }
        header.addView(sourceLabel, LinearLayout.LayoutParams(WRAP_PX, WRAP_PX).apply { rightMargin = u(10) })
        ageBadge = text(12, 700, Color.WHITE).apply {
            background = rounded(Color.parseColor("#15171c"), 6)
            setPadding(u(7), u(2), u(7), u(2))
        }
        header.addView(ageBadge, LinearLayout.LayoutParams(WRAP_PX, WRAP_PX).apply { rightMargin = u(10) })
        ratingText = text(13, 600, Color.parseColor("#2f7a3a"))
        header.addView(ratingText)
        card.addView(header)

        titleText = text(44, 700, TileView.INK).apply {
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setLineSpacing(0f, 1.0f)
        }
        card.addView(titleText, gap(12))
        metaText = text(16, 400, Color.parseColor("#3d4250"))
        card.addView(metaText, gap(12))
        descText = text(15, 400, Color.parseColor("#3d4250")).apply {
            maxLines = 3
            ellipsize = TextUtils.TruncateAt.END
            setLineSpacing(0f, 1.3f)
        }
        card.addView(descText, gap(12))

        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        watchButton = heroButton("Смотреть")
        moreButton = heroButton("Подробнее")
        buttons.addView(watchButton, LinearLayout.LayoutParams(WRAP_PX, WRAP_PX).apply { rightMargin = u(12) })
        buttons.addView(moreButton)
        card.addView(buttons, gap(18))

        watchButton.setOnClickListener { watchCurrent() }
        moreButton.setOnClickListener { showDetails() }
        // ◀ на первой кнопке и ▶ на второй листают новинки
        watchButton.setOnKeyListener { _, code, ev ->
            if (code == KeyEvent.KEYCODE_DPAD_LEFT && ev.action == KeyEvent.ACTION_DOWN) {
                showRelease(current - 1); true
            } else false
        }
        moreButton.setOnKeyListener { _, code, ev ->
            if (code == KeyEvent.KEYCODE_DPAD_RIGHT && ev.action == KeyEvent.ACTION_DOWN) {
                showRelease(current + 1); true
            } else false
        }

        chips = FlowLayout(this, u(6), u(6))
        card.addView(chips, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, WRAP_PX).apply { topMargin = u(14) })

        // Часы и дата
        val clock = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            background = rounded(Color.argb(189, 255, 255, 255), 14)
            setPadding(u(16), u(8), u(16), u(8))
        }
        clock.addView(TextClock(this).apply {
            format24Hour = "EEE, d MMMM"
            format12Hour = "EEE, d MMMM"
            setTextSize(TypedValue.COMPLEX_UNIT_PX, u(14).toFloat())
            typeface = Fonts.get(this@MainActivity, 400)
            setTextColor(Color.parseColor("#3d4250"))
        }, LinearLayout.LayoutParams(WRAP_PX, WRAP_PX).apply { rightMargin = u(10); bottomMargin = u(2) })
        clock.addView(TextClock(this).apply {
            format24Hour = "HH:mm"
            format12Hour = "HH:mm"
            setTextSize(TypedValue.COMPLEX_UNIT_PX, u(22).toFloat())
            typeface = Fonts.get(this@MainActivity, 600)
            setTextColor(TileView.INK)
            includeFontPadding = false
        })
        hero.addView(clock, FrameLayout.LayoutParams(WRAP_PX, WRAP_PX, Gravity.TOP or Gravity.END).apply {
            topMargin = u(24); rightMargin = u(32)
        })

        // Точки карусели
        val dotsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        dots = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        dotsRow.addView(dots)
        dotsRow.addView(text(13, 400, Color.parseColor("#3d4250")).apply { text = "◀ ▶ другие новинки" },
            LinearLayout.LayoutParams(WRAP_PX, WRAP_PX).apply { leftMargin = u(8) })
        hero.addView(dotsRow, FrameLayout.LayoutParams(WRAP_PX, WRAP_PX, Gravity.BOTTOM or Gravity.START).apply {
            leftMargin = u(600); bottomMargin = u(22)
        })
    }

    private fun heroButton(label: String) = text(16, 600, TileView.INK).apply {
        text = label
        isFocusable = true
        isClickable = true
        gravity = Gravity.CENTER
        setPadding(u(24), u(12), u(24), u(12))
        styleButton(this, false)
        setOnFocusChangeListener { v, has -> styleButton(v as TextView, has) }
    }

    /** Выбранная кнопка — тёмная с белым и тёмным кольцом (как на макете), остальные — светлые. */
    private fun styleButton(b: TextView, focused: Boolean) {
        if (focused) {
            val ring = rounded(TileView.INK, 17)
            val gapRing = rounded(Color.WHITE, 15)
            val fill = rounded(TileView.INK, 12)
            b.background = LayerDrawable(arrayOf(ring, gapRing, fill)).apply {
                setLayerInset(1, u(2), u(2), u(2), u(2))
                setLayerInset(2, u(5), u(5), u(5), u(5))
            }
            b.setTextColor(Color.WHITE)
            b.setPadding(u(29), u(17), u(29), u(17))
        } else {
            b.background = LayerDrawable(arrayOf(rounded(Color.argb(20, 21, 23, 28), 12))).apply {
                setLayerInset(0, u(5), u(5), u(5), u(5))
            }
            b.setTextColor(TileView.INK)
            b.setPadding(u(29), u(17), u(29), u(17))
        }
    }

    // ───────────────────────── превью ─────────────────────────

    private fun heroHasFocus(): Boolean = watchButton.isFocused || moreButton.isFocused

    private fun showRelease(index: Int) {
        if (releases.isEmpty()) return
        current = ((index % releases.size) + releases.size) % releases.size
        val r = releases[current]
        val t = Themes.byName(r.source)

        heroBg.setColor(t.bg)
        (softCircle.background as GradientDrawable).setColor(t.soft)
        (accentCircle.background as GradientDrawable).setColor(t.accent)
        poster.background = rounded(t.deep, 18)
        (posterCircle.background as GradientDrawable).setColor(t.accent)
        posterTitle.text = r.title
        sourceMark.background = rounded(t.accent, 3)
        sourceLabel.text = "НОВИНКА В ${r.source.uppercase()}"
        ageBadge.text = r.age
        ratingText.text = "★ ${r.rating}"
        titleText.text = r.title
        metaText.text = r.meta
        descText.text = r.description
        watchButton.text = "Смотреть в ${r.source}"
        renderDots()
        renderChips()
    }

    private fun renderDots() {
        dots.removeAllViews()
        releases.indices.forEach { i ->
            val active = i == current
            dots.addView(View(this).apply {
                background = rounded(if (active) TileView.INK else Color.argb(77, 21, 23, 28), 3)
            }, LinearLayout.LayoutParams(u(if (active) 22 else 6), u(6)).apply { rightMargin = u(8) })
        }
    }

    /** Метки «Есть в ваших приложениях»: установленные кинотеатры, кроме источника и YouTube. */
    private fun renderChips() {
        chips.removeAllViews()
        if (releases.isEmpty()) return
        val source = releases[current].source
        val hidden = prefs.hidden
        val others = Themes.streamingApps(apps)
            .filter { it.packageName !in hidden && Themes.forLabel(it.label)?.name != source }
        if (others.isEmpty()) return

        chips.addView(text(13, 400, Color.parseColor("#6a7080")).apply { text = "Есть в ваших приложениях:" })
        others.take(MAX_CHIPS).forEach { app ->
            val color = Themes.forLabel(app.label)?.accent ?: Themes.fallback.accent
            val chip = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = rounded(Color.argb(18, 21, 23, 28), 999)
                setPadding(u(10), u(4), u(10), u(4))
            }
            chip.addView(View(this).apply { background = rounded(color, 2) },
                LinearLayout.LayoutParams(u(8), u(8)).apply { rightMargin = u(6) })
            chip.addView(text(13, 500, TileView.INK).apply { text = app.label; maxLines = 1 })
            chips.addView(chip)
        }
        val more = others.size - MAX_CHIPS
        if (more > 0) {
            chips.addView(text(13, 500, Color.parseColor("#3d4250")).apply {
                text = "ещё $more"
                background = rounded(Color.argb(18, 21, 23, 28), 999)
                setPadding(u(10), u(4), u(10), u(4))
            })
        }
    }

    private fun findAppFor(source: String): AppEntry? =
        apps.firstOrNull { Themes.forLabel(it.label)?.name == source && it.packageName !in prefs.hidden }

    private fun watchCurrent() {
        val r = releases.getOrNull(current) ?: return
        val app = findAppFor(r.source)
        if (app != null) {
            searchIn(app, r.title)
        } else {
            val alternatives = Themes.streamingApps(apps).filter { it.packageName !in prefs.hidden }
            if (alternatives.isEmpty()) {
                toast("${r.source} не установлен на этом телевизоре")
            } else {
                chooseAppAndSearch("${r.source} не установлен. Искать в:", r.title, alternatives)
            }
        }
    }

    private fun showDetails() {
        val r = releases.getOrNull(current) ?: return
        val targets = Themes.streamingApps(apps).filter { it.packageName !in prefs.hidden }
        val message = "${r.meta}\nВозраст: ${r.age} · Рейтинг: ${r.rating}\n\n${r.description}"
        val builder = AlertDialog.Builder(this, DIALOG_THEME)
            .setTitle(r.title)
            .setMessage(message)
            .setNegativeButton("Закрыть", null)
        if (targets.isNotEmpty()) {
            builder.setPositiveButton("Найти в приложении…") { _, _ ->
                chooseAppAndSearch("Искать «${r.title}» в:", r.title, targets)
            }
        }
        builder.show()
    }

    private fun chooseAppAndSearch(title: String, query: String, targets: List<AppEntry>) {
        AlertDialog.Builder(this, DIALOG_THEME)
            .setTitle(title)
            .setItems(targets.map { it.label }.toTypedArray()) { _, i -> searchIn(targets[i], query) }
            .setNegativeButton("Отмена", null)
            .show()
    }

    /** Пробуем открыть поиск фильма прямо в приложении; если не поддерживается — просто открываем его. */
    private fun searchIn(app: AppEntry, query: String) {
        val search = Intent(Intent.ACTION_SEARCH)
            .setPackage(app.packageName)
            .putExtra(SearchManager.QUERY, query)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (search.resolveActivity(packageManager) != null && tryStart(search)) return
        launch(app)
        toast("Найдите «$query» в поиске ${app.label}")
    }

    // ───────────────────────── данные и ряды ─────────────────────────

    private fun reload() {
        val token = ++loadToken
        try {
            loader.execute {
                val list = AppRepository.load(applicationContext)
                main.post {
                    if (token == loadToken && !isFinishing && !isDestroyed) {
                        apps = list
                        renderApps()
                        renderChips()
                    }
                }
            }
        } catch (e: java.util.concurrent.RejectedExecutionException) {
        }
    }

    private fun renderApps(focusTag: String? = null) {
        val previous = currentFocus?.tag as? String
        val hidden = prefs.hidden
        val byPkg = apps.associateBy { it.packageName }

        if (!prefs.favoritesInitialized) {
            // При первом запуске: установленные кинотеатры (без YouTube) + известные ТВ-приложения
            val streaming = Themes.streamingApps(apps).map { it.packageName }
            prefs.favorites = (DEFAULT_FAVORITES.filter { it in byPkg } + streaming).distinct().take(8)
        }

        dockRow.removeAllViews()
        visibleFavorites().mapNotNull { byPkg[it] }.forEach { app ->
            dockRow.addView(appTile(app, TAG_FAV, dock = true), tileParams())
        }
        dockRow.addView(TileView(this, s, 168, 100, "Все приложения", glyph = "▦", captionOnFocusOnly = true).apply {
            tag = "act:all"
            setOnClickListener { focusGrid() }
        }, tileParams())

        val visible = apps.filter { it.packageName !in hidden }
        grid.removeAllViews()
        visible.forEach { grid.addView(appTile(it, TAG_APP, dock = false), gridParams()) }
        appsTitle.text = "Все приложения · ${visible.size}"

        val wanted = focusTag ?: previous
        val target = wanted?.let { scroll.findViewWithTag<View>(it) }
        when {
            target != null -> target.requestFocus()
            currentFocus == null || currentFocus?.isAttachedToWindow != true -> watchButton.requestFocus()
        }
    }

    private fun focusGrid() {
        if (grid.childCount > 0) {
            grid.getChildAt(0).requestFocus()
        }
    }

    private fun visibleFavorites(): List<String> {
        val installed = apps.mapTo(HashSet()) { it.packageName }
        val hidden = prefs.hidden
        return prefs.favorites.filter { it in installed && it !in hidden }
    }

    private fun appTile(app: AppEntry, prefix: String, dock: Boolean): TileView {
        val theme = Themes.forLabel(app.label)
        return TileView(
            context = this,
            scale = s,
            widthDesign = 168,
            heightDesign = 100,
            title = app.label,
            banner = app.banner,
            icon = if (app.banner == null) app.icon else null,
            background = theme?.accent ?: Color.WHITE,
            captionOnFocusOnly = dock,
        ).apply {
            tag = prefix + app.packageName
            setOnClickListener { launch(app) }
            setOnLongClickListener { showAppMenu(app); true }
            setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_MENU) {
                    if (event.action == KeyEvent.ACTION_UP) showAppMenu(app)
                    true
                } else false
            }
        }
    }

    private fun glyphTile(glyph: String, title: String, onClick: () -> Unit) =
        TileView(this, s, 168, 100, title, glyph = glyph).apply {
            tag = "act:$title"
            setOnClickListener { onClick() }
        }

    private fun buildInputs() {
        inputsRow.removeAllViews()
        val tim = getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager
        val inputs = try {
            tim?.tvInputList.orEmpty().filter { !it.isHidden(this) && it.parentId == null }
        } catch (e: RuntimeException) {
            emptyList()
        }
        for (input in inputs) {
            val label = (input.loadCustomLabel(this) ?: input.loadLabel(this))?.toString() ?: input.id
            val glyph = when (input.type) {
                TvInputInfo.TYPE_TUNER -> "TV"
                TvInputInfo.TYPE_HDMI -> "HDMI"
                else -> "AV"
            }
            inputsRow.addView(glyphTile(glyph, label) { openInput(input) }, tileParams())
        }
        val visibility = if (inputs.isEmpty()) View.GONE else View.VISIBLE
        inputsTitle.visibility = visibility
        inputsScroll.visibility = visibility
    }

    private fun openInput(input: TvInputInfo) {
        val uri = if (input.isPassthroughInput) {
            TvContract.buildChannelUriForPassthroughInput(input.id)
        } else {
            TvContract.buildChannelsUriForInput(input.id)
        }
        if (tryStart(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) return
        packageManager.getLaunchIntentForPackage(PHILIPS_TV_APP)?.let { if (tryStart(it)) return }
        toast("Не удалось открыть источник «${input.loadLabel(this)}»")
    }

    private fun buildActions() {
        actionsRow.removeAllViews()
        val actions = listOf<Triple<String, String, () -> Unit>>(
            Triple("✎", "Подпись") { editSignature() },
            Triple("⚙", "Настройки ТВ") { openSettings(Settings.ACTION_SETTINGS) },
            Triple("Wi‑Fi", "Сеть") { openSettings(Settings.ACTION_WIFI_SETTINGS) },
            Triple("BT", "Bluetooth") { openSettings(Settings.ACTION_BLUETOOTH_SETTINGS) },
            Triple("⌂", "Кнопка «Домой»") { showHomeKeyHelp() },
            Triple("◌", "Скрытые") { showHiddenApps() },
        )
        for ((glyph, title, action) in actions) {
            actionsRow.addView(glyphTile(glyph, title, action), tileParams())
        }
    }

    // ───────────────────────── подпись ─────────────────────────

    private fun editSignature() {
        val input = EditText(this).apply {
            setText(prefs.signature)
            setSelection(text.length)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            maxLines = 1
        }
        val box = FrameLayout(this).apply {
            setPadding(u(24), u(8), u(24), 0)
            addView(input)
        }
        AlertDialog.Builder(this, DIALOG_THEME)
            .setTitle("Подпись на главном экране")
            .setView(box)
            .setPositiveButton("Сохранить") { _, _ ->
                prefs.signature = input.text.toString()
                signature.text = prefs.signature
            }
            .setNeutralButton("По умолчанию") { _, _ ->
                prefs.signature = Prefs.DEFAULT_SIGNATURE
                signature.text = prefs.signature
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    // ───────────────────────── меню приложения ─────────────────────────

    private fun showAppMenu(app: AppEntry) {
        val pkg = app.packageName
        val favs = visibleFavorites()
        val isFav = pkg in favs
        val items = mutableListOf<Pair<String, () -> Unit>>()

        items += "Открыть" to { launch(app) }
        if (isFav) {
            val index = favs.indexOf(pkg)
            if (index > 0) items += "← Сдвинуть влево" to { moveFavorite(pkg, -1) }
            if (index < favs.lastIndex) items += "Сдвинуть вправо →" to { moveFavorite(pkg, +1) }
            items += "Убрать из верхнего ряда" to { setFavorite(pkg, false) }
        } else {
            items += "Добавить в верхний ряд" to { setFavorite(pkg, true) }
        }
        items += "Скрыть" to { hideApp(app) }
        items += "О приложении" to {
            openSettings(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
        }
        items += "Удалить" to {
            tryStart(Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }

        AlertDialog.Builder(this, DIALOG_THEME)
            .setTitle(app.label)
            .setItems(items.map { it.first }.toTypedArray()) { _, which -> items[which].second() }
            .show()
    }

    private fun setFavorite(pkg: String, add: Boolean) {
        val list = prefs.favorites.toMutableList()
        list.remove(pkg)
        if (add) list.add(pkg)
        prefs.favorites = list
        renderApps(focusTag = if (add) TAG_FAV + pkg else TAG_APP + pkg)
    }

    private fun moveFavorite(pkg: String, delta: Int) {
        val visible = visibleFavorites().toMutableList()
        val from = visible.indexOf(pkg)
        val to = from + delta
        if (from < 0 || to !in visible.indices) return
        Collections.swap(visible, from, to)
        prefs.favorites = visible + prefs.favorites.filter { it !in visible }
        renderApps(focusTag = TAG_FAV + pkg)
    }

    private fun hideApp(app: AppEntry) {
        prefs.hidden = prefs.hidden + app.packageName
        renderApps()
        renderChips()
        toast("«${app.label}» скрыто. Вернуть: Настройки → Скрытые")
    }

    private fun showHiddenApps() {
        val hidden = prefs.hidden.toList()
        if (hidden.isEmpty()) {
            toast("Скрытых приложений нет")
            return
        }
        val labels = hidden.map { pkg -> apps.firstOrNull { it.packageName == pkg }?.label ?: pkg }
        val checked = BooleanArray(hidden.size)
        AlertDialog.Builder(this, DIALOG_THEME)
            .setTitle("Отметьте, что вернуть")
            .setMultiChoiceItems(labels.toTypedArray(), checked) { _, i, value -> checked[i] = value }
            .setPositiveButton("Вернуть") { _, _ ->
                prefs.hidden = hidden.filterIndexed { i, _ -> !checked[i] }.toSet()
                renderApps()
                renderChips()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showHomeKeyHelp() {
        val message = if (isHomeServiceEnabled()) {
            "Перехват включён: кнопка «Домой» и включение телевизора открывают TV Shell.\n\n" +
                "Чтобы отключить, выключите службу «TV Shell — кнопка «Домой»» в специальных возможностях."
        } else {
            "Android TV по кнопке «Домой» открывает свой экран.\n\n" +
                "Чтобы вместо него открывалась эта оболочка, включите службу " +
                "«TV Shell — кнопка «Домой»» в разделе «Специальные возможности».\n\n" +
                "Если службы там нет — см. README, способ через ADB."
        }
        AlertDialog.Builder(this, DIALOG_THEME)
            .setTitle("Кнопка «Домой»")
            .setMessage(message)
            .setPositiveButton("Открыть спец. возможности") { _, _ ->
                openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            }
            .setNegativeButton("Закрыть", null)
            .show()
    }

    private fun isHomeServiceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        val me = ComponentName(this, HomeKeyService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    // ───────────────────────── запуск ─────────────────────────

    private fun launch(app: AppEntry) {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(if (app.isTv) Intent.CATEGORY_LEANBACK_LAUNCHER else Intent.CATEGORY_LAUNCHER)
            .setComponent(app.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        if (!tryStart(intent)) toast("Не удалось открыть «${app.label}»")
    }

    private fun openSettings(action: String, data: Uri? = null) {
        val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (data != null) intent.data = data
        if (!tryStart(intent) && action != Settings.ACTION_SETTINGS) {
            if (!tryStart(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) {
                toast("Настройки недоступны")
            }
        }
    }

    private fun tryStart(intent: Intent): Boolean = try {
        startActivity(intent)
        true
    } catch (e: RuntimeException) {
        false
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()

    // ───────────────────────── помощники разметки ─────────────────────────

    /** Пиксели макета → пиксели экрана. */
    private fun u(v: Int): Int = Math.round(v * s)

    private fun text(size: Int, weight: Int, color: Int) = TextView(this).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_PX, u(size).toFloat())
        typeface = Fonts.get(this@MainActivity, weight)
        setTextColor(color)
        includeFontPadding = false
    }

    private fun sectionTitle(label: String) = text(18, 600, Color.parseColor("#6a7080")).apply { text = label }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        cornerRadius = u(radius).toFloat()
        setColor(color)
    }

    private fun circle() = View(this).apply {
        background = GradientDrawable().apply { shape = GradientDrawable.OVAL }
    }

    /** Абсолютное размещение по координатам макета. [WRAP] — по содержимому. */
    private fun place(parent: FrameLayout, view: View, x: Int, y: Int, w: Int, h: Int) {
        val lp = FrameLayout.LayoutParams(
            if (w == WRAP) WRAP_PX else u(w),
            if (h == WRAP) WRAP_PX else u(h),
        )
        lp.leftMargin = u(x)
        lp.topMargin = u(y)
        if (x < 0 || y < 0) {
            // отрицательные отступы FrameLayout не поддерживает — сдвигаем через translation
            lp.leftMargin = 0
            lp.topMargin = 0
            view.translationX = u(x).toFloat()
            view.translationY = u(y).toFloat()
        }
        parent.addView(view, lp)
    }

    private fun gap(top: Int) = LinearLayout.LayoutParams(WRAP_PX, WRAP_PX).apply { topMargin = u(top) }

    private fun horizontalRow(): Pair<HorizontalScrollView, LinearLayout> {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            clipToPadding = false
            setPadding(u(64 - 14), u(14), u(64), u(6))
        }
        val hsv = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            clipChildren = false
            clipToPadding = false
            addView(row)
        }
        return hsv to row
    }

    private fun tileParams() = LinearLayout.LayoutParams(WRAP_PX, WRAP_PX).apply { rightMargin = u(28) - u(20) }

    private fun gridParams() = GridLayout.LayoutParams().apply { setMargins(u(4), u(10), u(4), u(10)) }

    private companion object {
        const val WRAP = -1
        const val WRAP_PX = ViewGroup.LayoutParams.WRAP_CONTENT
        const val TAG_FAV = "fav:"
        const val TAG_APP = "app:"
        const val MAX_CHIPS = 5
        const val AUTO_ADVANCE_MS = 12_000L
        const val PHILIPS_TV_APP = "org.droidtv.playtv"
        const val RECEIVER_EXPORTED_FLAG = 0x2 // Context.RECEIVER_EXPORTED (API 33)
        val DIALOG_THEME = android.R.style.Theme_DeviceDefault_Light_Dialog_Alert

        val DEFAULT_FAVORITES = listOf(
            PHILIPS_TV_APP,
            "com.android.vending",
        )
    }
}
