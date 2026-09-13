package com.filish.core.model

/**
 * What a file *is*, from the user's point of view.
 *
 * This is a classification of intent, not of format. A user does not think
 * "image/vnd.adobe.photoshop", they think "a picture". The taxonomy is
 * therefore as coarse as it can be while still supporting the decisions
 * FILISH needs to make: which viewer opens it, which glyph it gets, which
 * bucket it counts toward in storage analysis, and whether it is a plausible
 * candidate for cleanup.
 *
 * RAW is split out from Image deliberately. RAW files are typically 10-25x
 * the size of the JPEG beside them and are the single largest avoidable
 * consumer of storage on a phone belonging to anyone who shoots photographs.
 * Collapsing them into "Images" hides the one fact that would let the user
 * act. This is exactly the kind of distinction storage analysis exists for.
 */
enum class FileKind {
    Folder,
    Image,
    RawImage,
    Video,
    Audio,
    Document,
    Pdf,
    Text,
    Code,
    Archive,
    App,
    Font,
    Other;

    val isMedia: Boolean get() = this == Image || this == RawImage || this == Video || this == Audio

    /** Can FILISH itself open this, or must it hand off to another app? */
    val hasBuiltInViewer: Boolean
        get() = this == Image || this == RawImage || this == Video ||
            this == Audio || this == Pdf || this == Text || this == Code

    val label: String
        get() = when (this) {
            Folder -> "Folder"
            Image -> "Image"
            RawImage -> "RAW image"
            Video -> "Video"
            Audio -> "Audio"
            Document -> "Document"
            Pdf -> "PDF"
            Text -> "Text"
            Code -> "Code"
            Archive -> "Archive"
            App -> "App package"
            Font -> "Font"
            Other -> "File"
        }
}

/**
 * Extension to kind.
 *
 * Extension-based rather than content-based, and that is a considered
 * trade-off. Sniffing magic bytes means opening every file in a directory -
 * tens of thousands of syscalls to draw one screen. Extensions are wrong
 * occasionally; opening every file is wrong constantly, by being unusably
 * slow. FILISH classifies by extension for listing and only inspects content
 * where the answer actually matters (the viewers, which must know, and
 * duplicate detection, which hashes anyway).
 */
object Kinds {

    private val raw = setOf(
        // Only formats with unambiguous extensions are listed. Vendors reuse
        // extensions across unrelated formats; when in doubt it is better to
        // call a RAW file an image than to call an image a RAW file, because
        // the RAW bucket is the one the user will act on.
        "dng", "cr2", "cr3", "crw", "nef", "nrw", "arw", "srf", "sr2",
        "raf", "orf", "rw2", "rwl", "pef", "ptx", "srw", "erf", "mrw",
        "mos", "iiq", "3fr", "fff", "x3f", "dcr", "kdc", "mef", "gpr",
    )

    private val image = setOf(
        "jpg", "jpeg", "jpe", "jfif", "png", "gif", "bmp", "webp", "heic",
        "heif", "avif", "tif", "tiff", "ico", "svg", "psd", "xcf", "jxl",
    )

    private val video = setOf(
        "mp4", "m4v", "mkv", "webm", "avi", "mov", "3gp", "3g2", "flv",
        "wmv", "mpg", "mpeg", "mts", "m2ts", "ts", "ogv", "f4v", "rmvb", "asf",
    )

    private val audio = setOf(
        "mp3", "m4a", "aac", "flac", "wav", "ogg", "oga", "opus", "wma",
        "amr", "mid", "midi", "aiff", "aif", "ape", "mka", "ac3", "dsf", "wv",
    )

    private val pdf = setOf("pdf")

    private val document = setOf(
        "doc", "docx", "odt", "rtf", "xls", "xlsx", "ods", "csv", "tsv",
        "ppt", "pptx", "odp", "epub", "mobi", "azw", "azw3", "djvu", "pages",
        "numbers", "key",
    )

    private val text = setOf(
        "txt", "log", "md", "markdown", "rst", "nfo", "srt", "vtt", "ass",
        "ssa", "sub", "ini", "cfg", "conf", "properties", "env", "readme",
    )

    private val code = setOf(
        "kt", "kts", "java", "c", "h", "cpp", "hpp", "cc", "cs", "py", "rb",
        "js", "mjs", "cjs", "ts", "tsx", "jsx", "go", "rs", "swift", "php",
        "sh", "bash", "zsh", "bat", "ps1", "pl", "lua", "r", "scala", "dart",
        "sql", "json", "xml", "yaml", "yml", "toml", "html", "htm", "css",
        "scss", "sass", "less", "gradle", "proto", "graphql", "vue", "svelte",
    )

    private val archive = setOf(
        "zip", "tar", "gz", "tgz", "bz2", "tbz", "xz", "txz", "7z", "rar",
        "jar", "war", "iso", "cab", "lz", "lzma", "zst", "z", "arj", "ar",
    )

    private val app = setOf("apk", "apks", "xapk", "apkm", "aab", "obb")

    private val font = setOf("ttf", "otf", "woff", "woff2", "eot", "ttc", "fon")

    fun of(name: String, isDirectory: Boolean): FileKind {
        if (isDirectory) return FileKind.Folder
        val ext = extensionOf(name)
        if (ext.isEmpty()) return FileKind.Other
        return when (ext) {
            in raw -> FileKind.RawImage
            in image -> FileKind.Image
            in video -> FileKind.Video
            in audio -> FileKind.Audio
            in pdf -> FileKind.Pdf
            in document -> FileKind.Document
            in text -> FileKind.Text
            in code -> FileKind.Code
            in archive -> FileKind.Archive
            in app -> FileKind.App
            in font -> FileKind.Font
            else -> FileKind.Other
        }
    }

    /**
     * Extension, lowercased, without the dot.
     *
     * A leading dot means a hidden file, not an extension: ".bashrc" has no
     * extension, it is a file named ".bashrc". Getting this wrong makes every
     * dotfile in a directory claim to be a different type.
     */
    fun extensionOf(name: String): String {
        val dot = name.lastIndexOf('.')
        if (dot <= 0 || dot == name.length - 1) return ""
        // Compound extensions the user thinks of as one thing.
        val lower = name.lowercase()
        for (compound in arrayOf(".tar.gz", ".tar.bz2", ".tar.xz", ".tar.zst")) {
            if (lower.endsWith(compound)) return compound.substring(1)
        }
        return lower.substring(dot + 1)
    }

    /** Best-effort MIME type, used for share intents and hand-off. */
    fun mimeOf(name: String): String {
        val ext = extensionOf(name)
        if (ext.isEmpty()) return "application/octet-stream"
        android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)?.let { return it }
        return when (of(name, false)) {
            FileKind.Image, FileKind.RawImage -> "image/*"
            FileKind.Video -> "video/*"
            FileKind.Audio -> "audio/*"
            FileKind.Text, FileKind.Code -> "text/plain"
            FileKind.Pdf -> "application/pdf"
            else -> "application/octet-stream"
        }
    }
}
