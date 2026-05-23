// ═══════════════════════════════════════════════════════════════════════════
//  NavHost.kt — YENİ ROTALAR (mevcut dosyaya eklenecek değişiklikler)
//
//  1. Screen sealed class'ına eklenecekler (mevcut object'lerin yanına):
//
//    object AuthorDetail  : Screen("author_detail/{authorId}") {
//        fun go(id: String) = "author_detail/$id"
//    }
//    object LibraryBookDetail : Screen("library_book/{bookId}") {
//        fun go(id: String) = "library_book/$id"
//    }
//
//  2. Import'lara eklenecekler:
//
//    import com.heftreng.app.ui.screens.quotes.AuthorDetailScreen
//    import com.heftreng.app.ui.screens.quotes.LibraryBookDetailScreen
//
//  3. NavHost composable içine eklenecek rotalar
//     (mevcut "author_quotes/{author}" composable'ının yanına):
//
//    composable("author_detail/{authorId}") { back ->
//        val authorId = back.arguments?.getString("authorId") ?: ""
//        AuthorDetailScreen(authorId = authorId, navController = navController)
//    }
//
//    composable("library_book/{bookId}") { back ->
//        val bookId = back.arguments?.getString("bookId") ?: ""
//        LibraryBookDetailScreen(bookId = bookId, navController = navController)
//    }
//
// ═══════════════════════════════════════════════════════════════════════════
//
//  NOT: Mevcut "author_quotes/{author}" ve "book_quotes/{book}" rotaları
//  kaldırılmaz — geriye dönük uyumluluk için korunur.
//
//  Yeni rotalar ESKI feed alıntı kartlarına tıklandığında da kullanılabilir:
//  Kitap adı yerine artık libraryBookId varsa library_book/{id}'ye,
//  authorId varsa author_detail/{id}'ye yönlendir.
//
// ═══════════════════════════════════════════════════════════════════════════

// ═══════════════════════════════════════════════════════════════════════════
//  TAM DEĞİŞTİRİLECEK BLOKLAR (copy-paste ready)
// ═══════════════════════════════════════════════════════════════════════════

// ─── sealed class Screen içine (mevcut Yazar objesinden sonra) ──────────────

/*
    object AuthorDetail      : Screen("author_detail/{authorId}") {
        fun go(id: String) = "author_detail/$id"
    }
    object LibraryBookDetail : Screen("library_book/{bookId}") {
        fun go(id: String) = "library_book/$id"
    }
*/

// ─── NavHost composable içi — mevcut author_quotes bloğunun altına ───────────

/*
    composable("author_detail/{authorId}") { back ->
        val authorId = back.arguments?.getString("authorId") ?: ""
        AuthorDetailScreen(authorId = authorId, navController = navController)
    }

    composable("library_book/{bookId}") { back ->
        val bookId = back.arguments?.getString("bookId") ?: ""
        LibraryBookDetailScreen(bookId = bookId, navController = navController)
    }
*/
