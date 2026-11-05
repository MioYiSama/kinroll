package net.mioyi.kinroll.ml.clip

enum class PhotoCategory(val prompt: String) {
    PEOPLE("a photo of people"),
    CHILD("a photo of a baby or a young child"),
    PET("a photo of a pet cat or dog"),
    FOOD("a photo of food or a meal"),
    LANDSCAPE("a photo of a natural landscape"),
    CITY("a photo of city streets or buildings"),
    NIGHT("a photo taken at night"),
    PLANT("a photo of flowers or plants"),
    VEHICLE("a photo of a car or other vehicle"),
    DOCUMENT("a photo of a document or a page of text"),
    RECEIPT("a photo of a receipt or an invoice"),
    ART("a drawing, painting or illustration"),
    OBJECT("a photo of an everyday object or product"),
    OTHER("");
}

data class CategoryResult(val category: PhotoCategory, val score: Float)

internal val accidentalPrompts = listOf(
    "a blurry photo",
    "an accidental photo of the floor",
    "a photo taken inside a pocket",
    "a very dark underexposed photo",
    "a clear sharp photo",
    "a well-composed photo",
)
