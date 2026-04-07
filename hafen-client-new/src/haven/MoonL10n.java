package haven;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class MoonL10n {
    public static final String LANG_EN = "en";
    public static final String LANG_RU = "ru";

    private static String currentLang = Utils.getpref("moon-lang", LANG_RU);
    private static final Map<String, Map<String, String>> db = new HashMap<>();

    static {
        Map<String, String> en = new HashMap<>();
        Map<String, String> ru = new HashMap<>();

        en.put("moonwide.func", "MooNWide Functions");
        ru.put("moonwide.func", "Функции MooNWide");

        en.put("cat.combat", "Combat");        ru.put("cat.combat", "Бой");
        en.put("cat.visual", "Visual");         ru.put("cat.visual", "Визуальное");
        en.put("cat.automation", "Automation");  ru.put("cat.automation", "Автоматизация");
        en.put("cat.info", "Information");       ru.put("cat.info", "Информация");

        en.put("trace.hostile", "Trace: hostile mobs & players");
        ru.put("trace.hostile", "Трейс: враждебные мобы и игроки");
        en.put("trace.neutral", "Trace: neutral mobs");
        ru.put("trace.neutral", "Трейс: обычные мобы");
        en.put("trace.players", "Trace: players");
        ru.put("trace.players", "Трейс: игроки");
        en.put("trace.all", "Trace: all");
        ru.put("trace.all", "Трейс: все");
        en.put("trace.move.hint", "Ground path line (Combat map guides): uses LinMove while moving. Chase lines use Homing. Tree stand spot: Shift+Alt+LMB.");
        ru.put("trace.move.hint", "Линия к последнему клику по земле — в настройках боя (Homing/LinMove), только клиент. Точка рубки: Shift+Alt+ЛКМ.");

        en.put("esp.hostile", "Hostile mobs");        ru.put("esp.hostile", "Враждебные мобы");
        en.put("esp.player", "Players");              ru.put("esp.player", "Игроки");
        en.put("esp.neutral", "Neutral mobs");        ru.put("esp.neutral", "Обычные мобы");
        en.put("esp.vehicles", "Ships & vehicles");   ru.put("esp.vehicles", "Корабли и транспорт");
        en.put("esp.buildings", "Buildings");          ru.put("esp.buildings", "Здания");
        en.put("esp.resources", "Resources & stockpiles"); ru.put("esp.resources", "Ресурсы и стокпайлы");
        en.put("esp.containers", "Containers");       ru.put("esp.containers", "Контейнеры");
        en.put("esp.herbs", "Herbs & forage");        ru.put("esp.herbs", "Травы и собирательство");
        en.put("esp.dungeons", "Dungeons & POI");     ru.put("esp.dungeons", "Данжи и POI");
        en.put("esp.workstations", "Workstations");   ru.put("esp.workstations", "Верстаки и печи");
        en.put("esp.items", "World items (ground)");  ru.put("esp.items", "Предметы на земле");
        en.put("esp.item.font", "Item label size (px)"); ru.put("esp.item.font", "Размер имени (px)");
        en.put("esp.item.hitbox", "Hitbox half-width (world)"); ru.put("esp.item.hitbox", "Полуширина хитбокса");

        en.put("craft.softcap", "Softcap ≈ %.1f");     ru.put("craft.softcap", "Софткап ≈ %.1f");
        en.put("craft.skillline", "%s: %d");           ru.put("craft.skillline", "%s: %d");
        en.put("craft.hardcap", "Hardcap ~ %.0f");     ru.put("craft.hardcap", "Хардкап ~ %.0f");
        en.put("craft.expected", "Expected quality ≈ %.1f"); ru.put("craft.expected", "Примерное качество ≈ %.1f");
        en.put("craft.search", "Search recipes..."); ru.put("craft.search", "Поиск рецептов...");
        en.put("craft.recipes", "Recipes"); ru.put("craft.recipes", "Рецепты");
        en.put("craft.materials", "Materials"); ru.put("craft.materials", "Материалы");
        en.put("craft.results", "Results"); ru.put("craft.results", "Результат");
        en.put("craft.requirements", "Requirements"); ru.put("craft.requirements", "Требования");
        en.put("craft.reference", "Reference"); ru.put("craft.reference", "Справка");
        en.put("craft.tools_ref", "Tools"); ru.put("craft.tools_ref", "Инструменты");
        en.put("craft.station_ref", "Stations"); ru.put("craft.station_ref", "Станки");
        en.put("craft.prereq", "Required skill: %s"); ru.put("craft.prereq", "Требуемый навык: %s");
        en.put("craft.no_station", "No station requirement"); ru.put("craft.no_station", "Станок не требуется");
        en.put("craft.no_tool", "No extra tool requirement"); ru.put("craft.no_tool", "Доп. инструмент не требуется");
        en.put("craft.no_recipe", "Select a recipe from the list."); ru.put("craft.no_recipe", "Выберите рецепт из списка.");
        en.put("craft.unknown_hardcap", "Hardcap: unavailable"); ru.put("craft.unknown_hardcap", "Хардкап: недоступен");
        en.put("craft.optional", "(optional)"); ru.put("craft.optional", "(опционально)");
        en.put("craft.quality_unknown", "Q?"); ru.put("craft.quality_unknown", "Q?");
        en.put("craft.description", "Description"); ru.put("craft.description", "Описание");
        en.put("craft.characteristics", "Characteristics"); ru.put("craft.characteristics", "Характеристики");
        en.put("craft.no_desc", "No description yet."); ru.put("craft.no_desc", "Описание пока отсутствует.");
        en.put("craft.make_count", "Create amount"); ru.put("craft.make_count", "Создать количество");
        en.put("craft.make_max", "Maximum"); ru.put("craft.make_max", "Максимум");
        en.put("craft.max_avail", "Visible maximum ≈ %d"); ru.put("craft.max_avail", "Видимый максимум ≈ %d");
        en.put("craft.unlock_skill", "Unlock skill: %s"); ru.put("craft.unlock_skill", "Навык открытия: %s");
        en.put("craft.section_available", "Available recipes"); ru.put("craft.section_available", "Доступные рецепты");
        en.put("craft.section_wiki", "Wiki reference"); ru.put("craft.section_wiki", "Справочник wiki");
        en.put("craft.category.general", "General"); ru.put("craft.category.general", "Общее");
        en.put("craft.category.other", "Other"); ru.put("craft.category.other", "Прочее");

        en.put("esp.opt.dist", "dist");   ru.put("esp.opt.dist", "расст");
        en.put("esp.opt.name", "name");   ru.put("esp.opt.name", "имя");
        en.put("esp.opt.speed", "speed"); ru.put("esp.opt.speed", "скор");
        en.put("esp.opt.color.tip", "Click to change color"); ru.put("esp.opt.color.tip", "Клик для смены цвета");

        en.put("xray.enable", "X-Ray (extended visibility)");
        ru.put("xray.enable", "X-Ray (расширенная видимость)");
        en.put("xray.color.tip", "Click to change X-Ray overlay color");
        ru.put("xray.color.tip", "Клик — сменить цвет подсветки X-Ray");
        en.put("xray.style.tiles", "X-Ray: flat ground tiles (stump footprint)");
        ru.put("xray.style.tiles", "X-Ray: плоский след на земле (ромб у пенька)");
        en.put("xray.style.tiles.tip", "On: red diamond on the ground at the hitbox (like the reference). Off: old mode — screen projection of a tall 3D box (looks like a vertical rectangle that follows the view).");
        ru.put("xray.style.tiles.tip", "Вкл: красный ромб на плоскости земли по хитбоксу (как на референсе). Выкл: старый режим — экранная проекция высокого 3D-бокса (вертикальный прямоугольник, «крутится» с камерой).");
        en.put("xray.hide.rocks", "X-Ray hide mode: trees + rocks");
        ru.put("xray.hide.rocks", "X-Ray режим скрытия: деревья + камни");
        en.put("xray.hide.rocks.tip", "Off: hide only trees/bushes/trellis. On: also hide rocks/bumlings.");
        ru.put("xray.hide.rocks.tip", "Выкл: скрывать только деревья/кусты/шпалеры. Вкл: дополнительно скрывать камни/булыжники.");
        en.put("xray.mode.trees.only", "X-Ray mode: hide trees only");
        ru.put("xray.mode.trees.only", "X-Ray режим: скрывать только деревья");
        en.put("xray.mode.trees.rocks", "X-Ray mode: hide trees and rocks");
        ru.put("xray.mode.trees.rocks", "X-Ray режим: скрывать деревья и камни");
        en.put("fight.overlay.fixed", "Combat overlay: fixed screen position (not tied to character)");
        ru.put("fight.overlay.fixed", "Боевой оверлей: фиксированная позиция на экране (не за персонажем)");
        en.put("fight.overlay.hint", "Alt+drag a section to move it. Alt+mouse wheel over a section changes its size. Each section is saved separately.");
        ru.put("fight.overlay.hint", "Alt+перетаскивание по секции — перенос. Alt+колесо над секцией меняет размер. Каждая секция сохраняется отдельно.");
        en.put("flat.terrain", "Flat terrain (visual)");
        ru.put("flat.terrain", "Плоская поверхность (визуально)");
        en.put("flat.terrain.relief", "Terrain relief (when flat)");
        ru.put("flat.terrain.relief", "Рельеф при плоском режиме");
        en.put("flat.terrain.relief.note", "0 = fully flat, 1 = full height. Use ~0.4–0.7 to keep hills visible.");
        ru.put("flat.terrain.relief.note", "0 — совсем плоско, 1 — полная высота. ~0.4–0.7 — чтобы горы и холмы были видны.");
        en.put("daylight.always", "Always day (local lighting & calendar)");
        ru.put("daylight.always", "Всегда день (локально: свет и календарь)");
        en.put("daylight.note", "Server time unchanged; only your view.");
        ru.put("daylight.note", "Игровое время на сервере не меняется, только отображение.");
        en.put("moon.nightvision.note", "Blends your lighting toward white for darker periods. Server time and mechanics are unchanged.");
        ru.put("moon.nightvision.note", "Осветляет картинку смешиванием к белому в тёмное время. Время и логика на сервере не меняются.");

        en.put("aggro.radius", "Aggro radius (3 zones)");
        ru.put("aggro.radius", "Радиус агра (3 зоны)");

        en.put("safe.mode", "Safe mode: retreat from hostile mobs");
        ru.put("safe.mode", "Безопасный режим: отход от враждебных мобов");
        en.put("safe.mode.busyonly", "…only during craft/mine/gather (progress bar)");
        ru.put("safe.mode.busyonly", "…только во время действия (полоска прогресса)");
        en.put("safe.mode.nofight", "…pause while in combat (fight target)");
        ru.put("safe.mode.nofight", "…не мешать в бою (есть цель в бою)");
        en.put("safe.mode.mintiles", "Min distance (tiles)");
        ru.put("safe.mode.mintiles", "Мин. дистанция (тайлы)");
        en.put("activity.hud", "HUD above player: progress & target HP");
        ru.put("activity.hud", "Над персонажем: прогресс и прочность цели");
        en.put("activity.hud.range", "Structure search (tiles)");
        ru.put("activity.hud.range", "Поиск цели (тайлы)");
        en.put("activity.hud.action", "Progress: %.0f%%");
        ru.put("activity.hud.action", "Прогресс: %.0f%%");
        en.put("activity.hud.eta", "~%.0fs left");
        ru.put("activity.hud.eta", "~%.0f с");
        en.put("activity.hud.structure", "Target: %.0f%%");
        ru.put("activity.hud.structure", "Цель: %.0f%% прочности");

        en.put("quality.objects", "Show item quality overlay");
        ru.put("quality.objects", "Показывать качество предметов");
        en.put("opt.invQuality", "Inventory quality…");
        ru.put("opt.invQuality", "Качество в инвентаре…");
        en.put("moon.inv.qual.title", "Inventory quality display");
        ru.put("moon.inv.qual.title", "Отображение качества в инвентаре");
        en.put("moon.inv.qual.note", "Thresholds are checked from highest to lowest (first match wins).");
        ru.put("moon.inv.qual.note", "Пороги цвета проверяются сверху вниз (первое совпадение).");
        en.put("moon.inv.qual.rounded", "Rounded quality number");
        ru.put("moon.inv.qual.rounded", "Округлять число качества");
        en.put("moon.inv.qual.customcolors", "Custom colors by tier");
        ru.put("moon.inv.qual.customcolors", "Свои цвета по ярусам");
        en.put("moon.inv.qual.corner", "Quality label position (0=top-right … 4=center)");
        ru.put("moon.inv.qual.corner", "Позиция числа качества (0=справа сверху … 4=центр)");
        en.put("moon.inv.qual.fontsize", "Quality text size (px, scaled)");
        ru.put("moon.inv.qual.fontsize", "Размер текста качества (px, с масштабом UI)");
        en.put("moon.inv.qual.r", "R");
        ru.put("moon.inv.qual.r", "R");
        en.put("moon.inv.qual.g", "G");
        ru.put("moon.inv.qual.g", "G");
        en.put("moon.inv.qual.b", "B");
        ru.put("moon.inv.qual.b", "B");
        en.put("moon.inv.qual.a", "A");
        ru.put("moon.inv.qual.a", "A");
        en.put("moon.inv.qual.reset", "Reset");
        ru.put("moon.inv.qual.reset", "Сброс");
        en.put("moon.inv.qual.import.legacy", "Import quality settings from shared prefs");
        ru.put("moon.inv.qual.import.legacy", "Импорт настроек качества из общих префов");
        en.put("moon.inv.qual.import.ok", "Quality settings imported from shared preferences.");
        ru.put("moon.inv.qual.import.ok", "Настройки качества импортированы из общих префов.");
        en.put("moon.inv.qual.import.none", "No matching quality keys found in preferences.");
        ru.put("moon.inv.qual.import.none", "В настройках нет подходящих ключей качества.");
        en.put("moon.inv.qual.tier.0", "Godlike (threshold)");
        ru.put("moon.inv.qual.tier.0", "Божественное (порог)");
        en.put("moon.inv.qual.tier.1", "Legendary (threshold)");
        ru.put("moon.inv.qual.tier.1", "Легендарное (порог)");
        en.put("moon.inv.qual.tier.2", "Epic (threshold)");
        ru.put("moon.inv.qual.tier.2", "Эпическое (порог)");
        en.put("moon.inv.qual.tier.3", "Rare (threshold)");
        ru.put("moon.inv.qual.tier.3", "Редкое (порог)");
        en.put("moon.inv.qual.tier.4", "Uncommon (threshold)");
        ru.put("moon.inv.qual.tier.4", "Необычное (порог)");
        en.put("moon.inv.qual.tier.5", "Common (threshold)");
        ru.put("moon.inv.qual.tier.5", "Обычное (порог)");
        en.put("moon.inv.qual.tier.6", "Junk (threshold)");
        ru.put("moon.inv.qual.tier.6", "Хлам (порог)");

        en.put("autodrink.enable", "Auto-drink");
        ru.put("autodrink.enable", "Автопитьё");
        en.put("autodrink.maintainfull", "Maintain full stamina (real drink actions)");
        ru.put("autodrink.maintainfull", "Держать полную стамину (реальные действия питья)");
        en.put("autodrink.maintainfull.note", "Sends the same iact / flower flow as a manual drink; server must accept. Target is 100% — threshold slider ignored. Energy is not restored by water.");
        ru.put("autodrink.maintainfull.note", "Те же iact/flower, что при ручном питье; сервер валидирует. Цель 100% — порог выше не используется. Энергию вода не поднимает.");
        en.put("autodrink.threshold", "Stamina threshold %");
        ru.put("autodrink.threshold", "Порог стамины %");
        en.put("autodrink.interval", "Auto-drink interval (sec)");
        ru.put("autodrink.interval", "Интервал автопитья (сек)");
        en.put("autodrink.liquid", "Preferred liquid");
        ru.put("autodrink.liquid", "Предпочитаемая жидкость");
        en.put("autodrink.whatever", "Drink any liquid from container");
        ru.put("autodrink.whatever", "Пить любую жидкость из ёмкости");
        en.put("autodrink.sipmode", "Use Sip mode");
        ru.put("autodrink.sipmode", "Использовать режим Sip");
        en.put("autodrink.smartsip", "Smart sip mode (water=Drink, else=Sip)");
        ru.put("autodrink.smartsip", "Умный Sip (вода=Drink, остальное=Sip)");
        en.put("autodrink.siponce", "Sip only once");
        ru.put("autodrink.siponce", "Делать Sip один раз");
        en.put("autodrink.sipthreshold", "Sip target stamina %");
        ru.put("autodrink.sipthreshold", "Целевая стамина для Sip %");
        en.put("autodrink.msg", "Show autodrink status messages");
        ru.put("autodrink.msg", "Показывать сообщения автопитья");
        en.put("autodrink.directsip", "Direct Sip mode (experimental)");
        ru.put("autodrink.directsip", "Direct Sip режим (экспериментально)");
        en.put("autodrink.directsip.int", "Direct Sip interval (ms)");
        ru.put("autodrink.directsip.int", "Интервал Direct Sip (мс)");
        en.put("autodrink.serverhook", "Server stamina hook (experimental, low latency)");
        ru.put("autodrink.serverhook", "Хук стамины с сервера (эксперимент, меньше задержка)");
        en.put("autodrink.serverhook.note", "Runs autodrink when the stamina meter widget receives server set (same UI tick as the packet). Uses Sip; spacing uses Direct Sip interval when hook or Direct Sip is on.");
        ru.put("autodrink.serverhook.note", "Автопитьё при входящем set метра стамины (тот же тик UI, что и пакет). Sip; интервал — как у Direct Sip, если включён хук или Direct Sip.");

        en.put("combatbot.enable", "Combat bot");
        ru.put("combatbot.enable", "Автобой");
        en.put("combatbot.desc", "IP + cooldowns + move hints + server slot hints");
        ru.put("combatbot.desc", "IP, кулдауны, эвристики по картам, подсказки слотов");

        en.put("speed.boost", "Speed boost (sprint cycling)");
        ru.put("speed.boost", "Ускорение (спринт-цикл)");
        en.put("speed.note", "Requires sprint (max 3). Auto-enables drink. While on, sprint/lift assist on the wire is off; outgoing Speedget mult (>1) still substitutes packets.");
        ru.put("speed.note", "Нужен спринт (макс. 3). Автопитьё. При включении откл. assist спринт/lift на wire; подмена mult Speedget (>1) в исходящих пакетах остаётся.");
        en.put("speed.clientscale", "Movement assist (outgoing Speedget to server)");
        ru.put("speed.clientscale", "Помощь движению (Speedget на сервер)");
        en.put("speed.clientscale.note", "Required for \"force sprint to server\". The +N tiers slider can work without this. World position still follows server s+v*t.");
        ru.put("speed.clientscale.note", "Нужна для «форс спринт на сервер». Ползунок +N ступеней может работать и без этого. Позиция в мире по-прежнему s+v*t с сервера.");
        en.put("speed.serversprint", "Also send sprint to server (Speedget)");
        ru.put("speed.serversprint", "Отправлять спринт на сервер (Speedget)");
        en.put("speed.serversprint.note", "Requires movement assist: upgrades outgoing speed toward max when not slowing down. Lower tiers (walk/crawl) work when you click down. Off during speed boost. Skips the +N slider.");
        ru.put("speed.serversprint.note", "Нужна «помощь движению»: поднимает скорость на сервер к макс., если ты не снижаешь шаг. Ползунок вниз (ходьба/ползок) уходит как есть. Выкл. при «Ускорение». Слайдер +N не используется.");
        en.put("speed.serverlift", "Server speed +N tiers (all gaits)");
        ru.put("speed.serverlift", "+N ступеней скорости на сервер (все режимы)");
        en.put("speed.serverlift.val", "+%d on wire");
        ru.put("speed.serverlift.val", "+%d на сервер");
        en.put("speed.serverlift.note", "Adds N to speed index sent to server (clamped to max). Ignored while \"force sprint\" is on. Works without movement assist. Off during speed boost.");
        ru.put("speed.serverlift.note", "Добавляет N к индексу скорости на сервер (до макс.). Не применяется, если включён «форс спринт». Работает без «помощи движению». Выкл. при спринт-бусте.");
        en.put("speed.debugwire", "Log speed wire (stderr: [MoonSpeedWire])");
        ru.put("speed.debugwire", "Лог скорости в консоль ([MoonSpeedWire])");
        en.put("speed.debugwire.note", "OUT/SEND = client→server Speedget index; IN cur/max = server. Hook runs before SEND (substituted args).");
        ru.put("speed.debugwire.note", "OUT/SEND — индекс Speedget; IN — ответ сервера. Хук до SEND (подменённые args).");
        en.put("speed.wiremult", "Wire: Speedget tier bump (discrete index → server)");
        ru.put("speed.wiremult", "Wire: ступень Speedget (дискретный индекс на сервер)");
        en.put("speed.wiremult.val", "%.2f x");
        ru.put("speed.wiremult.val", "%.2f x");
        en.put("speed.wiremult.note", "Minimum 1.0× = vanilla on the wire. Above that rewrites Speedget \"set\" in UI.wdgmsg → server: adds floor((mult-1)/0.55) to the sent index (clamped to max). Every click/hotkey and the periodic resend below use the same hook. No extra index when already at widget max.");
        ru.put("speed.wiremult.note", "Минимум 1.0× = ваниль на wire. Выше — подмена исходящего Speedget «set»: +floor((mult-1)/0.55) к индексу (до max). Каждый клик/хоткей и периодическая отправка ниже проходят через тот же хук. На макс. ступени виджета эффекта нет.");
        en.put("speed.visualmult", "Display: move-line speed (client only, any mode)");
        ru.put("speed.visualmult", "Отображение: скорость по линии (только клиент, любой режим)");
        en.put("speed.visualmult.val", "× %.2f");
        ru.put("speed.visualmult.val", "× %.2f");
        en.put("speed.visualmult.note", "Multiplies v from each incoming OD_LINBEG / OD_HOMING delta for your gob (Java apply path, not JNI). Not the Speedget tier. Sprite can drift vs server linstep until correction; others see normal speed.");
        ru.put("speed.visualmult.note", "Умножает v из каждой входящей дельты OD_LINBEG / OD_HOMING для вашего гоба (Java, не JNI). Не ступень Speedget. Спрайт может разъехаться с linstep до коррекции; у других обычная скорость.");
        en.put("speed.mult.resend", "Re-send Speedget interval (keep mult on server)");
        ru.put("speed.mult.resend", "Интервал повторной отправки Speedget (удержать mult на сервере)");
        en.put("speed.mult.resend.off", "Off");
        ru.put("speed.mult.resend.off", "Выкл.");
        en.put("speed.mult.resend.note", "Default off (0). If >0 and mult>1, sends set(current tier) on this interval so the server keeps seeing the bumped index (skipped while Speed boost is on). Try ~4–8s if the server “forgets” your tier.");
        ru.put("speed.mult.resend.note", "По умолчанию выкл. (0). Если >0 и mult>1 — периодический set(текущая ступень), чтобы сервер держал поднятый индекс (не при «Ускорение»). Попробуй ~4–8 с, если сервер «сбрасывает» ступень.");
        en.put("exp.jni.subhdr", "JNI / HCrypt wire (not Speedget)");
        ru.put("exp.jni.subhdr", "JNI / канал HCrypt (не Speedget)");
        en.put("exp.jni.enable", "Enable JNI plaintext hook (moonpackethook)");
        ru.put("exp.jni.enable", "Включить JNI-хук plaintext (moonpackethook)");
        en.put("exp.jni.note.short", "Edits [type][payload] after decrypt / before encrypt. Object deltas & map bulk are separate. Needs DLL + this checkbox (or -Dhaven.moonjni.wire=true).");
        ru.put("exp.jni.note.short", "Правка [тип][тело] после расшифровки / до шифрования. Дельты объектов и карта — отдельно. Нужны DLL и эта галка (или -Dhaven.moonjni.wire=true).");
        en.put("exp.jni.log", "Log HCrypt frames [MoonJniWire]");
        ru.put("exp.jni.log", "Лог кадров HCrypt [MoonJniWire]");
        en.put("exp.jni.log.note", "stderr: direction, session type (REL/MAPDATA/…), length, hex prefix. Works with or without HCrypt (cleartext sessions used to print nothing — now they log as phase=clear). Enable “mirror stdout” if your launcher hides stderr.");
        ru.put("exp.jni.log.note", "stderr: направление, тип сессии, длина, hex. С HCrypt и без (раньше без шифрования логов не было — теперь фаза clear). Если консоли нет — включи дубль в stdout.");
        en.put("exp.jni.mirrorstdout", "Mirror [MoonJniWire] to stdout");
        ru.put("exp.jni.mirrorstdout", "Дублировать [MoonJniWire] в stdout");
        en.put("exp.jni.mirrorstdout.note", "Some Windows shortcuts drop stderr; stdout may still appear in a wrapper log.");
        ru.put("exp.jni.mirrorstdout.note", "У части ярлыков stderr не виден; stdout иногда есть в логе лаунчера.");
        en.put("exp.jni.nativestderr", "Native one-line echo [MoonJniNative]");
        ru.put("exp.jni.nativestderr", "Натив: одна строка в stderr [MoonJniNative]");
        en.put("exp.jni.nativestderr.note", "Requires DLL; prints from C++ when JNI transform runs (hook must be on). Duplicates type/len briefly.");
        ru.put("exp.jni.nativestderr.note", "Нужна DLL; печать из C++ при вызове JNI (хук должен быть вкл.). Кратко type/len.");
        en.put("exp.jni.status.yes", "moonpackethook DLL: loaded");
        ru.put("exp.jni.status.yes", "moonpackethook DLL: загружена");
        en.put("exp.jni.status.no", "moonpackethook DLL: not loaded (build native/moonpackethook, check java.library.path)");
        ru.put("exp.jni.status.no", "moonpackethook DLL: нет (собери native/moonpackethook, java.library.path)");
        en.put("exp.speedwire.subhdr", "Java speed wire (Speedget \"set\")");
        ru.put("exp.speedwire.subhdr", "Java: скорость (Speedget «set»)");
        en.put("exp.speedwire.enable", "Allow outgoing speed wire assist");
        ru.put("exp.speedwire.enable", "Разрешить подмену скорости в исходящем «set»");
        en.put("exp.speedwire.note", "Experimental: server may see higher tier than UI (lift / mult / sprint assist). Off = vanilla Speedget. Not JNI.");
        ru.put("exp.speedwire.note", "Эксперимент: сервер может видеть выше ступень, чем в UI (lift / mult / sprint). Выкл. = обычный Speedget. Не JNI.");
        en.put("speed.see.functions", "All speed / movement settings: «Speed & Movement» (S) on the main options page.");
        ru.put("speed.see.functions", "Все настройки скорости/движения: «Скорость и движение» (S) на главной странице опций.");
        en.put("cat.movement", "Movement & speed");
        ru.put("cat.movement", "Движение и скорость");
        en.put("cat.experimental.wire", "Wire / JNI (experimental)");
        ru.put("cat.experimental.wire", "Wire / JNI (эксперимент)");
        en.put("cat.experimental.visual", "World visuals (experimental)");
        ru.put("cat.experimental.visual", "Визуал мира (эксперимент)");
        en.put("moonwide.experimental", "Experimental");
        ru.put("moonwide.experimental", "Экспериментальные");
        en.put("moonwide.experimental.note", "JNI wire hook, flat terrain, always day, stamina maintain-full. May desync or break on server updates. Speed settings moved to «Speed & Movement».");
        ru.put("moonwide.experimental.note", "JNI wire hook, плоский рельеф, вечный день, автопитьё «держать 100%». Настройки скорости перенесены в «Скорость и движение».");
        en.put("opt.speed", "Speed & Movement");
        ru.put("opt.speed", "Скорость и движение");
        en.put("opt.speed.tip", "Speedget wire, visual speed, sprint boost, Diablo move");
        ru.put("opt.speed.tip", "Speedget на wire, визуальная скорость, спринт-буст, Diablo");
        en.put("moonwide.speed", "Speed & Movement");
        ru.put("moonwide.speed", "Скорость и движение");
        en.put("moonwide.speed.note", "Server decides actual velocity (v) in each OD_LINBEG/OD_HOMING. Wire features only switch speed TIER (crawl/walk/run/sprint 0-3) — if already at max, wire does nothing. Visual mult causes rubber-banding. Sprint boost causes micro-stutters.");
        ru.put("moonwide.speed.note", "Сервер определяет скорость (v) в каждом OD_LINBEG/OD_HOMING. Wire-фичи только переключают ТИР (ползок/шаг/бег/спринт 0-3) — если уже на макс., wire бесполезен. Визуальный множитель вызывает резину. Спринт-буст — микрофризы.");
        en.put("cat.speed.basic", "Basic movement");
        ru.put("cat.speed.basic", "Базовое движение");
        en.put("cat.speed.wire", "Speedget wire (outgoing → server)");
        ru.put("cat.speed.wire", "Speedget wire (исходящее → сервер)");
        en.put("cat.speed.visual", "Visual speed (client only — not server)");
        ru.put("cat.speed.visual", "Визуальная скорость (только клиент — не сервер)");
        en.put("cat.speed.boost", "Sprint boost");
        ru.put("cat.speed.boost", "Спринт-буст");
        en.put("cat.speed.debug", "Debug");
        ru.put("cat.speed.debug", "Отладка");
        en.put("opt.experimental", "Experimental…");
        ru.put("opt.experimental", "Эксперимент…");
        en.put("opt.experimental.tip", "JNI hook, terrain/day tweaks, aggressive autodrink");
        ru.put("opt.experimental.tip", "JNI, рельеф/день, агрессивное автопитьё");
        en.put("exp.movement.explain", "Speed / movement settings are now in the dedicated «Speed & Movement» panel (S).");
        ru.put("exp.movement.explain", "Настройки скорости/движения теперь в панели «Скорость и движение» (S).");
        en.put("speed.remember", "Remember movement speed mode after relog");
        ru.put("speed.remember", "Запоминать режим скорости (ползунок) после входа");

        en.put("diablo.move", "Diablo-style movement (hold LMB)");
        ru.put("diablo.move", "Движение как в Diablo (зажать ЛКМ)");

        en.put("hitbox.toggle", "Hitbox display");
        ru.put("hitbox.toggle", "Отображение хитбоксов");

        en.put("pathfind.note", "LMB: move. Alt+LMB: add waypoint to route");
        ru.put("pathfind.note", "ЛКМ: движение. Alt+ЛКМ: добавить точку маршрута");

        en.put("settings.language", "Language / Язык");
        ru.put("settings.language", "Язык / Language");
        en.put("settings.general", "General");
        ru.put("settings.general", "Основные");

        en.put("opt.video", "Video");         ru.put("opt.video", "Видео");
        en.put("opt.audio", "Audio");         ru.put("opt.audio", "Аудио");
        en.put("opt.display", "Display");     ru.put("opt.display", "Дисплей");
        en.put("opt.keybindings", "Keybindings"); ru.put("opt.keybindings", "Клавиши");
        en.put("opt.chat", "Chat");           ru.put("opt.chat", "Чат");
        en.put("opt.chat.suppress.minesupport", "Hide mine-support coverage notice in System chat");
        ru.put("opt.chat.suppress.minesupport", "Скрыть уведомление о mine-support в системном чате");
        en.put("opt.chat.suppress.minesupport.hint", "The server may send a red line asking to update the client for mine-support visualization. This only hides that line; it does not add the overlay (that needs a matching client build).");
        ru.put("opt.chat.suppress.minesupport.hint", "Сервер может присылать красную строку с просьбой обновить клиент для отображения mine-support. Скрытие убирает только строку в чате, не добавляет оверлей (нужна сборка клиента с этой функцией).");
        en.put("opt.layout", "Layout");       ru.put("opt.layout", "Расположение");
        en.put("opt.automation", "Automation (movement, drink…)");
        ru.put("opt.automation", "Автоматизация (движение, питьё…)");
        en.put("opt.overlay", "World Overlay"); ru.put("opt.overlay", "Оверлей мира");
        en.put("opt.combat", "Combat");       ru.put("opt.combat", "Бой");
        en.put("opt.combat.tip", "Combat HUD, cooldowns, and «Combat bot AI settings» (card bot / ИИ) at the top of this tab.");
        ru.put("opt.combat.tip", "HUD боя, кулдауны и вверху вкладки — «Настройки ИИ автобоя» (бот картами).");
        en.put("opt.performance", "Performance"); ru.put("opt.performance", "Производительность");
        en.put("opt.integrations", "Integrations"); ru.put("opt.integrations", "Интеграции");
        en.put("opt.moonwide", "MooNWide Functions"); ru.put("opt.moonwide", "Функции MooNWide");
        en.put("opt.back", "Back"); ru.put("opt.back", "Назад");
        en.put("opt.close", "Close"); ru.put("opt.close", "Закрыть");
        en.put("opt.confirm_close", "Confirm close (ask before exit)");
        ru.put("opt.confirm_close", "Подтверждение выхода");
        en.put("opt.switch_char", "Switch character"); ru.put("opt.switch_char", "Сменить персонажа");
        en.put("opt.logout", "Log out"); ru.put("opt.logout", "Выйти");
        en.put("opt.store", "Visit store"); ru.put("opt.store", "Магазин");

        en.put("menu.vanilla", "— Haven Settings —");
        ru.put("menu.vanilla", "— Настройки Haven —");
        en.put("menu.moonwide", "— MooNWide —");
        ru.put("menu.moonwide", "— MooNWide —");
        en.put("menu.general", "— General —");
        ru.put("menu.general", "— Общие —");

        en.put("guide.title", "MooNWide — full interface guide");
        ru.put("guide.title", "MooNWide — полное обучение интерфейсу");
        en.put("guide.ok", "Got it, close");
        ru.put("guide.ok", "Понятно, закрыть");
        en.put("guide.quicknote", "$col[180,200,255]{Update: the lower quick bar no longer has a Craft button. Craft now opens from the main action menu only. Special MooNWide tiles and the Bots window use thematic icons; belt slots show a styled empty-slot mark.}");
        ru.put("guide.quicknote", "$col[180,200,255]{Обновление: в нижней быстрой панели больше нет кнопки крафта. Крафт теперь открывается только из главного меню действий. Особые тайлы MooNWide и окно «Боты» используют тематические иконки, а пустые слоты поясов показывают отдельную отметку.}");
        en.put("menu.guide", "Interface guide");
        ru.put("menu.guide", "Обучение интерфейсу");
        en.put("opt.guide", "Interface guide (MooNWide)");
        ru.put("opt.guide", "Обучение интерфейсу (MooNWide)");
        en.put("guide.body",
            "$col[200,220,255]{What is MooNWide}\n"
            + "Extra floating toolbars, world overlay (ESP, traces, X-Ray), combat helpers, automation (drink, tree bot, combat bot), hitbox/grid debug, parallel sessions, and Russian/English UI strings.\n\n"
            + "$col[210,185,120]{Floating panels}\n"
            + "• Drag the colored header to move.\n"
            + "• Ring on the right = lock: while on, the panel does not jump when you click the world.\n"
            + "• Corner grip = resize.\n"
            + "• × = close (guide also sets “already seen”).\n\n"
            + "$col[210,185,120]{Left quick bar}\n"
            + "• Inventory, Equipment, Character, Kith & Kin, Minimap, World map — same functions as vanilla shortcuts; here they are icons.\n\n"
            + "$col[210,185,120]{Right quick bar}\n"
            + "• Chat, Crafting (opens the advanced craft browser), Search (actions), Overlay (MooNWide world overlay tab), Combat (combat tab), Combat bot (card auto-play window), Settings (full options).\n\n"
            + "$col[210,185,120]{Hands & belt}\n"
            + "• Hands strip: quick swap main/off hand items.\n"
            + "• Bottom belt / keyring: server grid + MooNWide belt panels (1–0, F-keys, Num) — layout in belt panel menu (gear on those panels).\n\n"
            + "$col[210,185,120]{Action menu (grid)}\n"
            + "• Vanilla Haven tiles + MooNWide specials: Bots, Interface guide, Cave flat, Safe dig, Combat damage HUD, Reset taken damage.\n"
            + "• Drag the grid by its header; lock so it stays. In combat, letter hotkeys for tiles may be suppressed so fight keys work.\n\n"
            + "$col[210,185,120]{Settings — main menu}\n"
            + "• MooNWide Functions — hub: session switcher, link to Combat bot AI, categories Combat / Visual / Automation.\n"
            + "• Automation — auto-drink, tree-bot related toggles; movement & speed live under Functions → Movement & speed.\n"
            + "• World Overlay — ESP rows (distance, name, color per type), traces, aggro ring, safe mode, activity HUD, X-Ray, flat terrain, always day, fight overlay position.\n"
            + "• Combat — fight HUD (dealt/taken, enemy HP), cave walls, safe-dig overlay, button “Combat bot AI settings…”.\n"
            + "• Performance / Integrations — as labeled.\n"
            + "• Vanilla: Video, Audio, Display, Keybindings, Chat, Layout + General (language MooNWide en/ru).\n"
            + "• Button “Interface guide (MooNWide)” reopens this help.\n\n"
            + "$col[210,185,120]{World overlay (summary)}\n"
            + "• ESP: per-category labels on mobs, players, buildings, items, etc.\n"
            + "• Traces: lines to hostile/neutral/players; optional LinMove ground path and Homing chase lines (Combat); tree stand spot Shift+Alt+LMB.\n"
            + "• X-Ray: hide trees (optional rocks) to see behind; style and color in options.\n"
            + "• Aggro radius & safe mode: distance helpers around you.\n"
            + "• Activity HUD: progress and structure HP above your character.\n\n"
            + "$col[210,185,120]{Hitboxes & grid}\n"
            + "• Keybindings → MooNWide: global object hitbox cycle (default Shift+B), entity/structure hitbox modes, tile grid on map (Ctrl+G cycles off / normal / thick).\n\n"
            + "$col[210,185,120]{Combat bot}\n"
            + "• Enables automatic card use in fightview; brain modes legacy / 1-ply / expectiminimax; optional server slot hints and decision log.\n"
            + "• Log file ~/.haven/moon-combat-ai.log — each line includes pid= so two clients (bot vs bot) stay separable.\n"
            + "• “Analyze combat AI log” runs the bundled Python script (needs py/python on PATH).\n\n"
            + "$col[210,185,120]{Bots window}\n"
            + "• Combat bot — enable and open detailed AI window.\n"
            + "• Tree chop — chop area, stockpile, water, optional split/firewood phases, auto phase chain, status text.\n\n"
            + "$col[210,185,120]{Sessions}\n"
            + "• Native login only: switch or add another account inside the same client (from Functions). Steam build cannot use this.\n\n"
            + "$col[210,185,120]{Language}\n"
            + "• Settings → General → Language: MooNWide strings (this guide, options labels, messages) switch EN/RU; vanilla Haven text stays game default.\n\n"
            + "$col[180,200,255]{This window opens once on first world entry; reopen: Settings → Interface guide, menu tile “Interface guide”, or MooNWide Functions.}");
        ru.put("guide.body",
            "$col[200,220,255]{Что такое MooNWide}\n"
            + "Дополнительные плавающие панели, оверлей мира (ESP, трейсы, X-Ray), помощь в бою, автоматизация (питьё, бот рубки, автобой по картам), отладка хитбоксов и сетки, параллельные сессии, русский и английский текст интерфейса клиента.\n\n"
            + "$col[210,185,120]{Плавающие панели}\n"
            + "• Тащите за цветную шапку — перенос.\n"
            + "• Кольцо справа — замок: пока включён, панель не «прыгает» от кликов по миру.\n"
            + "• Уголок — размер.\n"
            + "• × — закрыть (для гайда ещё отмечается «уже показывали»).\n\n"
            + "$col[210,185,120]{Левая быстрая панель}\n"
            + "• Инвентарь, экипировка, персонаж, родственники, миникарта, карта мира — те же функции, что у горячих клавиш Haven, здесь в виде кнопок.\n\n"
            + "$col[210,185,120]{Правая быстрая панель}\n"
            + "• Чат, Крафт (открывает новый браузер крафта), Поиск (действия), Оверлей (вкладка оверлея MooNWide), Бой (вкладка боя), Автобой (окно ИИ по картам), Настройки (полное меню опций).\n\n"
            + "$col[210,185,120]{Руки и пояс}\n"
            + "• Полоска «Руки»: быстрая смена предметов в основной и второй руке.\n"
            + "• Нижний пояс / связка ключей: сетка как на сервере + панели MooNWide (1–0, F, Num) — разметка в меню шестерёнки на этих панелях.\n\n"
            + "$col[210,185,120]{Меню действий (сетка)}\n"
            + "• Обычные тайлы Haven + особые MooNWide: Боты, Обучение интерфейсу, Шахта без стен, Безопасные клетки копки, HUD урона в бою, Сброс полученного урона.\n"
            + "• Перетаскивание за шапку; замок фиксирует позицию. В бою буквенные привязки сетки могут подавляться, чтобы не мешать боевым клавишам.\n\n"
            + "$col[210,185,120]{Настройки — главное меню}\n"
            + "• Функции MooNWide — центр: переключение сессий, ссылка на настройки ИИ автобоя, категории Бой / Визуальное / Автоматизация.\n"
            + "• Автоматизация — автопитьё, опции, связанные с рубкой; движение и скорость — во вкладке Функции → Движение и скорость.\n"
            + "• Оверлей мира — строки ESP (расстояние, имя, цвет по типу), трейсы, кольцо агра, безопасный режим, HUD активности, X-Ray, плоская земля, всегда день, положение боевого оверлея.\n"
            + "• Бой — HUD боя (урон врагу/себе, полоска HP цели), стены пещер, подсветка безопасной копки, кнопка «Настройки ИИ автобоя…».\n"
            + "• Производительность / Интеграции — по названиям.\n"
            + "• Классика Haven: Видео, Аудио, Дисплей, Клавиши, Чат, Расположение + Основные (язык интерфейса MooNWide en/ru).\n"
            + "• Кнопка «Обучение интерфейсу (MooNWide)» снова открывает эту справку.\n\n"
            + "$col[210,185,120]{Оверлей мира (кратко)}\n"
            + "• ESP: подписи по категориям — мобы, игроки, постройки, предметы и т.д.\n"
            + "• Трейсы: линии к враждебным/нейтральным/игрокам; трейс ходьбы — клик по земле/объекту, Alt — путь по клеткам; точка стояния для рубки Shift+Alt+ЛКМ.\n"
            + "• X-Ray: скрытие деревьев (опционально камней), стиль и цвет в настройках.\n"
            + "• Радиус агра и безопасный режим — подсказки по дистанции вокруг персонажа.\n"
            + "• HUD активности — прогресс действия и прочность цели над персонажем.\n\n"
            + "$col[210,185,120]{Хитбоксы и сетка}\n"
            + "• Настройки → Клавиши → MooNWide: цикл глобальных хитбоксов (по умолчанию Shift+B), режимы хитбоксов сущностей/строений, сетка тайлов на карте (Ctrl+G: выкл / обычная / жирная).\n\n"
            + "$col[210,185,120]{Автобой}\n"
            + "• Автоматический выбор карт в окне боя; режимы мозга: ключевые слова / ИИ один ход / минимакс+шанс; опционально доверять подсветке слотов сервера и вести лог решений.\n"
            + "• Файл ~/.haven/moon-combat-ai.log — в каждой строке pid= (ид процесса), чтобы два клиента (бот против бота) различались в одном файле.\n"
            + "• «Разобрать лог ИИ боя» запускает встроенный скрипт Python (нужен py/python в PATH).\n\n"
            + "$col[210,185,120]{Окно «Боты»}\n"
            + "• Автобой — включение и переход в окно настроек ИИ.\n"
            + "• Рубка деревьев — области рубки и склада, вода, опционально разрубка/полена, цепочка этапов, текст статуса.\n\n"
            + "$col[210,185,120]{Сессии}\n"
            + "• Только вход по логину/паролю: переключение или второй аккаунт в том же клиенте (из «Функций»). В сборке Steam недоступно.\n\n"
            + "$col[210,185,120]{Язык}\n"
            + "• Настройки → Основные → Язык: строки MooNWide (этот гайд, подписи опций, сообщения) переключаются EN/RU; текст самого Haven остаётся как в игре.\n\n"
            + "$col[180,200,255]{Окно один раз при первом входе в мир; снова: Настройки → Обучение интерфейсу, тайл меню «Обучение интерфейсу» или раздел Функции MooNWide.}");

        en.put("shortcut.inv", "Inventory");       ru.put("shortcut.inv", "Инвентарь");
        en.put("inv.sortAz", "Sort A-Z");          ru.put("inv.sortAz", "А→Я");
        en.put("shortcut.equ", "Equipment");       ru.put("shortcut.equ", "Экипировка");
        en.put("shortcut.chr", "Character");       ru.put("shortcut.chr", "Персонаж");
        en.put("shortcut.kin", "Kith & Kin");      ru.put("shortcut.kin", "Родные и близкие");
        en.put("shortcut.map", "Minimap");         ru.put("shortcut.map", "Миникарта");
        en.put("shortcut.bigmap", "World Map");    ru.put("shortcut.bigmap", "Карта мира");
        en.put("shortcut.chat", "Chat");           ru.put("shortcut.chat", "Чат");
        en.put("shortcut.search", "Search");       ru.put("shortcut.search", "Поиск");
        en.put("shortcut.crafting", "Crafting");   ru.put("shortcut.crafting", "Крафт");
        en.put("shortcut.automation", "Automation"); ru.put("shortcut.automation", "Автоматизация");
        en.put("shortcut.bots", "Bots");           ru.put("shortcut.bots", "Боты");
        en.put("bots.hub.nav.tip", "Saved map points (short-range navigation)");
        ru.put("bots.hub.nav.tip", "Сохранённые точки (короткие переходы)");
        en.put("menu.bots", "Bots");
        ru.put("menu.bots", "Боты");
        en.put("menu.tpnav", "Navigation: saved points");
        ru.put("menu.tpnav", "Навигация: точки");
        en.put("tp.title", "Navigation");
        ru.put("tp.title", "Навигация");
        en.put("tp.opt.enable", "Enable navigation assist");
        ru.put("tp.opt.enable", "Включить помощник навигации");
        en.put("tp.opt.clamp", "Clamp ground clicks to max tile radius (MapView)");
        ru.put("tp.opt.clamp", "Ограничивать клики по земле радиусом (карта)");
        en.put("tp.opt.path", "Require client pathfinder path");
        ru.put("tp.opt.path", "Требовать путь (pathfinder на клиенте)");
        en.put("tp.opt.passivepath", "Passive obstacle avoidance on left click");
        ru.put("tp.opt.passivepath", "Пассивный обход препятствий на левый клик");
        en.put("tp.opt.nohostile", "Block targets near hostile mobs");
        ru.put("tp.opt.nohostile", "Не целиться рядом с враждебными мобами");
        en.put("tp.opt.log", "Log moves to stderr");
        ru.put("tp.opt.log", "Лог в stderr");
        en.put("tp.filter", "List filter:");
        ru.put("tp.filter", "Фильтр:");
        en.put("tp.cat.all", "all");
        ru.put("tp.cat.all", "все");
        en.put("tp.cat.home", "home");
        ru.put("tp.cat.home", "дом");
        en.put("tp.cat.resource", "resources");
        ru.put("tp.cat.resource", "ресурсы");
        en.put("tp.cat.friend", "friends");
        ru.put("tp.cat.friend", "друзья");
        en.put("tp.cat.other", "other");
        ru.put("tp.cat.other", "прочее");
        en.put("tp.btn.filter", "Cycle filter");
        ru.put("tp.btn.filter", "Сменить фильтр");
        en.put("tp.btn.go", "Go (selected)");
        ru.put("tp.btn.go", "Идти (выбранная)");
        en.put("tp.btn.del", "Delete");
        ru.put("tp.btn.del", "Удалить");
        en.put("tp.btn.pick.map", "Pick point on map");
        ru.put("tp.btn.pick.map", "Выбрать точку на карте");
        en.put("tp.btn.pick.cancel", "Cancel map pick");
        ru.put("tp.btn.pick.cancel", "Отмена выбора на карте");
        en.put("tp.save.hdr", "Save current position");
        ru.put("tp.save.hdr", "Сохранить текущую позицию");
        en.put("tp.btn.newcat", "Category for new point");
        ru.put("tp.btn.newcat", "Категория новой точки");
        en.put("tp.newcat", "New point category:");
        ru.put("tp.newcat", "Категория:");
        en.put("tp.btn.savehere", "Save here");
        ru.put("tp.btn.savehere", "Сохранить здесь");
        en.put("tp.io.hdr", "Import / export JSON");
        ru.put("tp.io.hdr", "Импорт / экспорт JSON");
        en.put("tp.btn.export", "Export to field");
        ru.put("tp.btn.export", "Экспорт в поле");
        en.put("tp.btn.import", "Replace from field");
        ru.put("tp.btn.import", "Заменить из поля");
        en.put("tp.io.exported", "JSON placed in field below.");
        ru.put("tp.io.exported", "JSON в поле ниже.");
        en.put("tp.io.imported", "Imported points:");
        ru.put("tp.io.imported", "Импортировано точек:");
        en.put("tp.io.bad", "Import failed (invalid JSON).");
        ru.put("tp.io.bad", "Импорт не удался (неверный JSON).");
        en.put("tp.needsel", "Select a point first.");
        ru.put("tp.needsel", "Сначала выберите точку.");
        en.put("tp.saved", "Point saved.");
        ru.put("tp.saved", "Точка сохранена.");
        en.put("tp.noname", "Waypoint");
        ru.put("tp.noname", "Точка");
        en.put("tp.ok", "Move command sent.");
        ru.put("tp.ok", "Команда движения отправлена.");
        en.put("tp.fail.no_map", "No map.");
        ru.put("tp.fail.no_map", "Нет карты.");
        en.put("tp.fail.disabled", "Navigation assist is off.");
        ru.put("tp.fail.disabled", "Помощник навигации выключен.");
        en.put("tp.fail.loading", "Map loading.");
        ru.put("tp.fail.loading", "Карта загружается.");
        en.put("tp.fail.mapseg", "That point is not reachable from the current map segment.");
        ru.put("tp.fail.mapseg", "Эта точка недоступна с текущего фрагмента карты.");
        en.put("tp.fail.no_player", "No player.");
        ru.put("tp.fail.no_player", "Нет персонажа.");
        en.put("tp.fail.cooldown", "Cooldown active.");
        ru.put("tp.fail.cooldown", "Пауза между переходами.");
        en.put("tp.fail.reason", "Blocked");
        ru.put("tp.fail.reason", "Отклонено");
        en.put("tp.empty", "No saved points yet.");
        ru.put("tp.empty", "Сохранённых точек пока нет.");
        en.put("tp.quick.empty", "No saved point for this slot.");
        ru.put("tp.quick.empty", "Нет точки для этого слота.");
        en.put("tp.pick.on", "Map point picking enabled: click the world map or minimap.");
        ru.put("tp.pick.on", "Выбор точки на карте включён: щёлкните по карте или миникарте.");
        en.put("tp.pick.off", "Map point picking disabled.");
        ru.put("tp.pick.off", "Выбор точки на карте отключён.");
        en.put("keybind.mw.speed-wire-panel", "Toggle speed wire HUD (Shift+X)");
        ru.put("keybind.mw.speed-wire-panel", "Панель скорости на wire (Shift+X)");
        en.put("speedwire.panel.title", "Speed (Shift+X)");
        ru.put("speedwire.panel.title", "Скорость (Shift+X)");
        en.put("speedwire.panel.assist", "Wire assist (rewrite Speedget → server)");
        ru.put("speedwire.panel.assist", "Wire assist (подмена Speedget → сервер)");
        en.put("speedwire.panel.boost", "Sprint boost (cycle sprint/run)");
        ru.put("speedwire.panel.boost", "Спринт-буст (цикл спринт/бег)");
        en.put("speedwire.panel.sprint", "Force sprint to server");
        ru.put("speedwire.panel.sprint", "Спринт на сервер");
        en.put("speedwire.panel.mult", "Wire: %.1f×");
        ru.put("speedwire.panel.mult", "Wire: %.1f×");
        en.put("speedwire.panel.visual", "Visual: %.1f×");
        ru.put("speedwire.panel.visual", "Визуал: %.1f×");
        en.put("speedwire.panel.resend.off", "Re-send: off");
        ru.put("speedwire.panel.resend.off", "Повтор: выкл.");
        en.put("speedwire.panel.resend.on", "Re-send: %ds");
        ru.put("speedwire.panel.resend.on", "Повтор: %dс");
        en.put("speedwire.panel.opts", "All speed settings…");
        ru.put("speedwire.panel.opts", "Все настройки скорости…");
        en.put("speedwire.panel.shown", "Speed panel shown (Shift+X).");
        ru.put("speedwire.panel.shown", "Панель скорости показана (Shift+X).");
        en.put("speedwire.panel.hidden", "Speed panel hidden.");
        ru.put("speedwire.panel.hidden", "Панель скорости скрыта.");
        en.put("keybind.mw.tpnav", "Open navigation points window");
        ru.put("keybind.mw.tpnav", "Окно точек навигации");
        en.put("keybind.mw.tp-quick1", "Navigate: 1st recent point");
        ru.put("keybind.mw.tp-quick1", "Навигация: 1-я точка");
        en.put("keybind.mw.tp-quick2", "Navigate: 2nd recent point");
        ru.put("keybind.mw.tp-quick2", "Навигация: 2-я точка");
        en.put("keybind.mw.tp-quick3", "Navigate: 3rd recent point");
        ru.put("keybind.mw.tp-quick3", "Навигация: 3-я точка");
        en.put("menu.caveflat", "Mine: flat (no walls)");
        ru.put("menu.caveflat", "Шахта: без стен");
        en.put("menu.safedig", "Mine: safe tiles");
        ru.put("menu.safedig", "Шахта: безопасные клетки");
        en.put("menu.combathud", "Combat: damage HUD");
        ru.put("menu.combathud", "Бой: урон HUD");
        en.put("menu.resettaken", "Reset damage taken");
        ru.put("menu.resettaken", "Сброс получ. урона");
        en.put("msg.caveflat.on", "Cave walls shortened to bottom stone strip (reloads terrain).");
        ru.put("msg.caveflat.on", "Стены пещер укорочены до нижней полосы камня (перестроение земли).");
        en.put("msg.caveflat.off", "Cave walls visible.");
        ru.put("msg.caveflat.off", "Стены пещер снова видны.");
        en.put("msg.safedig.on", "Safe-dig tile highlight on.");
        ru.put("msg.safedig.on", "Подсветка клеток вкл.");
        en.put("msg.safedig.off", "Safe-dig tile highlight off.");
        ru.put("msg.safedig.off", "Подсветка клеток выкл.");
        en.put("msg.combathud.on", "Combat damage HUD on.");
        ru.put("msg.combathud.on", "HUD урона в бою вкл.");
        en.put("msg.combathud.off", "Combat damage HUD off.");
        ru.put("msg.combathud.off", "HUD урона в бою выкл.");
        en.put("msg.dmg.reset", "Received damage counters reset.");
        ru.put("msg.dmg.reset", "Счётчики полученного урона сброшены.");
        en.put("fight.hud.dealt", "R:%d Y:%d A:%d");
        ru.put("fight.hud.dealt", "Кр:%d Жт:%d Бр:%d");
        en.put("fight.hud.taken", "Taken R:%d Y:%d A:%d");
        ru.put("fight.hud.taken", "Получено Кр:%d Жт:%d Бр:%d");
        en.put("opt.caveflat", "Hide cave walls (keep bottom stone band, not black void)");
        ru.put("opt.caveflat", "Скрывать стены пещер (низ камня остаётся, без чёрной пустоты)");
        en.put("opt.safedig", "Mine support safety: exact tile-center rule; runtime overlay / resource data / trusted support tiers; collapsed (0 HP) ignored");
        ru.put("opt.safedig", "Зона подпорок: точное правило по центру клетки; runtime overlay / данные ресурса / проверенные типы подпорок; обрушенные (0 HP) не считаются");
        en.put("opt.safedig.r", "Fallback radius (tiles) if resource has no data");
        ru.put("opt.safedig.r", "Запасной радиус (кл.), если в ресурсе нет данных");
        en.put("opt.safedig.fill", "Safe area fill opacity");
        ru.put("opt.safedig.fill", "Прозрачность заливки зоны");
        en.put("opt.safedig.edgea", "Outer outline opacity");
        ru.put("opt.safedig.edgea", "Непрозрачность контура");
        en.put("opt.safedig.edgew", "Outline thickness (px)");
        ru.put("opt.safedig.edgew", "Толщина контура (px)");
        en.put("opt.safedig.or", "Outline color R");
        ru.put("opt.safedig.or", "Контур: красный");
        en.put("opt.safedig.og", "Outline color G");
        ru.put("opt.safedig.og", "Контур: зелёный");
        en.put("opt.safedig.ob", "Outline color B");
        ru.put("opt.safedig.ob", "Контур: синий");
        en.put("opt.mine.risk", "Mine: highlight unsupported cave tiles (collapse risk vs supports)");
        ru.put("opt.mine.risk", "Шахта: подсветка клеток без зоны подпорки (риск обвала)");
        en.put("opt.combathud", "Combat: IP loss HUD + mob HP bar");
        ru.put("opt.combathud", "Бой: HUD потерь IP + полоска HP моба");
        en.put("opt.combathud.dealt", "Show dealt damage numbers above foes");
        ru.put("opt.combathud.dealt", "Показывать нанесённый урон над противниками");
        en.put("opt.combathud.taken", "Show taken damage above self");
        ru.put("opt.combathud.taken", "Показывать полученный урон над собой");
        en.put("opt.combathud.hp", "Enemy HP bar");
        ru.put("opt.combathud.hp", "Полоска HP противника");
        en.put("opt.combathud.hp.0", "only when not full");
        ru.put("opt.combathud.hp.0", "только если не полное HP");
        en.put("opt.combathud.hp.1", "whenever client has HP");
        ru.put("opt.combathud.hp.1", "если клиент знает HP");
        en.put("opt.combathud.hp.2", "always (frame even if unknown)");
        ru.put("opt.combathud.hp.2", "всегда (рамка даже без данных)");
        en.put("opt.combathud.swap", "Swap fight IP/oIP (if dealt/taken look reversed)");
        ru.put("opt.combathud.swap", "Поменять местами IP/oIP боя (если цифры перепутаны)");
        en.put("opt.combathud.persist.scope", "Dealt damage persistence scope");
        ru.put("opt.combathud.persist.scope", "Сохранение нанесённого урона");
        en.put("opt.combathud.persist.scope.0", "only opponents");
        ru.put("opt.combathud.persist.scope.0", "только противники");
        en.put("opt.combathud.persist.scope.1", "all targets");
        ru.put("opt.combathud.persist.scope.1", "все цели");
        en.put("opt.dmg.resetbtn", "Reset received damage counters");
        ru.put("opt.dmg.resetbtn", "Сбросить полученный урон");
        en.put("bots.title", "MooNWide bots");
        ru.put("bots.title", "Боты MooNWide");
        en.put("bots.hub.combat.tip", "Combat bot — auto-fight (enable/disable and options).");
        ru.put("bots.hub.combat.tip", "Бот боевки — автобой (вкл/выкл и настройки).");
        en.put("bots.hub.tree.tip", "Tree chop — areas on the map, stockpile, water source.");
        ru.put("bots.hub.tree.tip", "Рубка деревьев — области на карте, склад, источник воды.");
        en.put("bots.placeholder.farming", "Reserved (farming bot).");
        ru.put("bots.placeholder.farming", "Зарезервировано (бот фермерства).");
        en.put("bots.placeholder.mining", "Open mining bot from the M tile in the Bots window.");
        ru.put("bots.placeholder.mining", "Бот копки — плитка M в окне «Боты».");
        en.put("bots.hub.mine.tip", "Mine sweeper — cave walls in an area, safe vs collapse-risk overlay.");
        ru.put("bots.hub.mine.tip", "Копка / сапёр — стены в области, подсветка безопасных и обвальных клеток.");
        en.put("bots.placeholder.fish", "Reserved (fishing bot).");
        ru.put("bots.placeholder.fish", "Зарезервировано (бот рыбалки).");
        en.put("bots.placeholder.more", "More bots later.");
        ru.put("bots.placeholder.more", "Другие боты позже.");

        en.put("combatbot.wnd.title", "Combat bot");
        ru.put("combatbot.wnd.title", "Автобой");
        en.put("combatbot.wnd.hint", "Modes: legacy keywords, 1-ply AI (tables + buffs + profiling), or expectiminimax. Data: haven/res/moon-combat-data.json.");
        ru.put("combatbot.wnd.hint", "Режимы: ключевые слова, ИИ (1 ход, таблицы, баффы, профиль), минимакс+шанс. Данные: haven/res/moon-combat-data.json.");
        en.put("combatbot.wnd.keybind", "Menu: Bots tile or key (see keybindings).");
        ru.put("combatbot.wnd.keybind", "Меню: плитка «Боты» или клавиша (см. привязки).");
        en.put("combatbot.wnd.data", "Table load:");
        ru.put("combatbot.wnd.data", "Таблица:");
        en.put("combatbot.ai.brain", "Brain");
        ru.put("combatbot.ai.brain", "Мозг");
        en.put("combatbot.ai.brain.0", "Legacy keywords");
        ru.put("combatbot.ai.brain.0", "Ключевые слова");
        en.put("combatbot.ai.brain.1", "AI 1-ply");
        ru.put("combatbot.ai.brain.1", "ИИ 1 ход");
        en.put("combatbot.ai.brain.2", "Expectiminimax");
        ru.put("combatbot.ai.brain.2", "Минимакс+шанс");
        en.put("combatbot.ai.agg", "Aggression");
        ru.put("combatbot.ai.agg", "Агрессия");
        en.put("combatbot.ai.depth", "Search depth");
        ru.put("combatbot.ai.depth", "Глубина поиска");
        en.put("combatbot.ai.risk", "Risk aversion");
        ru.put("combatbot.ai.risk", "Осторожность");
        en.put("combatbot.ai.hints", "Trust server slot hints");
        ru.put("combatbot.ai.hints", "Доверять подсказкам слотов");
        en.put("combatbot.ai.log", "Log decisions (~/.haven/moon-combat-ai.log)");
        ru.put("combatbot.ai.log", "Лог решений (~/.haven/moon-combat-ai.log)");
        en.put("opt.combatai.open", "Combat bot AI settings…");
        ru.put("opt.combatai.open", "Настройки ИИ автобоя…");
        en.put("opt.combatai.section", "Combat bot (auto-play cards)");
        ru.put("opt.combatai.section", "Автобой (карты)");
        en.put("opt.combatai.where", "Also: toolbar «Combat bot» icon, Bots window (C), or keybindings → MooNWide → Toggle combat bot.");
        ru.put("opt.combatai.where", "Ещё: иконка «Автобой» на панели инструментов, окно «Боты» (плитка C), привязки MooNWide.");
        en.put("opt.combatai.where.short", "Toolbar / Bots (C) / keybindings.");
        ru.put("opt.combatai.where.short", "Панель / Боты (C) / привязки.");
        en.put("combatbot.ai.analyze", "Analyze combat AI log");
        ru.put("combatbot.ai.analyze", "Разобрать лог ИИ боя");
        en.put("combatbot.ai.analyze.start", "Running log analyzer…");
        ru.put("combatbot.ai.analyze.start", "Запуск анализа лога…");
        en.put("combatbot.ai.analyze.busy", "Analyzer already running.");
        ru.put("combatbot.ai.analyze.busy", "Анализ уже выполняется.");
        en.put("combatbot.ai.analyze.done1", "Report (text):");
        ru.put("combatbot.ai.analyze.done1", "Отчёт (текст):");
        en.put("combatbot.ai.analyze.done2", "JSON report:");
        ru.put("combatbot.ai.analyze.done2", "Отчёт JSON:");
        en.put("combatbot.ai.analyze.nopy", "Python not found (try py -3, python3, or python on PATH).");
        ru.put("combatbot.ai.analyze.nopy", "Не найден Python (нужны py -3, python3 или python в PATH).");
        en.put("combatbot.ai.analyze.nolog", "Log file missing. Enable logging and fight once.");
        ru.put("combatbot.ai.analyze.nolog", "Нет файла лога. Включите лог и проведите бой.");
        en.put("combatbot.ai.analyze.timeout", "Analyzer timed out.");
        ru.put("combatbot.ai.analyze.timeout", "Анализ превысил время ожидания.");
        en.put("combatbot.ai.analyze.fail", "Analyzer failed.");
        ru.put("combatbot.ai.analyze.fail", "Ошибка анализа.");

        en.put("treebot.title", "Tree chop bot");
        ru.put("treebot.title", "Рубка деревьев");
        en.put("treebot.start", "Start tree bot");
        ru.put("treebot.start", "Запустить рубку");
        en.put("treebot.stop", "Stop tree bot");
        ru.put("treebot.stop", "Остановить рубку");
        en.put("treebot.need.chop", "Select a chop area on the map first (button below).");
        ru.put("treebot.need.chop", "Сначала задайте область рубки на карте (кнопка ниже).");
        en.put("treebot.hitstack", "Dense stacking (hitboxes)");
        ru.put("treebot.hitstack", "Плотная укладка (хитбоксы)");
        en.put("treebot.mobs", "Avoid / react to mobs");
        ru.put("treebot.mobs", "Избегать / реагировать на мобов");
        en.put("treebot.water", "Auto drink / refill water");
        ru.put("treebot.water", "Пить воду / поднимать запас");
        en.put("treebot.chop.hdr", "Chop area (tiles)");
        ru.put("treebot.chop.hdr", "Область рубки (тайлы)");
        en.put("treebot.chop.none", "Not set — drag on the map.");
        ru.put("treebot.chop.none", "Не задано — выделите на карте.");
        en.put("treebot.chop.line", "Area %s — ~%d tree(s) nearby");
        ru.put("treebot.chop.line", "Область %s — ~%d деревьев");
        en.put("treebot.chop.pick", "Select chop area…");
        ru.put("treebot.chop.pick", "Выбрать область рубки…");
        en.put("treebot.stock.hdr", "Stockpile area (tiles)");
        ru.put("treebot.stock.hdr", "Область складирования (тайлы)");
        en.put("treebot.stock.none", "Not set.");
        ru.put("treebot.stock.none", "Не задано.");
        en.put("treebot.stock.line", "Area %s");
        ru.put("treebot.stock.line", "Область %s");
        en.put("treebot.stock.pick", "Select stockpile…");
        ru.put("treebot.stock.pick", "Выбрать склад…");
        en.put("treebot.water.hdr", "Water (one tile)");
        ru.put("treebot.water.hdr", "Вода (один тайл)");
        en.put("treebot.water.none", "Not set — barrel, fill spot, or water tile.");
        ru.put("treebot.water.none", "Не задано — бочка, место набора или вода.");
        en.put("treebot.water.line", "Tile %d, %d");
        ru.put("treebot.water.line", "Тайл %d, %d");
        en.put("treebot.water.pick", "Select water tile…");
        ru.put("treebot.water.pick", "Выбрать тайл воды…");
        en.put("treebot.water.hint", "Assign where the bot drinks or refills (barrel with water, pond, etc.).");
        ru.put("treebot.water.hint", "Куда бот ходит пить или набирать воду (бочка, водоём и т.д.).");
        en.put("treebot.water.stock.hdr", "Water stock area (tiles)");
        ru.put("treebot.water.stock.hdr", "Область склада воды (тайлы)");
        en.put("treebot.water.stock.none", "Not set (optional).");
        ru.put("treebot.water.stock.none", "Не задано (необязательно).");
        en.put("treebot.water.stock.line", "Area %s");
        ru.put("treebot.water.stock.line", "Область %s");
        en.put("treebot.water.stock.pick", "Select water stock…");
        ru.put("treebot.water.stock.pick", "Выбрать склад воды…");
        en.put("treebot.split.enable", "Split to firewood (haul logs → output area)");
        ru.put("treebot.split.enable", "Разрубка на полена (брёвна → область выкладки)");
        en.put("treebot.split.log.hdr", "Logs to split (tiles)");
        ru.put("treebot.split.log.hdr", "Брёвна для разрубки (тайлы)");
        en.put("treebot.split.log.none", "Not set.");
        ru.put("treebot.split.log.none", "Не задано.");
        en.put("treebot.split.log.line", "Area %s");
        ru.put("treebot.split.log.line", "Область %s");
        en.put("treebot.split.log.pick", "Select log area…");
        ru.put("treebot.split.log.pick", "Область брёвен…");
        en.put("treebot.split.out.hdr", "Firewood / output (tiles)");
        ru.put("treebot.split.out.hdr", "Полена / выкладка (тайлы)");
        en.put("treebot.split.out.none", "Not set.");
        ru.put("treebot.split.out.none", "Не задано.");
        en.put("treebot.split.out.line", "Area %s");
        ru.put("treebot.split.out.line", "Область %s");
        en.put("treebot.split.out.pick", "Select output…");
        ru.put("treebot.split.out.pick", "Область выкладки…");
        en.put("treebot.need.split", "Enable split: set both log areas and output.");
        ru.put("treebot.need.split", "Разрубка: задайте обе области — брёвна и выкладку.");
        en.put("treebot.chop.first", "Chop all trees in the area first, then haul logs to stock");
        ru.put("treebot.chop.first", "Сначала дорубить деревья в зоне, потом таскать брёвна на склад");
        en.put("treebot.auto.pipeline", "Auto-advance phases (1→2→3)");
        ru.put("treebot.auto.pipeline", "Автопереход этапов (1→2→3)");
        en.put("treebot.hud.line", "Show status above player");
        ru.put("treebot.hud.line", "Статус над персонажем");
        en.put("treebot.phase.row", "Phase (manual)");
        ru.put("treebot.phase.row", "Этап (вручную)");
        en.put("treebot.phase.btn1", "P1 Fell");
        ru.put("treebot.phase.btn1", "П1 Рубка");
        en.put("treebot.spots.line", "Stand spots in prefs (world x y per line):");
        ru.put("treebot.spots.line", "Точки в prefs (мир x y, строка за строкой):");
        en.put("treebot.spot.added", "Stand point #%d: %s (Shift+Alt+LMB on map)");
        ru.put("treebot.spot.added", "Точка стояния #%d: %s (Shift+Alt+ЛКМ на карте)");
        en.put("treebot.reset", "Reset bot state (stuck chop / carry)");
        ru.put("treebot.reset", "Сброс состояния (зависла рубка / перенос)");
        en.put("treebot.reset.done", "Tree bot state cleared.");
        ru.put("treebot.reset.done", "Состояние бота сброшено.");
        en.put("treebot.stock.hint", "Set stockpile tiles so logs go into piles. Without it, carried logs are dropped at your feet.");
        ru.put("treebot.stock.hint", "Задайте тайлы склада — брёвна пойдут в стаки. Иначе перенос сбрасывается у ног.");
        en.put("treebot.limit.piles", "Full automation (RMB → build pile → place → fill grid) is not available in the client; haul drops on your stock tiles. Walk uses short steps, not maze pathfinding.");
        ru.put("treebot.limit.piles", "Полная автоматизация (ПКМ → пайл → установка → сетка заполнения) в клиенте недоступна; перенос — сброс на тайлы склада. Ходьба короткими шагами, без обхода лабиринтов.");
        en.put("treebot.phase1.done", "[Tree bot] Phase 1 done — felling finished. (Auto-pipeline starts phase 2 if enabled.)");
        ru.put("treebot.phase1.done", "[Рубка] Этап 1 завершён. (При автопереходе начнётся этап 2.)");
        en.put("treebot.phase2.done", "[Tree bot] Phase 2 done — hauling finished.");
        ru.put("treebot.phase2.done", "[Рубка] Этап 2 завершён — перенос окончен.");
        en.put("treebot.phase3.stub", "[Tree bot] Phase 3 (split / piles) is not fully automated yet.");
        ru.put("treebot.phase3.stub", "[Рубка] Этап 3 (разрубка / пайлы) пока не автоматизирован полностью.");
        en.put("treebot.phase3.done", "[Tree bot] Phase 3 done — firewood hauled to stock.");
        ru.put("treebot.phase3.done", "[Рубка] Этап 3 завершён — полена отнесены на склад.");
        en.put("treebot.phase3.needstock", "[Tree bot] Phase 3 needs a stock area — set stockpile tiles.");
        ru.put("treebot.phase3.needstock", "[Рубка] Этап 3: задайте область основного склада.");
        en.put("treebot.phase3.skip", "[Tree bot] Phase 3 skipped — split areas not configured.");
        ru.put("treebot.phase3.skip", "[Рубка] Этап 3 пропущен — не заданы области разрубки.");
        en.put("treebot.watchdog", "[Tree bot] No movement for a while — reset chop/carry state.");
        ru.put("treebot.watchdog", "[Рубка] Долго нет движения — сброс состояния рубки/переноса.");
        en.put("treebot.mob.flee", "[Tree bot] Moving away from a hostile mob.");
        ru.put("treebot.mob.flee", "[Рубка] Отход от агрессивного моба.");
        en.put("treebot.discord.hint", "Webhook URL (optional) for phase notifications.");
        ru.put("treebot.discord.hint", "URL вебхука Discord (необязательно) для оповещений об этапах.");
        en.put("treebot.phase.btn2", "Start phase 2 — haul to stock");
        ru.put("treebot.phase.btn2", "Этап 2 — перенос на склад");
        en.put("treebot.phase.btn3", "Start phase 3 — haul split output to stock");
        ru.put("treebot.phase.btn3", "Этап 3 — перенос поленьев с выкладки на склад");
        en.put("treebot.areas.clear", "Clear all tree-bot areas");
        ru.put("treebot.areas.clear", "Сбросить области рубки/склада");
        en.put("treebot.areas.cleared", "Tree-bot areas cleared.");
        ru.put("treebot.areas.cleared", "Области бота сброшены.");

        en.put("minebot.title", "Mine / cave sapper");
        ru.put("minebot.title", "Копка / сапёр");
        en.put("minebot.start", "Start mine bot");
        ru.put("minebot.start", "Запустить копку");
        en.put("minebot.stop", "Stop mine bot");
        ru.put("minebot.stop", "Остановить копку");
        en.put("minebot.need.area", "Select a dig area on the map first.");
        ru.put("minebot.need.area", "Сначала выберите область копки на карте.");
        en.put("minebot.autoviz", "On start: show cave memory labels");
        ru.put("minebot.autoviz", "При старте: показывать метки сапёра");
        en.put("minebot.labels", "Show small labels on cave cells");
        ru.put("minebot.labels", "Показывать маленькие цифры в шахте");
        en.put("minebot.mobs", "Avoid / react to mobs");
        ru.put("minebot.mobs", "Избегать мобов");
        en.put("minebot.water", "Auto drink from inventory (stamina)");
        ru.put("minebot.water", "Автопитьё из инвентаря (стамина)");
        en.put("minebot.hud.line", "Show status above player");
        ru.put("minebot.hud.line", "Статус над персонажем");
        en.put("minebot.loose", "Loose wall match (any damaged structure in area — risky)");
        ru.put("minebot.loose", "Широкий поиск стен (любая конструкция с HP в области — осторожно)");
        en.put("minebot.reset", "Reset stuck target");
        ru.put("minebot.reset", "Сбросить зависшую цель");
        en.put("minebot.reset.done", "Mine bot state cleared.");
        ru.put("minebot.reset.done", "Состояние бота копки сброшено.");
        en.put("minebot.manual.hdr", "Manual cave memory");
        ru.put("minebot.manual.hdr", "Ручная память сапёра");
        en.put("minebot.mark.safe", "Mark safe cells…");
        ru.put("minebot.mark.safe", "Отметить безопасные…");
        en.put("minebot.mark.risk", "Mark collapse cells…");
        ru.put("minebot.mark.risk", "Отметить обвальные…");
        en.put("minebot.mark.safe.done", "Safe cells saved: %d,%d -> %d,%d");
        ru.put("minebot.mark.safe.done", "Безопасные клетки сохранены: %d,%d -> %d,%d");
        en.put("minebot.mark.risk.done", "Collapse cells saved: %d,%d -> %d,%d");
        ru.put("minebot.mark.risk.done", "Обвальные клетки сохранены: %d,%d -> %d,%d");
        en.put("minebot.mark.risk.reset", "Reset collapse marks");
        ru.put("minebot.mark.risk.reset", "Сбросить обвальные метки");
        en.put("minebot.mark.risk.reset.done", "Marked collapse cells cleared.");
        ru.put("minebot.mark.risk.reset.done", "Помеченные обвальные клетки сброшены.");
        en.put("minebot.session", "Session...");
        ru.put("minebot.session", "РЎРµСЃСЃРёСЏ...");
        en.put("minebot.session.save", "Save this session");
        ru.put("minebot.session.save", "РЎРѕС…СЂР°РЅРёС‚СЊ СЌС‚Сѓ СЃРµСЃСЃРёСЋ");
        en.put("minebot.session.save.done", "Mine session saved.");
        ru.put("minebot.session.save.done", "РЎРµСЃСЃРёСЏ СЃР°РїС‘СЂР° СЃРѕС…СЂР°РЅРµРЅР°.");
        en.put("minebot.session.load", "Load saved session");
        ru.put("minebot.session.load", "Р—Р°РіСЂСѓР·РёС‚СЊ СЃРѕС…СЂР°РЅРµРЅРЅСѓСЋ СЃРµСЃСЃРёСЋ");
        en.put("minebot.session.load.done", "Saved mine session loaded.");
        ru.put("minebot.session.load.done", "РЎРѕС…СЂР°РЅРµРЅРЅР°СЏ СЃРµСЃСЃРёСЏ СЃР°РїС‘СЂР° Р·Р°РіСЂСѓР¶РµРЅР°.");
        en.put("minebot.session.export", "Export to clipboard");
        ru.put("minebot.session.export", "Р­РєСЃРїРѕСЂС‚ РІ Р±СѓС„РµСЂ");
        en.put("minebot.session.export.done", "Mine session copied to clipboard.");
        ru.put("minebot.session.export.done", "РЎРµСЃСЃРёСЏ СЃР°РїС‘СЂР° СЃРєРѕРїРёСЂРѕРІР°РЅР° РІ Р±СѓС„РµСЂ.");
        en.put("minebot.session.import", "Import from clipboard");
        ru.put("minebot.session.import", "РРјРїРѕСЂС‚ РёР· Р±СѓС„РµСЂР°");
        en.put("minebot.session.import.done", "Mine session imported from clipboard.");
        ru.put("minebot.session.import.done", "РЎРµСЃСЃРёСЏ СЃР°РїС‘СЂР° РёРјРїРѕСЂС‚РёСЂРѕРІР°РЅР° РёР· Р±СѓС„РµСЂР°.");
        en.put("minebot.session.show", "Show marks");
        ru.put("minebot.session.show", "РџРѕРєР°Р·Р°С‚СЊ РјРµС‚РєРё");
        en.put("minebot.session.hide", "Hide marks");
        ru.put("minebot.session.hide", "РЎРєСЂС‹С‚СЊ РјРµС‚РєРё");
        en.put("minebot.session.fail", "Mine session operation failed.");
        ru.put("minebot.session.fail", "РћРїРµСЂР°С†РёСЏ СЃРµСЃСЃРёРё СЃР°РїС‘СЂР° РЅРµ СѓРґР°Р»Р°СЃСЊ.");
        en.put("minebot.display.reset", "Hide labels only");
        ru.put("minebot.display.reset", "Скрыть только отображение");
        en.put("minebot.display.reset.done", "Labels hidden, cave memory kept.");
        ru.put("minebot.display.reset.done", "Цифры скрыты, память сапёра сохранена.");
        en.put("minebot.mode.hdr", "Target filter");
        ru.put("minebot.mode.hdr", "Фильтр целей");
        en.put("minebot.mode.0", "All walls");
        ru.put("minebot.mode.0", "Все стены");
        en.put("minebot.mode.1", "Supported only");
        ru.put("minebot.mode.1", "Только в зоне подпорки");
        en.put("minebot.mode.2", "Risk only (no support)");
        ru.put("minebot.mode.2", "Только обвальные (без подпорки)");
        en.put("minebot.area.hdr", "Dig area (tiles)");
        ru.put("minebot.area.hdr", "Область копки (тайлы)");
        en.put("minebot.area.none", "Not set — drag on the map.");
        ru.put("minebot.area.none", "Не задано — выделите на карте.");
        en.put("minebot.area.line", "Area %s");
        ru.put("minebot.area.line", "Область %s");
        en.put("minebot.area.pick", "Select dig area…");
        ru.put("minebot.area.pick", "Выбрать область копки…");
        en.put("minebot.hint", "Turn on the mining cursor on the action bar. The bot only digs mine walls inside the selected tile area.");
        ru.put("minebot.hint", "Включите на панели действий режим копки. Бот просто копает стенки в выбранной области тайлов.");
        en.put("minebot.hint2", "Manual labels are stored permanently: walls show 0/1, dug cells show the number of adjacent collapse cells.");
        ru.put("minebot.hint2", "Ручные метки хранятся постоянно: на стенах показывается 0/1, а на вскопанных клетках — число соседних обвальных клеток.");

        en.put("keybind.mw.tree-hitbox", "Global hitbox cycle (default Shift+B)");
        ru.put("keybind.mw.tree-hitbox", "Глобальные хитбоксы (по умолчанию Shift+B)");
        en.put("hitbox.mode.0", "off");
        ru.put("hitbox.mode.0", "выкл");
        en.put("hitbox.mode.1", "hitboxes");
        ru.put("hitbox.mode.1", "хитбоксы");
        en.put("hitbox.mode.2", "only hitboxes");
        ru.put("hitbox.mode.2", "только хитбоксы");
        en.put("map.grid.mode.0", "Tile grid: off");
        ru.put("map.grid.mode.0", "Сетка тайлов: выкл");
        en.put("map.grid.mode.1", "Tile grid: standard");
        ru.put("map.grid.mode.1", "Сетка тайлов: обычная");
        en.put("map.grid.mode.2", "Tile grid: thick (super)");
        ru.put("map.grid.mode.2", "Сетка тайлов: жирная (супер)");

        en.put("keybind.mw.entity-hitbox", "Entity hitboxes (structures, terrain outlines)");
        ru.put("keybind.mw.entity-hitbox", "Хитбоксы объектов (строения, контуры по земле)");
        en.put("hitbox.keybind.hint", "Hotkey: Options → Keybindings → MooNWide — Shift+B = global wireframes; entity/structures bind separately if needed.");
        ru.put("hitbox.keybind.hint", "Клавиша: Настройки → Привязки → MooNWide — Shift+B = глобальные контуры; строения — отдельная привязка.");
        en.put("entity.hitbox.mode.0", "off");
        ru.put("entity.hitbox.mode.0", "выкл");
        en.put("entity.hitbox.mode.1", "terrain outlines + visible");
        ru.put("entity.hitbox.mode.1", "контуры по земле + объекты");
        en.put("entity.hitbox.mode.2", "terrain outlines, hide meshes");
        ru.put("entity.hitbox.mode.2", "контуры, скрыть модели");
        en.put("move.trace.mode.pixel", "Path mode (Ctrl+Shift+G): exact click (pixels)");
        ru.put("move.trace.mode.pixel", "Режим пути (Ctrl+Shift+G): точный клик (пиксели)");
        en.put("move.trace.mode.center", "Path mode (Ctrl+Shift+G): tile centers");
        ru.put("move.trace.mode.center", "Режим пути (Ctrl+Shift+G): центры клеток");
        en.put("bots.combat.section", "Combat bot");
        ru.put("bots.combat.section", "Автобой");
        en.put("bots.combat.hint", "Chooses moves from cooldowns, IP (you vs opponent), resource name hints, and server suggestions (highlighted slots). Not a full rules clone of the server.");
        ru.put("bots.combat.hint", "Выбор хода по кулдаунам, IP (вы vs противник), эвристикам по имени карты и подсказкам сервера (подсвеченные слоты). Не полная копия серверных правил.");
        en.put("bots.tree.section", "Tree chop");
        ru.put("bots.tree.section", "Рубка деревьев");
        en.put("bots.tree", "Tree chop settings…");
        ru.put("bots.tree", "Настройки рубки…");
        en.put("shortcut.overlay", "Overlay");     ru.put("shortcut.overlay", "Оверлей");
        en.put("shortcut.combat", "Combat");       ru.put("shortcut.combat", "Бой");
        en.put("shortcut.combatbot", "Combat bot"); ru.put("shortcut.combatbot", "Автобой");
        en.put("shortcut.opts", "Settings");       ru.put("shortcut.opts", "Настройки");
        en.put("shortcut.sessions", "Sessions");   ru.put("shortcut.sessions", "Сессии");

        en.put("session.switch.title", "Sessions");
        ru.put("session.switch.title", "Сессии");
        en.put("session.list.title", "Sessions:");
        ru.put("session.list.title", "Сессии:");
        en.put("session.new", "New session");
        ru.put("session.new", "Новая сессия");
        en.put("session.spawn.fail", "Could not open a second session in this client.");
        ru.put("session.spawn.fail", "Не удалось открыть вторую сессию в этом клиенте.");
        en.put("session.switch.hint", "Reconnect with a saved account (from the login list). Current session closes.");
        ru.put("session.switch.hint", "Переподключение к сохранённому аккаунту (как в списке на экране входа). Текущая сессия закроется.");
        en.put("session.switch.hint2", "New session opens inside this window (no second client). Use the list to switch to an account that is already logged in, or open another login. Accounts stay in the list with Remember me.");
        ru.put("session.switch.hint2", "Новая сессия открывается в этом же окне (отдельный клиент не запускается). Клик по аккаунту в списке переключает на уже открытую сессию или открывает вход. С «Запомнить» аккаунты остаются в списке.");
        en.put("session.switch.empty", "No saved accounts. Log in once from the login screen and choose “Remember me” to add one.");
        ru.put("session.switch.empty", "Нет сохранённых аккаунтов. Войдите с экрана входа и включите «Запомнить», чтобы добавить.");
        en.put("session.switch.other", "Other account (login screen)…");
        ru.put("session.switch.other", "Другой аккаунт (экран входа)…");
        en.put("session.switch.open", "Open session switcher");
        ru.put("session.switch.open", "Переключение сессий");
        en.put("session.switch.steamonly", "Session switching is only available with native (username) login, not Steam.");
        ru.put("session.switch.steamonly", "Переключение сессий доступно только при входе по логину/паролю, не через Steam.");

        en.put("login.sessions.add", "Add account…");
        ru.put("login.sessions.add", "Добавить аккаунт…");
        en.put("login.opt", "Settings");
        ru.put("login.opt", "Настройки");
        en.put("login.user", "Username");
        ru.put("login.user", "Имя пользователя");
        en.put("login.pass", "Password");
        ru.put("login.pass", "Пароль");
        en.put("login.remember", "Remember me");
        ru.put("login.remember", "Запомнить");
        en.put("login.remember.tip", "Saves a login token (not your password). Manage tokens in account settings on the server.");
        ru.put("login.remember.tip", "Сохраняется токен входа (не пароль). Управление — в настройках аккаунта на сервере.");
        en.put("login.saved", "Login saved");
        ru.put("login.saved", "Вход сохранён");
        en.put("login.forget", "Forget me");
        ru.put("login.forget", "Забыть меня");
        en.put("login.submit", "Sign in");
        ru.put("login.submit", "Войти");
        en.put("login.steam.title", "Logging in with Steam");
        ru.put("login.steam.title", "Вход через Steam");
        en.put("login.steam.go", "Continue");
        ru.put("login.steam.go", "Продолжить");
        en.put("login.status.check", "Server status: Checking…");
        ru.put("login.status.check", "Сервер: проверка…");
        en.put("login.status.up", "Server status: Up");
        ru.put("login.status.up", "Сервер: работает");
        en.put("login.status.players", "Hearthlings online: %,d");
        ru.put("login.status.players", "Игроков онлайн: %,d");
        en.put("login.status.down", "Server status: Down");
        ru.put("login.status.down", "Сервер: недоступен");
        en.put("login.status.shutting", "Server status: Shutting down");
        ru.put("login.status.shutting", "Сервер: отключается");
        en.put("login.status.crashed", "Server status: Crashed");
        ru.put("login.status.crashed", "Сервер: сбой");
        en.put("charlist.play", "Enter world");
        ru.put("charlist.play", "Войти в мир");

        en.put("shortcut.settings", "Panel settings");  ru.put("shortcut.settings", "Настройки панели");
        en.put("shortcut.layout", "Layout");             ru.put("shortcut.layout", "Развёрстка");
        en.put("shortcut.orientation", "View");          ru.put("shortcut.orientation", "Вид");
        en.put("shortcut.horizontal", "Horizontal");     ru.put("shortcut.horizontal", "Горизонтально");
        en.put("shortcut.vertical", "Vertical");         ru.put("shortcut.vertical", "Вертикально");
        en.put("shortcut.reset_pos", "Reset position");  ru.put("shortcut.reset_pos", "Сбросить позицию");

        en.put("moonmisc.settings", "Belt / keyring");
        ru.put("moonmisc.settings", "Сумка на поясе / ключница");
        en.put("moonmisc.resetpos", "Tap: center window");
        ru.put("moonmisc.resetpos", "Нажми: по центру экрана");
        en.put("moonmisc.hint", "Grid size is set by the server. For modular hotbars (layout, pages), use the MooNWide belt panels (numpad / F-keys / numpad belts) in options.");
        ru.put("moonmisc.hint", "Сетка задаётся сервером. Модульные панели быстрого доступа (разметка, страницы) — внизу: панели MooNWide (цифры / F / Num) и в настройках.");
        en.put("belt.panel.num", "Belt 1–0");     ru.put("belt.panel.num", "Панель 1–0");
        en.put("belt.panel.f", "Belt F1–F12");   ru.put("belt.panel.f", "Панель F1–F12");
        en.put("belt.panel.numpad", "Belt Num"); ru.put("belt.panel.numpad", "Панель Num");
        en.put("belt.pageabbr.num", "1–0");       ru.put("belt.pageabbr.num", "1–0");
        en.put("belt.pageabbr.f", "F");          ru.put("belt.pageabbr.f", "F");
        en.put("belt.pageabbr.numpad", "Num");   ru.put("belt.pageabbr.numpad", "Num");

        en.put("hands.toolbar", "Hands"); ru.put("hands.toolbar", "Руки");
        en.put("hands.main", "Main hand"); ru.put("hands.main", "Основная рука");
        en.put("hands.off", "Off hand"); ru.put("hands.off", "Вторая рука");

        en.put("on", "ON"); ru.put("on", "ВКЛ");
        en.put("off", "OFF"); ru.put("off", "ВЫКЛ");
        en.put("enabled", "Enabled"); ru.put("enabled", "Включено");
        en.put("disabled", "Disabled"); ru.put("disabled", "Выключено");

        en.put("moon.kb.on", "On"); ru.put("moon.kb.on", "Вкл");
        en.put("moon.kb.off", "Off"); ru.put("moon.kb.off", "Выкл");
        en.put("moon.connect.socks.no_udp", "SOCKS proxy does not support UDP (game needs SOCKS5 UDP ASSOCIATE). Use another proxy with UDP (Dante, 3proxy, …) or full VPN, or clear the game chain in Options → Integrations.");
        ru.put("moon.connect.socks.no_udp", "Прокси SOCKS не поддерживает UDP (игре нужен SOCKS5 UDP ASSOCIATE). Нужен другой прокси с UDP (Dante, 3proxy, …) или полноценный VPN, либо очистите цепочку игры в Настройки → Интеграции.");
        en.put("moon.connect.game.unreachable", "Could not connect to game server. Check firewall and that the network allows UDP.");
        ru.put("moon.connect.game.unreachable", "Не удалось подключиться к игровому серверу. Проверьте файрвол и что сеть пропускает UDP.");
        en.put("moon.connect.game.noserver", "Could not connect to server");
        ru.put("moon.connect.game.noserver", "Не удалось подключиться к серверу");

        en.put("keybind.mw.esp", "Toggle ESP overlay"); ru.put("keybind.mw.esp", "ESP оверлей");
        en.put("keybind.mw.xray", "Toggle X-Ray"); ru.put("keybind.mw.xray", "X-Ray");
        en.put("keybind.mw.trace-hostile", "Toggle trace: hostile"); ru.put("keybind.mw.trace-hostile", "Трейс: враждебные");
        en.put("keybind.mw.trace-neutral", "Toggle trace: neutral mobs"); ru.put("keybind.mw.trace-neutral", "Трейс: нейтральные мобы");
        en.put("keybind.mw.trace-players", "Toggle trace: players"); ru.put("keybind.mw.trace-players", "Трейс: игроки");
        en.put("keybind.mw.autodrink", "Toggle auto-drink"); ru.put("keybind.mw.autodrink", "Автопитьё");
        en.put("keybind.mw.combatbot", "Toggle combat bot"); ru.put("keybind.mw.combatbot", "Автобой");
        en.put("keybind.mw.aggro", "Toggle aggro radius"); ru.put("keybind.mw.aggro", "Радиус агра");
        en.put("keybind.mw.speedboost", "Toggle speed boost"); ru.put("keybind.mw.speedboost", "Ускорение ходьбы");
        en.put("keybind.mw.flatterrain", "Toggle flat terrain"); ru.put("keybind.mw.flatterrain", "Плоская местность");
        en.put("keybind.mw.daylight", "Toggle always daylight"); ru.put("keybind.mw.daylight", "Всегда день");
        en.put("keybind.mw.qual-objs", "Toggle item quality overlay"); ru.put("keybind.mw.qual-objs", "Качество предметов");
        en.put("keybind.mw.crop-hide", "Toggle crop hide"); ru.put("keybind.mw.crop-hide", "Скрыть посевы");
        en.put("keybind.mw.move-trace-mode", "Toggle path mode"); ru.put("keybind.mw.move-trace-mode", "Режим пути");

        en.put("opt.window.title", "Options"); ru.put("opt.window.title", "Настройки");
        en.put("opt.deco.title", "Options"); ru.put("opt.deco.title", "Настройки");
        en.put("opt.camera.btn", "Camera"); ru.put("opt.camera.btn", "Камера");
        en.put("opt.vanilla.video.title", "Video Settings"); ru.put("opt.vanilla.video.title", "Видео");
        en.put("opt.vanilla.video.intro", "Renderer settings now rebuild safely instead of crashing the client when the graphics profile changes.");
        ru.put("opt.vanilla.video.intro", "Настройки рендера пересобираются безопасно — смена профиля графики не должна ронять клиент.");
        en.put("opt.vanilla.shadows", "Render shadows"); ru.put("opt.vanilla.shadows", "Тени");
        en.put("opt.vanilla.vsync", "Vertical sync"); ru.put("opt.vanilla.vsync", "Вертикальная синхронизация");
        en.put("opt.vanilla.render_scale", "Render scale"); ru.put("opt.vanilla.render_scale", "Масштаб рендера");
        en.put("opt.vanilla.fps_active", "FPS limit (active window)"); ru.put("opt.vanilla.fps_active", "Лимит FPS (активное окно)");
        en.put("opt.vanilla.fps_bg", "FPS limit (background window)"); ru.put("opt.vanilla.fps_bg", "Лимит FPS (фоновое окно)");
        en.put("opt.vanilla.fps_unlimited", "Unlimited"); ru.put("opt.vanilla.fps_unlimited", "Без лимита");
        en.put("opt.vanilla.fps_fmt", "%d fps"); ru.put("opt.vanilla.fps_fmt", "%d к/с");
        en.put("opt.vanilla.lighting_mode", "Lighting mode"); ru.put("opt.vanilla.lighting_mode", "Режим освещения");
        en.put("opt.vanilla.light.global", "Global"); ru.put("opt.vanilla.light.global", "Глобально");
        en.put("opt.vanilla.light.zoned", "Zoned"); ru.put("opt.vanilla.light.zoned", "По зонам");
        en.put("opt.vanilla.light_limit", "Light-source limit"); ru.put("opt.vanilla.light_limit", "Лимит источников света");
        en.put("opt.vanilla.frame_sync", "Frame sync mode"); ru.put("opt.vanilla.frame_sync", "Режим синхронизации кадров");
        en.put("opt.vanilla.sync.hint", "Higher entries reduce latency, lower ones reduce CPU/GPU contention.");
        ru.put("opt.vanilla.sync.hint", "Верхние пункты — меньше задержка, нижние — меньше нагрузка на CPU/GPU.");
        en.put("opt.vanilla.sync.0", "One-frame overlap"); ru.put("opt.vanilla.sync.0", "Перекрытие на кадр");
        en.put("opt.vanilla.sync.1", "Tick overlap"); ru.put("opt.vanilla.sync.1", "Перекрытие тиков");
        en.put("opt.vanilla.sync.2", "CPU-sequential"); ru.put("opt.vanilla.sync.2", "Последовательно (CPU)");
        en.put("opt.vanilla.sync.3", "GPU-sequential"); ru.put("opt.vanilla.sync.3", "Последовательно (GPU)");
        en.put("opt.vanilla.reset_renderer", "Reset renderer defaults"); ru.put("opt.vanilla.reset_renderer", "Сброс видео");
        en.put("opt.vanilla.video_err", "Video settings are temporarily unavailable.");
        ru.put("opt.vanilla.video_err", "Видеонастройки временно недоступны.");
        en.put("opt.vanilla.video_err2", "The panel stayed alive; open it again after changing renderer options.");
        ru.put("opt.vanilla.video_err2", "Панель осталась открытой; откройте снова после смены параметров рендера.");
        en.put("opt.vanilla.audio.title", "Audio Settings"); ru.put("opt.vanilla.audio.title", "Аудио");
        en.put("opt.vanilla.audio.intro", "Core sound controls, music toggle and low-latency tuning.");
        ru.put("opt.vanilla.audio.intro", "Громкость, музыка и задержка звука.");
        en.put("opt.vanilla.vol.master", "Master audio volume"); ru.put("opt.vanilla.vol.master", "Общая громкость");
        en.put("opt.vanilla.vol.ui", "Interface sound volume"); ru.put("opt.vanilla.vol.ui", "Звуки интерфейса");
        en.put("opt.vanilla.vol.event", "In-game event volume"); ru.put("opt.vanilla.vol.event", "События в игре");
        en.put("opt.vanilla.vol.ambient", "Ambient volume"); ru.put("opt.vanilla.vol.ambient", "Окружение");
        en.put("opt.vanilla.music", "Enable background music"); ru.put("opt.vanilla.music", "Фоновая музыка");
        en.put("opt.vanilla.audio_latency", "Audio latency"); ru.put("opt.vanilla.audio_latency", "Задержка аудио");
        en.put("opt.vanilla.audio_latency.hint", "Larger buffers can fix crackling, smaller buffers lower delay.");
        ru.put("opt.vanilla.audio_latency.hint", "Больше буфер — меньше треск; меньше буфер — меньше задержка.");
        en.put("opt.vanilla.display.title", "Display & HUD"); ru.put("opt.vanilla.display.title", "Экран и HUD");
        en.put("opt.vanilla.display.intro", "Interface scale, window theme and movable HUD behavior.");
        ru.put("opt.vanilla.display.intro", "Масштаб интерфейса, тема окон и поведение перетаскиваемого HUD.");
        en.put("opt.vanilla.uiscale", "Interface scale (requires restart)"); ru.put("opt.vanilla.uiscale", "Масштаб UI (нужен перезапуск)");
        en.put("opt.vanilla.theme_min", "Minimal window theme"); ru.put("opt.vanilla.theme_min", "Минималистичная тема окон");
        en.put("opt.vanilla.simple_drag", "Simple drag for movable HUD widgets"); ru.put("opt.vanilla.simple_drag", "Простое перетаскивание виджетов HUD");
        en.put("opt.vanilla.compact_meters", "Compact/minimal meters"); ru.put("opt.vanilla.compact_meters", "Компактные индикаторы");
        en.put("opt.vanilla.meter_text", "Show meter text"); ru.put("opt.vanilla.meter_text", "Текст на индикаторах");
        en.put("opt.vanilla.meter_trans", "Meter transparency"); ru.put("opt.vanilla.meter_trans", "Прозрачность индикаторов");
        en.put("opt.vanilla.chat_bg_opacity", "Chat background opacity"); ru.put("opt.vanilla.chat_bg_opacity", "Прозрачность фона чата");
        en.put("opt.vanilla.minimap_startup", "Show minimap on startup"); ru.put("opt.vanilla.minimap_startup", "Миникарта при запуске");
        en.put("opt.vanilla.camera.title", "Camera & Placement"); ru.put("opt.vanilla.camera.title", "Камера и размещение");
        en.put("opt.vanilla.camera.intro", "Default camera profile and placement snapping controls.");
        ru.put("opt.vanilla.camera.intro", "Камера по умолчанию и шаги при размещении объектов.");
        en.put("opt.vanilla.default_cam", "Default camera"); ru.put("opt.vanilla.default_cam", "Камера по умолчанию");
        en.put("opt.vanilla.cam.ortho", "Ortho camera"); ru.put("opt.vanilla.cam.ortho", "Ортографическая");
        en.put("opt.vanilla.cam.follow", "Follow camera"); ru.put("opt.vanilla.cam.follow", "Следующая");
        en.put("opt.vanilla.cam.free", "Free camera"); ru.put("opt.vanilla.cam.free", "Свободная");
        en.put("opt.vanilla.cam.legacy", "Legacy simple camera"); ru.put("opt.vanilla.cam.legacy", "Упрощённая (старая)");
        en.put("opt.vanilla.place_gran", "Placement position granularity"); ru.put("opt.vanilla.place_gran", "Шаг позиции при размещении");
        en.put("opt.vanilla.place_angle", "Placement angle step"); ru.put("opt.vanilla.place_angle", "Шаг угла при размещении");
        en.put("opt.vanilla.reset_cam", "Reset camera defaults"); ru.put("opt.vanilla.reset_cam", "Сброс камеры");
        en.put("opt.kb.help", "$col[255,255,0]{Escape}: Cancel input\n$col[255,255,0]{Backspace}: Revert to default\n$col[255,255,0]{Delete}: Disable keybinding");
        ru.put("opt.kb.help", "$col[255,255,0]{Escape}: отмена\n$col[255,255,0]{Backspace}: по умолчанию\n$col[255,255,0]{Delete}: отключить привязку");
        en.put("opt.kb.click_other", "Click another element..."); ru.put("opt.kb.click_other", "Кликните другой элемент…");
        en.put("opt.kb.section.main", "Main menu"); ru.put("opt.kb.section.main", "Главное меню");
        en.put("opt.kb.section.mapopts", "Map options"); ru.put("opt.kb.section.mapopts", "Карта");
        en.put("opt.kb.section.cam", "Camera control"); ru.put("opt.kb.section.cam", "Камера");
        en.put("opt.kb.section.mapwnd", "Map window"); ru.put("opt.kb.section.mapwnd", "Окно карты");
        en.put("opt.kb.section.speed", "Walking speed"); ru.put("opt.kb.section.speed", "Скорость ходьбы");
        en.put("opt.kb.section.combat", "Combat actions"); ru.put("opt.kb.section.combat", "Боевые действия");
        en.put("opt.kb.section.mw", "MooNWide (toggles)"); ru.put("opt.kb.section.mw", "MooNWide (переключатели)");
        en.put("opt.kb.inv", "Inventory"); ru.put("opt.kb.inv", "Инвентарь");
        en.put("opt.kb.equ", "Equipment"); ru.put("opt.kb.equ", "Снаряжение");
        en.put("opt.kb.chr", "Character sheet"); ru.put("opt.kb.chr", "Персонаж");
        en.put("opt.kb.map", "Map window"); ru.put("opt.kb.map", "Карта");
        en.put("opt.kb.bud", "Kith & Kin"); ru.put("opt.kb.bud", "Родня и друзья");
        en.put("opt.kb.opt", "Options"); ru.put("opt.kb.opt", "Настройки");
        en.put("opt.kb.srch", "Search actions"); ru.put("opt.kb.srch", "Поиск действий");
        en.put("opt.kb.chat", "Toggle chat"); ru.put("opt.kb.chat", "Чат вкл/выкл");
        en.put("opt.kb.quick", "Quick chat"); ru.put("opt.kb.quick", "Быстрый чат");
        en.put("opt.kb.shoot", "Take screenshot"); ru.put("opt.kb.shoot", "Скриншот");
        en.put("opt.kb.ico", "Minimap icons"); ru.put("opt.kb.ico", "Иконки миникарты");
        en.put("opt.kb.hide", "Toggle UI"); ru.put("opt.kb.hide", "Интерфейс вкл/выкл");
        en.put("opt.kb.logout", "Log out"); ru.put("opt.kb.logout", "Выход");
        en.put("opt.kb.switchchr", "Switch character"); ru.put("opt.kb.switchchr", "Сменить персонажа");
        en.put("opt.kb.claim", "Display claims"); ru.put("opt.kb.claim", "Показать клеймы");
        en.put("opt.kb.vil", "Display villages"); ru.put("opt.kb.vil", "Деревни");
        en.put("opt.kb.rlm", "Display realms"); ru.put("opt.kb.rlm", "Владения");
        en.put("opt.kb.grid", "Display grid-lines"); ru.put("opt.kb.grid", "Сетка на карте");
        en.put("opt.kb.camleft", "Rotate left"); ru.put("opt.kb.camleft", "Поворот влево");
        en.put("opt.kb.camright", "Rotate right"); ru.put("opt.kb.camright", "Поворот вправо");
        en.put("opt.kb.camin", "Zoom in"); ru.put("opt.kb.camin", "Приблизить");
        en.put("opt.kb.camout", "Zoom out"); ru.put("opt.kb.camout", "Отдалить");
        en.put("opt.kb.camreset", "Reset"); ru.put("opt.kb.camreset", "Сброс");
        en.put("opt.kb.mhome", "Reset view"); ru.put("opt.kb.mhome", "Сброс вида");
        en.put("opt.kb.mmark", "Place marker"); ru.put("opt.kb.mmark", "Метка");
        en.put("opt.kb.mhmark", "Toggle markers"); ru.put("opt.kb.mhmark", "Метки вкл/выкл");
        en.put("opt.kb.mcompact", "Compact mode"); ru.put("opt.kb.mcompact", "Компактный режим");
        en.put("opt.kb.speedup", "Increase speed"); ru.put("opt.kb.speedup", "Быстрее");
        en.put("opt.kb.speeddn", "Decrease speed"); ru.put("opt.kb.speeddn", "Медленнее");
        en.put("opt.kb.speedset", "Set speed %d"); ru.put("opt.kb.speedset", "Скорость %d");
        en.put("opt.kb.fightact", "Combat action %d"); ru.put("opt.kb.fightact", "Боевое действие %d");
        en.put("opt.kb.relcycle", "Switch targets"); ru.put("opt.kb.relcycle", "Смена цели");
        en.put("opt.moonchat.title", "Chat"); ru.put("opt.moonchat.title", "Чат");
        en.put("opt.moonchat.intro", "Windowed chat lives inside the HUD again. Size, opacity and startup behavior are stored immediately.");
        ru.put("opt.moonchat.intro", "Окно чата в HUD. Размер, прозрачность и показ при запуске сохраняются сразу.");
        en.put("opt.moonchat.startup", "Show chat on startup"); ru.put("opt.moonchat.startup", "Показывать чат при запуске");
        en.put("opt.moonchat.width", "Chat width"); ru.put("opt.moonchat.width", "Ширина чата");
        en.put("opt.moonchat.height", "Chat height"); ru.put("opt.moonchat.height", "Высота чата");
        en.put("opt.moonchat.opacity", "Chat opacity"); ru.put("opt.moonchat.opacity", "Прозрачность чата");
        en.put("opt.moonchat.reset_size", "Reset chat size"); ru.put("opt.moonchat.reset_size", "Сброс размера чата");
        en.put("opt.moonchat.reset_pos", "Reset chat position"); ru.put("opt.moonchat.reset_pos", "Сброс позиции чата");
        en.put("opt.moonauto.title", "Automation"); ru.put("opt.moonauto.title", "Автоматизация");
        en.put("opt.moonauto.intro", "MooNWide automation controls, rebuilt around MooNWide modules and current client hooks.");
        ru.put("opt.moonauto.intro", "Автоматизация MooNWide: модули и хуки клиента.");
        en.put("opt.mod.autodrink", "Auto Drink/Eat"); ru.put("opt.mod.autodrink", "Автопитьё / еда");
        en.put("opt.mod.autodrink.tip", "Automatic upkeep thresholds."); ru.put("opt.mod.autodrink.tip", "Пороги жажды и голода.");
        en.put("opt.mod.taskq", "Task Queue"); ru.put("opt.mod.taskq", "Очередь задач");
        en.put("opt.mod.taskq.tip", "Queued actions executed by AutoRunner."); ru.put("opt.mod.taskq.tip", "Задачи выполняет AutoRunner.");
        en.put("opt.mod.smarthk", "Smart Hotkeys"); ru.put("opt.mod.smarthk", "Умные горячие клавиши");
        en.put("opt.mod.smarthk.tip", "Extra MooNWide keybind actions."); ru.put("opt.mod.smarthk.tip", "Доп. действия по клавишам MooNWide.");
        en.put("opt.automation.queue_fmt", "Queued tasks: %d%s"); ru.put("opt.automation.queue_fmt", "В очереди задач: %d%s");
        en.put("opt.automation.next", " | Next: "); ru.put("opt.automation.next", " | Далее: ");
        en.put("opt.moonauto.drink", "Drink threshold"); ru.put("opt.moonauto.drink", "Порог питья");
        en.put("opt.moonauto.eat", "Eat threshold"); ru.put("opt.moonauto.eat", "Порог еды");
        en.put("opt.moonauto.clearq", "Clear queued tasks"); ru.put("opt.moonauto.clearq", "Очистить очередь");
        en.put("opt.moonoverlay.title", "World Overlay"); ru.put("opt.moonoverlay.title", "Оверлей мира");
        en.put("opt.moonoverlay.intro", "Visibility and cleanup tools kept in one place instead of a floating control center.");
        ru.put("opt.moonoverlay.intro", "Видимость и уборка интерфейса в одном месте.");
        en.put("opt.mod.uiclean", "UI Cleaner"); ru.put("opt.mod.uiclean", "Очистка UI");
        en.put("opt.mod.uiclean.tip", "Compact MooNWide HUD helpers."); ru.put("opt.mod.uiclean.tip", "Компактный HUD MooNWide.");
        en.put("opt.mod.pathhl", "Path Highlighter"); ru.put("opt.mod.pathhl", "Подсветка пути");
        en.put("opt.mod.pathhl.tip", "Shows the current movement path."); ru.put("opt.mod.pathhl.tip", "Текущий путь движения.");
        en.put("opt.mod.radar", "Resource Radar"); ru.put("opt.mod.radar", "Радар ресурсов");
        en.put("opt.mod.radar.tip", "Tracks nearby resources."); ru.put("opt.mod.radar.tip", "Ресурсы поблизости.");
        en.put("opt.moonoverlay.hideclutter", "Hide clutter in MooNWide HUD"); ru.put("opt.moonoverlay.hideclutter", "Скрыть лишнее в HUD MooNWide");
        en.put("opt.moonoverlay.compact", "Compact bars and compact panels"); ru.put("opt.moonoverlay.compact", "Компактные панели");
        en.put("opt.moonoverlay.radar_dist", "Radar distance"); ru.put("opt.moonoverlay.radar_dist", "Дальность радара");
        en.put("opt.vanilla.tiles_fmt", "%d tiles"); ru.put("opt.vanilla.tiles_fmt", "%d кл.");
        en.put("opt.moonoverlay.reset_hud", "Reset HUD positions"); ru.put("opt.moonoverlay.reset_hud", "Сброс позиций HUD");
        en.put("opt.mooncombat.title", "Combat"); ru.put("opt.mooncombat.title", "Бой");
        en.put("opt.mooncombat.intro", "Combat helpers and live readouts, separated from the base client options noise.");
        ru.put("opt.mooncombat.intro", "Помощники боя и показатели, отдельно от прочих настроек.");
        en.put("opt.mooncombat.suggest_fmt", "Suggestion: %s"); ru.put("opt.mooncombat.suggest_fmt", "Подсказка: %s");
        en.put("opt.mooncombat.idle", "Idle"); ru.put("opt.mooncombat.idle", "Ожидание");
        en.put("opt.mooncombat.stamina_fmt", "Stamina %.0f%% | Energy %.0f%%"); ru.put("opt.mooncombat.stamina_fmt", "Стамина %.0f%% | Энергия %.0f%%");
        en.put("opt.mooncombat.cooldown", "Attack cooldown"); ru.put("opt.mooncombat.cooldown", "Кулдаун атаки");
        en.put("opt.mooncombat.mapguides", "Map guides"); ru.put("opt.mooncombat.mapguides", "Подсказки на карте");
        en.put("opt.mooncombat.chasevec", "Homing / chase lines (self / party / others)");
        ru.put("opt.mooncombat.chasevec", "Линии преследования (я / группа / остальные)");
        en.put("opt.mooncombat.clickpath", "Line to last ground click while moving");
        ru.put("opt.mooncombat.clickpath", "Линия к последнему клику по земле при движении");
        en.put("opt.mooncombat.mapguides.note", "Uses Homing attributes and LinMove; client-side only.");
        ru.put("opt.mooncombat.mapguides.note", "По атрибутам Homing и LinMove; только на клиенте.");
        en.put("opt.mod.combatov", "Combat Overlay"); ru.put("opt.mod.combatov", "Боевой оверлей");
        en.put("opt.mod.combatov.tip", "Suggestions, stamina and cooldown helpers."); ru.put("opt.mod.combatov.tip", "Подсказки, стамина, кулдауны.");
        en.put("opt.perf.title", "Performance"); ru.put("opt.perf.title", "Производительность");
        en.put("opt.perf.intro", "Quick rendering controls: useful sliders first, without a giant legacy tree.");
        ru.put("opt.perf.intro", "Быстрые настройки рендера без лишнего дерева опций.");
        en.put("opt.perf.section.gfx", "--- Modular graphics (live) ---"); ru.put("opt.perf.section.gfx", "--- Модульная графика (живьём) ---");
        en.put("opt.perf.section.hud", "--- HUD & Scanning ---"); ru.put("opt.perf.section.hud", "--- HUD и сканирование ---");
        en.put("opt.perf.espdraw", "ESP/trace overlay draw"); ru.put("opt.perf.espdraw", "Отрисовка ESP/трейса");
        en.put("opt.perf.xraymod", "X-Ray module"); ru.put("opt.perf.xraymod", "Модуль X-Ray");
        en.put("opt.perf.hitboxmod", "Global hitboxes module"); ru.put("opt.perf.hitboxmod", "Глобальные хитбоксы");
        en.put("opt.perf.miningov", "Mining safe-tiles overlay"); ru.put("opt.perf.miningov", "Оверлей безопасных клеток (шахта)");
        en.put("opt.perf.fighthud", "Fight HUD overlay"); ru.put("opt.perf.fighthud", "Оверлей боя");
        en.put("opt.perf.activityhud", "Activity HUD overlay"); ru.put("opt.perf.activityhud", "Оверлей активности");
        en.put("opt.perf.fpsping", "FPS and ping overlay"); ru.put("opt.perf.fpsping", "Оверлей FPS и пинга");
        en.put("opt.perf.fpsping.note", "FPS; RTT from ACKs; q:N = swap backlog.");
        ru.put("opt.perf.fpsping.note", "FPS; пинг по ACK; q:N — очередь кадров.");
        en.put("opt.perf.hideclutter", "Hide clutter in MooNWide HUD"); ru.put("opt.perf.hideclutter", "Скрыть лишнее в HUD MooNWide");
        en.put("opt.perf.compactbars", "Compact MooNWide bars"); ru.put("opt.perf.compactbars", "Компактные панели MooNWide");
        en.put("opt.perf.simpledrag", "Simple drag for movable widgets"); ru.put("opt.perf.simpledrag", "Простое перетаскивание виджетов");
        en.put("opt.perf.minmeters", "Minimal meters"); ru.put("opt.perf.minmeters", "Минимальные индикаторы");
        en.put("opt.perf.fepmeter", "Show FEP meter on HUD"); ru.put("opt.perf.fepmeter", "Показывать FEP на HUD");
        en.put("opt.perf.metertext", "Show meter text"); ru.put("opt.perf.metertext", "Текст на индикаторах");
        en.put("opt.perf.radar_dist", "Resource radar distance"); ru.put("opt.perf.radar_dist", "Дальность радара ресурсов");
        en.put("opt.integ.title", "System & Integrations"); ru.put("opt.integ.title", "Система и интеграции");
        en.put("opt.integ.intro", "Global behavior toggles plus saved connector settings.");
        ru.put("opt.integ.intro", "Поведение клиента и сохранённые подключения.");
        en.put("opt.integ.dpi.all.hdr", "All outbound TCP — DPI bypass");
        ru.put("opt.integ.dpi.all.hdr", "Весь исходящий TCP — обход DPI");
        en.put("opt.integ.dpi.all.master", "Master: login HOBF + split first bytes on every TCP connection (proxies, direct)");
        ru.put("opt.integ.dpi.all.master", "Главный переключатель: HOBF при входе + дробление первых байт на каждом TCP (прокси и напрямую)");
        en.put("opt.integ.dpi.all.budget", "TCP split budget (bytes per connection, 0 = split off)");
        ru.put("opt.integ.dpi.all.budget", "Лимит дробления TCP (байт на соединение, 0 = без дробления)");
        en.put("opt.integ.dpi.hdr", "Auth server (login) — extra DPI workarounds");
        ru.put("opt.integ.dpi.hdr", "Сервер авторизации — доп. обход DPI");
        en.put("opt.integ.dpi.note", "When the master switch above is on, login already uses HOBF and TCP splitting; the checkboxes below apply only if master is off (e.g. login-only tweaks). Off by default: stock-like login.");
        ru.put("opt.integ.dpi.note", "Если включён главный переключатель выше, вход уже с HOBF и дроблением TCP; галочки ниже имеют смысл при выключенном главном (только вход). По умолчанию — как обычный клиент.");
        en.put("opt.integ.dpi.obf", "Login: try HOBF obfuscation before plain TLS (ISP workaround)");
        ru.put("opt.integ.dpi.obf", "Вход: сначала HOBF, затем обычный TLS (обход у провайдера)");
        en.put("opt.integ.dpi.split", "Login: split first TLS writes into small segments (ISP workaround)");
        ru.put("opt.integ.dpi.split", "Вход: дробить первые байты TLS (обход у провайдера)");
        en.put("opt.integ.netproxy.hdr", "Proxy — all Haven traffic");
        ru.put("opt.integ.netproxy.hdr", "Прокси — весь трафик Haven");
        en.put("opt.integ.netproxy.note", "Game chain: UDP — SOCKS5 must support UDP ASSOCIATE. With \"Strict\" on (default), |direct is ignored whenever SOCKS/HTTP appear, so nothing bypasses the proxy. Turn Strict off only if you intentionally want fallback to direct. Login: TCP; empty = same as game. File haven-net-proxyfile overrides prefs.");
        ru.put("opt.integ.netproxy.note", "Игра: UDP — SOCKS5 с UDP ASSOCIATE. «Строго» (по умолчанию): при наличии SOCKS/HTTP хопы |direct не используются — обхода прокси нет. Выкл. строгий режим только если нужен запасной direct. Вход: TCP; пусто = как игра. Файл haven-net-proxyfile перекрывает prefs.");
        en.put("opt.integ.netproxy.game", "Game + default login (UDP + TCP if login field empty)");
        ru.put("opt.integ.netproxy.game", "Игра и вход по умолчанию (UDP + TCP, если поле входа пустое)");
        en.put("opt.integ.netproxy.auth", "Login only (optional; empty = same as game line)");
        ru.put("opt.integ.netproxy.auth", "Только вход (необязательно; пусто = как строка игры)");
        en.put("opt.integ.netproxy.apply", "Save proxy chains");
        ru.put("opt.integ.netproxy.apply", "Сохранить цепочки");
        en.put("opt.integ.netproxy.strict", "Strict: no direct traffic when chain has SOCKS/HTTP (recommended)");
        ru.put("opt.integ.netproxy.strict", "Строго: без прямого трафика, если в цепочке есть SOCKS/HTTP (рекомендуется)");
        en.put("opt.integ.confirm", "Confirm close (ask before exit)"); ru.put("opt.integ.confirm", "Подтверждение при выходе");
        en.put("opt.integ.discord", "--- Discord Bot ---"); ru.put("opt.integ.discord", "--- Discord-бот ---");
        en.put("opt.integ.url", "Server URL:"); ru.put("opt.integ.url", "URL сервера:");
        en.put("opt.integ.token", "Bot Token:"); ru.put("opt.integ.token", "Токен бота:");
        en.put("opt.integ.discord_status", "Show player status in Discord"); ru.put("opt.integ.discord_status", "Статус игрока в Discord");
        en.put("opt.integ.save", "Save Settings"); ru.put("opt.integ.save", "Сохранить");
        en.put("opt.integ.connect", "Connect"); ru.put("opt.integ.connect", "Подключить");
        en.put("opt.integ.status", "Status: Not connected (feature in development)"); ru.put("opt.integ.status", "Статус: не подключено (в разработке)");
        en.put("opt.integ.webdash", "--- Web Dashboard ---"); ru.put("opt.integ.webdash", "--- Веб-панель ---");
        en.put("opt.integ.webdash1", "Web dashboard integration is planned for a future update."); ru.put("opt.integ.webdash1", "Интеграция веб-панели запланирована.");
        en.put("opt.integ.webdash2", "This will allow browser-based monitoring of your character."); ru.put("opt.integ.webdash2", "Мониторинг персонажа из браузера.");
        en.put("opt.layout.title", "Layout & Interface"); ru.put("opt.layout.title", "Разметка и интерфейс");
        en.put("opt.layout.intro", "Saved layout controls for belts, panels and default HUD visibility.");
        ru.put("opt.layout.intro", "Сетка меню, пояса и видимость HUD по умолчанию.");
        en.put("opt.layout.theme_all", "Minimal theme (all windows)"); ru.put("opt.layout.theme_all", "Минималистичная тема (все окна)");
        en.put("opt.layout.chrome_style", "Window chrome style"); ru.put("opt.layout.chrome_style", "Стиль оконного chrome");
        en.put("opt.layout.chrome_style.note", "Switch between the compact header layout and the decorative orbital variant.");
        ru.put("opt.layout.chrome_style.note", "Переключает компактные шапки и более декоративный орбитальный вариант.");
        en.put("opt.layout.chrome_style.compact", "Compact style"); ru.put("opt.layout.chrome_style.compact", "Компактный стиль");
        en.put("opt.layout.chrome_style.orbital", "Orbital style"); ru.put("opt.layout.chrome_style.orbital", "Орбитальный стиль");
        en.put("opt.layout.menu_cols", "Menu grid columns"); ru.put("opt.layout.menu_cols", "Колонки сетки меню");
        en.put("opt.layout.menu_rows", "Menu grid rows"); ru.put("opt.layout.menu_rows", "Строки сетки меню");
        en.put("opt.layout.belt_panels", "Belt panels"); ru.put("opt.layout.belt_panels", "Панели пояса");
        en.put("opt.layout.belt_f", "Show F-key belt"); ru.put("opt.layout.belt_f", "Пояс F1–F12");
        en.put("opt.layout.belt_num", "Show Numpad belt"); ru.put("opt.layout.belt_num", "Пояс Num");
        en.put("opt.layout.reset_panels", "Reset all panel positions"); ru.put("opt.layout.reset_panels", "Сброс позиций всех панелей");
        en.put("opt.esp.section", "--- ESP ---"); ru.put("opt.esp.section", "--- ESP ---");

        en.put("mcc.title", "MooNWide Control Center"); ru.put("mcc.title", "Центр управления MooNWide");
        en.put("mcc.subtitle", "Quick access for bots, overlays, settings and performance switches.");
        ru.put("mcc.subtitle", "Быстрый доступ к ботам, оверлеям, настройкам и производительности.");
        en.put("mcc.tab.overview", "Overview"); ru.put("mcc.tab.overview", "Обзор");
        en.put("mcc.tab.bots", "Bots"); ru.put("mcc.tab.bots", "Боты");
        en.put("mcc.tab.visual", "Visual"); ru.put("mcc.tab.visual", "Визуал");
        en.put("mcc.tab.combat", "Combat"); ru.put("mcc.tab.combat", "Бой");
        en.put("mcc.tab.settings", "Settings"); ru.put("mcc.tab.settings", "Настройки");
        en.put("mcc.apply", "Apply"); ru.put("mcc.apply", "Применить");
        en.put("mcc.ov.section", "Session Snapshot"); ru.put("mcc.ov.section", "Снимок сессии");
        en.put("mcc.ov.desc", "One place for live status, quick windows and module health."); ru.put("mcc.ov.desc", "Статус, быстрые окна и состояние модулей.");
        en.put("mcc.qa.section", "Quick Access"); ru.put("mcc.qa.section", "Быстрый доступ");
        en.put("mcc.qa.desc", "Fast paths to the windows you already use while playing."); ru.put("mcc.qa.desc", "Окна, которые вы открываете во время игры.");
        en.put("mcc.shortcuts.section", "Shortcuts"); ru.put("mcc.shortcuts.section", "Горячие клавиши");
        en.put("mcc.shortcuts.desc", "Core MooNWide toggles stay on function keys so you can work without hunting through menus.");
        ru.put("mcc.shortcuts.desc", "Основные переключатели MooNWide на функциональных клавишах.");
        en.put("mcc.btn.timers", "Timers"); ru.put("mcc.btn.timers", "Таймеры");
        en.put("mcc.btn.accounts", "Saved accounts"); ru.put("mcc.btn.accounts", "Сохранённые аккаунты");
        en.put("mcc.btn.highlight", "Highlight manager"); ru.put("mcc.btn.highlight", "Подсветка");
        en.put("mcc.btn.hidden", "Hidden manager"); ru.put("mcc.btn.hidden", "Скрытые объекты");
        en.put("mcc.btn.deleted", "Deleted manager"); ru.put("mcc.btn.deleted", "Удалённые");
        en.put("mcc.btn.gameopts", "Game options"); ru.put("mcc.btn.gameopts", "Настройки игры");
        en.put("mcc.btn.savecfg", "Save config now"); ru.put("mcc.btn.savecfg", "Сохранить настройки");
        en.put("mcc.btn.closepanel", "Close panel"); ru.put("mcc.btn.closepanel", "Закрыть панель");
        en.put("mcc.bots.section", "Automation Core"); ru.put("mcc.bots.section", "Ядро автоматизации");
        en.put("mcc.bots.desc", "Simple bot-facing controls with the important automation switches kept up front."); ru.put("mcc.bots.desc", "Переключатели автоматизации на видном месте.");
        en.put("mcc.lbl.autodrink", "Auto Drink/Eat"); ru.put("mcc.lbl.autodrink", "Автопитьё / еда");
        en.put("mcc.desc.autodrink", "Keeps hunger and thirst helpers ready for automated use."); ru.put("mcc.desc.autodrink", "Помощники голода и жажды для автоматизации.");
        en.put("mcc.lbl.taskq", "Task Queue"); ru.put("mcc.lbl.taskq", "Очередь задач");
        en.put("mcc.desc.taskq", "Runs queued tasks one by one when automation is enabled."); ru.put("mcc.desc.taskq", "Задачи по очереди при включённой автоматизации.");
        en.put("mcc.queue.section", "Queue Tools"); ru.put("mcc.queue.section", "Очередь");
        en.put("mcc.queue.desc", "Task queue is still lightweight, but the control surface is now ready for daily use."); ru.put("mcc.queue.desc", "Лёгкая очередь задач с нормальным интерфейсом.");
        en.put("mcc.drink_thr", "Drink threshold"); ru.put("mcc.drink_thr", "Порог питья");
        en.put("mcc.drink_thr.desc", "Value between 0.0 and 1.0. Lower means it waits longer before drinking."); ru.put("mcc.drink_thr.desc", "От 0,0 до 1,0. Меньше — реже пьёт.");
        en.put("mcc.eat_thr", "Eat threshold"); ru.put("mcc.eat_thr", "Порог еды");
        en.put("mcc.eat_thr.desc", "Value between 0.0 and 1.0. Higher means food logic reacts earlier."); ru.put("mcc.eat_thr.desc", "От 0,0 до 1,0. Больше — ест раньше.");
        en.put("mcc.btn.clearq", "Clear task queue"); ru.put("mcc.btn.clearq", "Очистить очередь");
        en.put("mcc.btn.opentimers", "Open timers"); ru.put("mcc.btn.opentimers", "Открыть таймеры");
        en.put("mcc.vis.section", "World Visuals"); ru.put("mcc.vis.section", "Мир");
        en.put("mcc.vis.desc", "Keep the important visibility tools grouped together."); ru.put("mcc.vis.desc", "Инструменты видимости в одном месте.");
        en.put("mcc.lbl.pathhl", "Path Highlighter"); ru.put("mcc.lbl.pathhl", "Подсветка пути");
        en.put("mcc.desc.pathhl", "Shows active movement path and target flow when path data exists."); ru.put("mcc.desc.pathhl", "Путь движения, если есть данные.");
        en.put("mcc.lbl.radar", "Resource Radar"); ru.put("mcc.lbl.radar", "Радар ресурсов");
        en.put("mcc.desc.radar", "Tracks nearby resources and keeps the overlay channel alive."); ru.put("mcc.desc.radar", "Ресурсы рядом и канал оверлея.");
        en.put("mcc.lbl.uiclean", "UI Cleaner"); ru.put("mcc.lbl.uiclean", "Очистка UI");
        en.put("mcc.desc.uiclean", "Keeps room on the screen for play instead of clutter."); ru.put("mcc.desc.uiclean", "Меньше визуального шума на экране.");
        en.put("mcc.cb.ridges", "Purus pathfinder: evade ridges"); ru.put("mcc.cb.ridges", "Purus: обход гребней");
        en.put("mcc.cb.hideclutter", "Hide clutter in MooNWide UI"); ru.put("mcc.cb.hideclutter", "Скрыть лишнее в UI MooNWide");
        en.put("mcc.cb.compact", "Compact bars and panels"); ru.put("mcc.cb.compact", "Компактные панели");
        en.put("mcc.radar_dist", "Radar distance"); ru.put("mcc.radar_dist", "Дальность радара");
        en.put("mcc.radar_dist.desc", "Maximum scan radius for resource radar. Higher values can cost more work every frame."); ru.put("mcc.radar_dist.desc", "Радиус сканирования радара. Больше — тяжелее каждый кадр.");
        en.put("mcc.cmb.section", "Combat Helpers"); ru.put("mcc.cmb.section", "Помощники боя");
        en.put("mcc.cmb.desc", "Compact controls for overlay logic and key-driven combat handling."); ru.put("mcc.cmb.desc", "Оверлей и горячие клавиши боя.");
        en.put("mcc.lbl.combatov", "Combat Overlay"); ru.put("mcc.lbl.combatov", "Боевой оверлей");
        en.put("mcc.desc.combatov", "Keeps combat analyzer, stamina hints and overlay hooks active."); ru.put("mcc.desc.combatov", "Анализ боя, стамина, оверлей.");
        en.put("mcc.lbl.smarthk", "Smart Hotkeys"); ru.put("mcc.lbl.smarthk", "Умные клавиши");
        en.put("mcc.desc.smarthk", "Routes MooNWide hotkeys without relying on extra Swing windows."); ru.put("mcc.desc.smarthk", "Горячие клавиши MooNWide без лишних окон.");
        en.put("mcc.cmb.views.section", "Quick Combat Views"); ru.put("mcc.cmb.views.section", "Бой: быстрый вид");
        en.put("mcc.cmb.views.desc", "The panel stays simple, but the hotkey map remains visible and editable through config."); ru.put("mcc.cmb.views.desc", "Карта клавиш — в конфиге.");
        en.put("mcc.cmb.cooldown", "Attack cooldown (ms)"); ru.put("mcc.cmb.cooldown", "Кулдаун атаки (мс)");
        en.put("mcc.cmb.cooldown.desc", "Fallback cooldown used by the combat helper when it chooses between attack and wait."); ru.put("mcc.cmb.cooldown.desc", "Запасной кулдаун помощника боя (атака или ожидание).");
        en.put("mcc.btn.openopts", "Open game options"); ru.put("mcc.btn.openopts", "Настройки игры");
        en.put("mcc.set.section", "Client Settings"); ru.put("mcc.set.section", "Клиент");
        en.put("mcc.set.desc", "Important toggles are persisted immediately so the panel behaves like a real control center."); ru.put("mcc.set.desc", "Важные переключатели сохраняются сразу.");
        en.put("mcc.cb.debug", "Debug logging"); ru.put("mcc.cb.debug", "Отладочный лог");
        en.put("mcc.cb.dark", "Dark theme flag"); ru.put("mcc.cb.dark", "Тёмная тема");
        en.put("mcc.surface.section", "Control Surface"); ru.put("mcc.surface.section", "Действия");
        en.put("mcc.surface.desc", "Buttons below are the safe maintenance actions you need most often."); ru.put("mcc.surface.desc", "Частые безопасные действия.");
        en.put("mcc.btn.savecfg2", "Save config"); ru.put("mcc.btn.savecfg2", "Сохранить");
        en.put("mcc.btn.havenopts", "Open Haven options"); ru.put("mcc.btn.havenopts", "Настройки Haven");
        en.put("mcc.status.hooks", "World hooks: live | menu key: %s"); ru.put("mcc.status.hooks", "Хуки мира: активны | меню: %s");
        en.put("mcc.status.modules", "Modules online: %d / %d"); ru.put("mcc.status.modules", "Модули: %d / %d");
        en.put("mcc.status.automation", "Automation queue: %d | runner: %s"); ru.put("mcc.status.automation", "Очередь: %d | исполнитель: %s");
        en.put("mcc.status.queuehead", "Current queue head: %s"); ru.put("mcc.status.queuehead", "Текущая задача: %s");
        en.put("mcc.status.bots", "Task queue: %d queued | next: %s"); ru.put("mcc.status.bots", "Очередь: %d | далее: %s");
        en.put("mcc.status.radar", "Radar cache: %d resources | distance %s"); ru.put("mcc.status.radar", "Кэш радара: %d ресурсов | дальность %s");
        en.put("mcc.status.combat", "Combat hint: %s | cooldown %d ms"); ru.put("mcc.status.combat", "Бой: %s | кулдаун %d мс");
        en.put("mcc.status.settings", "Debug log: %s | dark flag: %s"); ru.put("mcc.status.settings", "Отладка: %s | тёмная тема: %s");
        en.put("mcc.runner.active", "active"); ru.put("mcc.runner.active", "активен");
        en.put("mcc.runner.sleep", "sleeping"); ru.put("mcc.runner.sleep", "ожидание");
        en.put("mcc.idle", "idle"); ru.put("mcc.idle", "простой");
        en.put("mcc.state.on", "on"); ru.put("mcc.state.on", "вкл");
        en.put("mcc.state.off", "off"); ru.put("mcc.state.off", "выкл");
        en.put("mcc.msg.queue_cleared", "Task queue cleared."); ru.put("mcc.msg.queue_cleared", "Очередь очищена.");
        en.put("mcc.msg.drink_saved", "Drink threshold saved."); ru.put("mcc.msg.drink_saved", "Порог питья сохранён.");
        en.put("mcc.err.drink_range", "Drink threshold must be between 0.0 and 1.0."); ru.put("mcc.err.drink_range", "Порог питья: от 0,0 до 1,0.");
        en.put("mcc.msg.eat_saved", "Eat threshold saved."); ru.put("mcc.msg.eat_saved", "Порог еды сохранён.");
        en.put("mcc.err.eat_range", "Eat threshold must be between 0.0 and 1.0."); ru.put("mcc.err.eat_range", "Порог еды: от 0,0 до 1,0.");
        en.put("mcc.msg.radar_saved", "Radar distance saved."); ru.put("mcc.msg.radar_saved", "Дальность радара сохранена.");
        en.put("mcc.err.radar_pos", "Radar distance must be a positive number."); ru.put("mcc.err.radar_pos", "Дальность радара — положительное число.");
        en.put("mcc.msg.cooldown_saved", "Combat cooldown saved."); ru.put("mcc.msg.cooldown_saved", "Кулдаун боя сохранён.");
        en.put("mcc.err.cooldown_pos", "Attack cooldown must be a positive whole number."); ru.put("mcc.err.cooldown_pos", "Кулдаун атаки — целое число > 0.");

        en.put("chr.title", "Character Sheet"); ru.put("chr.title", "Персонаж");
        en.put("chr.tab.battr", "Base Attributes"); ru.put("chr.tab.battr", "Базовые атрибуты");
        en.put("chr.tab.sattr", "Abilities"); ru.put("chr.tab.sattr", "Навыки");
        en.put("chr.tab.skill", "Lore & Skills"); ru.put("chr.tab.skill", "Знания и умения");
        en.put("chr.tab.fight", "Martial Arts & Combat Schools"); ru.put("chr.tab.fight", "Боевые школы");
        en.put("chr.tab.wound", "Health & Wounds"); ru.put("chr.tab.wound", "Здоровье и раны");
        en.put("chr.tab.quest", "Quest Log"); ru.put("chr.tab.quest", "Журнал заданий");
        en.put("chr.hdr.base", "Base Attributes"); ru.put("chr.hdr.base", "Базовые атрибуты");
        en.put("chr.hdr.satiations", "Food Satiations"); ru.put("chr.hdr.satiations", "Сытость по еде");
        en.put("chr.hdr.fep", "Food Event Points"); ru.put("chr.hdr.fep", "Очки событий еды");
        en.put("chr.hdr.hunger", "Hunger Level"); ru.put("chr.hdr.hunger", "Уровень голода");
        en.put("chr.foodmeter.tip", "%s: %.1f‰\nFood efficacy: %d%%"); ru.put("chr.foodmeter.tip", "%s: %.1f‰\nЭффективность еды: %d%%");
        en.put("chr.foodmeter.total", "Total: %s/%s"); ru.put("chr.foodmeter.total", "Итого: %s/%s");
        en.put("chr.foodmeter.gained", "You gained %s"); ru.put("chr.foodmeter.gained", "Вы получили: %s");
        en.put("foodinfo.total_line", "Total: $col[128,192,255]{%s} ($col[128,192,255]{%s}/‰ hunger)");
        ru.put("foodinfo.total_line", "Итого: $col[128,192,255]{%s} ($col[128,192,255]{%s}/‰ голода)");
        en.put("foodinfo.stat_line", "Energy: $col[128,128,255]{%s%%}, Hunger: $col[255,192,128]{%s\u2030}");
        ru.put("foodinfo.stat_line", "Энергия: $col[128,128,255]{%s%%}, голод: $col[255,192,128]{%s\u2030}");
        en.put("foodinfo.satiation_suffix", ", Satiation: $col[192,192,128]{%s%%}");
        ru.put("foodinfo.satiation_suffix", ", сытость: $col[192,192,128]{%s%%}");
        en.put("foodinfo.effect_chance", "$i{($col[192,192,255]{%d%%} chance)}");
        ru.put("foodinfo.effect_chance", "$i{($col[192,192,255]{%d%%} шанс)}");
        en.put("iteminfo.contents", "Contents:");
        ru.put("iteminfo.contents", "Содержимое:");
        en.put("equ.side.toggle_tip", "Equipment effects & mining (from item tooltips)"); ru.put("equ.side.toggle_tip", "Эффекты экипировки и шахта (из подсказок предметов)");
        en.put("equ.side.hdr_mining", "Mining"); ru.put("equ.side.hdr_mining", "Шахта / добыча");
        en.put("equ.side.mining_power_main", "Est. mining power: %.1f  (sqrt(STR x k x Q))"); ru.put("equ.side.mining_power_main", "Оценка силы копки: %.1f  (sqrt(STR x k x Q))");
        en.put("equ.side.mining_power_sub", "STR=%d  Q~%.0f  k=%.0f (%s)"); ru.put("equ.side.mining_power_sub", "STR=%d  Q~%.0f  k=%.0f (%s)");
        en.put("equ.side.mining_kind_pick", "pickaxe"); ru.put("equ.side.mining_kind_pick", "кирка");
        en.put("equ.side.mining_kind_saxe", "stone axe"); ru.put("equ.side.mining_kind_saxe", "каменный топор");
        en.put("equ.side.mining_power_src", "Community formula (wiki/forums; pickaxe k=2, stone axe k=1)."); ru.put("equ.side.mining_power_src", "Формула сообщества (вики/форумы; кирка k=2, каменный топор k=1).");
        en.put("equ.side.mining_power_na", "No pickaxe/stone axe with quality in slots, or STR unknown."); ru.put("equ.side.mining_power_na", "Нет кирки/кам. топора с качеством в слотах или STR неизвестен.");
        en.put("equ.side.mining_power_no_str", "STR not loaded yet (open Character or wait for attrs)."); ru.put("equ.side.mining_power_no_str", "STR ещё не получен (откройте персонажа или подождите атрибуты).");
        en.put("equ.side.mining_power_no_tool", "Equip a pickaxe or stone axe to estimate mining power."); ru.put("equ.side.mining_power_no_tool", "Наденьте кирку или каменный топор для оценки силы копки.");
        en.put("equ.side.mining_power_no_q", "Mining tool has no readable Q in tooltip yet (hover item or wait)."); ru.put("equ.side.mining_power_no_q", "У инструмента пока нет читаемого Q в подсказке (наведите на предмет или подождите).");
        en.put("equ.side.hdr_mining_mods", "Mining %% from tooltips"); ru.put("equ.side.hdr_mining_mods", "Модификаторы %% «Mining» из подсказок");
        en.put("equ.side.mining_mods_none", "(no \"Mining\" %% lines in equipped tooltips)"); ru.put("equ.side.mining_mods_none", "(нет строк «Mining» %% в подсказках экипировки)");
        en.put("equ.side.mining_sum", "Sum of %% modifiers (parsed): %+.1f%%"); ru.put("equ.side.mining_sum", "Сумма %% из строк «Mining»: %+.1f%%");
        en.put("equ.side.hdr_buffs", "Equipped items (full tooltips)"); ru.put("equ.side.hdr_buffs", "Предметы (полные подсказки)");
        en.put("equ.side.empty", "Nothing equipped."); ru.put("equ.side.empty", "Ничего не надето.");
        en.put("chr.sattr.abilities", "Abilities"); ru.put("chr.sattr.abilities", "Навыки");
        en.put("chr.sattr.study", "Study Report"); ru.put("chr.sattr.study", "Отчёт об изучении");
        en.put("chr.sattr.attention", "Attention:"); ru.put("chr.sattr.attention", "Внимание:");
        en.put("chr.sattr.exp_cost", "Experience cost:"); ru.put("chr.sattr.exp_cost", "Стоимость опыта:");
        en.put("chr.sattr.lp", "Learning points:"); ru.put("chr.sattr.lp", "Очки обучения:");
        en.put("chr.sattr.exp_pts", "Experience points:"); ru.put("chr.sattr.exp_pts", "Очки опыта:");
        en.put("chr.sattr.learn_cost", "Learning cost:"); ru.put("chr.sattr.learn_cost", "Стоимость обучения:");
        en.put("chr.btn.buy", "Buy"); ru.put("chr.btn.buy", "Купить");
        en.put("chr.btn.reset", "Reset"); ru.put("chr.btn.reset", "Сброс");
        en.put("curiosity.lp", "Learning points: $col[192,192,255]{%s} ($col[192,192,255]{%s}/h)\n"); ru.put("curiosity.lp", "Очки обучения: $col[192,192,255]{%s} ($col[192,192,255]{%s}/ч)\n");
        en.put("curiosity.time", "Study time: $col[192,255,192]{%s}\n"); ru.put("curiosity.time", "Время изучения: $col[192,255,192]{%s}\n");
        en.put("curiosity.mw", "Mental weight: $col[255,192,255]{%d}\n"); ru.put("curiosity.mw", "Умственный вес: $col[255,192,255]{%d}\n");
        en.put("curiosity.enc", "Experience cost: $col[255,255,192]{%d}\n"); ru.put("curiosity.enc", "Стоимость опыта: $col[255,255,192]{%d}\n");
        en.put("curiosity.unit.s", "s"); ru.put("curiosity.unit.s", "с");
        en.put("curiosity.unit.m", "m"); ru.put("curiosity.unit.m", "м");
        en.put("curiosity.unit.h", "h"); ru.put("curiosity.unit.h", "ч");
        en.put("curiosity.unit.d", "d"); ru.put("curiosity.unit.d", "д");
        en.put("skill.hdr.lore", "Lore & Skills"); ru.put("skill.hdr.lore", "Знания и умения");
        en.put("skill.hdr.entries", "Entries"); ru.put("skill.hdr.entries", "Записи");
        en.put("skill.grp.avail", "Available Skills"); ru.put("skill.grp.avail", "Доступные умения");
        en.put("skill.grp.known", "Known Skills"); ru.put("skill.grp.known", "Известные умения");
        en.put("skill.tab.skills", "Skills"); ru.put("skill.tab.skills", "Умения");
        en.put("skill.tab.credos", "Credos"); ru.put("skill.tab.credos", "Кредо");
        en.put("skill.tab.lore", "Lore"); ru.put("skill.tab.lore", "Знания");
        en.put("skill.cost_lbl", "Cost:"); ru.put("skill.cost_lbl", "Цена:");
        en.put("skill.buy", "Buy"); ru.put("skill.buy", "Купить");
        en.put("skill.cost_na", "N/A"); ru.put("skill.cost_na", "—");
        en.put("skill.cost_lp_fmt", "%,d / %,d LP"); ru.put("skill.cost_lp_fmt", "%,d / %,d ОО");
        en.put("skill.detail.cost", "Cost: %d\n\n"); ru.put("skill.detail.cost", "Цена: %d\n\n");
        en.put("skill.exp.line", "Experience points: "); ru.put("skill.exp.line", "Очки опыта: ");
        en.put("credo.pursuing", "Pursuing"); ru.put("credo.pursuing", "Изучаемое");
        en.put("credo.avail", "Credos Available"); ru.put("credo.avail", "Доступные кредо");
        en.put("credo.acquired", "Credos Acquired"); ru.put("credo.acquired", "Полученные кредо");
        en.put("credo.pursue", "Pursue"); ru.put("credo.pursue", "Принять");
        en.put("credo.showq", "Show quest"); ru.put("credo.showq", "К заданию");
        en.put("credo.level_fmt", "Level: %d/%d"); ru.put("credo.level_fmt", "Уровень: %d/%d");
        en.put("credo.quest_fmt", "Quest: %d/%d"); ru.put("credo.quest_fmt", "Задание: %d/%d");
        en.put("credo.cost_lp_fmt", "Cost: %,d LP"); ru.put("credo.cost_lp_fmt", "Цена: %,d ОО");
        en.put("fight.hdr", "Martial Arts & Combat Schools"); ru.put("fight.hdr", "Боевые школы");
        en.put("fight.load", "Load"); ru.put("fight.load", "Загрузить");
        en.put("fight.save", "Save"); ru.put("fight.save", "Сохранить");
        en.put("fight.unused", "Unused save"); ru.put("fight.unused", "Пусто");
        en.put("fight.saved_fmt", "Saved school %d"); ru.put("fight.saved_fmt", "Школа %d");
        en.put("fight.used_fmt", "Used: %d/%d"); ru.put("fight.used_fmt", "Использовано: %d/%d");
        en.put("fight.err.nosel", "No save entry selected."); ru.put("fight.err.nosel", "Не выбран слот сохранения.");
        en.put("wound.hdr", "Health & Wounds"); ru.put("wound.hdr", "Здоровье и раны");
        en.put("quest.log", "Quest Log"); ru.put("quest.log", "Журнал заданий");
        en.put("quest.tab.current", "Current"); ru.put("quest.tab.current", "Текущие");
        en.put("quest.tab.done", "Completed"); ru.put("quest.tab.done", "Завершённые");
        en.put("quest.done.banner", "Quest completed"); ru.put("quest.done.banner", "Задание выполнено");
        en.put("quest.fail.banner", "Quest failed"); ru.put("quest.fail.banner", "Задание провалено");
        en.put("chat.chan.system", "System"); ru.put("chat.chan.system", "Система");
        en.put("chat.chan.area", "Area Chat"); ru.put("chat.chan.area", "Локальный чат");
        en.put("chat.chan.party", "Party"); ru.put("chat.chan.party", "Группа");
        en.put("chat.mute", "Mute"); ru.put("chat.mute", "Заглушить");
        en.put("chat.unmute", "Unmute"); ru.put("chat.unmute", "Снять заглушку");
        en.put("chat.title", "Chat"); ru.put("chat.title", "Чат");
        en.put("map.title", "Map"); ru.put("map.title", "Карта");
        en.put("map.tip.claim", "Display personal claims"); ru.put("map.tip.claim", "Личные клеймы");
        en.put("map.tip.vil", "Display village claims"); ru.put("map.tip.vil", "Клеймы деревень");
        en.put("map.tip.prov", "Display provinces"); ru.put("map.tip.prov", "Провинции");
        en.put("map.tip.mapwnd", "Map"); ru.put("map.tip.mapwnd", "Карта");
        en.put("map.tip.icons", "Icon settings"); ru.put("map.tip.icons", "Настройки значков");
        en.put("map.tip.nav", "Navigation"); ru.put("map.tip.nav", "Навигация");
        en.put("hud.quest.title", "Quest"); ru.put("hud.quest.title", "Задание");
        en.put("makewindow.input", "Input:"); ru.put("makewindow.input", "Вход:");
        en.put("makewindow.result", "Result:"); ru.put("makewindow.result", "Результат:");
        en.put("makewindow.craft", "Craft"); ru.put("makewindow.craft", "Создать");
        en.put("makewindow.quality", "Quality:"); ru.put("makewindow.quality", "Качество:");
        en.put("makewindow.tools", "Tools:"); ru.put("makewindow.tools", "Инструменты:");
        en.put("makewindow.craft_all", "Craft All"); ru.put("makewindow.craft_all", "Создать всё");

        en.put("menu.caveflat", "Mine: flat caves");
        ru.put("menu.caveflat", "Шахта: плоские стены");
        en.put("msg.caveflat.on", "Flat cave walls enabled (terrain rebuilt).");
        ru.put("msg.caveflat.on", "Плоские стены пещер включены (земля перестроена).");
        en.put("msg.caveflat.off", "Flat cave walls disabled.");
        ru.put("msg.caveflat.off", "Плоские стены пещер выключены.");
        en.put("opt.caveflat", "Flat caves (short cave walls)");
        ru.put("opt.caveflat", "Плоские пещеры (укороченные стены)");
        en.put("opt.cavestraight", "Straight cave walls (remove random bends)");
        ru.put("opt.cavestraight", "Прямые стены пещер (убрать случайные изгибы)");
        en.put("opt.flatwalls", "Flat palisades and walls");
        ru.put("opt.flatwalls", "Плоский палисад и стены");
        en.put("opt.flatcupboards", "Flat cupboards");
        ru.put("opt.flatcupboards", "Плоские шкафы");
        en.put("opt.flatcupboards.note", "Render squash; optional custom-res/cupboard.res.");
        ru.put("opt.flatcupboards.note", "Сжатие в рендере; при желании cupboard.res в custom-res.");
        en.put("opt.status.section", "Object status highlights");
        ru.put("opt.status.section", "Подсветка состояний объектов");
        en.put("opt.status.dframe", "Drying frames and tanning tubs");
        ru.put("opt.status.dframe", "Сушильные рамки и дубильные чаны");
        en.put("opt.status.rack", "Cheese racks");
        ru.put("opt.status.rack", "Сырные стойки");
        en.put("opt.status.storage", "Cupboards and storage");
        ru.put("opt.status.storage", "Шкафы и хранилища");
        en.put("opt.status.storage.partial", "Show partial / in-between storage states");
        ru.put("opt.status.storage.partial", "Показывать частично заполненные состояния");
        en.put("opt.status.shed", "Sheds");
        ru.put("opt.status.shed", "Шеды");
        en.put("opt.status.coop", "Chicken coops");
        ru.put("opt.status.coop", "Курятники");
        en.put("opt.status.hutch", "Rabbit hutches");
        ru.put("opt.status.hutch", "Клетки для кроликов");
        en.put("opt.status.trough", "Troughs");
        ru.put("opt.status.trough", "Кормушки");
        en.put("opt.status.beehive", "Beehives");
        ru.put("opt.status.beehive", "Ульи");
        en.put("opt.status.pots", "Garden pots and moundbeds");
        ru.put("opt.status.pots", "Горшки и грядки");

        en.put("opt.perf.section.mapload", "Live map loading");
        ru.put("opt.perf.section.mapload", "Живая загрузка карты");
        en.put("opt.perf.mapload", "Extra map load radius");
        ru.put("opt.perf.mapload", "Дополнительный радиус загрузки карты");
        en.put("opt.perf.mapload.default", "Default camera-driven radius");
        ru.put("opt.perf.mapload.default", "Стандартный радиус от камеры");
        en.put("opt.perf.mapload.val", "+%d cuts (~%d tiles)");
        ru.put("opt.perf.mapload.val", "+%d cut'ов (~%d тайлов)");
        en.put("opt.perf.mapload.note", "Requests more live map cuts from the server. Terrain usually extends farther, but distant moving objects still depend on what the server sends.");
        ru.put("opt.perf.mapload.note", "Запрашивает у сервера больше живых чанков карты. Рельеф обычно видно дальше, но дальние движущиеся объекты все равно зависят от того, что сервер реально присылает.");

        en.put("opt.audio.alert.section", "Player encounter alerts");
        ru.put("opt.audio.alert.section", "Оповещения о встрече игроков");
        en.put("opt.audio.alert.master", "Enable player encounter sounds");
        ru.put("opt.audio.alert.master", "Включить звуки встречи игроков");
        en.put("opt.audio.alert.cooldown", "Re-alert cooldown for the same player");
        ru.put("opt.audio.alert.cooldown", "Задержка повторного сигнала для того же игрока");
        en.put("opt.audio.alert.note", "Friendly uses party members, hostile uses active combat opponents, unknown is any other player gob that becomes visible.");
        ru.put("opt.audio.alert.note", "Дружелюбные — члены группы, враждебные — активные противники в бою, неизвестные — любые другие видимые игроки.");
        en.put("opt.audio.alert.friendly", "Friendly / party players");
        ru.put("opt.audio.alert.friendly", "Свои / игроки группы");
        en.put("opt.audio.alert.unknown", "Unknown players");
        ru.put("opt.audio.alert.unknown", "Незнакомые игроки");
        en.put("opt.audio.alert.hostile", "Hostile players");
        ru.put("opt.audio.alert.hostile", "Враждебные игроки");
        en.put("opt.audio.alert.test", "Test");
        ru.put("opt.audio.alert.test", "Тест");
        en.put("opt.audio.alert.path.note", "Path field accepts a custom PCM WAV file. Leave it empty to use the built-in alert tone.");
        ru.put("opt.audio.alert.path.note", "В поле можно указать свой PCM WAV-файл. Оставьте пустым, чтобы использовать встроенный сигнал.");

	en.put("opt.perf.section.profile", "--- Performance profile ---");
	ru.put("opt.perf.section.profile", "--- Профиль производительности ---");
	en.put("opt.perf.section.cpu", "--- CPU & runtime ---");
	ru.put("opt.perf.section.cpu", "--- CPU и рантайм ---");
	en.put("opt.perf.cpu_profile", "CPU preset");
	ru.put("opt.perf.cpu_profile", "Пресет процессора");
	en.put("opt.perf.cpu.note", "Writes launcher runtime tuning for JVM cores, loader threads and heap. Full effect applies after restart.");
	ru.put("opt.perf.cpu.note", "Записывает рантайм-настройки лаунчера для ядер JVM, loader/defer потоков и памяти. Полный эффект будет после перезапуска.");
	en.put("opt.perf.cpu.summary", "Target: %d cores | loader %d | defer %d | heap %d-%d GB | current runtime %d");
	ru.put("opt.perf.cpu.summary", "Цель: %d ядер | loader %d | defer %d | память %d-%d GB | текущий рантайм %d");
	en.put("opt.perf.cpu.active", "Active processor count");
	ru.put("opt.perf.cpu.active", "Количество активных ядер");
	en.put("opt.perf.cpu.loader", "Loader workers");
	ru.put("opt.perf.cpu.loader", "Потоки loader");
	en.put("opt.perf.cpu.defer", "Deferred workers");
	ru.put("opt.perf.cpu.defer", "Потоки defer");
	en.put("opt.perf.cpu.heapmax", "Max heap");
	ru.put("opt.perf.cpu.heapmax", "Максимальная память");
	en.put("opt.perf.cpu.auto", "Auto (safe default)");
	ru.put("opt.perf.cpu.auto", "Auto (безопасный по умолчанию)");
	en.put("opt.perf.cpu.amd.ryzen5", "AMD Ryzen 5 (1600/2600/3600/5600/7600)");
	ru.put("opt.perf.cpu.amd.ryzen5", "AMD Ryzen 5 (1600/2600/3600/5600/7600)");
	en.put("opt.perf.cpu.amd.ryzen7", "AMD Ryzen 7 (3700/5700X/7700)");
	ru.put("opt.perf.cpu.amd.ryzen7", "AMD Ryzen 7 (3700/5700X/7700)");
	en.put("opt.perf.cpu.amd.x3d", "AMD Ryzen X3D (5800X3D/7800X3D/9800X3D)");
	ru.put("opt.perf.cpu.amd.x3d", "AMD Ryzen X3D (5800X3D/7800X3D/9800X3D)");
	en.put("opt.perf.cpu.intel.i5", "Intel Core i5 (8400/9600/10400/11400)");
	ru.put("opt.perf.cpu.intel.i5", "Intel Core i5 (8400/9600/10400/11400)");
	en.put("opt.perf.cpu.intel.hybrid.mid", "Intel Core 12-14 gen mid (12400/12600/13400/13600)");
	ru.put("opt.perf.cpu.intel.hybrid.mid", "Intel Core 12-14 gen mid (12400/12600/13400/13600)");
	en.put("opt.perf.cpu.intel.hybrid.high", "Intel Core 12-14 gen high (12700/13700/14700/14900)");
	ru.put("opt.perf.cpu.intel.hybrid.high", "Intel Core 12-14 gen high (12700/13700/14700/14900)");
	en.put("opt.perf.cpu.custom", "Custom");
	ru.put("opt.perf.cpu.custom", "Свои значения");
	en.put("opt.perf.profile", "Selected preset");
	ru.put("opt.perf.profile", "Выбранный пресет");
        en.put("opt.perf.profile.note", "Presets only affect the current graphics profile after you press apply.");
        ru.put("opt.perf.profile.note", "Пресет меняет текущий графический профиль только после нажатия Apply.");
        en.put("opt.perf.preset.balanced", "Balanced");
        ru.put("opt.perf.preset.balanced", "Balanced");
        en.put("opt.perf.preset.maxfps", "Max FPS");
        ru.put("opt.perf.preset.maxfps", "Max FPS");
        en.put("opt.perf.preset.visual", "Visual");
        ru.put("opt.perf.preset.visual", "Visual");
        en.put("opt.perf.apply_current", "Apply current preset to profile");
        ru.put("opt.perf.apply_current", "Применить текущий пресет к профилю");
        en.put("opt.perf.compact_hud", "Compact performance HUD");
        ru.put("opt.perf.compact_hud", "Компактный performance HUD");
        en.put("opt.perf.progressive_load", "Progressive world loading");
        ru.put("opt.perf.progressive_load", "Плавная поэтапная загрузка мира");
        en.put("opt.perf.autoshed", "Auto-shed optional workload under stress");
        ru.put("opt.perf.autoshed", "Автоматически урезать необязательные задачи при лагах");
        en.put("opt.perf.section.streaming", "--- Streaming budgets ---");
        ru.put("opt.perf.section.streaming", "--- Бюджеты стриминга ---");
        en.put("opt.perf.budget.critical", "Terrain critical budget");
        ru.put("opt.perf.budget.critical", "Бюджет критичного terrain");
        en.put("opt.perf.budget.critical.val", "%d cuts/frame");
        ru.put("opt.perf.budget.critical.val", "%d cut/кадр");
        en.put("opt.perf.budget.secondary", "Terrain secondary budget");
        ru.put("opt.perf.budget.secondary", "Бюджет вторичного terrain");
        en.put("opt.perf.budget.secondary.val", "%d cuts/frame");
        ru.put("opt.perf.budget.secondary.val", "%d cut/кадр");
        en.put("opt.perf.budget.gobs", "Gob build budget");
        ru.put("opt.perf.budget.gobs", "Бюджет построения gob");
        en.put("opt.perf.budget.gobs.val", "%d gobs/frame");
        ru.put("opt.perf.budget.gobs.val", "%d gob/кадр");
        db.put(LANG_EN, en);
        db.put(LANG_RU, ru);
    }

    public static String lang() { return currentLang; }

    public static void setLang(String lang) {
        currentLang = lang;
        Utils.setpref("moon-lang", lang);
        try {
            LocalizationManager.reloadOverlays();
        } catch(Throwable ignored) {
        }
        MoonGameOverlay.reload();
    }

    /** Russian overlay for dynamic English from server/resources (tooltips, names, paginae). */
    public static String game(String s) {
        return MoonGameOverlay.apply(s);
    }

    /** Prefer {@link LocalizationManager#tr(String)}. */
    @Deprecated
    public static String t(String key) {
	return(LocalizationManager.tr(key));
    }

    /** Used by {@link LocalizationManager#tr(String)} after JSON overlays (avoids recursion). */
    static String lookupStaticForManager(String key) {
	if(key == null)
	    return(null);
	Map<String, String> m = db.get(currentLang);
	if(m != null) {
	    String v = m.get(key);
	    if(v != null)
		return(v);
	}
	Map<String, String> en = db.get(LANG_EN);
	if(en != null) {
	    String v = en.get(key);
	    if(v != null)
		return(v);
	}
	return(null);
    }

    /** Display name for standard chat channels; server-specific names (e.g. village) pass through. */
    public static String chatChan(String raw) {
        if(raw == null)
            return("");
        String k = raw.trim().toLowerCase(Locale.ROOT);
        if(k.equals("system"))
            return t("chat.chan.system");
        if(k.equals("area chat"))
            return t("chat.chan.area");
        if(k.equals("party"))
            return t("chat.chan.party");
        return raw;
    }
}
