package haven;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MoonAutoDropWnd extends MoonChatStylePanel {
    private static final Coord INNER = UI.scale(new Coord(760, 508));
    private static final RichText.Foundry RTF = new RichText.Foundry(Text.std.font, Color.WHITE);
    private static final int CHEVRON_W = UI.scale(18);

    private final GameUI gui;
    private final List<CategoryRow> rows = new ArrayList<>();
    private final Set<String> collapsedFoldGroups = new LinkedHashSet<>();
    private final Map<String, Tex> previewTexCache = new HashMap<>();
    private final Set<String> missingPreviewRes = new HashSet<>();

    private Scrollport leftPort;
    private TextEntry search;
    private Label selectedLbl;
    private Label itemPreviewLbl;
    private Label wallPreviewLbl;
    private RichTextBox descBox;
    private RichTextBox tokensBox;
    private CheckBox masterEnable;
    private Button toggleBtn;
    private Label summaryLbl;
    private MoonAutoDrop.Category selected;
    private SelectedResPreview selectedItemPreview;
    private SelectedResPreview selectedWallPreview;

    public MoonAutoDropWnd(GameUI gui) {
        super(outer(), "moon-autodrop", text("Автодроп предметов", "Item auto-drop"));
        this.gui = gui;
        setMinSize(outer());
        loadFoldState();

        Coord co = contentOffset();
        int pad = UI.scale(8);
        int leftW = UI.scale(292);
        int rightX = co.x + leftW + pad;
        int rightW = INNER.x - leftW - pad;

        add(new Label(text("Категории автодропа", "Auto-drop categories")), co);
        search = add(new TextEntry(leftW, "") {
            @Override
            protected void changed() {
                super.changed();
                rebuildList();
            }
        }, co.add(0, UI.scale(20)));
        search.settip(text("Поиск по категориям", "Search categories"), true);

        leftPort = add(new Scrollport(Coord.of(leftW, UI.scale(336))), co.add(0, UI.scale(48)));

        masterEnable = add(new CheckBox(text("Включить автодроп из инвентаря", "Enable inventory auto-drop"), true) {
            {a = MoonConfig.mineAutoDropEnabled;}

            @Override
            public void set(boolean val) {
                MoonConfig.setMineAutoDropEnabled(val);
                a = val;
                refreshSummary();
            }
        }, co.add(0, UI.scale(392)));
        masterEnable.settip(text(
            "Пока включено, выбранные категории сбрасываются из основного инвентаря на землю. Работает всегда, бот копки не обязателен.",
            "While enabled, selected categories are dropped from the main inventory to the ground. Works anytime; mine bot does not need to run."), true);

        add(new Button(UI.scale(132), text("Только шахта", "Mine default"), this::applyMineDefault), co.add(UI.scale(148), UI.scale(420)));
        add(new Button(UI.scale(138), text("Включить всё", "Enable all"), this::enableAll), Coord.of(rightX, co.y + UI.scale(430)));
        add(new Button(UI.scale(138), text("Выключить всё", "Disable all"), this::disableAll), Coord.of(rightX + UI.scale(148), co.y + UI.scale(430)));

        add(new Label(text("Выбранная категория", "Selected category")), Coord.of(rightX, co.y));
        itemPreviewLbl = add(new Label(text("\u041f\u0440\u0435\u0434\u043c\u0435\u0442", "Item")), Coord.of(rightX, co.y + UI.scale(18)));
        wallPreviewLbl = add(new Label(text("\u0412 \u0441\u0442\u0435\u043d\u0435", "In wall")), Coord.of(rightX + UI.scale(64), co.y + UI.scale(18)));
        selectedItemPreview = add(new SelectedResPreview(false), Coord.of(rightX, co.y + UI.scale(34)));
        selectedWallPreview = add(new SelectedResPreview(true), Coord.of(rightX + UI.scale(64), co.y + UI.scale(34)));
        selectedLbl = add(new Label("-"), Coord.of(rightX + UI.scale(128), co.y + UI.scale(24)));
        toggleBtn = add(new Button(UI.scale(214), text("Включить категорию", "Enable category"), this::toggleSelected),
            Coord.of(rightX + UI.scale(128), co.y + UI.scale(52)));

        add(new Label(text("Описание", "Description")), Coord.of(rightX, co.y + UI.scale(96)));
        descBox = add(new RichTextBox(Coord.of(rightW, UI.scale(118)), "", RTF), Coord.of(rightX, co.y + UI.scale(116)));
        descBox.bg = new Color(14, 12, 22, 150);

        add(new Label(text("Примеры и токены", "Examples and tokens")), Coord.of(rightX, co.y + UI.scale(246)));
        tokensBox = add(new RichTextBox(Coord.of(rightW, UI.scale(128)), "", RTF), Coord.of(rightX, co.y + UI.scale(266)));
        tokensBox.bg = new Color(14, 12, 22, 150);

        summaryLbl = add(new Label(""), Coord.of(rightX, co.y + UI.scale(404)));

        rebuildList();
        if(selected == null && !MoonAutoDrop.CATEGORIES.isEmpty())
            selected = MoonAutoDrop.CATEGORIES.get(0);
        refreshRight();
    }

    private static Coord outer() {
        return Coord.of(INNER.x + MoonPanel.PAD * 2, INNER.y + MoonPanel.HEADER_H + MoonPanel.PAD * 2);
    }

    private void loadFoldState() {
        collapsedFoldGroups.clear();
        String raw = Utils.getpref("moon-autodrop-collapsed", "");
        if(raw == null || raw.isBlank())
            return;
        for(String p : raw.split(",")) {
            String s = p.trim();
            if(!s.isEmpty())
                collapsedFoldGroups.add(s);
        }
    }

    private void saveFoldState() {
        Utils.setpref("moon-autodrop-collapsed", String.join(",", collapsedFoldGroups));
    }

    private boolean foldExpanded(MoonAutoDrop.Category cat, boolean searching) {
        if(cat.foldGroup == null)
            return true;
        if(searching)
            return true;
        return !collapsedFoldGroups.contains(cat.foldGroup);
    }

    private void toggleFoldGroup(String g) {
        if(collapsedFoldGroups.contains(g))
            collapsedFoldGroups.remove(g);
        else
            collapsedFoldGroups.add(g);
        saveFoldState();
        rebuildList();
    }

    private void applyMineDefault() {
        List<String> ids = List.of("stone_any", "ore_any", "coal_any");
        LinkedHashSet<String> set = new LinkedHashSet<>(ids);
        MoonAutoDrop.saveSelected(set);
        rebuildList();
        refreshRight();
    }

    private void enableAll() {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for(MoonAutoDrop.Category cat : MoonAutoDrop.CATEGORIES)
            ids.add(cat.id);
        MoonAutoDrop.saveSelected(ids);
        rebuildList();
        refreshRight();
    }

    private void disableAll() {
        MoonAutoDrop.saveSelected(new LinkedHashSet<>());
        rebuildList();
        refreshRight();
    }

    private void toggleSelected() {
        if(selected == null)
            return;
        MoonAutoDrop.setSelected(selected.id, !MoonAutoDrop.isSelected(selected.id));
        refreshRows();
        refreshRight();
    }

    private void rebuildList() {
        Widget cont = leftPort.cont;
        for(Widget ch = cont.lchild; ch != null;) {
            Widget prev = ch.prev;
            ch.reqdestroy();
            ch = prev;
        }
        rows.clear();
        String filter = (search == null || search.text() == null) ? "" : search.text().trim().toLowerCase(Locale.ROOT);
        boolean searching = !filter.isEmpty();
        int rowW = Math.max(UI.scale(48), leftPort.cont.sz.x);
        int y = 0;
        for(MoonAutoDrop.Category cat : MoonAutoDrop.CATEGORIES) {
            if(cat.foldGroup != null && !cat.foldHeader && !foldExpanded(cat, searching))
                continue;
            String hay = (cat.label() + " " + cat.desc() + " " + String.join(" ", cat.tokens)).toLowerCase(Locale.ROOT);
            if(searching && !hay.contains(filter))
                continue;
            CategoryRow row = cont.add(new CategoryRow(cat, rowW), Coord.of(0, y));
            rows.add(row);
            y += row.sz.y + UI.scale(2);
        }
        leftPort.cont.update();
        refreshRows();
    }

    private void refreshRows() {
        for(CategoryRow row : rows)
            row.refresh();
    }

    private void refreshRight() {
        if(selected == null) {
            selectedLbl.settext("-");
            descBox.settext("");
            tokensBox.settext("");
            toggleBtn.change(text("Включить категорию", "Enable category"));
            if(selectedItemPreview != null)
                selectedItemPreview.clear();
            if(selectedWallPreview != null)
                selectedWallPreview.clear();
            if(wallPreviewLbl != null)
                wallPreviewLbl.setcolor(new Color(160, 160, 160));
            refreshSummary();
            return;
        }
        boolean on = MoonAutoDrop.isSelected(selected.id);
        selectedLbl.settext(selected.label() + (on ? text(" [вкл]", " [on]") : text(" [выкл]", " [off]")));
        descBox.settext(selected.desc());
        tokensBox.settext(text("Примеры совпадений:\n", "Match examples:\n") + formatTokenList(selected));
        toggleBtn.change(on ? text("Выключить категорию", "Disable category") : text("Включить категорию", "Enable category"));
        String wallRes = wallPreviewRes(selected);
        if(selectedItemPreview != null)
            selectedItemPreview.setPreviewRes(selected.previewRes);
        if(selectedWallPreview != null)
            selectedWallPreview.setPreviewRes(wallRes);
        if(wallPreviewLbl != null)
            wallPreviewLbl.setcolor((wallRes != null) ? Color.WHITE : new Color(160, 160, 160));
        refreshSummary();
    }

    private String formatTokenList(MoonAutoDrop.Category c) {
        final int max = 26;
        if(c.tokens.length <= max)
            return String.join(", ", c.tokens);
        return String.join(", ", Arrays.copyOf(c.tokens, max))
            + "\n... " + text("и ещё ", "and ") + (c.tokens.length - max) + text(" токенов", " tokens");
    }

    private void refreshSummary() {
        int count = MoonAutoDrop.selectedIds().size();
        String mode = MoonConfig.mineAutoDropEnabled ? text("активен", "enabled") : text("выключен", "disabled");
        summaryLbl.settext(text("Режим: ", "Mode: ") + mode + " | " +
            text("категорий выбрано: ", "selected categories: ") + count);
    }

    @Override
    protected void onCloseClicked() {
        reqdestroy();
    }

    @Override
    public void remove() {
        clearPreviewCache();
        if(gui != null && gui.moonAutoDropWnd == this)
            gui.moonAutoDropWnd = null;
        super.remove();
    }

    private static String text(String ru, String en) {
        return LocalizationManager.LANG_RU.equals(MoonL10n.lang()) ? ru : en;
    }

    private BufferedImage loadPreviewImage(String previewRes) {
        if(previewRes == null || previewRes.isBlank())
            return null;
        BufferedImage img = loadPreviewLayer(Resource.remote().load(previewRes));
        if(img != null)
            return img;
        if(previewRes.startsWith("gfx/tiles/rocks/")) {
            String token = resBaseName(previewRes);
            BufferedImage alt = loadPreviewLayer(Resource.remote().load("gfx/invobjs/" + token));
            if(alt != null)
                return alt;
        }
        try {
            Resource res = Resource.local().load(previewRes).get();
            if(res != null) {
                Resource.Image layer = res.layer(Resource.imgc);
                if(layer != null && layer.img != null)
                    return layer.img;
            }
        } catch(Loading l) {
            throw l;
        } catch(Exception ignored) {
        }
        if(gui != null && gui.ui != null) {
            try {
                ItemSpec sp = new ItemSpec(OwnerContext.uictx.curry(gui.ui),
                    new ResData(Resource.remote().load(previewRes), Message.nil), null);
                BufferedImage im = sp.image();
                if(im != null)
                    return im;
            } catch(Loading l) {
                throw l;
            } catch(Exception ignored) {
            }
            try {
                ItemSpec sp = new ItemSpec(OwnerContext.uictx.curry(gui.ui),
                    new ResData(Resource.local().load(previewRes), Message.nil), null);
                BufferedImage im = sp.image();
                if(im != null)
                    return im;
            } catch(Loading l) {
                throw l;
            } catch(Exception ignored) {
            }
        }
        try {
            BufferedImage im = Resource.loadimg(previewRes);
            if(im != null)
                return im;
        } catch(Loading l) {
            throw l;
        } catch(Exception ignored) {
        }
        return null;
    }

    private static BufferedImage loadPreviewLayer(Indir<Resource> indir) {
        try {
            Resource res = indir.get();
            if(res == null)
                return null;
            Resource.Image layer = res.layer(Resource.imgc);
            return (layer != null) ? layer.img : null;
        } catch(Loading l) {
            throw l;
        } catch(Exception ignored) {
            return null;
        }
    }

    private static String resBaseName(String res) {
        if(res == null || res.isBlank())
            return null;
        int p = res.lastIndexOf('/');
        return (p >= 0) ? res.substring(p + 1) : res;
    }

    private static boolean hasWallPreview(MoonAutoDrop.Category cat) {
        if(cat == null)
            return false;
        if("stone".equals(cat.foldGroup) || "ore".equals(cat.foldGroup) || "coal".equals(cat.foldGroup))
            return true;
        return cat.id.startsWith("st_") || cat.id.startsWith("ore_") || cat.id.startsWith("coal_")
            || "stone_any".equals(cat.id) || "ore_any".equals(cat.id) || "coal_any".equals(cat.id);
    }

    private static String wallPreviewRes(MoonAutoDrop.Category cat) {
        if(!hasWallPreview(cat))
            return null;
        String token = resBaseName(cat.previewRes);
        if((token == null || token.isBlank()) && (cat.tokens.length > 0))
            token = cat.tokens[0];
        if(token == null || token.isBlank())
            return null;
        return "gfx/tiles/rocks/" + token;
    }

    private static Coord fitIcon(BufferedImage img, Coord box) {
        Coord src = Utils.imgsz(img);
        if(src.x <= 0 || src.y <= 0)
            return box;
        double scale = Math.min((double)box.x / (double)src.x, (double)box.y / (double)src.y);
        Coord out = Coord.of(Math.max(1, (int)Math.round(src.x * scale)), Math.max(1, (int)Math.round(src.y * scale)));
        return Coord.of(Math.min(box.x, out.x), Math.min(box.y, out.y));
    }

    private Tex previewTex(String previewRes, Coord box) {
        if(previewRes == null || previewRes.isBlank() || missingPreviewRes.contains(previewRes))
            return null;
        String key = previewRes + "@" + box.x + "x" + box.y;
        Tex cached = previewTexCache.get(key);
        if(cached != null)
            return cached;
        BufferedImage img = loadPreviewImage(previewRes);
        if(img == null) {
            missingPreviewRes.add(previewRes);
            return null;
        }
        Coord sz = fitIcon(img, box);
        Tex tex = new TexI(PUtils.uiscale(img, sz), false);
        previewTexCache.put(key, tex);
        return tex;
    }

    private void clearPreviewCache() {
        for(Tex tex : previewTexCache.values()) {
            if(tex != null)
                tex.dispose();
        }
        previewTexCache.clear();
        missingPreviewRes.clear();
    }

    private final class CategoryRow extends Widget {
        private static final int ICON = UI.scale(30);

        private final MoonAutoDrop.Category cat;
        private final int indent;
        private boolean hover = false;

        private CategoryRow(MoonAutoDrop.Category cat, int rowW) {
            super(Coord.of(rowW, UI.scale(38)));
            this.cat = cat;
            this.indent = rowIndent(cat);
        }

        private static int rowIndent(MoonAutoDrop.Category c) {
            if(c.foldHeader || c.foldGroup == null)
                return 0;
            return UI.scale(12);
        }

        private void refresh() {
        }

        private boolean foldOpen() {
            return !collapsedFoldGroups.contains(cat.foldGroup);
        }

        @Override
        public void draw(GOut g) {
            boolean sel = (cat == selected);
            boolean on = MoonAutoDrop.isSelected(cat.id);
            Tex iconTex = null;
            try {
                iconTex = previewTex(cat.previewRes, Coord.of(ICON, ICON));
            } catch(Loading ignored) {
            }
            Color bg = sel ? new Color(108, 104, 30, 220) : (hover ? new Color(72, 68, 90, 180) : new Color(40, 36, 56, 140));
            g.chcolor(bg);
            g.frect(Coord.z, sz);
            g.chcolor(sel ? MoonPanel.MOON_ACCENT : MoonPanel.MOON_BORDER);
            g.rect(Coord.z, sz.sub(1, 1));
            g.chcolor();

            int x = indent;
            if(cat.foldHeader && cat.foldGroup != null) {
                String chev = foldOpen() ? "▼" : "▶";
                g.aimage(Text.std.render(chev, Color.WHITE).tex(), Coord.of(x + CHEVRON_W / 2, sz.y / 2), 0.5, 0.5);
                x += CHEVRON_W + UI.scale(4);
            }

            int iconY = (sz.y - ICON) / 2;
            int textX = x + UI.scale(6);
            if(iconTex != null) {
                Coord box = Coord.of(ICON, ICON);
                Coord ic = Coord.of(x + UI.scale(2), iconY);
                Coord isz = iconTex.sz();
                g.image(iconTex, ic.add((box.x - isz.x) / 2, (box.y - isz.y) / 2));
                textX = x + UI.scale(8) + ICON;
            } else if(cat.previewRes != null) {
                g.chcolor(48, 44, 62, 255);
                g.frect(Coord.of(x + UI.scale(2), iconY), Coord.of(ICON, ICON));
                g.chcolor(MoonPanel.MOON_BORDER);
                g.rect(Coord.of(x + UI.scale(2), iconY), Coord.of(ICON, ICON).sub(1, 1));
                g.chcolor();
                textX = x + UI.scale(8) + ICON;
            }
            g.aimage(Text.std.render((on ? "[x] " : "[ ] ") + cat.label(), on ? MoonPanel.MOON_ACCENT : Color.WHITE).tex(),
                Coord.of(textX, sz.y / 2), 0.0, 0.5);
        }

        @Override
        public boolean mousedown(MouseDownEvent ev) {
            if(ev.b != 1)
                return false;
            if(cat.foldHeader && cat.foldGroup != null) {
                int chev0 = indent;
                int chev1 = indent + CHEVRON_W + UI.scale(2);
                if(ev.c.x >= chev0 && ev.c.x < chev1) {
                    toggleFoldGroup(cat.foldGroup);
                    return true;
                }
            }
            selected = cat;
            refreshRows();
            refreshRight();
            return true;
        }

        @Override
        public void mousemove(MouseMoveEvent ev) {
            hover = ev.c.isect(Coord.z, sz);
            super.mousemove(ev);
        }
    }

    private final class SelectedResPreview extends Widget {
        private static final int BOX = UI.scale(52);

        private final boolean wallMode;
        private String previewRes;

        private SelectedResPreview(boolean wallMode) {
            super(Coord.of(BOX, BOX));
            this.wallMode = wallMode;
        }

        private void clear() {
            previewRes = null;
        }

        private void setPreviewRes(String previewRes) {
            if(Utils.eq(this.previewRes, previewRes))
                return;
            this.previewRes = previewRes;
        }

        @Override
        public void draw(GOut g) {
            if(wallMode) {
                g.chcolor(34, 32, 44, 255);
                g.frect(Coord.z, sz);
                g.chcolor(60, 58, 76, 255);
                for(int y = UI.scale(5); y < sz.y; y += UI.scale(10))
                    g.line(Coord.of(UI.scale(3), y), Coord.of(sz.x - UI.scale(4), y + UI.scale(2)), 1);
                g.chcolor(94, 92, 112, 110);
                for(int x = UI.scale(6); x < sz.x; x += UI.scale(11))
                    g.line(Coord.of(x, UI.scale(3)), Coord.of(x - UI.scale(2), sz.y - UI.scale(4)), 1);
            } else {
                g.chcolor(28, 24, 40, 255);
                g.frect(Coord.z, sz);
            }
            g.chcolor(MoonPanel.MOON_BORDER);
            g.rect(Coord.z, sz.sub(1, 1));
            g.chcolor();
            Tex tex = null;
            if(previewRes != null) {
                try {
                    tex = previewTex(previewRes, sz.sub(UI.scale(6), UI.scale(6)));
                } catch(Loading ignored) {
                }
            }
            if(tex != null) {
                Coord margin = Coord.of(UI.scale(3), UI.scale(3));
                Coord box = sz.sub(margin.mul(2));
                Coord isz = tex.sz();
                g.image(tex, margin.add((box.x - isz.x) / 2, (box.y - isz.y) / 2));
            } else if(wallMode) {
                g.aimage(Text.std.render("?", new Color(220, 220, 220, 180)).tex(), sz.div(2), 0.5, 0.5);
            }
        }
    }
}
