// ─────────────────────────────────────────────────────────
//  KÜTÜPHANE — Yazar / LibraryBook / BookQuote / BookReview
//  Firestore yapısı:
//    authors/{authorId}
//      books/ (sub) → {bookId} referansları
//    library_books/{bookId}
//      quotes/ (sub)
//      reviews/ (sub)
// ─────────────────────────────────────────────────────────

data class Author(
    val id          : String    = "",
    val name        : String    = "",
    val bio         : String    = "",
    val photoURL    : String    = "",
    val birthYear   : Int       = 0,
    val nationality : String    = "",
    val bookCount   : Int       = 0,
    val quoteCount  : Int       = 0,
    val reviewCount : Int       = 0,
    val followerCount: Int      = 0,
    val isFollowedByMe: Boolean = false,
)

data class LibraryBook(
    val id          : String    = "",
    val title       : String    = "",
    val authorId    : String    = "",
    val authorName  : String    = "",
    val coverImg    : String    = "",
    val genre       : String    = "",
    val publishYear : Int       = 0,
    val synopsis    : String    = "",
    val pageCount   : Int       = 0,
    val quoteCount  : Int       = 0,
    val reviewCount : Int       = 0,
    val avgRating   : Float     = 0f,
    val ts          : com.google.firebase.Timestamp? = null,
)

data class BookQuote(
    val id          : String    = "",
    val bookId      : String    = "",
    val authorId    : String    = "",
    val bookTitle   : String    = "",
    val authorName  : String    = "",
    val text        : String    = "",
    // Paylaşan kullanıcı
    val uid         : String    = "",
    val userDisplayName: String = "",
    val userPhotoURL: String    = "",
    val feedPostId  : String    = "",   // feed'deki post id (ters referans)
    val likesCount  : Int       = 0,
    val ts          : com.google.firebase.Timestamp? = null,
)

data class BookReview(
    val id          : String    = "",
    val bookId      : String    = "",
    val authorId    : String    = "",
    val bookTitle   : String    = "",
    val authorName  : String    = "",
    val text        : String    = "",
    val rating      : Float     = 0f,  // 1..5
    // Paylaşan kullanıcı
    val uid         : String    = "",
    val userDisplayName: String = "",
    val userPhotoURL: String    = "",
    val feedPostId  : String    = "",
    val ts          : com.google.firebase.Timestamp? = null,
)
