package app.kaziyami.core

/**
 * Structured exception thrown when a critical page element selector yields zero results
 * on a page that is not an explicit empty state.
 *
 * Guarantees that no sensitive tokens, cookies, or full query strings leak into UI notifications or crash logs.
 */
class SourceParseException : RuntimeException {
    val source: String
    val pageType: String
    val selectorName: String
    val urlPath: String

    constructor(
        source: String,
        pageType: String,
        selectorName: String,
        urlPath: String,
        cause: Throwable? = null
    ) : super("Kaziyami: $source could not read the $pageType; the site layout may have changed", cause) {
        this.source = source
        this.pageType = pageType
        this.selectorName = selectorName
        this.urlPath = urlPath
    }

    constructor(message: String, cause: Throwable? = null) : super(message, cause) {
        this.source = "Kaziyami"
        this.pageType = "custom"
        this.selectorName = ""
        this.urlPath = message
    }

    /**
     * Sanitized diagnostic message for developer logs (free of query tokens and cookies).
     */
    val diagnosticDetails: String
        get() = "Source: $source, PageType: $pageType, Selector: $selectorName, Path: ${urlPath.substringBefore('?')}"
}
