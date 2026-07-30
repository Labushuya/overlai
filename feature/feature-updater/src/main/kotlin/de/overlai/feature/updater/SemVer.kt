package de.overlai.feature.updater

// CHANGE-MARKER v0.1.0: In-App-Updater (siehe CHANGELOG.md)
// Minimaler SemVer-Vergleich für den Updater. Vergleicht major.minor.patch
// (Pre-Release-Suffixe werden für den Vorwärts-Vergleich ignoriert). Reines
// Kotlin -> unit-testbar. Downgrade wird vom UpdateChecker abgelehnt (T4).
data class SemVer(
    val major: Int,
    val minor: Int,
    val patch: Int,
) : Comparable<SemVer> {
    override fun compareTo(other: SemVer): Int =
        compareValuesBy(this, other, SemVer::major, SemVer::minor, SemVer::patch)

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        // Parst "1.2.3", "v1.2.3", "1.2.3-beta1" (Suffix wird abgeschnitten).
        fun parse(raw: String): SemVer? {
            val cleaned = raw.trim().removePrefix("v").substringBefore('-').substringBefore('+')
            val parts = cleaned.split('.')
            if (parts.size !in 1..3) return null
            val nums = parts.map { it.toIntOrNull() ?: return null }
            return SemVer(
                major = nums.getOrElse(0) { 0 },
                minor = nums.getOrElse(1) { 0 },
                patch = nums.getOrElse(2) { 0 },
            )
        }
    }
}

// True, wenn `remote` echt neuer als `current` ist (Downgrade/Gleichstand => false).
fun isUpdateAvailable(
    current: String,
    remote: String,
): Boolean {
    val c = SemVer.parse(current) ?: return false
    val r = SemVer.parse(remote) ?: return false
    return r > c
}
