package com.navio.damtests

/**
 * App-wide constants.
 *
 * When publishing a new GitHub Release with updated PDFs, bump the version tag
 * in [PDF_BASE_URL] to match the release tag (e.g. "v1.1").
 */
object Constants {

    /**
     * Base URL for PDF study notes hosted as assets in a GitHub Release.
     * Files are expected at: <PDF_BASE_URL><subjectId>_<topicNumber>.pdf
     * Example: https://github.com/alfonsonavio/DAMTest/releases/download/v1.0/base_de_datos_1.pdf
     */
    const val PDF_BASE_URL =
        "https://github.com/alfonsonavio/DAMTest/releases/download/v1.0.0-resources/"
}
