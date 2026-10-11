package com.example.offlinemediavault;

import java.util.HashMap;
import java.util.Map;

/** Все тексты интерфейса: en, ru, es, fa. */
final class Strings {
    static final String[] LANGS = {"en", "ru", "es", "fa"};
    static final String[] NAMES = {"English", "Русский", "Español", "فارسی"};
    private static final Map<String, String[]> M = new HashMap<>();

    private static void a(String k, String en, String ru, String es, String fa) {
        M.put(k, new String[]{en, ru, es, fa});
    }

    static String norm(String l) {
        if (l == null) return "en";
        for (String s : LANGS) if (s.equals(l)) return s;
        return "en";
    }

    static String get(String lang, String key) {
        String[] v = M.get(key);
        if (v == null) return key;
        int i = 0;
        for (int j = 0; j < LANGS.length; j++) if (LANGS[j].equals(lang)) i = j;
        return v[i];
    }

    static {
        a("tab_feed", "Feed", "Лента", "Feed", "فید");
        a("tab_albums", "Albums", "Альбомы", "Álbumes", "آلبوم‌ها");
        a("tab_upload", "Upload", "Загрузка", "Subir", "بارگذاری");
        a("tab_settings", "Settings", "Настройки", "Ajustes", "تنظیمات");
        a("ok", "OK", "OK", "OK", "تأیید");
        a("cancel", "Cancel", "Отмена", "Cancelar", "لغو");
        a("save", "Save", "Сохранить", "Guardar", "ذخیره");
        a("rename", "Rename", "Переименовать", "Renombrar", "تغییر نام");
        a("apply", "Apply", "Применить", "Aplicar", "اعمال");
        a("reset", "Reset", "Сбросить", "Restablecer", "بازنشانی");
        a("yes", "Yes", "Да", "Sí", "بله");
        a("on", "On", "Вкл", "Act.", "روشن");
        a("off", "Off", "Выкл", "Des.", "خاموش");
        a("filter", "Filter", "Фильтр", "Filtro", "فیلتر");
        a("count_of", "%d of %d", "%d из %d", "%d de %d", "%d از %d");
        a("with_tags", "with tags: %s", "с тегами: %s", "con etiquetas: %s", "با برچسب: %s");
        a("hidden_tags", "hidden: %s", "скрыто: %s", "ocultas: %s", "پنهان: %s");
        a("oldest_first_note", "oldest first", "старые сверху", "más antiguos primero", "قدیمی‌ترین ابتدا");
        a("empty_all", "Nothing here yet. Add files on the Upload tab.", "Пока пусто. Загрузи файлы на вкладке «Загрузка».", "Aún no hay nada. Añade archivos en la pestaña Subir.", "هنوز چیزی نیست. فایل‌ها را در تب بارگذاری اضافه کنید.");
        a("empty_filter", "No files match the filters.", "Нет файлов с такими фильтрами.", "Ningún archivo coincide con los filtros.", "هیچ فایلی با فیلترها مطابقت ندارد.");
        a("filter_title", "Feed filter", "Фильтр ленты", "Filtro del feed", "فیلتر فید");
        a("sec_tags", "TAGS · show only files with them", "ТЕГИ · показывать только с ними", "ETIQUETAS · mostrar solo archivos con ellas", "برچسب‌ها · فقط فایل‌های دارای آن‌ها");
        a("sec_black", "BLACK TAGS · hide files with them", "ЧЁРНЫЕ ТЕГИ · скрывать файлы с ними", "ETIQUETAS NEGRAS · ocultar archivos con ellas", "برچسب‌های سیاه · پنهان‌کردن فایل‌های دارای آن‌ها");
        a("sec_match", "MATCH MODE", "РЕЖИМ СОВПАДЕНИЯ", "MODO DE COINCIDENCIA", "حالت تطبیق");
        a("match_all", "All tags", "Все теги", "Todas las etiquetas", "همه برچسب‌ها");
        a("match_any", "At least one", "Хотя бы один", "Al menos una", "حداقل یکی");
        a("sec_sort", "SORT BY UPLOAD TIME", "СОРТИРОВКА ПО ВРЕМЕНИ ЗАГРУЗКИ", "ORDENAR POR FECHA DE CARGA", "مرتب‌سازی بر اساس زمان بارگذاری");
        a("sort_new", "Newest first", "Сначала новые", "Más nuevos primero", "جدیدترین ابتدا");
        a("sort_old", "Oldest first", "Сначала старые", "Más antiguos primero", "قدیمی‌ترین ابتدا");
        a("clear_sel", "Clear selected tags", "Очистить выбранные теги", "Borrar etiquetas seleccionadas", "پاک‌کردن برچسب‌های انتخاب‌شده");
        a("tag_search", "Search tags", "Поиск тега", "Buscar etiqueta", "جستجوی برچسب");
        a("no_tags_yet", "No tags yet. Add them on the Upload tab (✎ button).", "Тегов пока нет. Добавь их на вкладке «Загрузка» (кнопка ✎).", "Aún no hay etiquetas. Añádelas en la pestaña Subir (botón ✎).", "هنوز برچسبی نیست. آن‌ها را در تب بارگذاری (دکمه ✎) اضافه کنید.");
        a("no_tag_found", "No tags found", "Такой тег не найден", "No se encontraron etiquetas", "برچسبی پیدا نشد");
        a("new_album", "New album", "Новый альбом", "Nuevo álbum", "آلبوم جدید");
        a("album_name", "Album name", "Название альбома", "Nombre del álbum", "نام آلبوم");
        a("del_album", "Delete album", "Удалить альбом", "Eliminar álbum", "حذف آلبوم");
        a("del_album_msg", "The album will be removed. Files stay in the library.", "Альбом будет удалён, файлы останутся в библиотеке.", "El álbum se eliminará. Los archivos permanecen en la biblioteca.", "آلبوم حذف می‌شود. فایل‌ها در کتابخانه می‌مانند.");
        a("no_albums", "No albums yet. They are created automatically when you add a ZIP or a manga PDF.", "Альбомов пока нет. Они создаются автоматически при добавлении ZIP или манги (PDF).", "Aún no hay álbumes. Se crean automáticamente al añadir un ZIP o un manga PDF.", "هنوز آلبومی نیست. هنگام افزودن ZIP یا مانگا (PDF) خودکار ساخته می‌شوند.");
        a("items_n", "%d items", "Файлов: %d", "%d archivos", "%d فایل");
        a("album_empty", "This album is empty.", "Этот альбом пуст.", "Este álbum está vacío.", "این آلبوم خالی است.");
        a("pick_album", "Choose album", "Выбери альбом", "Elige un álbum", "آلبوم را انتخاب کنید");
        a("no_album", "No album", "Без альбома", "Sin álbum", "بدون آلبوم");
        a("moved_album", "Moved: %d", "Перемещено: %d", "Movidos: %d", "منتقل شد: %d");
        a("add_files", "+ Files", "+ Файлы", "+ Archivos", "+ فایل");
        a("add_zip", "Import ZIP", "Импорт ZIP", "Importar ZIP", "درون‌ریزی ZIP");
        a("add_manga", "Manga (PDF)", "Манга (PDF)", "Manga (PDF)", "مانگا (PDF)");
        a("search_hint", "Search by name or tags", "Поиск по названию или тегам", "Buscar por nombre o etiquetas", "جستجو بر اساس نام یا برچسب");
        a("uploaded", "UPLOADED FILES", "ЗАГРУЖЕННЫЕ ФАЙЛЫ", "ARCHIVOS SUBIDOS", "فایل‌های بارگذاری‌شده");
        a("tap_add_tags", "Tap ✎ to add tags", "Нажми ✎, чтобы добавить теги", "Pulsa ✎ para añadir etiquetas", "برای افزودن برچسب ✎ را بزنید");
        a("upload_empty", "Nothing yet. Add photos, videos, a ZIP or a manga PDF.", "Пока пусто. Добавь фото, видео, ZIP или мангу (PDF).", "Aún nada. Añade fotos, vídeos, un ZIP o un manga PDF.", "هنوز چیزی نیست. عکس، ویدیو، ZIP یا مانگا (PDF) اضافه کنید.");
        a("upload_nomatch", "Nothing found. Try other words or tags.", "Ничего не найдено. Попробуй другие слова или теги.", "Nada encontrado. Prueba otras palabras o etiquetas.", "چیزی پیدا نشد. کلمات یا برچسب‌های دیگر را امتحان کنید.");
        a("stored_local", "Files are stored only on this device.", "Файлы хранятся только на этом устройстве.", "Los archivos se guardan solo en este dispositivo.", "فایل‌ها فقط روی این دستگاه ذخیره می‌شوند.");
        a("kind_photo", "Photo", "Фото", "Foto", "عکس");
        a("kind_video", "Video", "Видео", "Vídeo", "ویدیو");
        a("kind_manga", "Manga PDF", "Манга PDF", "Manga PDF", "مانگا PDF");
        a("sel_n", "%d selected", "Выбрано: %d", "%d seleccionados", "%d انتخاب‌شده");
        a("sel_all", "Select all", "Выбрать все", "Seleccionar todo", "انتخاب همه");
        a("act_tags", "Tags", "Теги", "Etiquetas", "برچسب‌ها");
        a("act_album", "To album", "В альбом", "Al álbum", "به آلبوم");
        a("act_trash", "To trash", "В корзину", "A la papelera", "به سطل زباله");
        a("tags_add", "Add", "Добавить", "Añadir", "افزودن");
        a("tags_replace", "Replace", "Заменить", "Reemplazar", "جایگزینی");
        a("tags_hint", "e.g. horror, animation, tiktok", "например: horror, animation, tiktok", "p. ej.: terror, animación, tiktok", "مثلاً: horror, animation, tiktok");
        a("moved_trash", "Moved to trash: %d", "В корзину: %d", "A la papelera: %d", "به سطل زباله: %d");
        a("trash", "Trash", "Корзина", "Papelera", "سطل زباله");
        a("trash_empty", "Trash is empty.", "Корзина пуста.", "La papelera está vacía.", "سطل زباله خالی است.");
        a("trash_hint", "Files stay here until you delete them permanently.", "Файлы лежат здесь, пока ты не удалишь их навсегда.", "Los archivos permanecen aquí hasta que los elimines para siempre.", "فایل‌ها تا زمانی که برای همیشه حذف نکنید اینجا می‌مانند.");
        a("restore", "Restore", "Восстановить", "Restaurar", "بازیابی");
        a("restore_all", "Restore all", "Восстановить всё", "Restaurar todo", "بازیابی همه");
        a("del_forever", "Delete forever", "Удалить навсегда", "Eliminar para siempre", "حذف دائمی");
        a("empty_trash", "Empty trash", "Очистить корзину", "Vaciar papelera", "خالی‌کردن سطل زباله");
        a("confirm_forever", "Delete %d file(s) permanently? This cannot be undone.", "Удалить файлов навсегда: %d? Это нельзя отменить.", "¿Eliminar %d archivo(s) para siempre? No se puede deshacer.", "%d فایل برای همیشه حذف شود؟ این کار قابل بازگشت نیست.");
        a("restored", "Restored: %d", "Восстановлено: %d", "Restaurados: %d", "بازیابی شد: %d");
        a("deleted", "Deleted: %d", "Удалено: %d", "Eliminados: %d", "حذف شد: %d");
        a("importing", "Importing…", "Импорт…", "Importando…", "در حال درون‌ریزی…");
        a("imported_n", "Added files: %d", "Добавлено файлов: %d", "Archivos añadidos: %d", "فایل‌های افزوده‌شده: %d");
        a("skipped", "Skipped unsupported file: %s", "Пропущен неподдерживаемый файл: %s", "Archivo no compatible omitido: %s", "فایل پشتیبانی‌نشده رد شد: %s");
        a("import_failed", "Could not import %s", "Не удалось импортировать %s", "No se pudo importar %s", "درون‌ریزی %s ناموفق بود");
        a("zip_failed", "Could not read ZIP: %s", "Не удалось прочитать ZIP: %s", "No se pudo leer el ZIP: %s", "خواندن ZIP ناموفق بود: %s");
        a("album_created", "Album created: %s", "Создан альбом: %s", "Álbum creado: %s", "آلبوم ساخته شد: %s");
        a("no_tags", "No tags", "Без тегов", "Sin etiquetas", "بدون برچسب");
        a("missing", "File not found", "Файл не найден", "Archivo no encontrado", "فایل پیدا نشد");
        a("unsupported", "This format cannot be previewed", "Формат не поддерживается для предпросмотра", "Formato sin vista previa", "این قالب قابل پیش‌نمایش نیست");
        a("page_n", "Page %d / %d", "Стр. %d / %d", "Pág. %d / %d", "صفحه %d / %d");
        a("pdf_failed", "Could not open the PDF", "Не удалось открыть PDF", "No se pudo abrir el PDF", "باز کردن PDF ناموفق بود");
        a("set_language", "Language", "Язык", "Idioma", "زبان");
        a("set_appearance", "Appearance", "Внешний вид", "Apariencia", "ظاهر");
        a("set_theme", "Theme", "Тема", "Tema", "تم");
        a("theme_system", "System", "Системная", "Sistema", "سیستم");
        a("theme_dark", "Dark", "Тёмная", "Oscuro", "تیره");
        a("theme_light", "Light", "Светлая", "Claro", "روشن");
        a("set_columns", "Gallery columns", "Столбцов в галерее", "Columnas de la galería", "ستون‌های گالری");
        a("set_names", "Show file names", "Показывать названия файлов", "Mostrar nombres de archivo", "نمایش نام فایل‌ها");
        a("set_security", "Security", "Безопасность", "Seguridad", "امنیت");
        a("pin_code", "PIN code", "ПИН-код", "Código PIN", "رمز PIN");
        a("pin_on", "Enabled", "Включён", "Activado", "فعال");
        a("pin_off", "Disabled", "Выключен", "Desactivado", "غیرفعال");
        a("pin_set", "Set PIN", "Установить ПИН", "Establecer PIN", "تنظیم PIN");
        a("pin_change", "Change PIN", "Сменить ПИН", "Cambiar PIN", "تغییر PIN");
        a("pin_remove", "Remove PIN", "Убрать ПИН", "Quitar PIN", "حذف PIN");
        a("pin_enter", "Enter PIN", "Введи ПИН-код", "Introduce el PIN", "PIN را وارد کنید");
        a("pin_current", "Current PIN", "Текущий ПИН-код", "PIN actual", "PIN فعلی");
        a("pin_new", "New PIN (4–8 digits)", "Новый ПИН-код (4–8 цифр)", "Nuevo PIN (4–8 dígitos)", "PIN جدید (۴ تا ۸ رقم)");
        a("pin_repeat", "Repeat PIN", "Повтори ПИН-код", "Repite el PIN", "PIN را تکرار کنید");
        a("pin_mismatch", "PINs do not match", "ПИН-коды не совпадают", "Los PIN no coinciden", "PINها مطابقت ندارند");
        a("pin_wrong", "Wrong PIN", "Неверный ПИН-код", "PIN incorrecto", "PIN اشتباه است");
        a("pin_short", "PIN must be 4–8 digits", "ПИН-код: 4–8 цифр", "El PIN debe tener 4–8 dígitos", "PIN باید ۴ تا ۸ رقم باشد");
        a("pin_warn", "If you forget the PIN, it cannot be recovered. The PIN only locks the app entrance; files on the device are not encrypted.", "Если забудешь ПИН-код, восстановить его нельзя. ПИН блокирует только вход в приложение, файлы на устройстве не шифруются.", "Si olvidas el PIN, no se puede recuperar. El PIN solo bloquea la entrada a la app; los archivos del dispositivo no se cifran.", "اگر PIN را فراموش کنید، قابل بازیابی نیست. PIN فقط ورود به برنامه را قفل می‌کند؛ فایل‌های دستگاه رمزگذاری نمی‌شوند.");
        a("pin_saved", "PIN saved", "ПИН-код сохранён", "PIN guardado", "PIN ذخیره شد");
        a("pin_removed", "PIN removed", "ПИН-код убран", "PIN eliminado", "PIN حذف شد");
        a("pin_blocked", "Too many attempts. Wait %d s.", "Слишком много попыток. Подожди %d с.", "Demasiados intentos. Espera %d s.", "تلاش‌های زیاد. %d ثانیه صبر کنید.");
        a("set_data", "Data", "Данные", "Datos", "داده‌ها");
        a("export_all", "Export all content to ZIP", "Выгрузить весь контент в ZIP", "Exportar todo el contenido a ZIP", "خروجی‌گرفتن از همه محتوا در ZIP");
        a("export_hint", "Includes tags and albums. You can import this ZIP back.", "Вместе с тегами и альбомами. Этот ZIP можно импортировать обратно.", "Incluye etiquetas y álbumes. Puedes volver a importar este ZIP.", "شامل برچسب‌ها و آلبوم‌ها. این ZIP را می‌توانید دوباره درون‌ریزی کنید.");
        a("exporting", "Exporting…", "Выгрузка…", "Exportando…", "در حال خروجی‌گرفتن…");
        a("export_done", "Export finished: %d files", "Выгрузка завершена: файлов %d", "Exportación terminada: %d archivos", "خروجی کامل شد: %d فایل");
        a("export_failed", "Export failed: %s", "Не удалось выгрузить: %s", "Error al exportar: %s", "خروجی ناموفق بود: %s");
        a("delete_all", "Delete all content", "Удалить весь контент", "Eliminar todo el contenido", "حذف همه محتوا");
        a("delete_all_msg", "Delete ALL files, albums and trash from this device?", "Удалить ВСЕ файлы, альбомы и корзину с этого устройства?", "¿Eliminar TODOS los archivos, álbumes y la papelera de este dispositivo?", "همه فایل‌ها، آلبوم‌ها و سطل زباله از این دستگاه حذف شوند؟");
        a("delete_all_q1", "Are you sure?", "Ты уверен?", "¿Seguro?", "مطمئن هستید؟");
        a("delete_all_q2", "Absolutely sure? This cannot be undone.", "Точно уверен? Это нельзя отменить.", "¿Totalmente seguro? No se puede deshacer.", "کاملاً مطمئن هستید؟ این کار قابل بازگشت نیست.");
        a("yes_delete_all", "Yes, delete everything", "Да, удалить всё", "Sí, eliminar todo", "بله، همه را حذف کن");
        a("all_deleted", "All content deleted", "Весь контент удалён", "Todo el contenido eliminado", "همه محتوا حذف شد");
        a("reset_settings", "Reset settings to defaults", "Сбросить настройки", "Restablecer ajustes", "بازنشانی تنظیمات");
        a("reset_msg", "Language, theme, columns and file names return to defaults. PIN and content are not affected.", "Язык, тема, столбцы и названия вернутся к значениям по умолчанию. ПИН-код и контент не затрагиваются.", "El idioma, el tema, las columnas y los nombres volverán a los valores por defecto. El PIN y el contenido no se ven afectados.", "زبان، تم، ستون‌ها و نام فایل‌ها به حالت پیش‌فرض برمی‌گردند. PIN و محتوا تغییر نمی‌کنند.");
        a("reset_done", "Settings reset", "Настройки сброшены", "Ajustes restablecidos", "تنظیمات بازنشانی شد");
    }
}
