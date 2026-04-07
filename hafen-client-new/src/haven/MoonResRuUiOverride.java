package haven;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.imageio.ImageIO;

/**
 * Language-aware {@link Resource.Image} PNG overrides.
 *
 * <p>The legacy class name is kept so existing call sites stay compatible while the
 * implementation now supports both EN and RU UI packs.
 *
 * <p>Preferred layout:
 * <ul>
 *   <li>{@code res_ui/<lang>/*.png}</li>
 *   <li>{@code res_ui/*.png}</li>
 * </ul>
 *
 * <p>Search order:
 * <ol>
 *   <li>{@code HAFEN_RES_UI}</li>
 *   <li>{@code HAFEN_RES_<LANG>_UI}</li>
 *   <li>{@code -Dhaven.res.ui=...}</li>
 *   <li>{@code -Dhaven.res<lang>.ui=...}</li>
 *   <li>{@code ./res_ui}</li>
 *   <li>{@code ~/.haven/res_ui}</li>
 *   <li>legacy per-language dirs such as {@code ./res_ru/ui}</li>
 * </ol>
 *
 * <p><b>Naming</b> (first existing file wins):
 * <ol>
 *   <li>{@code <full_res_path_with_underscores>_<id>.png}</li>
 *   <li>{@code <full_res_path_with_underscores>.png} if {@code id == 0}</li>
 *   <li>{@code <last_segment>_<id>.png}</li>
 *   <li>{@code <last_segment>.png} if {@code id == 0}</li>
 *   <li>same as 3-4 with hyphens normalized to underscores and lower-case</li>
 * </ol>
 *
 * <p>Replacement size must match exactly; otherwise the original image is kept.
 */
public final class MoonResRuUiOverride {
    private MoonResRuUiOverride() {}

    public static BufferedImage apply(Resource res, int imageId, BufferedImage base) {
	if(base == null || res == null)
	    return(base);
	for(Path root : uiRoots(MoonL10n.lang())) {
	    Path file = findCandidate(root, res.name, imageId);
	    if(file == null)
		continue;
	    try {
		BufferedImage ovr = ImageIO.read(file.toFile());
		if(ovr == null)
		    continue;
		ovr = PUtils.coercergba(ovr);
		int w = base.getWidth(), h = base.getHeight();
		if(ovr.getWidth() != w || ovr.getHeight() != h) {
		    Warning.warn(String.format(Locale.ROOT,
			"res_ui: size mismatch %s (need %dx%d, got %dx%d) - using original",
			file.getFileName(), w, h, ovr.getWidth(), ovr.getHeight()));
		    return(base);
		}
		return(ovr);
	    } catch(IOException e) {
		new Warning(e, "res_ui: " + file).issue();
	    }
	}
	return(base);
    }

    private static List<Path> uiRoots(String lang) {
	String l = ((lang == null) || lang.isEmpty()) ? MoonL10n.LANG_EN : lang.toLowerCase(Locale.ROOT);
	Set<Path> roots = new LinkedHashSet<>();
	addRoot(roots, System.getenv("HAFEN_RES_UI"), l);
	addRoot(roots, System.getenv("HAFEN_RES_" + l.toUpperCase(Locale.ROOT) + "_UI"), l);
	addRoot(roots, System.getProperty("haven.res.ui", null), l);
	addRoot(roots, System.getProperty("haven.res" + l + ".ui", null), l);
	addRoot(roots, Paths.get("res_ui").toAbsolutePath().normalize(), l);
	addRoot(roots, Paths.get(System.getProperty("user.home", "."), ".haven", "res_ui").toAbsolutePath().normalize(), l);
	addLegacyRoot(roots, l);
	return(new ArrayList<>(roots));
    }

    private static void addRoot(Set<Path> out, String raw, String lang) {
	if((raw == null) || raw.isEmpty())
	    return;
	addRoot(out, Paths.get(raw), lang);
    }

    private static void addRoot(Set<Path> out, Path base, String lang) {
	if(base == null)
	    return;
	Path abs = base.toAbsolutePath().normalize();
	Path langDir = abs.resolve(lang);
	if(Files.isDirectory(langDir))
	    out.add(langDir);
	if(Files.isDirectory(abs))
	    out.add(abs);
    }

    private static void addLegacyRoot(Set<Path> out, String lang) {
	String upper = lang.toUpperCase(Locale.ROOT);
	addIfDir(out, System.getenv("HAFEN_RES_" + upper + "_UI"));
	addIfDir(out, System.getProperty("haven.res" + lang + ".ui", null));
	addIfDir(out, Paths.get("res_" + lang, "ui").toAbsolutePath().normalize());
	addIfDir(out, Paths.get(System.getProperty("user.home", "."), ".haven", "res_" + lang, "ui").toAbsolutePath().normalize());
	if(MoonL10n.LANG_RU.equals(lang)) {
	    addIfDir(out, System.getenv("HAFEN_RES_RU_UI"));
	    addIfDir(out, System.getProperty("haven.resru.ui", null));
	}
    }

    private static void addIfDir(Set<Path> out, String raw) {
	if((raw == null) || raw.isEmpty())
	    return;
	addIfDir(out, Paths.get(raw));
    }

    private static void addIfDir(Set<Path> out, Path path) {
	if(path == null)
	    return;
	Path abs = path.toAbsolutePath().normalize();
	if(Files.isDirectory(abs))
	    out.add(abs);
    }

    private static String slugPath(String resName) {
	return(resName.replace('/', '_'));
    }

    private static String lastSegment(String resName) {
	int i = resName.lastIndexOf('/');
	return((i < 0) ? resName : resName.substring(i + 1));
    }

    private static List<String> candidateNames(String resName, int id) {
	String slug = slugPath(resName);
	String last = lastSegment(resName);
	String norm = last.replace('-', '_').toLowerCase(Locale.ROOT);
	Set<String> order = new LinkedHashSet<>();
	order.add(slug + "_" + id + ".png");
	if(id == 0)
	    order.add(slug + ".png");
	order.add(last + "_" + id + ".png");
	if(id == 0)
	    order.add(last + ".png");
	order.add(norm + "_" + id + ".png");
	if(id == 0)
	    order.add(norm + ".png");
	return(new ArrayList<>(order));
    }

    private static Path findCandidate(Path root, String resName, int id) {
	for(String n : candidateNames(resName, id)) {
	    Path p = root.resolve(n);
	    if(Files.isRegularFile(p))
		return(p);
	}
	return(null);
    }
}
